package junzi.iwara

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.CommentItem
import junzi.iwara.model.ContentType
import junzi.iwara.model.ImageSummary
import junzi.iwara.model.PlaylistSummary
import junzi.iwara.model.ProfileDetail
import junzi.iwara.model.VideoDetail
import junzi.iwara.model.VideoVariant
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val detail = state.profile.detail
    val isOwnProfileRoute = detail?.isOwnProfile == true || state.profile.username == state.session?.user?.username
    BackHandler(enabled = !isOwnProfileRoute) {
        controller.loadFeed(
            sort = state.feed.sort,
            tag = state.feed.selectedTag,
            page = state.feed.page,
            contentType = state.feed.contentType,
        )
    }
    var profilePlaylistTargetId by remember(detail?.user?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.user?.username ?: state.profile.username.orEmpty()) },
                navigationIcon = {
                    if (!isOwnProfileRoute) {
                        IconButton(
                            onClick = {
                                controller.loadFeed(
                                    sort = state.feed.sort,
                                    tag = state.feed.selectedTag,
                                    page = state.feed.page,
                                    contentType = state.feed.contentType,
                                )
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = controller::openSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                },
            )
        },
        bottomBar = {
            MainBottomBar(
                route = state.route,
                isOwnProfile = isOwnProfileRoute,
                onOpenHome = controller::openFeed,
                onOpenAi = controller::openAi,
                onOpenMy = { controller.openOwnProfile() },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                detail != null -> {
                    ProfileBody(
                        detail = detail,
                        contentType = state.profile.contentType,
                        showSocialSections = detail.user.id == state.session?.user?.id || detail.user.username == state.session?.user?.username,
                        commentSubmitting = state.profile.commentSubmitting,
                        commentError = state.profile.commentError,
                        onOpenProfile = controller::openProfile,
                        onOpenVideo = controller::openVideo,
                        onOpenImage = controller::openImage,
                        onOpenPlaylist = controller::openPlaylist,
                        onAddToPlaylist = { profilePlaylistTargetId = it },
                        onContentTypeChange = controller::openProfileContentType,
                        onVideoPageChange = { page ->
                            controller.openProfile(
                                username = detail.user.username,
                                videoPage = page,
                                imagePage = detail.imagePage,
                                contentType = ContentType.Videos,
                            )
                        },
                        onImagePageChange = { page ->
                            controller.openProfile(
                                username = detail.user.username,
                                videoPage = detail.videoPage,
                                imagePage = page,
                                contentType = ContentType.Images,
                            )
                        },
                        isWorksPageLoading = state.profile.loading,
                        onSubmitComment = controller::submitProfileComment,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.profile.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.profile.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.profile.error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (detail != null && state.profile.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(
                                if (state.profile.contentType == ContentType.Videos) {
                                    R.string.message_loading_profile_videos
                                } else {
                                    R.string.message_loading_profile_images
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        detail?.videos?.firstOrNull { it.id == profilePlaylistTargetId }?.let { video ->
            PlaylistPickerDialog(video = video, controller = controller, onDismiss = { profilePlaylistTargetId = null })
        }
    }
}

@Composable
private fun ProfileBody(
    detail: ProfileDetail,
    contentType: ContentType,
    showSocialSections: Boolean,
    commentSubmitting: Boolean,
    commentError: String?,
    onOpenProfile: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onContentTypeChange: (ContentType) -> Unit,
    onVideoPageChange: (Int) -> Unit,
    onImagePageChange: (Int) -> Unit,
    isWorksPageLoading: Boolean,
    onSubmitComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileListState = rememberLazyListState()
    var lastObservedPage by remember(detail.user.id, contentType) { mutableStateOf<Int?>(null) }
    val worksSectionIndex = 2 +
        (if (showSocialSections) 2 else 0) +
        (if (detail.isOwnProfile && detail.playlists.isNotEmpty()) 1 + detail.playlists.size else 0)

    LaunchedEffect(detail.user.id, contentType, detail.videoPage, detail.imagePage, isWorksPageLoading, worksSectionIndex) {
        if (!isWorksPageLoading) {
            val currentPage = if (contentType == ContentType.Videos) detail.videoPage else detail.imagePage
            val previousPage = lastObservedPage
            if (previousPage != null && previousPage != currentPage) {
                profileListState.animateScrollToItem(worksSectionIndex)
            }
            lastObservedPage = currentPage
        }
    }

    val workItems: List<ImageSummary> = detail.images

    LazyColumn(
        state = profileListState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ProfileHeader(detail)
        }
        if (showSocialSections) {
            item {
                UserStrip(stringResource(R.string.section_followers), detail.followers, onOpenProfile)
            }
            item {
                UserStrip(stringResource(R.string.section_following), detail.following, onOpenProfile)
            }
        }
        if (detail.isOwnProfile && detail.playlists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_my_playlists)) }
            items(detail.playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist, onOpen = { onOpenPlaylist(playlist.id) })
            }
        }
        item {
            ContentTypeToggleBar(
                selectedType = contentType,
                onSelected = onContentTypeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        item {
            SectionTitle(
                stringResource(
                    if (contentType == ContentType.Videos) R.string.section_videos else R.string.section_images,
                ),
            )
        }
        if (contentType == ContentType.Videos) {
            items(detail.videos, key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    onOpen = { onOpenVideo(video.id) },
                    onOpenProfile = { onOpenProfile(video.authorUsername) },
                    onAddToPlaylist = { onAddToPlaylist(video.id) },
                )
            }
            item {
                PaginationBar(
                    currentPage = detail.videoPage,
                    totalCount = detail.videoCount,
                    pageSize = detail.videoLimit,
                    onPageSelected = onVideoPageChange,
                )
            }
        } else {
            items(workItems, key = { it.id }) { image ->
                ImageRow(
                    image = image,
                    onOpen = { onOpenImage(image.id) },
                    onOpenProfile = { onOpenProfile(image.authorUsername) },
                )
            }
            item {
                PaginationBar(
                    currentPage = detail.imagePage,
                    totalCount = detail.imageCount,
                    pageSize = detail.imageLimit,
                    onPageSelected = onImagePageChange,
                )
            }
        }
        item { SectionTitle(stringResource(R.string.section_profile_comments)) }
        items(detail.comments, key = { it.id }) { comment ->
            CommentRow(comment = comment, onOpenProfile = { onOpenProfile(comment.authorUsername) })
        }
        item {
            CommentComposer(
                label = stringResource(R.string.label_leave_profile_comment),
                submitting = commentSubmitting,
                error = commentError,
                onSubmit = onSubmitComment,
            )
        }
    }
}

@Composable
private fun ProfileHeader(detail: ProfileDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (detail.headerUrl != null) {
            junzi.iwara.ui.AsyncRemoteImage(
                url = detail.headerUrl,
                contentDescription = stringResource(R.string.label_profile_header),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(detail.user.avatarUrl, detail.user.name)
            Column {
                Text(detail.user.name, style = MaterialTheme.typography.headlineSmall)
                Text("@${detail.user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!detail.body.isNullOrBlank()) {
            Text(text = detail.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun UserStrip(
    title: String,
    users: List<junzi.iwara.model.IwaraUser>,
    onOpenProfile: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (users.isEmpty()) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.label_none)) })
            }
            users.forEach { user ->
                AssistChip(
                    onClick = { onOpenProfile(user.username) },
                    label = { Text(user.username) },
                )
            }
        }
    }
}
@Composable
fun PlayerScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val detail = state.player.detail
    val context = LocalContext.current
    val selectedVariant = remember(detail?.selectedVariantName, detail?.variants) {
        detail?.let { currentDetail ->
            currentDetail.variants.firstOrNull { it.name == currentDetail.selectedVariantName } ?: currentDetail.variants.firstOrNull()
        }
    }
    val player = remember(context, selectedVariant?.viewUrl) {
        selectedVariant?.viewUrl?.let { viewUrl ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(viewUrl))
                prepare()
                playWhenReady = true
            }
        }
    }
    val isLocalPlayback = remember(detail?.variants) {
        detail?.variants?.isNotEmpty() == true && detail.variants.all { it.type.equals("download", ignoreCase = true) }
    }
    val downloadableVariants = remember(detail?.variants, isLocalPlayback) {
        if (isLocalPlayback) {
            emptyList()
        } else {
            detail?.variants
                ?.filterNot { it.name.equals("preview", ignoreCase = true) }
                ?.filter { it.downloadUrl.isNotBlank() }
                ?: emptyList()
        }
    }
    val playlistVideo = remember(detail) {
        detail?.let {
            junzi.iwara.model.VideoSummary(
                id = it.id,
                title = it.title,
                authorName = it.authorName,
                authorUsername = it.authorUsername,
                views = it.views,
                likes = it.likes,
                durationSeconds = it.durationSeconds,
                thumbnailUrl = it.posterUrl,
                rating = it.rating,
                tags = it.tags,
            )
        }
    }
    var isFullscreen by remember(detail?.id) { mutableStateOf(false) }
    var showDownloadDialog by remember(detail?.id) { mutableStateOf(false) }
    var showPlaylistDialog by remember(detail?.id) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    LaunchedEffect(detail?.id) {
        if (detail == null) {
            isFullscreen = false
        }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }
    BackHandler(enabled = !isFullscreen, onBack = controller::closePlayer)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.player.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.player.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.player.error, color = MaterialTheme.colorScheme.error)
                }
            }

            detail != null -> {
                PlayerDetailBody(
                    detail = detail,
                    player = player,
                    isFullscreen = isFullscreen,
                    isLocalPlayback = isLocalPlayback,
                    downloadableVariants = downloadableVariants,
                    comments = state.player.comments,
                    commentsLoading = state.player.commentsLoading,
                    commentSubmitting = state.player.commentSubmitting,
                    commentError = state.player.commentError,
                    onVariantSelected = controller::selectVariant,
                    onOpenProfile = controller::openProfile,
                    onOpenTag = controller::openTag,
                    onSubmitComment = controller::submitVideoComment,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onShowDownloadDialog = { showDownloadDialog = true },
                    onShowPlaylistDialog = { showPlaylistDialog = true },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }

        if (detail != null && player != null && isFullscreen) {
            FullscreenPlayerOverlay(
                title = detail.title,
                player = player,
                onExitFullscreen = { isFullscreen = false },
            )
        }
    }

    if (showDownloadDialog && detail != null) {
        DownloadVariantDialog(
            title = detail.title,
            variants = downloadableVariants,
            onDismiss = { showDownloadDialog = false },
            onDownload = { variant ->
                controller.downloadVideo(detail, variant) { message ->
                    val toastText = message ?: context.getString(
                        R.string.message_download_started,
                        variantDisplayLabel(variant),
                    )
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                }
                showDownloadDialog = false
            },
        )
    }

    if (showPlaylistDialog && playlistVideo != null && !isLocalPlayback) {
        PlaylistPickerDialog(
            video = playlistVideo,
            controller = controller,
            onDismiss = { showPlaylistDialog = false },
        )
    }
}

