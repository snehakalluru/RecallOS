package com.recallos.ingestion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.recallos.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenshotMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var observer: ContentObserver

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())

        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        if (!preferences.contains(LAST_ID)) {
            preferences.edit().putLong(LAST_ID, latestScreenshotId()).apply()
        }

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                serviceScope.launch { importNewScreenshots() }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun importNewScreenshots() {
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        var lastId = preferences.getLong(LAST_ID, 0L)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media._ID} > ?",
            arrayOf(lastId.toString()),
            "${MediaStore.Images.Media._ID} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn).orEmpty()
                val path = cursor.getString(pathColumn).orEmpty()
                if (isScreenshot(name, path)) {
                    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString())
                        .build()
                    runCatching {
                        val repository = ImageIngestionRepository(applicationContext)
                        val memoryId = repository.saveImageForProcessing(uri)
                        repository.processMemoryItem(memoryId)
                    }
                }
                lastId = maxOf(lastId, id)
            }
        }
        preferences.edit().putLong(LAST_ID, lastId).apply()
    }

    private fun latestScreenshotId(): Long {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        return contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }

    private fun isScreenshot(name: String, relativePath: String): Boolean {
        val value = "$name $relativePath".lowercase()
        return "screenshot" in value || "screenshots" in value
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("RecallOS is watching screenshots")
            .setContentText("New screenshots are indexed automatically")
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Screenshot indexing",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "screenshot_indexing"
        private const val NOTIFICATION_ID = 1001
        private const val PREFERENCES = "screenshot_monitor"
        private const val LAST_ID = "last_media_id"

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenshotMonitorService::class.java),
            )
        }
    }
}