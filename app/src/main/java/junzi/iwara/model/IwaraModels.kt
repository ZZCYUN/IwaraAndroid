package junzi.iwara.model

import junzi.iwara.R

enum class FeedSort(val apiValue: String, val labelRes: Int) {
    Trending("trending", R.string.feed_sort_trending),
    Latest("date", R.string.feed_sort_latest),
}

enum class SearchType(val apiValue: String, val labelRes: Int) {
    Videos("videos", R.string.search_type_videos),
    Users("users", R.string.search_type_users),
}

enum class CommentTargetType(val apiValue: String) {
    Video("video"),
    Profile("profile"),
}

enum class AppRoute {
    Login,
    Feed,
    Search,
    Profile,
    Player,
    Playlist,
    Downloads,
}

enum class DownloadStatus {
    Pending,
    Running,
    Paused,
    Successful,
    Failed,
    Interrupted,
    Unknown,
}

data class IwaraUser(
    val id: String,
    val name: String,
    val username: String,
    val email: String?,
    val avatarUrl: String?,
)

data class SessionInfo(
    val refreshToken: String,
    val accessToken: String?,
    val user: IwaraUser,
)

data class VideoSummary(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUsername: String,
    val views: Int,
    val likes: Int,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val rating: String,
    val tags: List<String> = emptyList(),
)

data class ImageSummary(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUsername: String,
    val views: Int,
    val likes: Int,
    val thumbnailUrl: String?,
)

data class PlaylistSummary(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUsername: String,
    val numVideos: Int,
    val thumbnailUrl: String?,
)

data class CommentItem(
    val id: String,
    val body: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatarUrl: String?,
    val createdAt: String,
    val numReplies: Int,
)

data class VideoVariant(
    val id: String,
    val name: String,
    val type: String,
    val viewUrl: String,
    val downloadUrl: String,
)

data class VideoDetail(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUsername: String,
    val description: String,
    val rating: String,
    val views: Int,
    val likes: Int,
    val durationSeconds: Int?,
    val posterUrl: String?,
    val fileUrl: String?,
    val tags: List<String>,
    val variants: List<VideoVariant>,
    val selectedVariantName: String?,
)

data class ProfileDetail(
    val user: IwaraUser,
    val body: String?,
    val headerUrl: String?,
    val videos: List<VideoSummary>,
    val videoPage: Int,
    val videoCount: Int,
    val videoLimit: Int,
    val images: List<ImageSummary>,
    val followers: List<IwaraUser>,
    val following: List<IwaraUser>,
    val comments: List<CommentItem>,
    val isOwnProfile: Boolean = false,
    val playlists: List<PlaylistSummary> = emptyList(),
)
data class PlaylistDetail(
    val playlist: PlaylistSummary,
    val videos: List<VideoSummary>,
    val page: Int,
    val count: Int,
    val limit: Int,
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val count: Int,
    val limit: Int,
)

data class DownloadListItem(
    val downloadId: Long,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val qualityLabel: String,
    val fileName: String,
    val createdAtMs: Long,
    val status: DownloadStatus,
    val progressPercent: Int?,
    val localUri: String?,
)

data class FeedUiState(
    val loading: Boolean = false,
    val sort: FeedSort = FeedSort.Trending,
    val videos: List<VideoSummary> = emptyList(),
    val page: Int = 0,
    val count: Int = 0,
    val limit: Int = 24,
    val error: String? = null,
    val categories: List<String> = emptyList(),
    val selectedTag: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.Videos,
    val loading: Boolean = false,
    val videoResults: List<VideoSummary> = emptyList(),
    val userResults: List<IwaraUser> = emptyList(),
    val page: Int = 0,
    val count: Int = 0,
    val limit: Int = 32,
    val error: String? = null,
)

data class PlayerUiState(
    val loading: Boolean = false,
    val detail: VideoDetail? = null,
    val comments: List<CommentItem> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentSubmitting: Boolean = false,
    val commentError: String? = null,
    val error: String? = null,
)

data class ProfileUiState(
    val loading: Boolean = false,
    val username: String? = null,
    val detail: ProfileDetail? = null,
    val commentSubmitting: Boolean = false,
    val commentError: String? = null,
    val error: String? = null,
)

data class PlaylistUiState(
    val loading: Boolean = false,
    val detail: PlaylistDetail? = null,
    val error: String? = null,
)

data class DownloadsUiState(
    val loading: Boolean = false,
    val items: List<DownloadListItem> = emptyList(),
    val error: String? = null,
    val activeItemId: Long? = null,
)

data class AppUiState(
    val bootstrapping: Boolean = true,
    val route: AppRoute = AppRoute.Login,
    val session: SessionInfo? = null,
    val loginInFlight: Boolean = false,
    val loginError: String? = null,
    val feed: FeedUiState = FeedUiState(),
    val search: SearchUiState = SearchUiState(),
    val profile: ProfileUiState = ProfileUiState(),
    val player: PlayerUiState = PlayerUiState(),
    val playlist: PlaylistUiState = PlaylistUiState(),
    val downloads: DownloadsUiState = DownloadsUiState(),
)




