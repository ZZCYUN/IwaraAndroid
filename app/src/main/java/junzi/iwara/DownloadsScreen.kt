package junzi.iwara

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.DownloadListItem
import junzi.iwara.model.DownloadStatus
import junzi.iwara.ui.AsyncRemoteImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val context = LocalContext.current
    BackHandler(onBack = controller::closeDownloads)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_downloads)) },
                navigationIcon = {
                    IconButton(onClick = controller::closeDownloads) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = controller::refreshDownloads) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh_downloads))
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.downloads.loading && state.downloads.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.downloads.error != null && state.downloads.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.downloads.error, color = MaterialTheme.colorScheme.error)
                }
            }

            state.downloads.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.label_downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.downloads.loading) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    state.downloads.error?.let { error ->
                        item {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    items(state.downloads.items, key = { it.downloadId }) { item ->
                        DownloadRow(
                            item = item,
                            onOpen = {
                                controller.openDownloadedVideo(item.downloadId) { message ->
                                    if (message != null) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onRetry = {
                                controller.retryDownload(item.downloadId) { message ->
                                    if (message != null) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = {
                                controller.deleteDownload(item.downloadId) { message ->
                                    if (message != null) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadListItem,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val canOpen = item.status == DownloadStatus.Successful && item.localUri != null
    val canRetry = when (item.status) {
        DownloadStatus.Failed,
        DownloadStatus.Interrupted,
        DownloadStatus.Unknown,
        DownloadStatus.Paused
        -> true
        else -> false
    }
    val statusLabel = downloadStatusLabel(item.status)
    val statusColor = downloadStatusColor(item.status)
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surface)
        .let { modifier ->
            if (canOpen) modifier.clickable(onClick = onOpen) else modifier
        }
        .padding(12.dp)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncRemoteImage(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 132.dp, height = 90.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (item.status == DownloadStatus.Failed || item.status == DownloadStatus.Interrupted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = item.qualityLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = buildString {
                    append(statusLabel)
                    item.progressPercent?.let {
                        append(" · ")
                        append(it)
                        append('%')
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                item.progressPercent != null -> {
                    LinearProgressIndicator(
                        progress = { item.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item.status == DownloadStatus.Pending ||
                    item.status == DownloadStatus.Running ||
                    item.status == DownloadStatus.Paused -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canOpen) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_open_download))
                    }
                }
                if (canRetry) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry_download))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_download))
                }
            }
        }
    }
}

@Composable
private fun downloadStatusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.Pending -> stringResource(R.string.label_download_status_pending)
    DownloadStatus.Running -> stringResource(R.string.label_download_status_running)
    DownloadStatus.Paused -> stringResource(R.string.label_download_status_paused)
    DownloadStatus.Successful -> stringResource(R.string.label_download_status_success)
    DownloadStatus.Failed -> stringResource(R.string.label_download_status_failed)
    DownloadStatus.Interrupted -> stringResource(R.string.label_download_status_interrupted)
    DownloadStatus.Unknown -> stringResource(R.string.label_download_status_unknown)
}

@Composable
private fun downloadStatusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.Failed,
    DownloadStatus.Interrupted
    -> MaterialTheme.colorScheme.error
    DownloadStatus.Successful -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
