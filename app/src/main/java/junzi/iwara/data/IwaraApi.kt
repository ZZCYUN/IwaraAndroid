package junzi.iwara.data

import android.net.Uri
import junzi.iwara.BrowserBridge
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class IwaraApi {
    fun login(email: String, password: String): String {
        val response = requestJsonObject(
            method = "POST",
            url = apiUrl("/user/login"),
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString(),
        )
        return response.getString("token")
    }

    fun fetchCurrentUser(refreshToken: String): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl("/user"),
            bearerToken = refreshToken,
        )

    fun fetchAccessToken(refreshToken: String): String {
        val response = requestJsonObject(
            method = "POST",
            url = apiUrl("/user/token"),
            bearerToken = refreshToken,
        )
        return response.getString("accessToken")
    }

    fun fetchConfig(bearerToken: String? = null): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl("/config"),
            bearerToken = bearerToken,
        )

    fun fetchVideosPage(
        params: Map<String, String>,
        bearerToken: String? = null,
    ): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/videos", params)),
            bearerToken = bearerToken,
        )

    fun fetchVideos(
        params: Map<String, String>,
        bearerToken: String? = null,
    ): JSONArray =
        fetchVideosPage(params, bearerToken).optJSONArray("results") ?: JSONArray()

    fun fetchImages(
        params: Map<String, String>,
        bearerToken: String? = null,
    ): JSONArray {
        val response = requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/images", params)),
            bearerToken = bearerToken,
        )
        return response.optJSONArray("results") ?: JSONArray()
    }

    fun fetchPlaylists(
        params: Map<String, String>,
        bearerToken: String? = null,
    ): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/playlists", params)),
            bearerToken = bearerToken,
        )

    fun fetchPlaylist(
        id: String,
        params: Map<String, String> = emptyMap(),
        bearerToken: String? = null,
    ): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/playlist/$id", params)),
            bearerToken = bearerToken,
        )

    fun fetchUserContent(
        userId: String,
        type: String,
        params: Map<String, String>,
        bearerToken: String,
    ): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/user/$userId/content/$type", params)),
            bearerToken = bearerToken,
        )

    fun fetchSearchResults(
        query: String,
        type: String,
        page: Int = 0,
        bearerToken: String? = null,
    ): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/search", mapOf("type" to type, "page" to page.toString(), "query" to query))),
            bearerToken = bearerToken,
        )

    fun fetchVideo(id: String, bearerToken: String? = null): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl("/video/$id"),
            bearerToken = bearerToken,
        )

    fun fetchProfile(username: String, bearerToken: String? = null): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl("/profile/$username"),
            bearerToken = bearerToken,
        )

    fun fetchFollowers(userId: String, limit: Int = 6): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/user/$userId/followers", mapOf("limit" to limit.toString()))),
        )

    fun fetchFollowing(userId: String, limit: Int = 6): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/user/$userId/following", mapOf("limit" to limit.toString()))),
        )

    fun fetchComments(
        targetType: String,
        targetId: String,
        page: Int = 0,
        bearerToken: String? = null,
    ): JSONObject =
        requestJsonObject(
            method = "GET",
            url = apiUrl(buildPath("/$targetType/$targetId/comments", mapOf("page" to page.toString()))),
            bearerToken = bearerToken,
        )

    fun createComment(
        targetType: String,
        targetId: String,
        body: String,
        bearerToken: String,
        parentId: String? = null,
    ) {
        val payload = JSONObject().put("body", body)
        parentId?.let { payload.put("parentId", it) }
        request(
            method = "POST",
            url = apiUrl("/$targetType/$targetId/comments"),
            body = payload.toString(),
            bearerToken = bearerToken,
        )
    }

    fun createPlaylist(
        title: String,
        bearerToken: String,
    ): JSONObject =
        requestJsonObject(
            method = "POST",
            url = apiUrl("/playlists"),
            body = JSONObject().put("title", title).toString(),
            bearerToken = bearerToken,
        )

    fun addToPlaylist(
        playlistId: String,
        videoId: String,
        bearerToken: String,
    ) {
        request(
            method = "POST",
            url = apiUrl("/playlist/$playlistId/$videoId"),
            bearerToken = bearerToken,
        )
    }

    fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        bearerToken: String,
    ) {
        request(
            method = "DELETE",
            url = apiUrl("/playlist/$playlistId/$videoId"),
            bearerToken = bearerToken,
        )
    }
    fun deletePlaylist(
        playlistId: String,
        bearerToken: String,
    ) {
        request(
            method = "DELETE",
            url = apiUrl("/playlist/$playlistId"),
            bearerToken = bearerToken,
        )
    }

    fun sendView(id: String, bearerToken: String? = null) {
        request(
            method = "POST",
            url = apiUrl("/video/$id/view"),
            body = """{"stats":null}""",
            bearerToken = bearerToken,
        )
    }

    fun fetchFileVariants(
        fileUrl: String,
        title: String,
        videoId: String,
        bearerToken: String?,
    ): JSONArray {
        val enrichedUrl = addDownloadName(fileUrl, title, videoId)
        val xVersion = computeXVersion(enrichedUrl)
        return requestJsonArray(
            method = "GET",
            url = enrichedUrl,
            bearerToken = bearerToken,
            extraHeaders = mapOf("X-Version" to xVersion),
        )
    }

    private fun requestJsonObject(
        method: String,
        url: String,
        body: String? = null,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val response = request(method, url, body, bearerToken, extraHeaders)
        return JSONObject(response.body)
    }

    private fun requestJsonArray(
        method: String,
        url: String,
        body: String? = null,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONArray {
        val response = request(method, url, body, bearerToken, extraHeaders)
        return JSONArray(response.body)
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResponse {
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "User-Agent" to USER_AGENT,
        )
        bearerToken?.let { headers["Authorization"] = "Bearer $it" }
        extraHeaders.forEach { (key, value) -> headers[key] = value }

        if (BrowserBridge.isAttached()) {
            val bridgeResponse = runBlocking { BrowserBridge.fetch(method, url, headers, body) }
            if (bridgeResponse.statusCode !in 200..299) {
                val message = parseErrorMessage(bridgeResponse.body)
                throw IwaraApiException("$url :: $message", bridgeResponse.statusCode, bridgeResponse.body)
            }
            return ApiResponse(bridgeResponse.statusCode, bridgeResponse.body)
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        if (body != null) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        val responseBody = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        connection.disconnect()

        if (statusCode !in 200..299) {
            val message = parseErrorMessage(responseBody)
            throw IwaraApiException("$url :: $message", statusCode, responseBody)
        }

        return ApiResponse(statusCode, responseBody)
    }

    private fun parseErrorMessage(body: String): String {
        if (body.isBlank()) {
            return "Request failed"
        }
        return runCatching { JSONObject(body).optString("message") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body
    }

    private fun buildPath(path: String, params: Map<String, String>): String {
        if (params.isEmpty()) return path
        return path + "?" + params.entries.joinToString("&") { (key, value) ->
            "${Uri.encode(key)}=${Uri.encode(value)}"
        }
    }

    private fun addDownloadName(fileUrl: String, title: String, videoId: String): String {
        val uri = Uri.parse(fileUrl)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { value -> builder.appendQueryParameter(key, value) }
        }
        if (uri.getQueryParameter("download").isNullOrBlank()) {
            builder.appendQueryParameter("download", "Iwara - $title [$videoId].mp4")
        }
        return builder.build().toString()
    }

    private fun computeXVersion(fileUrl: String): String {
        val uri = Uri.parse(fileUrl)
        val fileName = uri.lastPathSegment.orEmpty()
        val expires = uri.getQueryParameter("expires").orEmpty()
        val seed = "${fileName}_${expires}_${X_VERSION_SALT}"
        val digest = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun apiUrl(path: String): String = "${API_BASE}${path}"

    private data class ApiResponse(
        val statusCode: Int,
        val body: String,
    )

    companion object {
        private const val API_BASE = "https://apiq.iwara.tv"
        private const val USER_AGENT =
            "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        private const val X_VERSION_SALT = "mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN"
    }
}

class IwaraApiException(
    override val message: String,
    val statusCode: Int,
    val body: String,
) : RuntimeException(message)




