package junzi.iwara.data

import junzi.iwara.model.CommentItem
import junzi.iwara.model.CommentTargetType
import junzi.iwara.model.FeedSort
import junzi.iwara.model.IwaraUser
import junzi.iwara.model.ImageSummary
import junzi.iwara.model.PlaylistDetail
import junzi.iwara.model.PlaylistSummary
import junzi.iwara.model.PagedResult
import junzi.iwara.model.ProfileDetail
import junzi.iwara.model.SearchType
import junzi.iwara.model.SessionInfo
import junzi.iwara.model.VideoDetail
import junzi.iwara.model.VideoSummary
import junzi.iwara.model.VideoVariant
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class IwaraRepository(
    private val api: IwaraApi,
    private val sessionStore: IwaraSessionStore,
) {
    fun bootstrapSession(): SessionInfo? {
        val refreshToken = sessionStore.readRefreshToken() ?: return null
        val accessToken = sessionStore.readAccessToken()
        val user = parseUser(api.fetchCurrentUser(refreshToken).getJSONObject("user"))
        return SessionInfo(refreshToken = refreshToken, accessToken = accessToken, user = user)
    }

    fun login(email: String, password: String): SessionInfo {
        val refreshToken = api.login(email, password)
        val userPayload = api.fetchCurrentUser(refreshToken)
        val accessToken = api.fetchAccessToken(refreshToken)
        val user = parseUser(userPayload.getJSONObject("user"))
        sessionStore.save(refreshToken, accessToken)
        return SessionInfo(refreshToken = refreshToken, accessToken = accessToken, user = user)
    }

    fun logout() {
        sessionStore.clear()
    }

    fun fetchCategories(session: SessionInfo?): List<String> {
        val payload = api.fetchConfig(session?.refreshToken)
        val categories = payload.optJSONArray("categories") ?: JSONArray()
        return buildList {
            for (index in 0 until categories.length()) {
                add(categories.getJSONObject(index).optString("id"))
            }
        }
    }

    fun fetchVideos(
        sort: FeedSort,
        session: SessionInfo?,
        tag: String? = null,
        page: Int = 0,
    ): PagedResult<VideoSummary> {
        val params = mutableMapOf(
            "sort" to sort.apiValue,
            "limit" to "24",
            "rating" to "all",
            "page" to page.toString(),
        )
        tag?.let { params["tags"] = it }
        val payload = api.fetchVideosPage(params, session?.refreshToken)
        return PagedResult(
            items = parseVideoList(payload.optJSONArray("results") ?: JSONArray()),
            page = payload.optInt("page", page),
            count = payload.optInt("count"),
            limit = payload.optInt("limit", params["limit"]?.toIntOrNull() ?: 24),
        )
    }

    fun searchVideos(query: String, session: SessionInfo?, page: Int = 0): PagedResult<VideoSummary> {
        val payload = api.fetchSearchResults(query, SearchType.Videos.apiValue, page, session?.refreshToken)
        return PagedResult(
            items = parseVideoList(payload.optJSONArray("results") ?: JSONArray()),
            page = payload.optInt("page", page),
            count = payload.optInt("count"),
            limit = payload.optInt("limit", 32),
        )
    }

    fun searchUsers(query: String, session: SessionInfo?, page: Int = 0): PagedResult<IwaraUser> {
        val payload = api.fetchSearchResults(query, SearchType.Users.apiValue, page, session?.refreshToken)
        return PagedResult(
            items = parseUserList(payload.optJSONArray("results") ?: JSONArray()),
            page = payload.optInt("page", page),
            count = payload.optInt("count"),
            limit = payload.optInt("limit", 32),
        )
    }

    fun fetchVideoDetail(id: String, session: SessionInfo?): VideoDetail {
        val payload = api.fetchVideo(id, session?.refreshToken)
        runCatching { api.sendView(id, session?.refreshToken) }

        val fileUrl = payload.optStringOrNull("fileUrl")
        val variants = if (fileUrl != null) {
            parseVariants(api.fetchFileVariants(fileUrl, payload.optString("title"), id, session?.refreshToken))
        } else {
            emptyList()
        }
        val selectedVariant = selectDefaultVariant(variants)?.name

        return VideoDetail(
            id = payload.optString("id"),
            title = payload.optString("title"),
            authorName = payload.optJSONObject("user")?.optString("name").orEmpty(),
            authorUsername = payload.optJSONObject("user")?.optString("username").orEmpty(),
            description = payload.optStringOrNull("body").orEmpty(),
            rating = payload.optString("rating"),
            views = payload.optInt("numViews"),
            likes = payload.optInt("numLikes"),
            durationSeconds = payload.optJSONObject("file")?.optInt("duration"),
            posterUrl = buildPosterUrl(payload),
            fileUrl = fileUrl,
            tags = parseTagIds(payload.optJSONArray("tags")),
            variants = variants,
            selectedVariantName = selectedVariant,
        )
    }

    fun fetchVideoComments(id: String, session: SessionInfo?): List<CommentItem> =
        parseComments(api.fetchComments(CommentTargetType.Video.apiValue, id, 0, session?.refreshToken))

    fun fetchProfile(username: String, session: SessionInfo?): ProfileDetail {
        val ownSession = session?.takeIf { it.user.username == username }
        if (ownSession != null) {
            return fetchOwnProfile(ownSession)
        }

        val profilePayload = api.fetchProfile(username, session?.refreshToken)
        val user = parseUser(profilePayload.getJSONObject("user"))
        val videos = parseVideoList(
            api.fetchVideos(
                params = mapOf(
                    "rating" to "all",
                    "user" to user.id,
                    "limit" to "8",
                ),
                bearerToken = session?.refreshToken,
            ),
        )
        val images = parseImageList(
            api.fetchImages(
                params = mapOf(
                    "rating" to "all",
                    "user" to user.id,
                    "limit" to "8",
                ),
                bearerToken = session?.refreshToken,
            ),
        )
        val followers = parseUserList(api.fetchFollowers(user.id).optJSONArray("results") ?: JSONArray())
        val following = parseUserList(api.fetchFollowing(user.id).optJSONArray("results") ?: JSONArray())
        val comments = parseComments(api.fetchComments(CommentTargetType.Profile.apiValue, user.id, 0, session?.refreshToken))

        return ProfileDetail(
            user = user,
            body = profilePayload.optStringOrNull("body"),
            headerUrl = buildOriginalImageUrl(profilePayload.optJSONObject("header")),
            videos = videos,
            images = images,
            followers = followers,
            following = following,
            comments = comments,
        )
    }

    private fun fetchOwnProfile(session: SessionInfo): ProfileDetail {
        val profilePayload = api.fetchProfile(session.user.username, session.refreshToken)
        val user = parseUser(profilePayload.getJSONObject("user"))
        val videos = runCatching {
            withAccessTokenResult(session) { accessToken ->
                parseVideoList(
                    api.fetchUserContent(
                        userId = user.id,
                        type = "videos",
                        params = mapOf(
                            "limit" to "8",
                            "sort" to "date",
                        ),
                        bearerToken = accessToken,
                    ).optJSONArray("results") ?: JSONArray(),
                )
            }
        }.getOrElse {
            parseVideoList(
                api.fetchVideos(
                    params = mapOf(
                        "rating" to "all",
                        "user" to user.id,
                        "limit" to "8",
                    ),
                    bearerToken = session.refreshToken,
                ),
            )
        }
        val images = runCatching {
            withAccessTokenResult(session) { accessToken ->
                parseImageList(
                    api.fetchUserContent(
                        userId = user.id,
                        type = "images",
                        params = mapOf(
                            "limit" to "8",
                            "sort" to "date",
                        ),
                        bearerToken = accessToken,
                    ).optJSONArray("results") ?: JSONArray(),
                )
            }
        }.getOrElse {
            parseImageList(
                api.fetchImages(
                    params = mapOf(
                        "rating" to "all",
                        "user" to user.id,
                        "limit" to "8",
                    ),
                    bearerToken = session.refreshToken,
                ),
            )
        }
        val followers = parseUserList(api.fetchFollowers(user.id).optJSONArray("results") ?: JSONArray())
        val following = parseUserList(api.fetchFollowing(user.id).optJSONArray("results") ?: JSONArray())
        val comments = parseComments(api.fetchComments(CommentTargetType.Profile.apiValue, user.id, 0, session.refreshToken))
        val playlists = runCatching {
            withAccessTokenResult(session) { accessToken ->
                parsePlaylistList(
                    api.fetchPlaylists(
                        params = mapOf(
                            "limit" to "8",
                            "user" to user.id,
                        ),
                        bearerToken = accessToken,
                    ).optJSONArray("results") ?: JSONArray(),
                )
            }
        }.getOrDefault(emptyList())
        return ProfileDetail(
            user = user,
            body = profilePayload.optStringOrNull("body"),
            headerUrl = buildOriginalImageUrl(profilePayload.optJSONObject("header")),
            videos = videos,
            images = images,
            followers = followers,
            following = following,
            comments = comments,
            isOwnProfile = true,
            playlists = playlists,
        )
    }

    fun fetchPlaylistDetail(id: String, session: SessionInfo?, page: Int = 0): PlaylistDetail {
        val payload = api.fetchPlaylist(
            id = id,
            params = mapOf("page" to page.toString()),
            bearerToken = session?.refreshToken,
        )
        val playlistJson = payload.getJSONObject("playlist")
        val playlist = parsePlaylistSummary(playlistJson)
        val videos = parseVideoList(payload.optJSONArray("results") ?: JSONArray())
        return PlaylistDetail(
            playlist = playlist,
            videos = videos,
            page = payload.optInt("page"),
            count = payload.optInt("count"),
            limit = payload.optInt("limit", 32),
        )
    }

    fun fetchOwnPlaylists(session: SessionInfo): List<PlaylistSummary> =
        withAccessTokenResult(session) { accessToken ->
            parsePlaylistList(
                api.fetchPlaylists(
                    params = mapOf(
                        "limit" to "32",
                        "user" to session.user.id,
                    ),
                    bearerToken = accessToken,
                ).optJSONArray("results") ?: JSONArray(),
            )
        }

    fun createPlaylist(title: String, session: SessionInfo): PlaylistSummary =
        withAccessTokenResult(session) { accessToken ->
            parsePlaylistSummary(api.createPlaylist(title, accessToken))
        }

    fun addVideoToPlaylist(playlistId: String, videoId: String, session: SessionInfo) {
        withAccessTokenResult(session) { accessToken ->
            api.addToPlaylist(playlistId, videoId, accessToken)
        }
    }

    fun removeVideoFromPlaylist(playlistId: String, videoId: String, session: SessionInfo) {
        withAccessTokenResult(session) { accessToken ->
            api.removeFromPlaylist(playlistId, videoId, accessToken)
        }
    }
    fun deletePlaylist(playlistId: String, session: SessionInfo) {
        withAccessTokenResult(session) { accessToken ->
            api.deletePlaylist(playlistId, accessToken)
        }
    }

    fun postComment(
        targetType: CommentTargetType,
        targetId: String,
        body: String,
        session: SessionInfo,
    ) {
        withAccessTokenResult(session) { accessToken ->
            api.createComment(targetType.apiValue, targetId, body, accessToken)
        }
    }

    private fun <T> withAccessTokenResult(
        session: SessionInfo,
        block: (String) -> T,
    ): T {
        val token = accessToken(session)
        return try {
            block(token)
        } catch (error: IwaraApiException) {
            if (error.statusCode != 401 && error.statusCode != 403) {
                throw error
            }
            block(accessToken(session, forceRefresh = true))
        }
    }

    private fun accessToken(
        session: SessionInfo,
        forceRefresh: Boolean = false,
    ): String {
        if (!forceRefresh) {
            val cachedToken = sessionStore.readAccessToken() ?: session.accessToken
            if (!cachedToken.isNullOrBlank() && !isExpiredAccessToken(cachedToken)) {
                return cachedToken
            }
        }
        val refreshedToken = api.fetchAccessToken(session.refreshToken)
        sessionStore.save(session.refreshToken, refreshedToken)
        return refreshedToken
    }

    private fun isExpiredAccessToken(token: String): Boolean {
        val payload = decodeJwtPayload(token) ?: return true
        val expiresAt = payload.optLong("exp", 0L)
        if (expiresAt <= 0L) {
            return true
        }
        val nowSeconds = System.currentTimeMillis() / 1000
        return expiresAt <= nowSeconds + 60
    }

    private fun decodeJwtPayload(token: String): JSONObject? =
        runCatching {
            val parts = token.split('.')
            require(parts.size >= 2)
            val paddedPayload = parts[1].padEnd(parts[1].length + (4 - parts[1].length % 4) % 4, '=')
            val decodedPayload = Base64.getUrlDecoder().decode(paddedPayload)
            JSONObject(String(decodedPayload, Charsets.UTF_8))
        }.getOrNull()

    private fun parseUser(json: JSONObject): IwaraUser =
        IwaraUser(
            id = json.optString("id"),
            name = json.optString("name"),
            username = json.optString("username"),
            email = json.optStringOrNull("email"),
            avatarUrl = buildAvatarUrl(json.optJSONObject("avatar")),
        )

    private fun parseUserList(payload: JSONArray): List<IwaraUser> = buildList {
        for (index in 0 until payload.length()) {
            add(parseUser(payload.getJSONObject(index)))
        }
    }

    private fun parseVideoList(payload: JSONArray): List<VideoSummary> = buildList {
        for (index in 0 until payload.length()) {
            add(parseVideoSummary(payload.getJSONObject(index)))
        }
    }

    private fun parseVideoSummary(json: JSONObject): VideoSummary {
        val file = json.optJSONObject("file")
        val thumbnailIndex = json.optInt("thumbnail", 0)
        return VideoSummary(
            id = json.optString("id"),
            title = json.optString("title"),
            authorName = json.optJSONObject("user")?.optString("name").orEmpty(),
            authorUsername = json.optJSONObject("user")?.optString("username").orEmpty(),
            views = json.optInt("numViews"),
            likes = json.optInt("numLikes"),
            durationSeconds = file?.optInt("duration"),
            thumbnailUrl = buildVideoThumbnailUrl(file, json.optJSONObject("customThumbnail"), thumbnailIndex),
            rating = json.optString("rating"),
            tags = parseTagIds(json.optJSONArray("tags")),
        )
    }

    private fun parseImageList(payload: JSONArray): List<ImageSummary> = buildList {
        for (index in 0 until payload.length()) {
            val json = payload.getJSONObject(index)
            val thumbnail = json.optJSONObject("thumbnail")
            add(
                ImageSummary(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    authorName = json.optJSONObject("user")?.optString("name").orEmpty(),
                    authorUsername = json.optJSONObject("user")?.optString("username").orEmpty(),
                    views = json.optInt("numViews"),
                    likes = json.optInt("numLikes"),
                    thumbnailUrl = buildImageThumbnailUrl(thumbnail),
                ),
            )
        }
    }

    private fun parsePlaylistList(payload: JSONArray): List<PlaylistSummary> = buildList {
        for (index in 0 until payload.length()) {
            add(parsePlaylistSummary(payload.getJSONObject(index)))
        }
    }

    private fun parsePlaylistSummary(json: JSONObject): PlaylistSummary =
        PlaylistSummary(
            id = json.optString("id"),
            title = json.optString("title"),
            authorName = json.optJSONObject("user")?.optString("name").orEmpty(),
            authorUsername = json.optJSONObject("user")?.optString("username").orEmpty(),
            numVideos = json.optInt("numVideos"),
            thumbnailUrl = buildPlaylistThumbnailUrl(json.optJSONObject("thumbnail")),
        )

    private fun parseComments(payload: JSONObject): List<CommentItem> {
        val results = payload.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (index in 0 until results.length()) {
                val json = results.getJSONObject(index)
                val user = json.optJSONObject("user")
                add(
                    CommentItem(
                        id = json.optString("id"),
                        body = json.optString("body"),
                        authorName = user?.optString("name").orEmpty(),
                        authorUsername = user?.optString("username").orEmpty(),
                        authorAvatarUrl = buildAvatarUrl(user?.optJSONObject("avatar")),
                        createdAt = json.optString("createdAt"),
                        numReplies = json.optInt("numReplies"),
                    ),
                )
            }
        }
    }

    private fun parseVariants(payload: JSONArray): List<VideoVariant> {
        val variants = buildList {
            for (index in 0 until payload.length()) {
                val item = payload.getJSONObject(index)
                val src = item.getJSONObject("src")
                add(
                    VideoVariant(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        type = item.optString("type"),
                        viewUrl = normalizeUrl(src.optString("view")),
                        downloadUrl = normalizeUrl(src.optString("download")),
                    ),
                )
            }
        }
        return variants.sortedWith(compareByDescending<VideoVariant> { qualityScore(it) }.thenBy { it.name })
    }

    private fun selectDefaultVariant(variants: List<VideoVariant>): VideoVariant? =
        variants.firstOrNull { it.name == "540" }
            ?: variants.firstOrNull { it.name.equals("Source", ignoreCase = true) }
            ?: variants.firstOrNull { !it.name.equals("preview", ignoreCase = true) }
            ?: variants.firstOrNull()

    private fun qualityScore(variant: VideoVariant): Int = when {
        variant.name.equals("Source", ignoreCase = true) -> 10_000
        variant.name.equals("preview", ignoreCase = true) -> -1
        else -> variant.name.toIntOrNull() ?: 0
    }

    private fun parseTagIds(payload: JSONArray?): List<String> = buildList {
        if (payload == null) return@buildList
        for (index in 0 until payload.length()) {
            add(payload.getJSONObject(index).optString("id"))
        }
    }

    private fun buildVideoThumbnailUrl(
        file: JSONObject?,
        customThumbnail: JSONObject?,
        thumbnailIndex: Int,
    ): String? {
        file?.optStringOrNull("id")?.let { fileId ->
            return "https://i.iwara.tv/image/thumbnail/$fileId/thumbnail-${thumbnailIndex.toThumbnailIndex()}.jpg"
        }
        customThumbnail?.optStringOrNull("id")?.let { customId ->
            return "https://i.iwara.tv/image/thumbnail/$customId/$customId.jpg"
        }
        return null
    }

    private fun buildImageThumbnailUrl(thumbnail: JSONObject?): String? {
        val thumbnailId = thumbnail?.optStringOrNull("id") ?: return null
        val thumbnailName = thumbnail.optStringOrNull("name") ?: return null
        return "https://i.iwara.tv/image/thumbnail/$thumbnailId/$thumbnailName"
    }

    private fun buildPlaylistThumbnailUrl(thumbnail: JSONObject?): String? {
        val file = thumbnail?.optJSONObject("file") ?: return null
        val fileId = file.optStringOrNull("id") ?: return null
        val thumbnailIndex = thumbnail.optInt("thumbnail", 0)
        return "https://i.iwara.tv/image/thumbnail/$fileId/thumbnail-${thumbnailIndex.toThumbnailIndex()}.jpg"
    }

    private fun buildPosterUrl(payload: JSONObject): String? {
        val fileId = payload.optJSONObject("file")?.optStringOrNull("id") ?: return null
        val thumbnailIndex = payload.optInt("thumbnail", 0)
        return "https://i.iwara.tv/image/original/$fileId/thumbnail-${thumbnailIndex.toThumbnailIndex()}.jpg"
    }

    private fun buildAvatarUrl(avatar: JSONObject?): String {
        val avatarId = avatar?.optStringOrNull("id")
        val avatarName = avatar?.optStringOrNull("name")
        if (avatarId.isNullOrBlank() || avatarName.isNullOrBlank()) {
            return DEFAULT_AVATAR_URL
        }
        return "https://i.iwara.tv/image/avatar/$avatarId/$avatarName"
    }

    private fun buildOriginalImageUrl(file: JSONObject?): String {
        val id = file?.optStringOrNull("id")
        val name = file?.optStringOrNull("name")
        if (id.isNullOrBlank() || name.isNullOrBlank()) {
            return DEFAULT_BACKGROUND_URL
        }
        return "https://i.iwara.tv/image/original/$id/$name"
    }

    private fun normalizeUrl(url: String): String =
        if (url.startsWith("//")) {
            "https:$url"
        } else {
            url
        }

    private fun Int.toThumbnailIndex(): String = toString().padStart(2, '0')

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val DEFAULT_AVATAR_URL = "https://www.iwara.tv/images/default-avatar.jpg"
        private const val DEFAULT_BACKGROUND_URL = "https://www.iwara.tv/images/default-background.jpg"
    }
}