@Composable
private fun PlayerDetailBody(
    detail: VideoDetail,
    player: ExoPlayer?,
    isFullscreen: Boolean,
    isLocalPlayback: Boolean,
    downloadableVariants: List<VideoVariant>,
    comments: List<CommentItem>,
    commentsLoading: Boolean,
    commentSubmitting: Boolean,
    commentError: String?,
    onVariantSelected: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onSubmitComment: (String) -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowDownloadDialog: () -> Unit,
    onShowPlaylistDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (!isFullscreen) {
                InlinePlayerCard(
                    player = player,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "@${detail.authorUsername} · ${stringResource(R.string.label_views_likes, detail.views, detail.likes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onOpenProfile(detail.authorUsername) },
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (downloadableVariants.isNotEmpty()) {
                        AssistChip(
                            onClick = onShowDownloadDialog,
                            label = { Text(stringResource(R.string.action_download)) },
                        )
                    }
                    if (!isLocalPlayback) {
                        AssistChip(
                            onClick = onShowPlaylistDialog,
                            label = { Text(stringResource(R.string.action_add_to_playlist)) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.variants.filterNot { it.name.equals("preview", ignoreCase = true) }.forEach { variant ->
                        val variantLabel = when {
                            variant.name.equals("Source", ignoreCase = true) -> stringResource(R.string.quality_source)
                            else -> "${variant.name}p"
                        }
                        FilterChip(
                            selected = variant.name == detail.selectedVariantName,
                            onClick = { onVariantSelected(variant.name) },
                            label = { Text(variantLabel) },
                        )
                    }
                }
                if (detail.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.tags.forEach { tag ->
                            AssistChip(
                                onClick = { onOpenTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
                if (detail.description.isNotBlank()) {
                    Text(text = detail.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (!isLocalPlayback) {
            item { SectionTitle(stringResource(R.string.section_comments)) }
            if (commentsLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            items(comments, key = { it.id }) { comment ->
                CommentRow(comment = comment, onOpenProfile = { onOpenProfile(comment.authorUsername) })
            }
            item {
                CommentComposer(
                    label = stringResource(R.string.label_add_comment),
                    submitting = commentSubmitting,
                    error = commentError,
                    onSubmit = onSubmitComment,
                )
            }
        }
    }
}

@Composable
private fun InlinePlayerCard(
    player: ExoPlayer?,
    onToggleFullscreen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        color = Color.Black,
    ) {
        if (player == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.label_unavailable_video), color = Color.White)
            }
        } else {
            PlayerViewport(
                player = player,
                isFullscreen = false,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun FullscreenPlayerOverlay(
    title: String,
    player: ExoPlayer,
    onExitFullscreen: () -> Unit,
) {
    var playerState by remember(player) { mutableStateOf(PlayerOverlayState.from(player)) }

    FullscreenPlaybackEffect(
        enabled = true,
        preferredOrientation = playerState.fullscreenOrientation,
    )

    PlayerViewport(
        player = player,
        title = title,
        isFullscreen = true,
        onToggleFullscreen = onExitFullscreen,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        onStateChanged = { playerState = it },
    )
}

@Composable
private fun PlayerViewport(
    player: ExoPlayer,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onStateChanged: ((PlayerOverlayState) -> Unit)? = null,
) {
    var feedback by remember { mutableStateOf<PlayerGestureFeedback?>(null) }
    var playerState by remember(player) { mutableStateOf(PlayerOverlayState.from(player)) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember(isFullscreen) { mutableStateOf(true) }
    var controlsNonce by remember(isFullscreen) { mutableStateOf(0) }
    val gestureHint = stringResource(R.string.label_gesture_hint)
    val stateChangedCallback by rememberUpdatedState(onStateChanged)

    fun refreshPlayerState() {
        val newState = PlayerOverlayState.from(player)
        playerState = newState
        stateChangedCallback?.invoke(newState)
    }

    fun showControls() {
        controlsVisible = true
        controlsNonce += 1
    }

    fun toggleControls() {
        if (controlsVisible) {
            controlsVisible = false
        } else {
            showControls()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (!isScrubbing) {
                    refreshPlayerState()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                refreshPlayerState()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                refreshPlayerState()
            }
            delay(if (player.isPlaying) 250L else 700L)
        }
    }

    LaunchedEffect(playerState.isPlaying) {
        if (!playerState.isPlaying) {
            controlsVisible = true
        }
    }

    LaunchedEffect(controlsVisible, playerState.isPlaying, isScrubbing, feedback, controlsNonce) {
        if (!controlsVisible || !playerState.isPlaying || isScrubbing || feedback != null) {
            return@LaunchedEffect
        }
        delay(2600L)
        controlsVisible = false
    }

    val displayFraction = if (isScrubbing) scrubFraction else playerState.progressFraction
    val displayPosition = if (isScrubbing) {
        (displayFraction * playerState.durationMs).toLong()
    } else {
        playerState.positionMs
    }
    val overlayVisible = controlsVisible || !playerState.isPlaying || isScrubbing

    Box(modifier = modifier.background(Color.Black)) {
        PlayerSurface(
            player = player,
            useController = false,
            modifier = Modifier.fillMaxSize(),
        )
        if (isFullscreen) {
            PlayerGestureLayer(
                player = player,
                modifier = Modifier.fillMaxSize(),
                onFeedbackChange = { feedback = it },
                onTap = ::toggleControls,
                onInteraction = ::showControls,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { toggleControls() })
                    },
            )
        }
        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (isFullscreen) 0.52f else 0.18f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isFullscreen) 0.82f else 0.62f),
                            ),
                        ),
                    ),
            )
            Column(modifier = Modifier.fillMaxSize()) {
                if (isFullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = if (isFullscreen) 16.dp else 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Slider(
                        value = displayFraction,
                        onValueChange = { newValue ->
                            showControls()
                            isScrubbing = true
                            scrubFraction = newValue
                        },
                        onValueChangeFinished = {
                            if (playerState.durationMs > 0L) {
                                player.seekTo((scrubFraction * playerState.durationMs).toLong())
                            }
                            showControls()
                            isScrubbing = false
                        },
                        valueRange = 0f..1f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                showControls()
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            },
                            modifier = Modifier.size(if (isFullscreen) 48.dp else 42.dp),
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (playerState.isPlaying) R.string.action_pause else R.string.action_play,
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullscreen) 24.dp else 20.dp),
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.label_player_position,
                                formatDuration(displayPosition),
                                formatDuration(playerState.durationMs),
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isFullscreen) {
                            Text(
                                text = gestureHint,
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f))
                        }
                        IconButton(
                            onClick = {
                                showControls()
                                onToggleFullscreen()
                            },
                            modifier = Modifier.size(if (isFullscreen) 48.dp else 42.dp),
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = stringResource(
                                    if (isFullscreen) R.string.action_exit_fullscreen else R.string.action_enter_fullscreen,
                                ),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
        if (feedback != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                GestureFeedbackBubble(feedback = feedback!!)
            }
        }
    }
}

@Composable
private fun PlayerGestureLayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    onFeedbackChange: (PlayerGestureFeedback?) -> Unit,
    onTap: () -> Unit,
    onInteraction: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
            ?: context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val feedbackCallback by rememberUpdatedState(onFeedbackChange)
    val tapCallback by rememberUpdatedState(onTap)
    val interactionCallback by rememberUpdatedState(onInteraction)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { tapCallback() })
            }
            .pointerInput(player, activity, audioManager) {
                var startX = 0f
                var totalDx = 0f
                var totalDy = 0f
                var startPosition = 0L
                var duration = 0L
                var startBrightness = 0.5f
                var startVolume = 0f
                var mode: PlayerGestureMode? = null
                var seekTarget = 0L

                detectDragGestures(
                    onDragStart = { offset ->
                        interactionCallback()
                        startX = offset.x
                        totalDx = 0f
                        totalDy = 0f
                        startPosition = player.currentPosition
                        duration = player.duration.takeIf { it > 0L } ?: 0L
                        startBrightness = activity?.currentWindowBrightness() ?: 0.5f
                        startVolume = audioManager.currentMusicVolumeFraction()
                        mode = null
                        seekTarget = startPosition
                    },
                    onDragEnd = {
                        if (mode == PlayerGestureMode.Seek && duration > 0L) {
                            player.seekTo(seekTarget)
                        }
                        interactionCallback()
                        mode = null
                        feedbackCallback(null)
                    },
                    onDragCancel = {
                        interactionCallback()
                        mode = null
                        feedbackCallback(null)
                    },
                ) { _, dragAmount ->
                    totalDx += dragAmount.x
                    totalDy += dragAmount.y

                    if (mode == null) {
                        mode = if (abs(totalDx) >= abs(totalDy)) {
                            PlayerGestureMode.Seek
                        } else if (startX < size.width / 2f) {
                            PlayerGestureMode.Brightness
                        } else {
                            PlayerGestureMode.Volume
                        }
                    }

                    when (mode) {
                        PlayerGestureMode.Seek -> {
                            if (duration > 0L) {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val fractionDelta = totalDx / width
                                seekTarget = (startPosition + (duration * fractionDelta).toLong())
                                    .coerceIn(0L, duration)
                                feedbackCallback(
                                    PlayerGestureFeedback(
                                        icon = Icons.Filled.PlayArrow,
                                        message = context.getString(
                                            R.string.label_player_seek_to,
                                            formatDuration(seekTarget),
                                        ),
                                    ),
                                )
                            }
                        }

                        PlayerGestureMode.Brightness -> {
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            val brightnessTarget = (startBrightness - (totalDy / height)).coerceIn(0.05f, 1f)
                            activity?.setWindowBrightness(brightnessTarget)
                            feedbackCallback(
                                PlayerGestureFeedback(
                                    icon = Icons.Filled.Brightness6,
                                    message = context.getString(
                                        R.string.label_player_brightness,
                                        (brightnessTarget * 100).toInt(),
                                    ),
                                ),
                            )
                        }

                        PlayerGestureMode.Volume -> {
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            val volumeTarget = (startVolume - (totalDy / height)).coerceIn(0f, 1f)
                            audioManager.setMusicVolumeFraction(volumeTarget)
                            feedbackCallback(
                                PlayerGestureFeedback(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    message = context.getString(
                                        R.string.label_player_volume,
                                        (volumeTarget * 100).toInt(),
                                    ),
                                ),
                            )
                        }

                        null -> Unit
                    }
                }
            },
    )
}

