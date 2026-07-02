package com.scanpaste.app.widget

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.RemoteViews
import android.widget.RemoteViewsService
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

        private fun loadQuickNote() {
            try {
                val uri = findOrCreateUri() ?: run {
                    header = "速记内容"; items = listOf("(空)"); return
                }
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                context.contentResolver.query(
                    uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        header = "🕐 ${df.format(Date(cursor.getLong(0) * 1000))}"
                    }
                }
                val txt = context.contentResolver.openInputStream(uri)?.use {
                    String(it.readBytes(), Charsets.UTF_8)
                } ?: ""
                items = if (txt.isEmpty()) listOf("(空)") else txt.lines()
                if (items.size > 100) items = items.take(100) + "…"
            } catch (e: Exception) {
                header = "读取失败"; items = listOf(e.message ?: "未知错误")
            }
        }

        private fun findOrCreateUri(): android.net.Uri? {
            val sel = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            val a = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/scanpaste/", "quicknote.txt")
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID), sel, a, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return MediaStore.Files.getContentUri("external").buildUpon()
                        .appendPath(cursor.getLong(0).toString()).build()
                }
            }
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "quicknote.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/scanpaste")
            }
            return context.contentResolver.insert(MediaStore.Files.getContentUri("external"), v)
        }
    }
}
