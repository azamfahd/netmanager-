package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.model.GitHubReleaseAsset
import com.example.data.model.GitHubReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {

    private const val GITHUB_OWNER = "azamfahd"
    private const val GITHUB_REPO = "netmanager-"
    const val GITHUB_RELEASES_WEB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Checks GitHub API for the latest published release.
     * Tries primary repo, fallback repo name, /releases/latest, and /releases array.
     * Returns Pair<GitHubReleaseInfo, isNewerVersion>.
     */
    suspend fun checkLatestRelease(currentVersion: String): Result<Pair<GitHubReleaseInfo, Boolean>> = withContext(Dispatchers.IO) {
        val repoCandidates = listOf(
            Pair(GITHUB_OWNER, GITHUB_REPO),
            Pair(GITHUB_OWNER, "netmanager")
        )

        var lastError: String? = null
        var isRateLimited = false

        for ((owner, repo) in repoCandidates) {
            try {
                // 1. Try /releases/latest first
                val latestUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val reqLatest = Request.Builder()
                    .url(latestUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "NetManager-Android-App")
                    .build()

                val respLatest = httpClient.newCall(reqLatest).execute()
                val codeLatest = respLatest.code
                val bodyLatest = respLatest.body?.string()

                if (respLatest.isSuccessful && !bodyLatest.isNullOrBlank()) {
                    val json = JSONObject(bodyLatest)
                    val releaseInfo = parseReleaseJson(json, "https://github.com/$owner/$repo/releases")
                    val isNewer = isVersionNewer(releaseInfo.tagName, currentVersion)
                    return@withContext Result.success(Pair(releaseInfo, isNewer))
                }

                if (codeLatest == 403) {
                    isRateLimited = true
                }

                // 2. If 404, fallback to /releases array (in case latest tag is pre-release or untagged latest)
                val allReleasesUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=5"
                val reqAll = Request.Builder()
                    .url(allReleasesUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "NetManager-Android-App")
                    .build()

                val respAll = httpClient.newCall(reqAll).execute()
                val bodyAll = respAll.body?.string()
                if (respAll.isSuccessful && !bodyAll.isNullOrBlank()) {
                    val array = org.json.JSONArray(bodyAll)
                    if (array.length() > 0) {
                        val firstRelease = array.getJSONObject(0)
                        val releaseInfo = parseReleaseJson(firstRelease, "https://github.com/$owner/$repo/releases")
                        val isNewer = isVersionNewer(releaseInfo.tagName, currentVersion)
                        return@withContext Result.success(Pair(releaseInfo, isNewer))
                    }
                }

                if (codeLatest == 404) {
                    lastError = "لم يتم العثور على إصدارات منشورة في GitHub ($owner/$repo). قم بنشر Release أولاً عبر GitHub Actions."
                } else if (codeLatest == 403) {
                    lastError = "تم تجاوز حد طلبات GitHub المؤقت (Rate Limit). يرجى المحاولة بعد قليل أو زيارة صفحة الإصدارات مباشرة."
                } else {
                    lastError = "فشل الاتصال بخادم GitHub (رمز الخطأ: $codeLatest)"
                }
            } catch (e: Exception) {
                lastError = "تعذر الاتصال بـ GitHub: ${e.localizedMessage ?: e.message}"
            }
        }

        if (isRateLimited) {
            Result.failure(Exception("تم تجاوز حد طلبات GitHub المؤقت (Rate Limit). يرجى فتح صفحة المستودع: $GITHUB_RELEASES_WEB_URL"))
        } else {
            Result.failure(Exception(lastError ?: "تعذر التحقق من تحديثات GitHub"))
        }
    }

    private fun parseReleaseJson(json: JSONObject, fallbackHtmlUrl: String): GitHubReleaseInfo {
        val tagName = json.optString("tag_name", "")
        val name = json.optString("name", tagName.ifBlank { "إصدار جديد" })
        val body = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")
        val htmlUrl = json.optString("html_url", fallbackHtmlUrl)

        val assetsArray = json.optJSONArray("assets")
        var apkAsset: GitHubReleaseAsset? = null

        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetJson = assetsArray.getJSONObject(i)
                val assetName = assetJson.optString("name", "")
                val assetSize = assetJson.optLong("size", 0L)
                val downloadUrl = assetJson.optString("browser_download_url", "")
                val contentType = assetJson.optString("content_type", "")

                if (assetName.endsWith(".apk", ignoreCase = true) ||
                    contentType.contains("android.package-archive", ignoreCase = true)
                ) {
                    apkAsset = GitHubReleaseAsset(
                        name = assetName,
                        size = assetSize,
                        downloadUrl = downloadUrl,
                        contentType = contentType
                    )
                    break
                }
            }
        }

        return GitHubReleaseInfo(
            tagName = tagName,
            name = name,
            body = body,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkAsset = apkAsset
        )
    }

    /**
     * Determines whether the remote version is newer than the current installed version.
     */
    fun isVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").trim()

        if (cleanRemote.isBlank()) return false
        if (cleanRemote.equals("latest", ignoreCase = true)) return true

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        return cleanRemote != cleanCurrent && cleanRemote > cleanCurrent
    }

    /**
     * Downloads the APK file with real-time percentage progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "NetManager-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("فشل تنزيل ملف APK (رمز الاستجابة: ${response.code})"))
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(Exception("محتوى ملف APK فارغ!"))

            val totalBytes = responseBody.contentLength()

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val apkFile = File(updatesDir, "NetManager_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            inputStream = responseBody.byteStream()
            outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead: Long = 0
            var lastReportedPercent = -1

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                if (totalBytes > 0) {
                    val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(percent, totalRead, totalBytes)
                    }
                } else {
                    onProgress(-1, totalRead, -1)
                }
            }

            outputStream.flush()
            onProgress(100, totalRead, totalRead)
            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(Exception("حدث خطأ أثناء تنزيل التحديث: ${e.localizedMessage ?: e.message}"))
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Prompts the Android PackageInstaller to update the app in-place.
     * Retains all user data, database records, and settings without loss.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() <= 0) {
                return Result.failure(Exception("ملف APK غير موجود أو تالف"))
            }

            // Android 8.0+ (Oreo) permission to install unknown apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return Result.failure(Exception("يرجى تفعيل خيار السماح بتثبيت التطبيقات من هذا المصدر للمتابعة"))
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Explicitly grant URI read permission to all matching package installer handlers
            val resolvingActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(installIntent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(installIntent, 0)
            }
            for (resolveInfo in resolvingActivities) {
                val pkgName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkgName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر بدء تثبيت التحديث: ${e.localizedMessage ?: e.message}"))
        }
    }
}