@Composable
private fun GestureFeedbackBubble(
    feedback: PlayerGestureFeedback,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = feedback.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = feedback.message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    useController: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                this.useController = useController
                keepScreenOn = true
            }
        },
        update = {
            it.player = player
            it.useController = useController
            it.keepScreenOn = true
        },
        modifier = modifier,
    )
}

@Composable
private fun FullscreenPlaybackEffect(
    enabled: Boolean,
    preferredOrientation: Int?,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, enabled) {
        if (!enabled || activity == null) {
            return@DisposableEffect onDispose { }
        }

        val window = activity.window
        val initialOrientation = activity.requestedOrientation
        val initialBrightness = window.attributes.screenBrightness
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity.requestedOrientation = initialOrientation
            val params = window.attributes
            params.screenBrightness = initialBrightness
            window.attributes = params
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(activity, enabled, preferredOrientation) {
        if (enabled && activity != null && preferredOrientation != null) {
            activity.requestedOrientation = preferredOrientation
        }
    }
}

private data class PlayerOverlayState(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f

    val fullscreenOrientation: Int?
        get() = when {
            videoWidth <= 0 || videoHeight <= 0 -> null
            videoWidth >= videoHeight -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

    companion object {
        fun from(player: Player): PlayerOverlayState {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            val position = player.currentPosition.coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
            val videoSize = player.videoSize
            return PlayerOverlayState(
                isPlaying = player.isPlaying,
                positionMs = position,
                durationMs = duration,
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
        }
    }
}

private data class PlayerGestureFeedback(
    val icon: ImageVector,
    val message: String,
)

private enum class PlayerGestureMode {
    Seek,
    Brightness,
    Volume,
}

@Composable
private fun DownloadVariantDialog(
    title: String,
    variants: List<VideoVariant>,
    onDismiss: () -> Unit,
    onDownload: (VideoVariant) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_download_quality)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.label_download_quality_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                variants.forEach { variant ->
                    TextButton(onClick = { onDownload(variant) }) {
                        Text(variantDisplayLabel(variant))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_back))
            }
        },
    )
}

private fun variantDisplayLabel(variant: VideoVariant): String = when {
    variant.name.equals("Source", ignoreCase = true) -> "Source"
    variant.name.endsWith("p", ignoreCase = true) -> variant.name
    else -> "${variant.name}p"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.currentWindowBrightness(): Float {
    val brightness = window.attributes.screenBrightness
    if (brightness in 0f..1f) {
        return brightness
    }
    return runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

private fun Activity.setWindowBrightness(brightness: Float) {
    val params = window.attributes
    params.screenBrightness = brightness.coerceIn(0.05f, 1f)
    window.attributes = params
}

private fun AudioManager.currentMusicVolumeFraction(): Float {
    val maxVolume = getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
}

private fun AudioManager.setMusicVolumeFraction(fraction: Float) {
    val maxVolume = getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val targetVolume = (fraction.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)
    setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
}








































