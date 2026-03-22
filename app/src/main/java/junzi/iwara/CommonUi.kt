package junzi.iwara

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import junzi.iwara.model.AppRoute
import junzi.iwara.model.CommentItem
import junzi.iwara.model.ContentType
import junzi.iwara.model.ImageSummary
import junzi.iwara.model.IwaraUser
import junzi.iwara.model.PlaylistSummary
import junzi.iwara.model.VideoSummary
import junzi.iwara.ui.AsyncRemoteImage

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
fun MainBottomBar(
    route: AppRoute,
    isOwnProfile: Boolean,
    onOpenHome: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenMy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = route == AppRoute.Feed,
            onClick = onOpenHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_home)) },
        )
        NavigationBarItem(
            selected = route == AppRoute.Ai,
            onClick = onOpenAi,
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_ai)) },
        )
        NavigationBarItem(
            selected = route == AppRoute.Profile && isOwnProfile,
            onClick = onOpenMy,
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_my)) },
        )
    }
}

@Composable
fun ContentTypeToggleBar(
    selectedType: ContentType,
    onSelected: (ContentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ContentType.entries.forEach { type ->
                val selected = selectedType == type
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelected(type) },
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    tonalElevation = if (selected) 2.dp else 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(type.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
) {
    if (url != null) {
        AsyncRemoteImage(
            url = url,
            contentDescription = name,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SingleLineChipText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoRow(
    video: VideoSummary,
    onOpen: () -> Unit,
    onOpenProfile: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null,
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
    ) {
        AsyncRemoteImage(
            url = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier
                .size(width = 148.dp, height = 100.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${video.authorUsername}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            Text(
                text = stringResource(R.string.label_views_likes, video.views, video.likes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                video.durationSeconds?.let {
                    AssistChip(onClick = onOpen, label = { SingleLineChipText(formatDuration(it)) })
                }
                if (onAddToPlaylist != null) {
                    AssistChip(
                        onClick = onAddToPlaylist,
                        label = { SingleLineChipText(stringResource(R.string.action_add_to_playlist)) },
                    )
                }
                if (onExtraAction != null && !extraActionLabel.isNullOrBlank()) {
                    AssistChip(
                        onClick = onExtraAction,
                        label = { SingleLineChipText(extraActionLabel) },
                    )
                }
            }
        }
    }
}

@Composable
fun UserRow(
    user: IwaraUser,
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
        AvatarImage(user.avatarUrl, user.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "@${user.username}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun PlaylistRow(playlist: PlaylistSummary, onOpen: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let { if (onOpen != null) it.clickable(onClick = onOpen) else it }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncRemoteImage(
            url = playlist.thumbnailUrl,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(width = 112.dp, height = 80.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playlist.authorUsername.isNotBlank()) "@${playlist.authorUsername}" else playlist.authorName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.label_playlist_videos, playlist.numVideos),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun ImageRow(
    image: ImageSummary,
    onOpen: () -> Unit,
    onOpenProfile: () -> Unit,
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
    ) {
        AsyncRemoteImage(
            url = image.thumbnailUrl,
            contentDescription = image.title,
            modifier = Modifier
                .size(width = 112.dp, height = 112.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = image.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${image.authorUsername}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            Text(
                text = stringResource(R.string.label_views_likes, image.views, image.likes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun CommentRow(
    comment: CommentItem,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarImage(comment.authorAvatarUrl, comment.authorName)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${comment.authorName} @${comment.authorUsername}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            Text(
                text = comment.createdAt.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = comment.body, style = MaterialTheme.typography.bodySmall)
            if (comment.numReplies > 0) {
                Text(
                    text = stringResource(R.string.label_replies, comment.numReplies),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CommentComposer(
    label: String,
    submitting: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable(label) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Button(
                onClick = {
                    val payload = text.trim()
                    if (payload.isNotBlank()) {
                        onSubmit(payload)
                        text = ""
                    }
                },
                enabled = !submitting && text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.action_send_comment),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (submitting) stringResource(R.string.label_posting) else stringResource(R.string.label_post))
            }
        }
    }
}

fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun PlaylistPickerDialog(
    video: VideoSummary,
    controller: junzi.iwara.app.IwaraAppController,
    onDismiss: () -> Unit,
) {
    var playlists by remember(video.id) { mutableStateOf(emptyList<PlaylistSummary>()) }
    var loading by remember(video.id) { mutableStateOf(true) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var creating by remember(video.id) { mutableStateOf(false) }
    var newPlaylistTitle by rememberSaveable(video.id) { mutableStateOf("") }

    LaunchedEffect(video.id) {
        controller.loadOwnPlaylists { items, message ->
            playlists = items
            error = normalizePlaylistError(message)
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (loading) {
                    CircularProgressIndicator()
                }
                playlists.forEach { playlist ->
                    AssistChip(
                        onClick = {
                            controller.addVideoToPlaylist(playlist.id, video.id) { message ->
                                error = normalizePlaylistError(message)
                                if (message == null) onDismiss()
                            }
                        },
                        label = { Text(playlist.title) },
                    )
                }
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text(stringResource(R.string.label_new_playlist)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val title = newPlaylistTitle.trim()
                        if (title.isBlank()) return@TextButton
                        creating = true
                        controller.createPlaylist(title) { playlist, message ->
                            error = normalizePlaylistError(message)
                            if (playlist == null) {
                                creating = false
                                return@createPlaylist
                            }
                            controller.addVideoToPlaylist(playlist.id, video.id) { addMessage ->
                                creating = false
                                error = normalizePlaylistError(addMessage)
                                if (addMessage == null) {
                                    onDismiss()
                                }
                            }
                        }
                    },
                    enabled = !creating && newPlaylistTitle.isNotBlank(),
                ) {
                    Text(if (creating) stringResource(R.string.label_loading) else stringResource(R.string.action_create_playlist))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
    )
}

private fun normalizePlaylistError(message: String?): String? {
    if (message.isNullOrBlank()) return null
    return if (message.contains("errors.forbidden")) {
        "????????????????"
    } else {
        message
    }
}






