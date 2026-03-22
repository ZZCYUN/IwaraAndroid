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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.ContentType
import junzi.iwara.model.FeedUiState
import junzi.iwara.model.ImageAsset
import junzi.iwara.model.IwaraSite
import junzi.iwara.ui.AsyncRemoteImage

@Composable
fun ReaderImageViewerScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val detail = state.imageViewer.detail
    val site = state.imageViewer.site
    val sourceFeed = feedForSite(state, site)
    BackHandler(onBack = controller::closeImageViewer)

    when {
        state.imageViewer.loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(readerGradient(site)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.imageViewer.error != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(readerGradient(site)),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.imageViewer.error, color = MaterialTheme.colorScheme.error)
            }
        }

        detail != null -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(readerGradient(site)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ReaderHeader(
                        state = state,
                        onBack = controller::closeImageViewer,
                        onOpenProfile = { controller.openProfile(detail.authorUsername, site = site) },
                        onOpenTag = { tag ->
                            controller.loadFeed(
                                sort = sourceFeed.sort,
                                tag = tag,
                                page = 0,
                                contentType = ContentType.Images,
                                site = site,
                            )
                        },
                    )
                }
                if (state.imageViewer.commentsLoading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
                itemsIndexed(detail.assets, key = { _, asset -> asset.id }) { index, asset ->
                    ReaderPageCard(asset = asset, index = index, total = detail.assets.size)
                }
                item { SectionTitle(stringResource(R.string.section_comments)) }
                items(state.imageViewer.comments, key = { it.id }) { comment ->
                    CommentRow(comment = comment, onOpenProfile = { controller.openProfile(comment.authorUsername, site = site) })
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

@Composable
private fun ReaderHeader(
    state: AppUiState,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val detail = state.imageViewer.detail ?: return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                ReaderSitePill(state.imageViewer.site)
            }
            Text(detail.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "@${detail.authorUsername} · ${stringResource(R.string.label_views_likes, detail.views, detail.likes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            if (detail.description.isNotBlank()) {
                Text(detail.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (detail.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    detail.tags.forEach { tag ->
                        AssistChip(onClick = { onOpenTag(tag) }, label = { Text(tag) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPageCard(
    asset: ImageAsset,
    index: Int,
    total: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.label_image_page_indicator, index + 1, total),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            AsyncRemoteImage(
                url = asset.originalUrl,
                contentDescription = asset.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
            )
        }
    }
}

@Composable
private fun ReaderSitePill(site: IwaraSite) {
    val container = if (site == IwaraSite.Ai) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (site == IwaraSite.Ai) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text = if (site == IwaraSite.Ai) stringResource(R.string.title_ai) else stringResource(R.string.app_name_short),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun feedForSite(state: AppUiState, site: IwaraSite): FeedUiState = if (site == IwaraSite.Ai) state.aiFeed else state.feed

@Composable
private fun readerGradient(site: IwaraSite): Brush {
    val colors = MaterialTheme.colorScheme
    val lead = if (site == IwaraSite.Ai) colors.tertiaryContainer else colors.secondaryContainer
    return Brush.verticalGradient(
        listOf(
            lead.copy(alpha = 0.24f),
            colors.background,
            colors.surface,
        ),
    )
}
