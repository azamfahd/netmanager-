package com.example.data.model

import java.io.File

data class GitHubReleaseAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val contentType: String
) {
    val formattedSize: String
        get() {
            if (size <= 0) return "حجم غير معروف"
            val mb = size.toDouble() / (1024 * 1024)
            return String.format(java.util.Locale.US, "%.1f ميجابايت", mb)
        }
}

data class GitHubReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkAsset: GitHubReleaseAsset?
) {
    val isMinorUpdate: Boolean
        get() {
            val clean = tagName.trim().removePrefix("v").removePrefix("V")
            val parts = clean.split(".")
            return parts.size >= 3 || (apkAsset != null && apkAsset.size in 1..(35 * 1024 * 1024L))
        }
}

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpdateAvailable(
        val release: GitHubReleaseInfo,
        val currentVersion: String,
        val isNewer: Boolean,
        val isMinorUpdate: Boolean = release.isMinorUpdate
    ) : UpdateCheckState()
    data class UpToDate(
        val currentVersion: String,
        val latestTag: String,
        val release: GitHubReleaseInfo? = null
    ) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class Completed(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
