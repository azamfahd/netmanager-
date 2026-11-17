package com.example.data.local

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CachedPortalMeta(
    val host: String,
    val serverName: String,
    val baseUrl: String,
    val lastVisitedUrl: String,
    val lastPageTitle: String = "",
    val targetPathUrl: String? = null,
    val username: String = "",
    val isAplusWifi: Boolean = false,
    val isMikroTikWebFig: Boolean = false,
    val archiveFilePath: String? = null,
    val lastSavedTime: Long = 0L
) {
    val hasValidArchive: Boolean
        get() = !archiveFilePath.isNullOrBlank() && File(archiveFilePath).let { it.exists() && it.length() > 0L }
}

object WebPortalCacheManager {

    private const val DIR_NAME = "portal_cache"
    private const val META_FILE = "meta.json"
    private const val ARCHIVE_FILE = "archive_latest.mht"

    fun extractHostKey(hostOrUrl: String): String {
        val trimmed = hostOrUrl.trim()
        val parsedHost = runCatching {
            val uri = Uri.parse(if (trimmed.contains("://")) trimmed else "https://$trimmed")
            uri.host
        }.getOrNull()
        val raw = (parsedHost ?: trimmed.substringBefore("/").substringBefore(":")).lowercase()
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "default_portal" }
    }

    private fun getHostDir(context: Context, hostOrUrl: String): File {
        return File(File(context.filesDir, DIR_NAME), extractHostKey(hostOrUrl)).apply { mkdirs() }
    }

    fun getArchiveTargetFile(context: Context, hostOrUrl: String): File {
        return File(getHostDir(context, hostOrUrl), ARCHIVE_FILE)
    }

    @Synchronized
    fun saveMeta(context: Context, meta: CachedPortalMeta) {
        try {
            val dir = getHostDir(context, meta.host.ifBlank { meta.baseUrl })
            val target = File(dir, META_FILE)
            val tmp = File(dir, "$META_FILE.tmp")
            val json = JSONObject().apply {
                put("host", meta.host)
                put("serverName", meta.serverName)
                put("baseUrl", meta.baseUrl)
                put("lastVisitedUrl", meta.lastVisitedUrl)
                put("lastPageTitle", meta.lastPageTitle)
                put("targetPathUrl", meta.targetPathUrl ?: "")
                put("username", meta.username)
                put("isAplusWifi", meta.isAplusWifi)
                put("isMikroTikWebFig", meta.isMikroTikWebFig)
                put("archiveFilePath", meta.archiveFilePath ?: "")
                put("lastSavedTime", meta.lastSavedTime)
            }
            tmp.writeText(json.toString())
            if (tmp.exists()) {
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun loadMeta(context: Context, hostOrUrl: String): CachedPortalMeta? {
        if (hostOrUrl.isBlank()) return null
        return try {
            val dir = File(File(context.filesDir, DIR_NAME), extractHostKey(hostOrUrl))
            val file = File(dir, META_FILE)
            if (!file.exists()) return null
            parseMeta(file)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getLatestCachedPortal(context: Context): CachedPortalMeta? {
        return try {
            val root = File(context.filesDir, DIR_NAME)
            if (!root.exists()) return null
            val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
            dirs.mapNotNull { dir ->
                val metaFile = File(dir, META_FILE)
                if (metaFile.exists()) parseMeta(metaFile) else null
            }.maxByOrNull { it.lastSavedTime }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMeta(file: File): CachedPortalMeta? {
        return try {
            val json = JSONObject(file.readText())
            val archivePath = json.optString("archiveFilePath").takeIf { it.isNotBlank() }
            val host = json.optString("host")
            val baseUrl = json.optString("baseUrl")
            if (host.isBlank() && baseUrl.isBlank()) return null
            CachedPortalMeta(
                host = host,
                serverName = json.optString("serverName").ifBlank { host },
                baseUrl = baseUrl,
                lastVisitedUrl = json.optString("lastVisitedUrl").ifBlank { baseUrl },
                lastPageTitle = json.optString("lastPageTitle"),
                targetPathUrl = json.optString("targetPathUrl").takeIf { it.isNotBlank() },
                username = json.optString("username"),
                isAplusWifi = json.optBoolean("isAplusWifi", false),
                isMikroTikWebFig = json.optBoolean("isMikroTikWebFig", false),
                archiveFilePath = archivePath,
                lastSavedTime = json.optLong("lastSavedTime", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun formatSavedTimeAr(timestamp: Long): String {
        if (timestamp <= 0L) return "غير محفوظ بعد"
        val diffMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val mins = diffMs / 60_000L
        val hours = mins / 60L
        val exact = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        return when {
            mins < 1 -> "الآن ($exact)"
            mins < 60 -> "منذ $mins دقيقة ($exact)"
            hours < 24 -> "منذ $hours ساعة ($exact)"
            else -> exact
        }
    }
}
