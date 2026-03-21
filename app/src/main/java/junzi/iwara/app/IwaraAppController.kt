package junzi.iwara.app

import android.content.Context
import android.net.Uri
import junzi.iwara.R
import junzi.iwara.data.IwaraApi
import junzi.iwara.data.IwaraDownloads
import junzi.iwara.data.IwaraRepository
import junzi.iwara.data.IwaraSessionStore
import junzi.iwara.model.AppRoute
import junzi.iwara.model.AppUiState
import junzi.iwara.model.CommentTargetType
import junzi.iwara.model.DownloadListItem
import junzi.iwara.model.DownloadStatus
import junzi.iwara.model.FeedSort
import junzi.iwara.model.PlayerUiState
import junzi.iwara.model.PlaylistUiState
import junzi.iwara.model.ProfileUiState
import junzi.iwara.model.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IwaraAppController(context: Context) {
    private val appContext = context.applicationContext
    private val repository = IwaraRepository(
        api = IwaraApi(),
        sessionStore = IwaraSessionStore(appContext),
    )
    private val downloads = IwaraDownloads(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadRefreshJob: Job? = null

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun bootstrap() {
        scope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { repository.bootstrapSession() }.getOrNull()
            }
            if (session == null) {
                _state.update {
                    it.copy(
                        bootstrapping = false,
                        route = AppRoute.Login,
                        session = null,
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    bootstrapping = false,
                    route = AppRoute.Feed,
                    session = session,
                    loginError = null,
                )
            }
            refreshCategories()
            loadFeed(FeedSort.Trending)
        }
    }

    fun login(email: String, password: String) {
        scope.launch {
            _state.update { it.copy(loginInFlight = true, loginError = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.login(email.trim(), password) }
            }
            result.onSuccess { session ->
                _state.update {
                    it.copy(
                        loginInFlight = false,
                        session = session,
                        route = AppRoute.Feed,
                        bootstrapping = false,
                    )
                }
                refreshCategories()
                loadFeed(FeedSort.Trending)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        loginInFlight = false,
                        loginError = throwable.message ?: appContext.getString(R.string.error_login_failed),
                    )
                }
            }
        }
    }

    fun logout() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.logout() }
            _state.value = AppUiState(bootstrapping = false)
        }
    }

    fun refreshCategories() {
        scope.launch {
            val categories = withContext(Dispatchers.IO) {
                runCatching { repository.fetchCategories(_state.value.session) }.getOrDefault(emptyList())
            }
            _state.update { current ->
                current.copy(feed = current.feed.copy(categories = categories))
            }
        }
    }

    fun loadFeed(
        sort: FeedSort = _state.value.feed.sort,
        tag: String? = _state.value.feed.selectedTag,
        page: Int = _state.value.feed.page,
    ) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Feed,
                    feed = it.feed.copy(
                        loading = true,
                        error = null,
                        sort = sort,
                        selectedTag = tag,
                        page = page,
                    ),
                )
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchVideos(sort, _state.value.session, tag, page) }
            }
            result.onSuccess { paged ->
                _state.update {
                    it.copy(
                        route = AppRoute.Feed,
                        feed = it.feed.copy(
                            loading = false,
                            videos = paged.items,
                            page = paged.page,
                            count = paged.count,
                            limit = paged.limit,
                            error = null,
                            sort = sort,
                            selectedTag = tag,
                        ),
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Feed,
                        feed = it.feed.copy(
                            loading = false,
                            error = throwable.message ?: appContext.getString(R.string.error_load_feed),
                        ),
                    )
                }
            }
        }
    }

    fun openTag(tag: String) {
        loadFeed(sort = _state.value.feed.sort, tag = tag, page = 0)
    }

    fun clearTag() {
        loadFeed(sort = _state.value.feed.sort, tag = null, page = 0)
    }

    fun openSearch() {
        _state.update { it.copy(route = AppRoute.Search) }
    }

    fun search(query: String, type: SearchType, page: Int = 0) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Search,
                    search = it.search.copy(
                        query = query,
                        type = type,
                        loading = true,
                        error = null,
                        page = page,
                    ),
                )
            }

            when (type) {
                SearchType.Videos -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.searchVideos(query, _state.value.session, page) }
                    }
                    result.onSuccess { paged ->
                        _state.update {
                            it.copy(
                                route = AppRoute.Search,
                                search = it.search.copy(
                                    loading = false,
                                    videoResults = paged.items,
                                    userResults = emptyList(),
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                ),
                            )
                        }
                    }.onFailure { throwable ->
                        _state.update {
                            it.copy(
                                route = AppRoute.Search,
                                search = it.search.copy(
                                    loading = false,
                                    error = throwable.message ?: appContext.getString(R.string.error_search_failed),
                                ),
                            )
                        }
                    }
                }

                SearchType.Users -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.searchUsers(query, _state.value.session, page) }
                    }
                    result.onSuccess { paged ->
                        _state.update {
                            it.copy(
                                route = AppRoute.Search,
                                search = it.search.copy(
                                    loading = false,
                                    videoResults = emptyList(),
                                    userResults = paged.items,
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                ),
                            )
                        }
                    }.onFailure { throwable ->
                        _state.update {
                            it.copy(
                                route = AppRoute.Search,
                                search = it.search.copy(
                                    loading = false,
                                    error = throwable.message ?: appContext.getString(R.string.error_search_failed),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun openProfile(username: String, page: Int = 0) {
        scope.launch {
            val currentProfile = _state.value.profile
            val currentDetail = currentProfile.detail
            val isPagingTransition =
                currentDetail != null &&
                currentProfile.username == username &&
                page != currentDetail.videoPage
            val requestStartedAt = System.currentTimeMillis()

            _state.update {
                it.copy(
                    route = AppRoute.Profile,
                    profile = it.profile.copy(
                        loading = true,
                        username = username,
                        detail = if (page == 0 || it.profile.username != username) null else it.profile.detail,
                        error = null,
                    ),
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchProfile(username, _state.value.session, page) }
            }
            val remainingDelay = if (isPagingTransition) {
                (350L - (System.currentTimeMillis() - requestStartedAt)).coerceAtLeast(0L)
            } else {
                0L
            }
            if (remainingDelay > 0L) {
                delay(remainingDelay)
            }
            result.onSuccess { detail ->
                _state.update {
                    it.copy(
                        route = AppRoute.Profile,
                        profile = it.profile.copy(
                            loading = false,
                            username = username,
                            detail = detail,
                            error = null,
                        ),
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Profile,
                        profile = it.profile.copy(
                            loading = false,
                            username = username,
                            error = throwable.message ?: appContext.getString(R.string.error_load_profile),
                        ),
                    )
                }
            }
        }
    }
    fun openOwnProfile(page: Int = 0) {
        val username = _state.value.session?.user?.username ?: return
        openProfile(username, page)
    }

    fun loadOwnPlaylists(onResult: (List<junzi.iwara.model.PlaylistSummary>, String?) -> Unit) {
        val session = _state.value.session ?: return onResult(emptyList(), appContext.getString(R.string.error_login_failed))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchOwnPlaylists(session) }
            }
            result.onSuccess { onResult(it, null) }
                .onFailure { onResult(emptyList(), it.message ?: appContext.getString(R.string.error_load_profile)) }
        }
    }

    fun createPlaylist(title: String, onResult: (junzi.iwara.model.PlaylistSummary?, String?) -> Unit) {
        val session = _state.value.session ?: return onResult(null, appContext.getString(R.string.error_login_failed))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.createPlaylist(title, session) }
            }
            result.onSuccess {
                refreshOwnProfileIfVisible()
                onResult(it, null)
            }.onFailure {
                onResult(null, it.message ?: appContext.getString(R.string.error_load_profile))
            }
        }
    }

    fun addVideoToPlaylist(playlistId: String, videoId: String, onResult: (String?) -> Unit) {
        val session = _state.value.session ?: return onResult(appContext.getString(R.string.error_login_failed))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.addVideoToPlaylist(playlistId, videoId, session) }
            }
            result.onSuccess {
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_load_profile))
            }
        }
    }

    fun removeVideoFromPlaylist(playlistId: String, videoId: String, onResult: (String?) -> Unit) {
        val session = _state.value.session ?: return onResult(appContext.getString(R.string.error_login_failed))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.removeVideoFromPlaylist(playlistId, videoId, session) }
            }
            result.onSuccess {
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_load_profile))
            }
        }
    }

    fun deletePlaylist(playlistId: String, onResult: (String?) -> Unit) {
        val session = _state.value.session ?: return onResult(appContext.getString(R.string.error_login_failed))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.deletePlaylist(playlistId, session) }
            }
            result.onSuccess {
                refreshOwnProfileIfVisible()
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_load_profile))
            }
        }
    }

    fun openPlaylist(playlistId: String, page: Int = 0) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Playlist,
                    playlist = PlaylistUiState(loading = true),
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchPlaylistDetail(playlistId, _state.value.session, page) }
            }
            result.onSuccess { detail ->
                _state.update {
                    it.copy(
                        route = AppRoute.Playlist,
                        playlist = PlaylistUiState(loading = false, detail = detail),
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Playlist,
                        playlist = PlaylistUiState(
                            loading = false,
                            error = throwable.message ?: appContext.getString(R.string.error_load_profile),
                        ),
                    )
                }
            }
        }
    }

    fun openVideo(videoId: String) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Player,
                    player = PlayerUiState(loading = true),
                )
            }
            val detailResult = withContext(Dispatchers.IO) {
                runCatching { repository.fetchVideoDetail(videoId, _state.value.session) }
            }
            detailResult.onSuccess { detail ->
                _state.update {
                    it.copy(
                        route = AppRoute.Player,
                        player = it.player.copy(
                            loading = false,
                            detail = detail,
                            error = null,
                            commentsLoading = true,
                            comments = emptyList(),
                        ),
                    )
                }
                loadVideoComments(videoId)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Player,
                        player = PlayerUiState(
                            loading = false,
                            error = throwable.message ?: appContext.getString(R.string.error_load_video),
                        ),
                    )
                }
            }
        }
    }

    fun loadVideoComments(videoId: String? = null) {
        val resolvedVideoId = videoId ?: _state.value.player.detail?.id ?: return
        scope.launch {
            _state.update { it.copy(player = it.player.copy(commentsLoading = true, commentError = null)) }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchVideoComments(resolvedVideoId, _state.value.session) }
            }
            result.onSuccess { comments ->
                _state.update {
                    it.copy(player = it.player.copy(commentsLoading = false, comments = comments))
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        player = it.player.copy(
                            commentsLoading = false,
                            commentError = throwable.message ?: appContext.getString(R.string.error_load_comments),
                        ),
                    )
                }
            }
        }
    }

    fun submitVideoComment(text: String) {
        val videoId = _state.value.player.detail?.id ?: return
        submitComment(CommentTargetType.Video, videoId, text) {
            loadVideoComments(videoId)
        }
    }

    fun submitProfileComment(text: String) {
        val profileId = _state.value.profile.detail?.user?.id ?: return
        submitComment(CommentTargetType.Profile, profileId, text) {
            val username = _state.value.profile.username
            if (username != null) {
                openProfile(username, _state.value.profile.detail?.videoPage ?: 0)
            }
        }
    }

    private fun submitComment(
        targetType: CommentTargetType,
        targetId: String,
        text: String,
        onSuccess: () -> Unit,
    ) {
        val session = _state.value.session ?: return
        val normalized = text.trim()
        if (normalized.isBlank()) return

        scope.launch {
            if (targetType == CommentTargetType.Video) {
                _state.update { it.copy(player = it.player.copy(commentSubmitting = true, commentError = null)) }
            } else {
                _state.update { it.copy(profile = it.profile.copy(commentSubmitting = true, commentError = null)) }
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { repository.postComment(targetType, targetId, normalized, session) }
            }
            result.onSuccess {
                if (targetType == CommentTargetType.Video) {
                    _state.update { it.copy(player = it.player.copy(commentSubmitting = false, commentError = null)) }
                } else {
                    _state.update { it.copy(profile = it.profile.copy(commentSubmitting = false, commentError = null)) }
                }
                onSuccess()
            }.onFailure { throwable ->
                if (targetType == CommentTargetType.Video) {
                    _state.update {
                        it.copy(
                            player = it.player.copy(
                                commentSubmitting = false,
                                commentError = throwable.message ?: appContext.getString(R.string.error_post_comment),
                            ),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            profile = it.profile.copy(
                                commentSubmitting = false,
                                commentError = throwable.message ?: appContext.getString(R.string.error_post_comment),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun closePlayer() {
        val targetRoute = if (_state.value.downloads.activeItemId != null) AppRoute.Downloads else AppRoute.Feed
        _state.update {
            it.copy(
                route = targetRoute,
                player = PlayerUiState(),
                downloads = it.downloads.copy(activeItemId = null),
            )
        }
        if (targetRoute == AppRoute.Downloads) {
            scope.launch {
                refreshDownloadsInternal(openRoute = false, showLoading = false)
            }
        }
    }

    private fun refreshOwnProfileIfVisible() {
        val session = _state.value.session ?: return
        val profile = _state.value.profile.detail ?: return
        if (profile.user.username == session.user.username) {
            openOwnProfile(profile.videoPage)
        }
    }

    fun closePlaylist() {
        _state.update {
            val targetRoute = if (it.profile.detail != null) AppRoute.Profile else AppRoute.Feed
            it.copy(route = targetRoute, playlist = PlaylistUiState())
        }
    }

    fun openDownloads() {
        scope.launch {
            _state.update { it.copy(route = AppRoute.Downloads, downloads = it.downloads.copy(error = null)) }
            refreshDownloadsInternal(openRoute = true, showLoading = true)
        }
    }

    fun refreshDownloads() {
        scope.launch {
            refreshDownloadsInternal(openRoute = false, showLoading = true)
        }
    }

    private suspend fun refreshDownloadsInternal(openRoute: Boolean, showLoading: Boolean) {
        if (showLoading) {
            _state.update {
                it.copy(
                    route = if (openRoute) AppRoute.Downloads else it.route,
                    downloads = it.downloads.copy(loading = true, error = null),
                )
            }
        }
        val result = withContext(Dispatchers.IO) {
            runCatching { downloads.list() }
        }
        result.onSuccess { items ->
            _state.update {
                it.copy(
                    route = if (openRoute) AppRoute.Downloads else it.route,
                    downloads = it.downloads.copy(loading = false, items = items, error = null),
                )
            }
            maybeStartDownloadRefreshLoop()
        }.onFailure { throwable ->
            _state.update {
                it.copy(
                    route = if (openRoute) AppRoute.Downloads else it.route,
                    downloads = it.downloads.copy(
                        loading = false,
                        error = throwable.message ?: appContext.getString(R.string.error_download_failed),
                    ),
                )
            }
        }
    }

    private fun maybeStartDownloadRefreshLoop() {
        if (_state.value.route != AppRoute.Downloads) return
        if (_state.value.downloads.items.none(::isDownloadActive)) return
        if (downloadRefreshJob?.isActive == true) return

        downloadRefreshJob = scope.launch {
            while (isActive && _state.value.route == AppRoute.Downloads && _state.value.downloads.items.any(::isDownloadActive)) {
                delay(1000)
                if (_state.value.route != AppRoute.Downloads) break
                refreshDownloadsInternal(openRoute = false, showLoading = false)
            }
            downloadRefreshJob = null
        }
    }

    private fun isDownloadActive(item: DownloadListItem): Boolean = when (item.status) {
        DownloadStatus.Pending,
        DownloadStatus.Running,
        DownloadStatus.Paused
        -> true
        else -> false
    }

    fun closeDownloads() {
        _state.update {
            val targetRoute = when {
                it.downloads.activeItemId != null -> AppRoute.Downloads
                it.player.detail != null -> AppRoute.Player
                it.playlist.detail != null -> AppRoute.Playlist
                it.profile.detail != null -> AppRoute.Profile
                else -> AppRoute.Feed
            }
            it.copy(route = targetRoute)
        }
    }

    fun openDownloadedVideo(downloadId: Long, onResult: (String?) -> Unit) {
        val item = _state.value.downloads.items.firstOrNull { it.downloadId == downloadId }
            ?: return onResult(appContext.getString(R.string.error_download_failed))
        val localUri = item.localUri ?: return onResult(appContext.getString(R.string.message_download_not_ready))
        if (item.status != DownloadStatus.Successful) {
            return onResult(appContext.getString(R.string.message_download_not_ready))
        }
        _state.update {
            it.copy(
                route = AppRoute.Player,
                downloads = it.downloads.copy(activeItemId = downloadId),
                player = PlayerUiState(
                    loading = false,
                    detail = junzi.iwara.model.VideoDetail(
                        id = item.videoId,
                        title = item.title,
                        authorName = "",
                        authorUsername = "",
                        description = "",
                        rating = "general",
                        views = 0,
                        likes = 0,
                        durationSeconds = null,
                        posterUrl = item.thumbnailUrl,
                        fileUrl = null,
                        tags = emptyList(),
                        variants = listOf(
                            junzi.iwara.model.VideoVariant(
                                id = item.downloadId.toString(),
                                name = item.qualityLabel,
                                type = "download",
                                viewUrl = localUri,
                                downloadUrl = localUri,
                            ),
                        ),
                        selectedVariantName = item.qualityLabel,
                    ),
                    comments = emptyList(),
                    commentsLoading = false,
                ),
            )
        }
        onResult(null)
    }

    fun downloadVideo(
        detail: junzi.iwara.model.VideoDetail,
        variant: junzi.iwara.model.VideoVariant,
        onResult: (String?) -> Unit,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { downloads.enqueue(detail, variant) }
            }
            result.onSuccess {
                refreshDownloadsInternal(openRoute = false, showLoading = false)
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_download_failed))
            }
        }
    }

    fun retryDownload(downloadId: Long, onResult: (String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { downloads.retry(downloadId) }
            }
            result.onSuccess {
                refreshDownloadsInternal(openRoute = false, showLoading = false)
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_download_failed))
            }
        }
    }

    fun deleteDownload(downloadId: Long, onResult: (String?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { downloads.delete(downloadId) }
            }
            result.onSuccess {
                refreshDownloadsInternal(openRoute = false, showLoading = false)
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_download_failed))
            }
        }
    }

    fun selectVariant(name: String) {
        _state.update { current ->
            val detail = current.player.detail ?: return@update current
            current.copy(
                player = current.player.copy(
                    detail = detail.copy(selectedVariantName = name),
                ),
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }
}










