package junzi.iwara

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import junzi.iwara.model.FeedSort
import junzi.iwara.model.FeedUiState
import junzi.iwara.model.IwaraSite
import junzi.iwara.model.SearchType
import junzi.iwara.model.SearchUiState

@Composable
fun TvHomeScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    SiteHomeScreen(
        site = IwaraSite.Tv,
        state = state,
        controller = controller,
        feed = state.feed,
    )
}

@Composable
fun AiHomeScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    SiteHomeScreen(
        site = IwaraSite.Ai,
        state = state,
        controller = controller,
        feed = state.aiFeed,
    )
}

@Composable
private fun SiteHomeScreen(
    site: IwaraSite,
    state: AppUiState,
    controller: IwaraAppController,
    feed: FeedUiState,
) {
    var playlistTargetId by rememberSaveable(site.name) { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MainBottomBar(
                route = state.route,
                isOwnProfile = false,
                onOpenHome = controller::openFeed,
                onOpenAi = controller::openAi,
                onOpenMy = { controller.openOwnProfile(site = site) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(monetPageGradient(site))
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HomeControlCard(
                    selected = feed.contentType,
                    onSelected = controller::openFeedContentType,
                    onSearch = controller::openSearch,
                )
            }
            item {
                FeedSortRow(
                    currentSort = feed.sort,
                    onSelect = { sort ->
                        controller.loadFeed(
                            sort = sort,
                            tag = feed.selectedTag,
                            page = 0,
                            contentType = feed.contentType,
                            site = site,
                        )
                    },
                )
            }
            if (feed.categories.isNotEmpty()) {
                item {
                    CategoryRow(
                        selectedTag = feed.selectedTag,
                        categories = feed.categories,
                        onSelect = { tag ->
                            controller.loadFeed(
                                sort = feed.sort,
                                tag = tag,
                                page = 0,
                                contentType = feed.contentType,
                                site = site,
                            )
                        },
                    )
                }
            }
            if (feed.loading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
            feed.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            homeFeedItems(
                feed = feed,
                site = site,
                controller = controller,
                onAddToPlaylist = { playlistTargetId = it },
            )
            item {
                PaginationBar(
                    currentPage = feed.page,
                    totalCount = feed.count,
                    pageSize = feed.limit,
                    onPageSelected = { page ->
                        controller.loadFeed(
                            sort = feed.sort,
                            tag = feed.selectedTag,
                            page = page,
                            contentType = feed.contentType,
                            site = site,
                        )
                    },
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    feed.videos.firstOrNull { it.id == playlistTargetId }?.let { video ->
        PlaylistPickerDialog(video = video, controller = controller, onDismiss = { playlistTargetId = null })
    }
}

private fun LazyListScope.homeFeedItems(
    feed: FeedUiState,
    site: IwaraSite,
    controller: IwaraAppController,
    onAddToPlaylist: (String) -> Unit,
) {
    when (feed.contentType) {
        ContentType.Videos -> {
            items(feed.videos, key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    onOpen = { controller.openVideo(video.id, site) },
                    onOpenProfile = { controller.openProfile(video.authorUsername, site = site) },
                    onAddToPlaylist = { onAddToPlaylist(video.id) },
                )
            }
        }

        ContentType.Images -> {
            items(feed.images, key = { it.id }) { image ->
                ImageRow(
                    image = image,
                    onOpen = { controller.openImage(image.id, site) },
                    onOpenProfile = { controller.openProfile(image.authorUsername, site = site) },
                )
            }
        }
    }
}

@Composable
fun TvSearchScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    SiteSearchScreen(
        site = IwaraSite.Tv,
        feed = state.feed,
        searchState = state.search,
        controller = controller,
    )
}

@Composable
fun AiSearchScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    SiteSearchScreen(
        site = IwaraSite.Ai,
        feed = state.aiFeed,
        searchState = state.aiSearch,
        controller = controller,
    )
}

@Composable
private fun SiteSearchScreen(
    site: IwaraSite,
    feed: FeedUiState,
    searchState: SearchUiState,
    controller: IwaraAppController,
) {
    BackHandler {
        controller.loadFeed(
            sort = feed.sort,
            tag = feed.selectedTag,
            page = feed.page,
            contentType = feed.contentType,
            site = site,
        )
    }
    var query by rememberSaveable(site.name, searchState.query) { mutableStateOf(searchState.query) }
    var playlistTargetId by rememberSaveable(site.name) { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(monetPageGradient(site))
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SearchControlCard(
                    site = site,
                    query = query,
                    onQueryChange = { query = it },
                    selectedType = searchState.type,
                    onTypeSelected = { type -> controller.search(query, type, page = 0, site = site) },
                    onSearch = { controller.search(query, searchState.type, page = 0, site = site) },
                    onBack = {
                        controller.loadFeed(
                            sort = feed.sort,
                            tag = feed.selectedTag,
                            page = feed.page,
                            contentType = feed.contentType,
                            site = site,
                        )
                    },
                )
            }
            if (searchState.loading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
            searchState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            when (searchState.type) {
                SearchType.Videos -> items(searchState.videoResults, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        onOpen = { controller.openVideo(video.id, site) },
                        onOpenProfile = { controller.openProfile(video.authorUsername, site = site) },
                        onAddToPlaylist = { playlistTargetId = video.id },
                    )
                }

                SearchType.Images -> items(searchState.imageResults, key = { it.id }) { image ->
                    ImageRow(
                        image = image,
                        onOpen = { controller.openImage(image.id, site) },
                        onOpenProfile = { controller.openProfile(image.authorUsername, site = site) },
                    )
                }

                SearchType.Users -> items(searchState.userResults, key = { it.id }) { user ->
                    UserRow(user = user, onOpen = { controller.openProfile(user.username, site = site) })
                }
            }
            item {
                PaginationBar(
                    currentPage = searchState.page,
                    totalCount = searchState.count,
                    pageSize = searchState.limit,
                    onPageSelected = { page -> controller.search(searchState.query, searchState.type, page, site) },
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    searchState.videoResults.firstOrNull { it.id == playlistTargetId }?.let { video ->
        PlaylistPickerDialog(video = video, controller = controller, onDismiss = { playlistTargetId = null })
    }
}

@Composable
private fun HomeControlCard(
    selected: ContentType,
    onSelected: (ContentType) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContentTypeToggleBar(
                selectedType = selected,
                onSelected = onSelected,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
            }
        }
    }
}

@Composable
private fun SearchControlCard(
    site: IwaraSite,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedType: SearchType,
    onTypeSelected: (SearchType) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                SiteLabel(site = site)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_search_query)) },
                )
                FilledTonalButton(
                    onClick = onSearch,
                    enabled = query.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            }
            SearchTypeRow(selected = selectedType, onSelected = onTypeSelected)
        }
    }
}

@Composable
private fun monetPageGradient(site: IwaraSite): Brush {
    val colors = MaterialTheme.colorScheme
    val lead = if (site == IwaraSite.Ai) colors.tertiaryContainer else colors.secondaryContainer
    return Brush.verticalGradient(
        listOf(
            lead.copy(alpha = 0.20f),
            colors.background,
            colors.surface,
        ),
    )
}

@Composable
private fun SiteLabel(site: IwaraSite) {
    val container = if (site == IwaraSite.Ai) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (site == IwaraSite.Ai) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text = if (site == IwaraSite.Ai) stringResource(R.string.title_ai) else stringResource(R.string.app_name_short),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

@Composable
private fun FeedSortRow(
    currentSort: FeedSort,
    onSelect: (FeedSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FeedSort.entries.forEach { sort ->
            FilterChip(
                selected = currentSort == sort,
                onClick = { onSelect(sort) },
                label = { Text(stringResource(sort.labelRes)) },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    selectedTag: String?,
    categories: List<String>,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(
            selected = selectedTag == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedTag == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun SearchTypeRow(
    selected: SearchType,
    onSelected: (SearchType) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(stringResource(type.labelRes)) },
            )
        }
    }
}
