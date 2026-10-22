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
    private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Checks GitHub API for the latest published release.
     * Returns Pair<GitHubReleaseInfo, isNewerVersion>.
     */
    suspend fun checkLatestRelease(currentVersion: String): Result<Pair<GitHubReleaseInfo, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "NetManager-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string()

            if (!response.isSuccessful || bodyString == null) {
                if (code == 404) {
                    return@withContext Result.failure(
                        Exception("لم يتم العثور على إصدارات منشورة بعد في مستودع GitHub. قم بنشر Release أولاً عبر GitHub Actions.")
                    )
                } else if (code == 403) {
                    return@withContext Result.failure(
                        Exception("تم تجاوز حد طلبات GitHub المؤقت (Rate Limit). يرجى المحاولة بعد قليل أو زيارة صفحة الإصدارات مباشرة.")
                    )
                } else {
                    return@withContext Result.failure(
                        Exception("فشل الاتصال بخادم GitHub (رمز الخطأ: $code)")
                    )
                }
            }

            val json = JSONObject(bodyString)
            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName.ifBlank { "إصدار جديد" })
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_WEB_URL)

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

            val releaseInfo = GitHubReleaseInfo(
                tagName = tagName,
                name = name,
                body = body,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                apkAsset = apkAsset
            )

            val isNewer = isVersionNewer(tagName, currentVersion)
            Result.success(Pair(releaseInfo, isNewer))
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بـ GitHub للتحقق من التحديثات: ${e.localizedMessage ?: e.message}"))
        }
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

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر بدء تثبيت التحديث: ${e.localizedMessage ?: e.message}"))
        }
    }
}
