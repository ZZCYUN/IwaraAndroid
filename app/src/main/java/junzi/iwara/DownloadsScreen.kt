package junzi.iwara

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                LazyColumn(
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncRemoteImage(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 148.dp, height = 100.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = if (item.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
