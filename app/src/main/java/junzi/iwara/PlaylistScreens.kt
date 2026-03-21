package junzi.iwara

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.PlaylistDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val context = LocalContext.current
    val detail = state.playlist.detail
    val canEditPlaylist = detail != null && state.session?.user?.username == detail.playlist.authorUsername
    var showDeleteDialog by remember(detail?.playlist?.id, canEditPlaylist) { mutableStateOf(false) }
    BackHandler(onBack = controller::closePlaylist)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.playlist?.title ?: stringResource(R.string.label_loading)) },
                navigationIcon = {
                    IconButton(onClick = controller::closePlaylist) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (canEditPlaylist) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_playlist))
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.playlist.loading -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.playlist.error != null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.playlist.error, color = MaterialTheme.colorScheme.error)
                }
            }

            detail != null -> {
                PlaylistDetailBody(
                    detail = detail,
                    canEditPlaylist = canEditPlaylist,
                    onOpenVideo = controller::openVideo,
                    onOpenProfile = controller::openProfile,
                    onRemoveVideo = { videoId ->
                        controller.removeVideoFromPlaylist(detail.playlist.id, videoId) { message ->
                            if (message == null) {
                                val targetPage = if (detail.videos.size == 1 && detail.page > 0) detail.page - 1 else detail.page
                                controller.openPlaylist(detail.playlist.id, targetPage)
                            } else {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPageChange = { page -> controller.openPlaylist(detail.playlist.id, page) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }

    if (showDeleteDialog && detail != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.action_delete_playlist)) },
            text = { Text(stringResource(R.string.message_delete_playlist_confirm, detail.playlist.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        controller.deletePlaylist(detail.playlist.id) { message ->
                            if (message == null) {
                                controller.closePlaylist()
                            } else {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete_playlist))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }
}

@Composable
private fun PlaylistDetailBody(
    detail: PlaylistDetail,
    canEditPlaylist: Boolean,
    onOpenVideo: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onRemoveVideo: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(detail.playlist.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (detail.playlist.authorUsername.isNotBlank()) {
                        "@${detail.playlist.authorUsername} · ${detail.count}"
                    } else {
                        stringResource(R.string.label_playlist_videos, detail.count)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(detail.videos, key = { it.id }) { video ->
            VideoRow(
                video = video,
                onOpen = { onOpenVideo(video.id) },
                onOpenProfile = { onOpenProfile(video.authorUsername) },
                onAddToPlaylist = null,
                extraActionLabel = if (canEditPlaylist) stringResource(R.string.action_remove_from_playlist) else null,
                onExtraAction = if (canEditPlaylist) { { onRemoveVideo(video.id) } } else null,
            )
        }
        item {
            PaginationBar(
                currentPage = detail.page,
                totalCount = detail.count,
                pageSize = detail.limit,
                onPageSelected = onPageChange,
            )
        }
    }
}
