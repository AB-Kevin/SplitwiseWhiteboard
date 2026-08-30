package com.kevin.splitwisewhiteboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kevin.splitwisewhiteboard.EditWhiteboardActivity
import com.kevin.splitwisewhiteboard.R
import com.kevin.splitwisewhiteboard.network.SplitwiseAuthException
import com.kevin.splitwisewhiteboard.network.SplitwiseClient
import com.kevin.splitwisewhiteboard.storage.SecureStore

/**
 * Builds/refreshes the widget's RemoteViews. Shared between
 * [WhiteboardWidgetProvider] (system-triggered updates) and the background
 * refresh worker, so both paths stay in sync. This does blocking network
 * I/O — always call it from a background thread.
 */
object WidgetUpdater {

    /** Re-fetches the whiteboard (if set up) and pushes it to every widget instance. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WhiteboardWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = buildRemoteViews(context)
        for (id in ids) {
            manager.updateAppWidget(id, views)
        }
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_whiteboard)

        val groupId = SecureStore.getSelectedGroupId(context)
        val cookie = SecureStore.getCookieHeader(context)

        if (cookie == null || groupId < 0) {
            views.setTextViewText(R.id.widgetGroupName, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widgetText, context.getString(R.string.widget_setup_prompt))
        } else {
            var text = SecureStore.getLastWhiteboard(context).orEmpty()
            try {
                val fetched = SplitwiseClient.getWhiteboard(cookie, groupId)
                if (fetched != null) {
                    text = fetched
                    SecureStore.saveLastWhiteboard(context, text, null)
                }
            } catch (e: SplitwiseAuthException) {
                text = context.getString(R.string.widget_session_expired_prompt)
            } catch (e: Exception) {
                // Offline / transient failure: fall back to the last-known text.
            }
            views.setTextViewText(
                R.id.widgetGroupName,
                SecureStore.getSelectedGroupName(context) ?: context.getString(R.string.app_name)
            )
            views.setTextViewText(
                R.id.widgetText,
                text.ifBlank { context.getString(R.string.widget_empty_prompt) }
            )
        }

        val editIntent = Intent(context, EditWhiteboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        return views
    }
}
