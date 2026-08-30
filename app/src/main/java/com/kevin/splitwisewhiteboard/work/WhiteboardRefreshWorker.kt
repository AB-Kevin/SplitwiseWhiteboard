package com.kevin.splitwisewhiteboard.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kevin.splitwisewhiteboard.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Periodic background heartbeat so the widget keeps in sync even when the app isn't open. */
class WhiteboardRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                WidgetUpdater.refreshAll(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
