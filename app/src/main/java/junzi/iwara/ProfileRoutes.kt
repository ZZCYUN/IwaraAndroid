package junzi.iwara

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.ContentType
import junzi.iwara.model.IwaraSite
import junzi.iwara.model.ProfileDetail

@Composable
fun ProfileRouteScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val detail = state.profile.detail
    val isOwnProfile = detail?.isOwnProfile == true || state.profile.username == state.session?.user?.username
    if (isOwnProfile) {
        MyProfileScreen(state = state, controller = controller)
    } else {
        UserProfileScreen(state = state, controller = controller)
    }
}

@Composable
private fun MyProfileScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    var playlistTargetId by rememberSaveable(state.profile.site.name) { mutableStateOf<String?>(null) }
    val detail = state.profile.detail

    ProfileScaffold(
        state = state,
        controller = controller,
        ownProfile = true,
    ) { currentDetail ->
        item {
            ProfileHeroCard(
                detail = currentDetail,
                site = state.profile.site,
                title = "我的主页",
                onBack = null,
                onOpenSearch = controller::openSearch,
                onOpenDownloads = controller::openDownloads,
                onLogout = controller::logout,
            )
        }
        if (currentDetail.playlists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_my_playlists)) }
            items(currentDetail.playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist = playlist, onOpen = { controller.openPlaylist(playlist.id, site = state.profile.site) })
            }
        }
        profileWorksSection(
            state = state,
            controller = controller,
            detail = currentDetail,
            ownProfile = true,
            onAddToPlaylist = { playlistTargetId = it },
        )
        profileCommentsSection(state = state, controller = controller, detail = currentDetail)
    }

    detail?.videos?.firstOrNull { it.id == playlistTargetId }?.let { video ->
        PlaylistPickerDialog(video = video, controller = controller, onDismiss = { playlistTargetId = null })
    }
}

@Composable
private fun UserProfileScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val sourceFeed = feedForSite(state, state.profile.site)
    var playlistTargetId by rememberSaveable(state.profile.site.name) { mutableStateOf<String?>(null) }
    val detail = state.profile.detail

    BackHandler {
        controller.loadFeed(
            sort = sourceFeed.sort,
            tag = sourceFeed.selectedTag,
            page = sourceFeed.page,
            contentType = sourceFeed.contentType,
            site = state.profile.site,
        )
    }

    ProfileScaffold(
        state = state,
        controller = controller,
        ownProfile = false,
    ) { currentDetail ->
        item {
            ProfileHeroCard(
                detail = currentDetail,
                site = state.profile.site,
                title = "用户主页",
                onBack = {
                    controller.loadFeed(
                        sort = sourceFeed.sort,
                        tag = sourceFeed.selectedTag,
                        page = sourceFeed.page,
                        contentType = sourceFeed.contentType,
                        site = state.profile.site,
                    )
                },
                onOpenSearch = controller::openSearch,
                onOpenDownloads = null,
                onLogout = null,
            )
        }
        profileWorksSection(
            state = state,
            controller = controller,
            detail = currentDetail,
            ownProfile = false,
            onAddToPlaylist = { playlistTargetId = it },
        )
        profileCommentsSection(state = state, controller = controller, detail = currentDetail)
    }

    detail?.videos?.firstOrNull { it.id == playlistTargetId }?.let { video ->
        PlaylistPickerDialog(video = video, controller = controller, onDismiss = { playlistTargetId = null })
    }
}

@Composable
private fun ProfileScaffold(
    state: AppUiState,
    controller: IwaraAppController,
    ownProfile: Boolean,
    content: LazyListScope.(ProfileDetail) -> Unit,
) {
    val detail = state.profile.detail
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MainBottomBar(
                route = state.route,
                isOwnProfile = ownProfile,
                onOpenHome = controller::openFeed,
                onOpenAi = controller::openAi,
                onOpenMy = { controller.openOwnProfile(site = state.profile.site) },
            )
        },
    ) { paddingValues ->
        when {
            state.profile.loading && detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(profileGradient(state.profile.site))
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.profile.error != null && detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(profileGradient(state.profile.site))
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.profile.error, color = MaterialTheme.colorScheme.error)
                }
            }

            detail != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(profileGradient(state.profile.site))
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content(detail)
                    item { Spacer(modifier = Modifier.size(4.dp)) }
                }
                if (state.profile.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeroCard(
    detail: ProfileDetail,
    site: IwaraSite,
    title: String,
    onBack: (() -> Unit)?,
    onOpenSearch: () -> Unit,
    onOpenDownloads: (() -> Unit)?,
    onLogout: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                } else {
                    SitePill(site = site)
                }
                if (onBack != null) {
                    SitePill(site = site)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarImage(detail.user.avatarUrl, detail.user.name, modifier = Modifier.size(68.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.user.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("@${detail.user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!detail.body.isNullOrBlank()) {
                Text(detail.body, style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(onClick = onOpenSearch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.action_search))
                }
                if (onOpenDownloads != null) {
                    FilledTonalButton(onClick = onOpenDownloads, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.action_download_list))
                    }
                }
                if (onLogout != null) {
                    TextButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.action_logout))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatPill(label = stringResource(R.string.section_videos), value = detail.videoCount)
                StatPill(label = stringResource(R.string.section_images), value = detail.imageCount)
            }
        }
    }
}

