package com.scanpaste.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.scanpaste.app.MainActivity
import com.scanpaste.app.R

class QuickNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // super.onReceive already triggers onUpdate for APPWIDGET_UPDATE.
        // For our custom UPDATE_ACTION, only notify data changed — no duplicate updateWidget call.
        if (intent.action == UPDATE_ACTION) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, QuickNoteWidgetProvider::class.java))
            for (id in ids) {
                mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }
    }

    companion object {
        const val UPDATE_ACTION = "com.scanpaste.app.WIDGET_UPDATE"
        const val EXTRA_LOAD_QUICKNOTE = "load_quicknote"

        fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quicknote)

            // Set up scrollable ListView
            val adapterIntent = Intent(context, QuickNoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse("content://com.scanpaste.app/widget/$id")
            }
            views.setRemoteAdapter(R.id.widget_list, adapterIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Click opens app
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_LOAD_QUICKNOTE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(context, id, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // ListView items: template + fill-in intent from factory
            views.setPendingIntentTemplate(R.id.widget_list, pi)
            // Empty view: direct click when list is empty
            views.setOnClickPendingIntent(R.id.widget_empty, PendingIntent.getActivity(
                context, id + 1000, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            mgr.updateAppWidget(id, views)
        }

        fun sendUpdateBroadcast(context: Context) {
            context.sendBroadcast(Intent(context, QuickNoteWidgetProvider::class.java).apply {
                action = UPDATE_ACTION
            })
        }
    }
}
