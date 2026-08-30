package com.kevin.splitwisewhiteboard.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.kevin.splitwisewhiteboard.work.WhiteboardRefreshScheduler

class WhiteboardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // onUpdate runs on the main thread; refreshing does network I/O, so
        // hop to a background thread and use goAsync() to keep the receiver
        // alive until that thread finishes.
        val pendingResult = goAsync()
        Thread {
            try {
                WidgetUpdater.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    override fun onEnabled(context: Context) {
        WhiteboardRefreshScheduler.schedule(context)
    }
}
