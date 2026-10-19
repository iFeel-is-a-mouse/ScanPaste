package com.scanpaste.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.scanpaste.app.QuickNoteStorage
import com.scanpaste.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuickNoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = QuickNoteFactory(this.applicationContext)

    class QuickNoteFactory(private val context: Context) : RemoteViewsFactory {
        private var items: List<String> = emptyList()
        private var header = ""
        private val clickIntent = Intent().apply {
            putExtra(QuickNoteWidgetProvider.EXTRA_LOAD_QUICKNOTE, true)
        }

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() { loadQuickNote() }

        override fun getCount() = items.size + 1

        override fun getViewAt(pos: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item)
            // Set fill-in intent for click-through
            rv.setOnClickFillInIntent(R.id.widget_item_text, clickIntent)
            rv.setOnClickFillInIntent(R.id.widget_item_header, clickIntent)

            if (pos == 0) {
                rv.setViewVisibility(R.id.widget_item_header, android.view.View.VISIBLE)
                rv.setTextViewText(R.id.widget_item_header, header)
                rv.setViewVisibility(R.id.widget_item_text, android.view.View.GONE)
            } else {
                rv.setViewVisibility(R.id.widget_item_header, android.view.View.GONE)
                rv.setTextViewText(R.id.widget_item_text, items[pos - 1])
                rv.setViewVisibility(R.id.widget_item_text, android.view.View.VISIBLE)
            }
            return rv
        }

        override fun getLoadingView() = null
        override fun getViewTypeCount() = 2
        override fun getItemId(pos: Int) = pos.toLong()
        override fun hasStableIds() = true

        /**
         * Reads the app-private quick note. This never creates or queries a
         * MediaStore entry; the widget only reflects the file the app writes.
         */
        private fun loadQuickNote() {
            val file = QuickNoteStorage.mainFile(context)
            if (!file.isFile) {
                showNoNote()
                return
            }

            val txt = QuickNoteStorage.read(context)
            if (txt == null) {
                // Distinguish a file that vanished mid-read (no note) from one
                // that exists but cannot be read (read failure).
                if (file.isFile) showReadFailed() else showNoNote()
                return
            }

            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            header = "🕐 ${df.format(Date(file.lastModified()))}"
            items = if (txt.isEmpty()) listOf(context.getString(R.string.widget_empty)) else txt.lines()
            if (items.size > 100) items = items.take(100) + "…"
        }

        private fun showNoNote() {
            header = context.getString(R.string.widget_header)
            items = listOf(context.getString(R.string.widget_no_note))
        }

        private fun showReadFailed() {
            header = context.getString(R.string.widget_read_failed)
            items = listOf(context.getString(R.string.widget_unknown_error))
        }
    }
}
