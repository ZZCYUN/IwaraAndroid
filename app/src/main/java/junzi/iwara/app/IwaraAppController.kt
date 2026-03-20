package junzi.iwara.app

import android.content.Context
import junzi.iwara.R
import junzi.iwara.data.IwaraApi
import junzi.iwara.data.IwaraRepository
import junzi.iwara.data.IwaraSessionStore
import junzi.iwara.model.AppRoute
import junzi.iwara.model.AppUiState
import junzi.iwara.model.CommentTargetType
import junzi.iwara.model.FeedSort
import junzi.iwara.model.PlayerUiState
import junzi.iwara.model.PlaylistUiState
import junzi.iwara.model.ProfileUiState
import junzi.iwara.model.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    fun loadFeed(sort: FeedSort = _state.value.feed.sort, tag: String? = _state.value.feed.selectedTag) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Feed,
                    feed = it.feed.copy(
                        loading = true,
                        error = null,
                        sort = sort,
                        selectedTag = tag,
                    ),
                )
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchVideos(sort, _state.value.session, tag) }
            }
            result.onSuccess { videos ->
                _state.update {
                    it.copy(
                        route = AppRoute.Feed,
                        feed = it.feed.copy(
                            loading = false,
                            videos = videos,
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
        loadFeed(sort = _state.value.feed.sort, tag = tag)
    }

    fun clearTag() {
        loadFeed(sort = _state.value.feed.sort, tag = null)
    }

    fun openSearch() {
        _state.update { it.copy(route = AppRoute.Search) }
    }

    fun search(query: String, type: SearchType) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Search,
                    search = it.search.copy(
                        query = query,
                        type = type,
                        loading = true,
                        error = null,
                    ),
                )
            }

            when (type) {
                SearchType.Videos -> {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.searchVideos(query, _state.value.session) }
                    }
                    result.onSuccess { videos ->
                        _state.update {
                            it.copy(
                                route = AppRoute.Search,
                                search = it.search.copy(
                                    loading = false,
                                    videoResults = videos,
                                    userResults = emptyList(),
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
                        runCatching { repository.searchUsers(query, _state.value.session) }
                    }
                    result.onSuccess { users ->
                        _state.update {
                            it.copy(
                                route = AppRoute.Search,
                                search = it.search.copy(
                                    loading = false,
                                    videoResults = emptyList(),
                                    userResults = users,
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

    fun openProfile(username: String) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Profile,
                    profile = ProfileUiState(loading = true, username = username),
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchProfile(username, _state.value.session) }
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
                        profile = ProfileUiState(
                            loading = false,
                            username = username,
                            error = throwable.message ?: appContext.getString(R.string.error_load_profile),
                        ),
                    )
                }
            }
        }
    }

    fun openOwnProfile() {
        val username = _state.value.session?.user?.username ?: return
        openProfile(username)
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

    fun openPlaylist(playlistId: String) {
        scope.launch {
            _state.update {
                it.copy(
                    route = AppRoute.Playlist,
                    playlist = PlaylistUiState(loading = true),
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.fetchPlaylistDetail(playlistId, _state.value.session) }
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
                openProfile(username)
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
        _state.update { it.copy(route = AppRoute.Feed, player = PlayerUiState()) }
    }

    private fun refreshOwnProfileIfVisible() {
        val session = _state.value.session ?: return
        val profile = _state.value.profile.detail ?: return
        if (profile.user.username == session.user.username) {
            openOwnProfile()
        }
    }

    fun closePlaylist() {
        _state.update {
            val targetRoute = if (it.profile.detail != null) AppRoute.Profile else AppRoute.Feed
            it.copy(route = targetRoute, playlist = PlaylistUiState())
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

