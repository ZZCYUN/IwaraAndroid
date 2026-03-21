package junzi.iwara

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppUiState
import junzi.iwara.model.FeedSort
import junzi.iwara.model.SearchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    var playlistTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val feedListState = rememberLazyListState()
    var lastCompletedFeedPage by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.feed.loading, state.feed.page, state.feed.videos.size) {
        if (!state.feed.loading) {
            val previousPage = lastCompletedFeedPage
            if (previousPage != null && previousPage != state.feed.page) {
                feedListState.animateScrollToItem(0)
            }
            lastCompletedFeedPage = state.feed.page
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name_short))
                        Text(state.session?.user?.username.orEmpty())
                    }
                },
                actions = {
                    IconButton(onClick = controller::openDownloads) {
                        Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.action_download_list))
                    }
                    IconButton(onClick = controller::openSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = controller::openOwnProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.action_profile))
                    }
                    IconButton(onClick = controller::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.action_logout))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.feed.sort == sort,
                        onClick = { controller.loadFeed(sort = sort, tag = state.feed.selectedTag, page = 0) },
                        label = { Text(stringResource(sort.labelRes)) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.feed.selectedTag == null,
                    onClick = { controller.loadFeed(sort = state.feed.sort, tag = null, page = 0) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                state.feed.categories.forEach { category ->
                    FilterChip(
                        selected = state.feed.selectedTag == category,
                        onClick = { controller.loadFeed(sort = state.feed.sort, tag = category, page = 0) },
                        label = { Text(category) },
                    )
                }
                state.feed.selectedTag
                    ?.takeIf { !state.feed.categories.contains(it) }
                    ?.let { tag ->
                        FilterChip(
                            selected = true,
                            onClick = { controller.loadFeed(sort = state.feed.sort, tag = tag, page = 0) },
                            label = { Text(tag) },
                        )
                    }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.feed.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.feed.error != null) {
                Text(
                    text = state.feed.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                state = feedListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.feed.videos, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        onOpen = { controller.openVideo(video.id) },
                        onOpenProfile = { controller.openProfile(video.authorUsername) },
                        onAddToPlaylist = { playlistTargetId = video.id },
                    )
                }
                item {
                    PaginationBar(
                        currentPage = state.feed.page,
                        totalCount = state.feed.count,
                        pageSize = state.feed.limit,
                        onPageSelected = { page -> controller.loadFeed(state.feed.sort, state.feed.selectedTag, page) },
                    )
                }
            }
            state.feed.videos.firstOrNull { it.id == playlistTargetId }?.let { video ->
                PlaylistPickerDialog(video = video, controller = controller, onDismiss = { playlistTargetId = null })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    BackHandler { controller.loadFeed(state.feed.sort, state.feed.selectedTag, state.feed.page) }
    var query by rememberSaveable(state.search.query) { mutableStateOf(state.search.query) }
    var searchPlaylistTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_search)) },
                navigationIcon = {
                    IconButton(onClick = { controller.loadFeed(state.feed.sort, state.feed.selectedTag, state.feed.page) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.label_search_query)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchType.entries.forEach { type ->
                    FilterChip(
                        selected = state.search.type == type,
                        onClick = { controller.search(query, type, page = 0) },
                        label = { Text(stringResource(type.labelRes)) },
                    )
                }
                FilledTonalIconButton(onClick = { controller.search(query, state.search.type, page = 0) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search_execute))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.search.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.search.error != null) {
                Text(
                    text = state.search.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (state.search.type == SearchType.Videos) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.search.videoResults, key = { it.id }) { video ->
                        VideoRow(
                            video = video,
                            onOpen = { controller.openVideo(video.id) },
                            onOpenProfile = { controller.openProfile(video.authorUsername) },
                            onAddToPlaylist = { searchPlaylistTargetId = video.id },
                        )
                    }
                    item {
                        PaginationBar(
                            currentPage = state.search.page,
                            totalCount = state.search.count,
                            pageSize = state.search.limit,
                            onPageSelected = { page -> controller.search(state.search.query, state.search.type, page) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.search.userResults, key = { it.id }) { user ->
                        UserRow(user = user, onOpen = { controller.openProfile(user.username) })
                    }
                    item {
                        PaginationBar(
                            currentPage = state.search.page,
                            totalCount = state.search.count,
                            pageSize = state.search.limit,
                            onPageSelected = { page -> controller.search(state.search.query, state.search.type, page) },
                        )
                    }
                }
            }
            state.search.videoResults.firstOrNull { it.id == searchPlaylistTargetId }?.let { video ->
                PlaylistPickerDialog(video = video, controller = controller, onDismiss = { searchPlaylistTargetId = null })
            }
        }
    }
}