private fun LazyListScope.profileWorksSection(
    state: AppUiState,
    controller: IwaraAppController,
    detail: ProfileDetail,
    ownProfile: Boolean,
    onAddToPlaylist: (String) -> Unit,
) {
    val hasWorks = detail.videoCount > 0 || detail.imageCount > 0
    if (!hasWorks) return

    item { SectionTitle(if (ownProfile) "我的作品" else "TA 的作品") }
    item {
        ContentTypeToggleBar(
            selectedType = state.profile.contentType,
            onSelected = controller::openProfileContentType,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
    when (state.profile.contentType) {
        ContentType.Videos -> {
            items(detail.videos, key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    onOpen = { controller.openVideo(video.id, state.profile.site) },
                    onOpenProfile = { controller.openProfile(video.authorUsername, site = state.profile.site) },
                    onAddToPlaylist = { onAddToPlaylist(video.id) },
                )
            }
            item {
                PaginationBar(
                    currentPage = detail.videoPage,
                    totalCount = detail.videoCount,
                    pageSize = detail.videoLimit,
                    onPageSelected = { page ->
                        if (ownProfile) {
                            controller.openOwnProfile(videoPage = page, imagePage = detail.imagePage, contentType = ContentType.Videos, site = state.profile.site)
                        } else {
                            controller.openProfile(detail.user.username, videoPage = page, imagePage = detail.imagePage, contentType = ContentType.Videos, site = state.profile.site)
                        }
                    },
                )
            }
        }

        ContentType.Images -> {
            items(detail.images, key = { it.id }) { image ->
                ImageRow(
                    image = image,
                    onOpen = { controller.openImage(image.id, state.profile.site) },
                    onOpenProfile = { controller.openProfile(image.authorUsername, site = state.profile.site) },
                )
            }
            item {
                PaginationBar(
                    currentPage = detail.imagePage,
                    totalCount = detail.imageCount,
                    pageSize = detail.imageLimit,
                    onPageSelected = { page ->
                        if (ownProfile) {
                            controller.openOwnProfile(videoPage = detail.videoPage, imagePage = page, contentType = ContentType.Images, site = state.profile.site)
                        } else {
                            controller.openProfile(detail.user.username, videoPage = detail.videoPage, imagePage = page, contentType = ContentType.Images, site = state.profile.site)
                        }
                    },
                )
            }
        }
    }
}

private fun LazyListScope.profileCommentsSection(
    state: AppUiState,
    controller: IwaraAppController,
    detail: ProfileDetail,
) {
    item { SectionTitle(stringResource(R.string.section_profile_comments)) }
    items(detail.comments, key = { it.id }) { comment ->
        CommentRow(comment = comment, onOpenProfile = { controller.openProfile(comment.authorUsername, site = state.profile.site) })
    }
    item {
        CommentComposer(
            label = stringResource(R.string.label_leave_profile_comment),
            submitting = state.profile.commentSubmitting,
            error = state.profile.commentError,
            onSubmit = controller::submitProfileComment,
        )
    }
}

@Composable
private fun SitePill(site: IwaraSite) {
    val container = if (site == IwaraSite.Ai) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (site == IwaraSite.Ai) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text = if (site == IwaraSite.Ai) stringResource(R.string.title_ai) else stringResource(R.string.app_name_short),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: Int,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Text(
            text = "$label $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun feedForSite(state: AppUiState, site: IwaraSite) = if (site == IwaraSite.Ai) state.aiFeed else state.feed

@Composable
private fun profileGradient(site: IwaraSite): Brush {
    val colors = MaterialTheme.colorScheme
    val lead = if (site == IwaraSite.Ai) colors.tertiaryContainer else colors.secondaryContainer
    return Brush.verticalGradient(listOf(lead.copy(alpha = 0.20f), colors.background, colors.surface))
}
