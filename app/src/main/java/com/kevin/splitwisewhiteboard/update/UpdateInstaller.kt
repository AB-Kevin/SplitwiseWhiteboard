package com.kevin.splitwisewhiteboard.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads an update APK from a GitHub release asset URL via the system
 * DownloadManager, then hands it to the system package installer.
 */
object UpdateInstaller {

    private const val APK_FILE_NAME = "splitwise-whiteboard-update.apk"

    /** True if the user still needs to grant "install unknown apps" for this app. */
    fun needsInstallPermission(context: Context): Boolean =
        !context.packageManager.canRequestPackageInstalls()

    /** Opens the system settings screen where the user grants that permission. */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }

    /**
     * Starts downloading [apkUrl] in the background (the system shows
     * progress in the notification shade) and, once it finishes, opens the
     * system installer for it.
     */
    fun downloadUpdate(context: Context, apkUrl: String, versionTag: String) {
        val appContext = context.applicationContext
        val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        File(downloadDir, APK_FILE_NAME).delete() // clear out any earlier attempt

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Splitwise Whiteboard $versionTag")
            .setDescription("Downloading update…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager =
            appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId == downloadId) {
                    receiverContext.unregisterReceiver(this)
                    promptInstall(receiverContext, downloadDir, downloadManager, downloadId)
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun promptInstall(
        context: Context,
        downloadDir: File?,
        downloadManager: DownloadManager,
        downloadId: Long
    ) {
        val succeeded = DownloadManager.Query().setFilterById(downloadId).let { query ->
            downloadManager.query(query).use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                    DownloadManager.STATUS_SUCCESSFUL
            }
        }
        if (!succeeded) return

        val apkFile = File(downloadDir, APK_FILE_NAME)
        if (!apkFile.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
