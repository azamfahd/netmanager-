package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.UpdateDownloadState
import com.example.ui.theme.NetworkCyan
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Switch

@Composable
fun AppUpdateDialog(
    release: GitHubReleaseInfo,
    currentVersion: String,
    downloadState: UpdateDownloadState,
    onDownloadAndInstall: (downloadUrl: String) -> Unit,
    onInstallDownloaded: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
    autoUpdateMinorReleases: Boolean = true,
    onToggleAutoUpdateMinor: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    val isCompleted = downloadState is UpdateDownloadState.Completed
    val isMinor = release.isMinorUpdate

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(RouterBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = RouterBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "تحديث جديد للبرنامج",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الإصدار الجديد: ${release.tagName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = RouterBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!isDownloading) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }
                }

                // Version info pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "الإصدار الحالي",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v$currentVersion",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = StatusActiveGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "الإصدار الجديد",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusActiveGreen
                            )
                            Text(
                                text = release.tagName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusActiveGreen
                            )
                        }
                    }
                }

                // Minor / Lightweight update direct banner
                if (isMinor) {
                    Surface(
                        color = StatusActiveGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(StatusActiveGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = StatusActiveGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تحديث خفيف وسريع ⚡",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusActiveGreen
                                )
                                Text(
                                    text = "تحديث بسيط ومباشر يتم تطبيقه تلقائياً لتحسين الأداء والحفاظ على كل البيانات.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Automatic Update for Minor Releases Toggle
                if (onToggleAutoUpdateMinor != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تحديث التحديثات البسيطة تلقائياً",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تنزيل وتطبيق التحديثات الخفيفة مباشرة بدون خطوات معقدة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoUpdateMinorReleases,
                                onCheckedChange = onToggleAutoUpdateMinor
                            )
                        }
                    }
                }

                // File size info if available
                release.apkAsset?.let { asset ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حجم ملف التحديث:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = asset.formattedSize,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Release notes / Changelog
                if (release.body.isNotBlank()) {
                    Text(
                        text = "ما الجديد في هذا التحديث:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Text(
                            text = release.body,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Download Progress
                if (isDownloading) {
                    val progress = (downloadState as UpdateDownloadState.Downloading).progressPercent
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "جاري تنزيل ملف التحديث...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (progress >= 0) {
                                Text(
                                    text = "$progress%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RouterBlue
                                )
                            }
                        }

                        if (progress >= 0) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RouterBlue
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RouterBlue
                            )
                        }

                        Text(
                            text = "لا تغلق التطبيق، سيتم فتح مثبت الحزم تلقائياً فور الانتهاء.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Completed indicator
                if (isCompleted) {
                    Surface(
                        color = StatusActiveGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusActiveGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "تم اكتمال تنزيل التحديث بنجاح! جاهز للتثبيت.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusActiveGreen
                            )
                        }
                    }
                }

                // Note about safe in-place upgrade
                Surface(
                    color = NetworkCyan.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ℹ️ تحديث مباشر وآمن: يتم التثبيت فوق النسخة الحالية دون الحاجة لحذفها، مع الحفاظ الكامل على كل البيانات والكروت وسجلات الجلسات.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (isCompleted) {
                val apkFile = (downloadState as UpdateDownloadState.Completed).apkFile
                Button(
                    onClick = { onInstallDownloaded(apkFile) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusActiveGreen)
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تثبيت التحديث الآن ⚡", fontWeight = FontWeight.Bold)
                }
            } else {
                val apkUrl = release.apkAsset?.downloadUrl ?: release.htmlUrl
                val isSameVersion = release.tagName.trim().removePrefix("v").removePrefix("V") == currentVersion.trim().removePrefix("v").removePrefix("V")
                val buttonText = when {
                    isSameVersion -> "إعادة تثبيت آخر تحديث"
                    release.isMinorUpdate -> "تحديث وتثبيت مباشر ⚡"
                    else -> "تحديث وتثبيت الآن"
                }
                Button(
                    onClick = { onDownloadAndInstall(apkUrl) },
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (release.isMinorUpdate) RouterBlue else RouterBlue
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري التنزيل المباشر...")
                    } else {
                        Icon(
                            if (release.isMinorUpdate) Icons.Default.Bolt else Icons.Default.Download,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(buttonText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("لاحقاً")
                }
            }
        }
    )
}

