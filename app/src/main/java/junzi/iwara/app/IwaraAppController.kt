package junzi.iwara.app

import android.content.Context
import junzi.iwara.R
import junzi.iwara.data.IwaraApi
import junzi.iwara.data.IwaraDownloads
import junzi.iwara.data.IwaraRepository
import junzi.iwara.data.IwaraSessionStore
import junzi.iwara.model.AppRoute
import junzi.iwara.model.AppUiState
import junzi.iwara.model.CommentTargetType
import junzi.iwara.model.ContentType
import junzi.iwara.model.DownloadListItem
import junzi.iwara.model.DownloadStatus
import junzi.iwara.model.FeedSort
import junzi.iwara.model.FeedUiState
import junzi.iwara.model.IwaraSite
import junzi.iwara.model.ImageViewerUiState
import junzi.iwara.model.PlayerUiState
import junzi.iwara.model.PlaylistUiState
import junzi.iwara.model.SearchUiState
import junzi.iwara.model.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IwaraAppController(context: Context) {
    private companion object {
        const val KEEP_CURRENT_TAG = "__KEEP_CURRENT_TAG__"
    }
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

    private fun routeForSite(site: IwaraSite): AppRoute = if (site == IwaraSite.Ai) AppRoute.Ai else AppRoute.Feed

    private fun feedStateFor(site: IwaraSite, state: AppUiState = _state.value): FeedUiState =
        if (site == IwaraSite.Ai) state.aiFeed else state.feed

    private fun updateFeedState(
        state: AppUiState,
        site: IwaraSite,
        transform: (FeedUiState) -> FeedUiState,
    ): AppUiState = if (site == IwaraSite.Ai) {
        state.copy(aiFeed = transform(state.aiFeed))
    } else {
        state.copy(feed = transform(state.feed))
    }

    private fun searchStateFor(site: IwaraSite, state: AppUiState = _state.value): SearchUiState =
        if (site == IwaraSite.Ai) state.aiSearch else state.search

    private fun updateSearchState(
        state: AppUiState,
        site: IwaraSite,
        transform: (SearchUiState) -> SearchUiState,
    ): AppUiState = if (site == IwaraSite.Ai) {
        state.copy(aiSearch = transform(state.aiSearch))
    } else {
        state.copy(search = transform(state.search))
    }

    private fun currentSite(state: AppUiState = _state.value): IwaraSite = when (state.route) {
        AppRoute.Ai -> IwaraSite.Ai
        AppRoute.Feed -> IwaraSite.Tv
        AppRoute.Search -> state.activeSearchSite
        AppRoute.Profile -> state.profile.site
        AppRoute.Player -> state.player.site
        AppRoute.ImageViewer -> state.imageViewer.site
        AppRoute.Playlist -> state.playlist.site
        AppRoute.Downloads -> state.profile.detail?.let { state.profile.site } ?: state.activeSearchSite
        else -> state.feed.site
    }

    fun bootstrap() {
        scope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(IwaraSite.Tv) { bootstrapSession() } }.getOrNull()
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
            refreshCategories(IwaraSite.Tv)
            loadFeed(sort = FeedSort.Trending, contentType = ContentType.Videos, page = 0, site = IwaraSite.Tv)
        }
    }

    fun login(email: String, password: String) {
        scope.launch {
            _state.update { it.copy(loginInFlight = true, loginError = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(IwaraSite.Tv) { login(email.trim(), password) } }
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
                refreshCategories(IwaraSite.Tv)
                loadFeed(sort = FeedSort.Trending, contentType = ContentType.Videos, page = 0, site = IwaraSite.Tv)
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

    fun refreshCategories(site: IwaraSite = currentSite()) {
        scope.launch {
            val categories = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchCategories(_state.value.session) } }.getOrDefault(emptyList())
            }
            _state.update { current ->
                updateFeedState(current, site) { feed ->
                    feed.copy(site = site, categories = categories)
                }
            }
        }
    }

    fun loadFeed(
        sort: FeedSort? = null,
        tag: String? = KEEP_CURRENT_TAG,
        page: Int? = null,
        contentType: ContentType? = null,
        site: IwaraSite = currentSite(),
    ) {
        scope.launch {
            val currentFeed = feedStateFor(site)
            val resolvedSort = sort ?: currentFeed.sort
            val resolvedTag = if (tag == KEEP_CURRENT_TAG) currentFeed.selectedTag else tag
            val resolvedPage = page ?: currentFeed.page
            val resolvedContentType = contentType ?: currentFeed.contentType

            _state.update { current ->
                updateFeedState(current, site) { feed ->
                    feed.copy(
                        site = site,
                        loading = true,
                        error = null,
                        sort = resolvedSort,
                        selectedTag = resolvedTag,
                        page = resolvedPage,
                        contentType = resolvedContentType,
                    )
                }.copy(route = routeForSite(site))
            }

            when (resolvedContentType) {
                ContentType.Videos -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.withSite(site) { fetchVideos(resolvedSort, _state.value.session, resolvedTag, resolvedPage) } }
                    }
                    result.onSuccess { paged ->
                        _state.update { current ->
                            updateFeedState(current, site) { feed ->
                                feed.copy(
                                    site = site,
                                    loading = false,
                                    videos = paged.items,
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                    error = null,
                                    sort = resolvedSort,
                                    selectedTag = resolvedTag,
                                    contentType = resolvedContentType,
                                )
                            }.copy(route = routeForSite(site))
                        }
                    }.onFailure { throwable ->
                        feedFailure(throwable, site)
                    }
                }

                ContentType.Images -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.withSite(site) { fetchImages(resolvedSort, _state.value.session, resolvedTag, resolvedPage) } }
                    }
                    result.onSuccess { paged ->
                        _state.update { current ->
                            updateFeedState(current, site) { feed ->
                                feed.copy(
                                    site = site,
                                    loading = false,
                                    images = paged.items,
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                    error = null,
                                    sort = resolvedSort,
                                    selectedTag = resolvedTag,
                                    contentType = resolvedContentType,
                                )
                            }.copy(route = routeForSite(site))
                        }
                    }.onFailure { throwable ->
                        feedFailure(throwable, site)
                    }
                }
            }
        }
    }

    private fun feedFailure(throwable: Throwable, site: IwaraSite) {
        _state.update { current ->
            updateFeedState(current, site) { feed ->
                feed.copy(
                    site = site,
                    loading = false,
                    error = throwable.message ?: appContext.getString(R.string.error_load_feed),
                )
            }.copy(route = routeForSite(site))
        }
    }

    fun openFeedContentType(contentType: ContentType) {
        val site = currentSite()
        val feed = feedStateFor(site)
        loadFeed(sort = feed.sort, tag = feed.selectedTag, page = 0, contentType = contentType, site = site)
    }

    fun openTag(tag: String) {
        val site = currentSite()
        val feed = feedStateFor(site)
        loadFeed(sort = feed.sort, tag = tag, page = 0, contentType = feed.contentType, site = site)
    }

    fun clearTag() {
        val site = currentSite()
        val feed = feedStateFor(site)
        loadFeed(sort = feed.sort, tag = null, page = 0, contentType = feed.contentType, site = site)
    }

    private fun openSiteFeed(site: IwaraSite) {
        val currentFeed = feedStateFor(site)
        val hasCachedContent = currentFeed.loading ||
            currentFeed.videos.isNotEmpty() ||
            currentFeed.images.isNotEmpty() ||
            currentFeed.categories.isNotEmpty() ||
            currentFeed.error != null
        if (hasCachedContent) {
            _state.update { current ->
                updateFeedState(current, site) { it.copy(site = site) }.copy(route = routeForSite(site))
            }
            return
        }
        refreshCategories(site)
        loadFeed(
            sort = currentFeed.sort,
            tag = currentFeed.selectedTag,
            page = 0,
            contentType = currentFeed.contentType,
            site = site,
        )
    }

    fun openFeed() {
        openSiteFeed(IwaraSite.Tv)
    }

    fun openAi() {
        openSiteFeed(IwaraSite.Ai)
    }

    fun openSearch() {
        val site = currentSite()
        _state.update { current ->
            val existing = searchStateFor(site, current)
            val nextSearch = existing.copy(site = site)
            updateSearchState(current, site) { nextSearch }.copy(route = AppRoute.Search, activeSearchSite = site)
        }
    }

    fun search(query: String, type: SearchType, page: Int = 0, site: IwaraSite = _state.value.activeSearchSite) {
        scope.launch {
            _state.update { current ->
                updateSearchState(current, site) { search ->
                    search.copy(
                        site = site,
                        query = query,
                        type = type,
                        loading = true,
                        error = null,
                        page = page,
                    )
                }.copy(route = AppRoute.Search, activeSearchSite = site)
            }

            when (type) {
                SearchType.Videos -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.withSite(site) { searchVideos(query, _state.value.session, page) } }
                    }
                    result.onSuccess { paged ->
                        _state.update { current ->
                            updateSearchState(current, site) { search ->
                                search.copy(
                                    site = site,
                                    loading = false,
                                    videoResults = paged.items,
                                    imageResults = emptyList(),
                                    userResults = emptyList(),
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                )
                            }.copy(route = AppRoute.Search, activeSearchSite = site)
                        }
                    }.onFailure { throwable ->
                        searchFailure(throwable, site)
                    }
                }

                SearchType.Images -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.withSite(site) { searchImages(query, _state.value.session, page) } }
                    }
                    result.onSuccess { paged ->
                        _state.update { current ->
                            updateSearchState(current, site) { search ->
                                search.copy(
                                    site = site,
                                    loading = false,
                                    videoResults = emptyList(),
                                    imageResults = paged.items,
                                    userResults = emptyList(),
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                )
                            }.copy(route = AppRoute.Search, activeSearchSite = site)
                        }
                    }.onFailure { throwable ->
                        searchFailure(throwable, site)
                    }
                }

                SearchType.Users -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.withSite(site) { searchUsers(query, _state.value.session, page) } }
                    }
                    result.onSuccess { paged ->
                        _state.update { current ->
                            updateSearchState(current, site) { search ->
                                search.copy(
                                    site = site,
                                    loading = false,
                                    videoResults = emptyList(),
                                    imageResults = emptyList(),
                                    userResults = paged.items,
                                    page = paged.page,
                                    count = paged.count,
                                    limit = paged.limit,
                                )
                            }.copy(route = AppRoute.Search, activeSearchSite = site)
                        }
                    }.onFailure { throwable ->
                        searchFailure(throwable, site)
                    }
                }
            }
        }
    }

    private fun searchFailure(throwable: Throwable, site: IwaraSite) {
        _state.update { current ->
            updateSearchState(current, site) { search ->
                search.copy(
                    site = site,
                    loading = false,
                    error = throwable.message ?: appContext.getString(R.string.error_search_failed),
                )
            }.copy(route = AppRoute.Search, activeSearchSite = site)
        }
    }


    fun openProfile(
        username: String,
        videoPage: Int? = null,
        imagePage: Int? = null,
        contentType: ContentType? = null,
        site: IwaraSite = currentSite(),
    ) {
        scope.launch {
            val currentProfile = _state.value.profile
            val currentDetail = currentProfile.detail
            val sameProfileDetail = currentDetail.takeIf {
                currentProfile.site == site && currentProfile.username == username
            }
            val isSameProfile = sameProfileDetail != null
            val resolvedContentType = contentType ?: if (isSameProfile) currentProfile.contentType else ContentType.Videos
            val resolvedVideoPage = videoPage ?: sameProfileDetail?.videoPage ?: 0
            val resolvedImagePage = imagePage ?: sameProfileDetail?.imagePage ?: 0
            val isPagingTransition = isSameProfile && (
                (resolvedContentType == ContentType.Videos && resolvedVideoPage != sameProfileDetail!!.videoPage) ||
                    (resolvedContentType == ContentType.Images && resolvedImagePage != sameProfileDetail!!.imagePage)
                )
            val requestStartedAt = System.currentTimeMillis()

            _state.update {
                it.copy(
                    route = AppRoute.Profile,
                    profile = it.profile.copy(
                        site = site,
                        loading = true,
                        username = username,
                        detail = if (isSameProfile) it.profile.detail else null,
                        contentType = resolvedContentType,
                        error = null,
                    ),
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.withSite(site) {
                        fetchProfile(username, _state.value.session, resolvedVideoPage, resolvedImagePage)
                    }
                }
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
                            site = site,
                            loading = false,
                            username = username,
                            detail = detail,
                            contentType = resolvedContentType,
                            error = null,
                        ),
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Profile,
                        profile = it.profile.copy(
                            site = site,
                            loading = false,
                            username = username,
                            contentType = resolvedContentType,
                            error = throwable.message ?: appContext.getString(R.string.error_load_profile),
                        ),
                    )
                }
            }
        }
    }

    fun openProfileContentType(contentType: ContentType) {
        val username = _state.value.profile.username ?: return
        val detail = _state.value.profile.detail
        _state.update { it.copy(profile = it.profile.copy(contentType = contentType)) }
        if (detail == null) {
            openProfile(username, contentType = contentType, site = _state.value.profile.site)
        }
    }

    fun openOwnProfile(
        videoPage: Int? = null,
        imagePage: Int? = null,
        contentType: ContentType? = null,
        site: IwaraSite = currentSite(),
    ) {
        val username = _state.value.session?.user?.username ?: return
        openProfile(username, videoPage, imagePage, contentType, site)
    }

    fun loadOwnPlaylists(onResult: (List<junzi.iwara.model.PlaylistSummary>, String?) -> Unit) {
        val session = _state.value.session ?: return onResult(emptyList(), appContext.getString(R.string.error_login_failed))
        val site = currentSite()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchOwnPlaylists(session) } }
            }
            result.onSuccess { onResult(it, null) }
                .onFailure { onResult(emptyList(), it.message ?: appContext.getString(R.string.error_load_profile)) }
        }
    }

    fun createPlaylist(title: String, onResult: (junzi.iwara.model.PlaylistSummary?, String?) -> Unit) {
        val session = _state.value.session ?: return onResult(null, appContext.getString(R.string.error_login_failed))
        val site = currentSite()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { createPlaylist(title, session) } }
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
        val site = currentSite()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { addVideoToPlaylist(playlistId, videoId, session) } }
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
        val site = currentSite()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { removeVideoFromPlaylist(playlistId, videoId, session) } }
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
        val site = currentSite()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { deletePlaylist(playlistId, session) } }
            }
            result.onSuccess {
                refreshOwnProfileIfVisible()
                onResult(null)
            }.onFailure {
                onResult(it.message ?: appContext.getString(R.string.error_load_profile))
            }
        }
    }

    fun openPlaylist(playlistId: String, page: Int = 0, site: IwaraSite = currentSite()) {
        val originRoute = _state.value.route
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Playlist,
                    playlist = PlaylistUiState(site = site, originRoute = originRoute, loading = true),
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchPlaylistDetail(playlistId, _state.value.session, page) } }
            }
            result.onSuccess { detail ->
                _state.update {
                    it.copy(
                        route = AppRoute.Playlist,
                        playlist = PlaylistUiState(site = site, originRoute = originRoute, loading = false, detail = detail),
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Playlist,
                        playlist = PlaylistUiState(
                            site = site,
                            originRoute = originRoute,
                            loading = false,
                            error = throwable.message ?: appContext.getString(R.string.error_load_profile),
                        ),
                    )
                }
            }
        }
    }

    fun openVideo(videoId: String, site: IwaraSite = currentSite()) {
        val originRoute = _state.value.route
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Player,
                    player = PlayerUiState(site = site, originRoute = originRoute, loading = true),
                )
            }
            val detailResult = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchVideoDetail(videoId, _state.value.session) } }
            }
            detailResult.onSuccess { detail ->
                _state.update {
                    it.copy(
                        route = AppRoute.Player,
                        player = it.player.copy(
                            site = site,
                            originRoute = originRoute,
                            loading = false,
                            detail = detail,
                            error = null,
                            commentsLoading = true,
                            comments = emptyList(),
                        ),
                    )
                }
                loadVideoComments(videoId, site)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.Player,
                        player = PlayerUiState(
                            site = site,
                            originRoute = originRoute,
                            loading = false,
                            error = throwable.message ?: appContext.getString(R.string.error_load_video),
                        ),
                    )
                }
            }
        }
    }

    fun openImage(imageId: String, site: IwaraSite = currentSite()) {
        val originRoute = _state.value.route
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.ImageViewer,
                    imageViewer = ImageViewerUiState(site = site, originRoute = originRoute, loading = true),
                )
            }
            val detailResult = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchImageDetail(imageId, _state.value.session) } }
            }
            detailResult.onSuccess { detail ->
                _state.update {
                    it.copy(
                        route = AppRoute.ImageViewer,
                        imageViewer = it.imageViewer.copy(
                            site = site,
                            originRoute = originRoute,
                            loading = false,
                            detail = detail,
                            error = null,
                            commentsLoading = true,
                            comments = emptyList(),
                        ),
                    )
                }
                loadImageComments(imageId, site)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        route = AppRoute.ImageViewer,
                        imageViewer = ImageViewerUiState(
                            site = site,
                            originRoute = originRoute,
                            loading = false,
                            error = throwable.message ?: appContext.getString(R.string.error_load_image),
                        ),
                    )
                }
            }
        }
    }

    fun loadVideoComments(videoId: String? = null, site: IwaraSite = _state.value.player.site) {
        val resolvedVideoId = videoId ?: _state.value.player.detail?.id ?: return
        scope.launch {
            _state.update { it.copy(player = it.player.copy(site = site, commentsLoading = true, commentError = null)) }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchVideoComments(resolvedVideoId, _state.value.session) } }
            }
            result.onSuccess { comments ->
                _state.update {
                    it.copy(player = it.player.copy(site = site, commentsLoading = false, comments = comments))
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        player = it.player.copy(
                            site = site,
                            commentsLoading = false,
                            commentError = throwable.message ?: appContext.getString(R.string.error_load_comments),
                        ),
                    )
                }
            }
        }
    }

    fun loadImageComments(imageId: String? = null, site: IwaraSite = _state.value.imageViewer.site) {
        val resolvedImageId = imageId ?: _state.value.imageViewer.detail?.id ?: return
        scope.launch {
            _state.update { it.copy(imageViewer = it.imageViewer.copy(site = site, commentsLoading = true, commentError = null)) }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { fetchImageComments(resolvedImageId, _state.value.session) } }
            }
            result.onSuccess { comments ->
                _state.update {
                    it.copy(imageViewer = it.imageViewer.copy(site = site, commentsLoading = false, comments = comments))
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        imageViewer = it.imageViewer.copy(
                            site = site,
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
        val site = _state.value.player.site
        submitComment(CommentTargetType.Video, videoId, text, site) {
            loadVideoComments(videoId, site)
        }
    }

    fun submitImageComment(text: String) {
        val imageId = _state.value.imageViewer.detail?.id ?: return
        val site = _state.value.imageViewer.site
        submitComment(CommentTargetType.Image, imageId, text, site) {
            loadImageComments(imageId, site)
        }
    }

    fun submitProfileComment(text: String) {
        val profile = _state.value.profile.detail ?: return
        val site = _state.value.profile.site
        submitComment(CommentTargetType.Profile, profile.user.id, text, site) {
            val username = _state.value.profile.username
            if (username != null) {
                openProfile(
                    username = username,
                    videoPage = profile.videoPage,
                    imagePage = profile.imagePage,
                    contentType = _state.value.profile.contentType,
                    site = site,
                )
            }
        }
    }

    private fun submitComment(
        targetType: CommentTargetType,
        targetId: String,
        text: String,
        site: IwaraSite,
        onSuccess: () -> Unit,
    ) {
        val session = _state.value.session ?: return
        val normalized = text.trim()
        if (normalized.isBlank()) return

        scope.launch {
            when (targetType) {
                CommentTargetType.Video -> {
                    _state.update { it.copy(player = it.player.copy(site = site, commentSubmitting = true, commentError = null)) }
                }

                CommentTargetType.Image -> {
                    _state.update { it.copy(imageViewer = it.imageViewer.copy(site = site, commentSubmitting = true, commentError = null)) }
                }

                CommentTargetType.Profile -> {
                    _state.update { it.copy(profile = it.profile.copy(site = site, commentSubmitting = true, commentError = null)) }
                }
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { repository.withSite(site) { postComment(targetType, targetId, normalized, session) } }
            }
            result.onSuccess {
                when (targetType) {
                    CommentTargetType.Video -> {
                        _state.update { it.copy(player = it.player.copy(site = site, commentSubmitting = false, commentError = null)) }
                    }

                    CommentTargetType.Image -> {
                        _state.update { it.copy(imageViewer = it.imageViewer.copy(site = site, commentSubmitting = false, commentError = null)) }
                    }

                    CommentTargetType.Profile -> {
                        _state.update { it.copy(profile = it.profile.copy(site = site, commentSubmitting = false, commentError = null)) }
                    }
                }
                onSuccess()
            }.onFailure { throwable ->
                val message = throwable.message ?: appContext.getString(R.string.error_post_comment)
                when (targetType) {
                    CommentTargetType.Video -> {
                        _state.update {
                            it.copy(
                                player = it.player.copy(
                                    site = site,
                                    commentSubmitting = false,
                                    commentError = message,
                                ),
                            )
                        }
                    }

                    CommentTargetType.Image -> {
                        _state.update {
                            it.copy(
                                imageViewer = it.imageViewer.copy(
                                    site = site,
                                    commentSubmitting = false,
                                    commentError = message,
                                ),
                            )
                        }
                    }

                    CommentTargetType.Profile -> {
                        _state.update {
                            it.copy(
                                profile = it.profile.copy(
                                    site = site,
                                    commentSubmitting = false,
                                    commentError = message,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun closePlayer() {
        _state.update {
            val targetRoute = if (it.downloads.activeItemId != null) AppRoute.Downloads else it.player.originRoute
            it.copy(
                route = targetRoute,
                player = PlayerUiState(site = it.player.site),
                downloads = it.downloads.copy(activeItemId = null),
            )
        }
        if (_state.value.route == AppRoute.Downloads) {
            scope.launch {
                refreshDownloadsInternal(openRoute = false, showLoading = false)
            }
        }
    }

    fun closeImageViewer() {
        _state.update {
            it.copy(
                route = it.imageViewer.originRoute,
                imageViewer = ImageViewerUiState(site = it.imageViewer.site),
            )
        }
    }

    private fun refreshOwnProfileIfVisible() {
        val session = _state.value.session ?: return
        val profile = _state.value.profile.detail ?: return
        if (profile.user.username == session.user.username) {
            openOwnProfile(
                videoPage = profile.videoPage,
                imagePage = profile.imagePage,
                contentType = _state.value.profile.contentType,
                site = _state.value.profile.site,
            )
        }
    }

    fun closePlaylist() {
        _state.update {
            it.copy(
                route = it.playlist.originRoute,
                playlist = PlaylistUiState(site = it.playlist.site),
            )
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
        DownloadStatus.Paused,
        -> true
        else -> false
    }

    fun closeDownloads() {
        _state.update {
            val targetRoute = when {
                it.downloads.activeItemId != null -> AppRoute.Downloads
                it.player.detail != null -> AppRoute.Player
                it.imageViewer.detail != null -> AppRoute.ImageViewer
                it.playlist.detail != null -> AppRoute.Playlist
                it.profile.detail != null -> AppRoute.Profile
                else -> routeForSite(it.activeSearchSite)
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
        val site = currentSite()
        _state.update {
            it.copy(
                route = AppRoute.Player,
                downloads = it.downloads.copy(activeItemId = downloadId),
                player = PlayerUiState(
                    site = site,
                    originRoute = AppRoute.Downloads,
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










