package junzi.iwara

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var playlistTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = controller::closePlaylist)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.playlist.detail?.playlist?.title ?: stringResource(R.string.label_loading)) },
                navigationIcon = {
                    IconButton(onClick = controller::closePlaylist) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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

            state.playlist.detail != null -> {
                PlaylistDetailBody(
                    detail = state.playlist.detail,
                    onOpenVideo = controller::openVideo,
                    onOpenProfile = controller::openProfile,
                    onAddToPlaylist = { playlistTargetId = it },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }

        state.playlist.detail?.videos?.firstOrNull { it.id == playlistTargetId }?.let { video ->
            PlaylistPickerDialog(video = video, controller = controller, onDismiss = { playlistTargetId = null })
        }
    }
}

@Composable
private fun PlaylistDetailBody(
    detail: PlaylistDetail,
    onOpenVideo: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
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
                onAddToPlaylist = { onAddToPlaylist(video.id) },
            )
        }
    }
}
