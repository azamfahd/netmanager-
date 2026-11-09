package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UpdateDownloadState
import com.example.ui.theme.RouterBlue
import com.example.ui.theme.StatusActiveGreen

@Composable
fun UpdateBanner(
    tagName: String,
    isMinorUpdate: Boolean = false,
    downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
    onStopDownload: (() -> Unit)? = null
) {
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    val isCompleted = downloadState is UpdateDownloadState.Completed

    Surface(
        color = when {
            isCompleted -> StatusActiveGreen.copy(alpha = 0.9f)
            isDownloading -> RouterBlue.copy(alpha = 0.85f)
            isMinorUpdate -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when {
                                isCompleted -> "✅ اكتمل التنزيل ($tagName)"
                                isDownloading -> "⏳ جاري التنزيل ($tagName)"
                                isMinorUpdate -> "⚡ تحديث متاح ($tagName)"
                                else -> "تحديث جديد ($tagName)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDownloading || isCompleted) Color.White else if (isMinorUpdate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Small "Minimize" indicator
                        Surface(
                            color = if (isDownloading || isCompleted) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Text(
                                text = "تصغير 🗕",
                                fontSize = 8.sp,
                                color = if (isDownloading || isCompleted) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isDownloading) "التحميل مستمر..." else "اضغط للبدء أو التثبيت",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isDownloading || isCompleted) Color.White.copy(alpha = 0.8f) else if (isMinorUpdate) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isDownloading && onStopDownload != null) {
                        TextButton(
                            onClick = onStopDownload,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("إيقاف ⏹", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = onUpdateClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isDownloading || isCompleted) Color.White else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = when {
                                    isCompleted -> "تثبيت"
                                    isDownloading -> "تفاصيل"
                                    isMinorUpdate -> "تحديث ⚡"
                                    else -> "تحديث"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isDownloading || isCompleted) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Progress bar for download
            if (isDownloading) {
                val progress = (downloadState as UpdateDownloadState.Downloading).progressPercent
                if (progress >= 0) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
