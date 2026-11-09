package com.example.data.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.UpdateDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val EXTRA_DOWNLOAD_URL = "extra_download_url"
        private const val CHANNEL_ID = "app_update_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, downloadUrl: String) {
            val intent = Intent(context, AppUpdateService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL)
        if (downloadUrl.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تحديث NetMaster Pro")
            .setContentText("جاري بدء تنزيل التحديث في الخلفية...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch {
            try {
                AppUpdateManager.updateState(UpdateDownloadState.Downloading(0, 0, 0))
                val result = AppUpdateManager.downloadApk(applicationContext, downloadUrl) { percent, downloaded, total ->
                    updateNotificationProgress(percent)
                    AppUpdateManager.updateState(UpdateDownloadState.Downloading(percent, downloaded, total))
                }
                
                if (result.isSuccess) {
                    val apkFile = result.getOrThrow()
                    AppUpdateManager.updateState(UpdateDownloadState.Completed(apkFile))
                    showCompletionNotification()
                    
                    withContext(Dispatchers.Main) {
                        AppUpdateManager.installApk(applicationContext, apkFile)
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "فشل التنزيل"
                    AppUpdateManager.updateState(UpdateDownloadState.Error(error))
                    showErrorNotification(error)
                }
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "فشل تنزيل التحديث"
                AppUpdateManager.updateState(UpdateDownloadState.Error(error))
                showErrorNotification(error)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "تحديثات التطبيق"
            val description = "تنزيل وتثبيت تحديثات NetMaster Pro في الخلفية"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotificationProgress(percent: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تحديث NetMaster Pro")
            .setContentText(if (percent >= 0) "جاري التنزيل: $percent%" else "جاري تنزيل التحديث...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)

        if (percent >= 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(100, 0, true)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showCompletionNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("اكتمل تنزيل التحديث ⚡")
            .setContentText("جاري تثبيت الإصدار الجديد تلقائياً...")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(errorMsg: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("فشل تحديث التطبيق")
            .setContentText(errorMsg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
