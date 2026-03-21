package junzi.iwara.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import junzi.iwara.R
import junzi.iwara.model.DownloadListItem
import junzi.iwara.model.DownloadStatus
import junzi.iwara.model.VideoDetail
import junzi.iwara.model.VideoVariant
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class IwaraDownloads(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
        ?: error("DownloadManager unavailable")
    private val indexFile = File(appContext.filesDir, "iwara-download-index.json")

    fun enqueue(detail: VideoDetail, variant: VideoVariant): DownloadListItem {
        require(variant.downloadUrl.isNotBlank()) { "Download URL unavailable" }

        val qualityLabel = variantLabel(variant)
        val fileName = buildFileName(detail.title, detail.id, qualityLabel)
        val record = DownloadRecord(
            downloadId = 0L,
            videoId = detail.id,
            title = detail.title,
            thumbnailUrl = detail.posterUrl,
            qualityLabel = qualityLabel,
            fileName = fileName,
            createdAtMs = System.currentTimeMillis(),
            downloadUrl = variant.downloadUrl,
            lastKnownStatus = DownloadStatus.Pending,
            lastKnownProgressPercent = 0,
        )
        val storedRecord = enqueueRecord(record)
        upsertRecord(storedRecord)
        return resolveItem(storedRecord, queryStatuses(longArrayOf(storedRecord.downloadId))[storedRecord.downloadId])
    }

    fun list(): List<DownloadListItem> {
        val records = readRecords().sortedByDescending { it.createdAtMs }
        if (records.isEmpty()) return emptyList()

        val statuses = queryStatuses(records.map { it.downloadId }.toLongArray())
        val refreshedRecords = ArrayList<DownloadRecord>(records.size)
        val items = ArrayList<DownloadListItem>(records.size)
        records.forEach { record ->
            val state = statuses[record.downloadId]
            val item = resolveItem(record, state)
            refreshedRecords += refreshRecord(record, state, item)
            items += item
        }
        if (refreshedRecords != records) {
            writeRecords(refreshedRecords)
        }
        return items
    }

    fun delete(downloadId: Long) {
        val records = readRecords()
        val record = records.firstOrNull { it.downloadId == downloadId }
        if (record != null) {
            removeArtifacts(record)
            writeRecords(records.filterNot { it.downloadId == downloadId })
            return
        }
        runCatching { downloadManager.remove(downloadId) }
    }

    fun retry(downloadId: Long): DownloadListItem {
        val records = readRecords()
        val record = records.firstOrNull { it.downloadId == downloadId }
            ?: throw IllegalArgumentException(appContext.getString(R.string.error_download_failed))
        val sourceUrl = record.downloadUrl.ifBlank {
            queryStatuses(longArrayOf(downloadId))[downloadId]?.sourceUrl.orEmpty()
        }
        if (sourceUrl.isBlank()) {
            throw IllegalStateException(appContext.getString(R.string.error_download_retry_unavailable))
        }

        removeArtifacts(record)
        val nextRecord = enqueueRecord(
            record.copy(
                downloadId = 0L,
                createdAtMs = System.currentTimeMillis(),
                downloadUrl = sourceUrl,
                lastKnownStatus = DownloadStatus.Pending,
                lastKnownProgressPercent = 0,
            ),
        )
        writeRecords(records.filterNot { it.downloadId == downloadId } + nextRecord)
        return resolveItem(nextRecord, queryStatuses(longArrayOf(nextRecord.downloadId))[nextRecord.downloadId])
    }

    private fun enqueueRecord(record: DownloadRecord): DownloadRecord {
        val downloadId = downloadManager.enqueue(buildRequest(record))
        return record.copy(downloadId = downloadId)
    }

    private fun buildRequest(record: DownloadRecord): DownloadManager.Request =
        DownloadManager.Request(Uri.parse(record.downloadUrl))
            .setMimeType("video/mp4")
            .setTitle(record.title.ifBlank { record.fileName })
            .setDescription(record.qualityLabel)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, record.fileName)

    private fun queryStatuses(ids: LongArray): Map<Long, DownloadQueryState> {
        if (ids.isEmpty()) return emptyMap()
        val query = DownloadManager.Query().setFilterById(*ids)
        val result = linkedMapOf<Long, DownloadQueryState>()
        downloadManager.query(query)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val downloadedIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val sourceUrlIndex = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            while (cursor.moveToNext()) {
                val downloadId = cursor.getLong(idIndex)
                val status = mapStatus(cursor.getInt(statusIndex))
                val downloaded = cursor.getLong(downloadedIndex)
                val total = cursor.getLong(totalIndex)
                val progress = if (total > 0L && downloaded >= 0L) {
                    ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                result[downloadId] = DownloadQueryState(
                    status = status,
                    progressPercent = progress,
                    localUri = cursor.getString(localUriIndex),
                    sourceUrl = if (sourceUrlIndex >= 0) cursor.getString(sourceUrlIndex) else null,
                )
            }
        }
        return result
    }

    private fun mapStatus(status: Int): DownloadStatus = when (status) {
        DownloadManager.STATUS_PENDING -> DownloadStatus.Pending
        DownloadManager.STATUS_RUNNING -> DownloadStatus.Running
        DownloadManager.STATUS_PAUSED -> DownloadStatus.Paused
        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Successful
        DownloadManager.STATUS_FAILED -> DownloadStatus.Failed
        else -> DownloadStatus.Unknown
    }

    private fun resolveItem(record: DownloadRecord, state: DownloadQueryState?): DownloadListItem {
        val localUri = resolveLocalUri(record, state?.localUri)
        val status = when {
            state != null && state.status == DownloadStatus.Successful && localUri == null -> DownloadStatus.Interrupted
            state != null -> state.status
            localUri != null -> DownloadStatus.Successful
            else -> when (record.lastKnownStatus) {
                DownloadStatus.Pending,
                DownloadStatus.Running,
                DownloadStatus.Paused,
                DownloadStatus.Successful,
                DownloadStatus.Unknown
                -> DownloadStatus.Interrupted
                DownloadStatus.Failed,
                DownloadStatus.Interrupted
                -> record.lastKnownStatus
            }
        }
        val progress = when {
            status == DownloadStatus.Successful -> 100
            state?.progressPercent != null -> state.progressPercent
            status == DownloadStatus.Interrupted -> record.lastKnownProgressPercent
            else -> null
        }
        return DownloadListItem(
            downloadId = record.downloadId,
            videoId = record.videoId,
            title = record.title,
            thumbnailUrl = record.thumbnailUrl,
            qualityLabel = record.qualityLabel,
            fileName = record.fileName,
            createdAtMs = record.createdAtMs,
            status = status,
            progressPercent = progress,
            localUri = localUri,
        )
    }

    private fun refreshRecord(
        record: DownloadRecord,
        state: DownloadQueryState?,
        item: DownloadListItem,
    ): DownloadRecord = record.copy(
        downloadUrl = state?.sourceUrl?.takeIf { it.isNotBlank() } ?: record.downloadUrl,
        lastKnownStatus = item.status,
        lastKnownProgressPercent = item.progressPercent,
    )

    private fun resolveLocalUri(record: DownloadRecord, candidateUri: String?): String? {
        candidateUri?.takeIf(::uriExists)?.let { return it }
        return expectedFile(record)
            .takeIf { it.exists() }
            ?.let { Uri.fromFile(it).toString() }
    }

    private fun uriExists(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            null, "" -> File(uriString).exists()
            "file" -> uri.path?.let(::File)?.exists() == true
            "content" -> true
            else -> false
        }
    }

    private fun expectedFile(record: DownloadRecord): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), record.fileName)

    private fun removeArtifacts(record: DownloadRecord) {
        runCatching { downloadManager.remove(record.downloadId) }
        expectedFile(record).takeIf { it.exists() }?.delete()
    }

    private fun upsertRecord(record: DownloadRecord) {
        val records = readRecords().filterNot { it.downloadId == record.downloadId } + record
        writeRecords(records)
    }

    private fun readRecords(): List<DownloadRecord> {
        if (!indexFile.exists()) return emptyList()
        val text = indexFile.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val array = JSONArray(text)
        return buildList {
            for (index in 0 until array.length()) {
                add(DownloadRecord.fromJson(array.getJSONObject(index)))
            }
        }
    }

    private fun writeRecords(records: List<DownloadRecord>) {
        val array = JSONArray()
        records.sortedByDescending { it.createdAtMs }.forEach { array.put(it.toJson()) }
        indexFile.writeText(array.toString(), Charsets.UTF_8)
    }

    private fun buildFileName(title: String, videoId: String, qualityLabel: String): String {
        val safeTitle = title
            .replace(Regex("""[\\/:*?\"<>|\p{Cntrl}]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.')
            .ifBlank { "Iwara Video" }
            .take(120)
        val safeQuality = qualityLabel
            .replace(Regex("""[\\/:*?\"<>|\p{Cntrl}]"""), "_")
            .trim(' ', '.')
            .ifBlank { "default" }
        return "$safeTitle [$safeQuality] [$videoId].mp4"
    }

    private fun variantLabel(variant: VideoVariant): String = when {
        variant.name.equals("Source", ignoreCase = true) -> "Source"
        variant.name.endsWith("p", ignoreCase = true) -> variant.name
        else -> "${variant.name}p"
    }

    private data class DownloadQueryState(
        val status: DownloadStatus,
        val progressPercent: Int?,
        val localUri: String?,
        val sourceUrl: String?,
    )

    private data class DownloadRecord(
        val downloadId: Long,
        val videoId: String,
        val title: String,
        val thumbnailUrl: String?,
        val qualityLabel: String,
        val fileName: String,
        val createdAtMs: Long,
        val downloadUrl: String,
        val lastKnownStatus: DownloadStatus,
        val lastKnownProgressPercent: Int?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("downloadId", downloadId)
            .put("videoId", videoId)
            .put("title", title)
            .put("thumbnailUrl", thumbnailUrl)
            .put("qualityLabel", qualityLabel)
            .put("fileName", fileName)
            .put("createdAtMs", createdAtMs)
            .put("downloadUrl", downloadUrl)
            .put("lastKnownStatus", lastKnownStatus.name)
            .put("lastKnownProgressPercent", lastKnownProgressPercent)

        companion object {
            fun fromJson(json: JSONObject): DownloadRecord = DownloadRecord(
                downloadId = json.optLong("downloadId"),
                videoId = json.optString("videoId"),
                title = json.optString("title"),
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() && it != "null" },
                qualityLabel = json.optString("qualityLabel"),
                fileName = json.optString("fileName"),
                createdAtMs = json.optLong("createdAtMs"),
                downloadUrl = json.optString("downloadUrl"),
                lastKnownStatus = json.optString("lastKnownStatus")
                    .takeIf { it.isNotBlank() }
                    ?.let(::statusFromName)
                    ?: DownloadStatus.Unknown,
                lastKnownProgressPercent = json.optInt("lastKnownProgressPercent", -1)
                    .takeIf { it >= 0 },
            )

            private fun statusFromName(name: String): DownloadStatus =
                runCatching { DownloadStatus.valueOf(name) }.getOrDefault(DownloadStatus.Unknown)
        }
    }
}
