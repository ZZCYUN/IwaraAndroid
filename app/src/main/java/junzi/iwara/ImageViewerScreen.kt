package junzi.iwara

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.ContentType
import junzi.iwara.model.ImageAsset
import junzi.iwara.ui.AsyncRemoteImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val detail = state.imageViewer.detail
    BackHandler(onBack = controller::closeImageViewer)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.title ?: stringResource(R.string.label_loading),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = controller::closeImageViewer) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.imageViewer.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.imageViewer.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.imageViewer.error, color = MaterialTheme.colorScheme.error)
                }
            }

            detail != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = "@${detail.authorUsername} · ${stringResource(R.string.label_views_likes, detail.views, detail.likes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { controller.openProfile(detail.authorUsername) },
                            )
                            if (detail.tags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    detail.tags.forEach { tag ->
                                        AssistChip(
                                            onClick = {
                                                controller.loadFeed(
                                                    sort = state.feed.sort,
                                                    tag = tag,
                                                    page = 0,
                                                    contentType = ContentType.Images,
                                                )
                                            },
                                            label = { Text(tag) },
                                        )
                                    }
                                }
                            }
                            if (detail.description.isNotBlank()) {
                                Text(detail.description, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    itemsIndexed(detail.assets, key = { _, asset -> asset.id }) { index, asset ->
                        ImagePreviewCard(asset = asset, index = index, total = detail.assets.size)
                    }
                    item { SectionTitle(stringResource(R.string.section_comments)) }
                    if (state.imageViewer.commentsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(state.imageViewer.comments, key = { comment -> comment.id }) { comment ->
                        CommentRow(comment = comment, onOpenProfile = { controller.openProfile(comment.authorUsername) })
                    }
                    item {
                        CommentComposer(
                            label = stringResource(R.string.label_add_comment),
                            submitting = state.imageViewer.commentSubmitting,
                            error = state.imageViewer.commentError,
                            onSubmit = controller::submitImageComment,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewCard(
    asset: ImageAsset,
    index: Int,
    total: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.label_image_page_indicator, index + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
        )
        AsyncRemoteImage(
            url = asset.originalUrl,
            contentDescription = asset.name,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
