package com.scanpaste.app.widget

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.scanpaste.app.R
import java.io.FileNotFoundException
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
            val uri = try {
                findQuickNoteUri()
            } catch (e: Exception) {
                showReadFailed()
                return
            }
            if (uri == null) {
                showNoNote()
                return
            }
            try {
                header = context.getString(R.string.widget_header)
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                context.contentResolver.query(
                    uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        header = "🕐 ${df.format(Date(cursor.getLong(0) * 1000))}"
                    }
                }
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("quicknote.txt is missing")
                val txt = stream.use { String(it.readBytes(), Charsets.UTF_8) }
                items = if (txt.isEmpty()) listOf(context.getString(R.string.widget_empty)) else txt.lines()
                if (items.size > 100) items = items.take(100) + "…"
            } catch (e: FileNotFoundException) {
                showNoNote()
            } catch (e: Exception) {
                // A MediaStore entry can exist while its backing file is gone; the
                // provider reports that as ENOENT wrapped in a generic exception.
                if (e.message?.contains("ENOENT") == true) showNoNote() else showReadFailed()
            }
        }

        private fun showNoNote() {
            header = context.getString(R.string.widget_header)
            items = listOf(context.getString(R.string.widget_no_note))
        }

        private fun showReadFailed() {
            header = context.getString(R.string.widget_read_failed)
            items = listOf(context.getString(R.string.widget_unknown_error))
        }

        /**
         * Query an existing quick note only; never create a MediaStore entry here.
         * RELATIVE_PATH is reported with or without a trailing slash depending on
         * the Android version, so both forms are attempted.
         */
        private fun findQuickNoteUri(): android.net.Uri? {
            val collection = MediaStore.Files.getContentUri("external")
            val sel = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            for (path in arrayOf(
                "${Environment.DIRECTORY_DOCUMENTS}/scanpaste/",
                "${Environment.DIRECTORY_DOCUMENTS}/scanpaste"
            )) {
                context.contentResolver.query(
                    collection, arrayOf(MediaStore.MediaColumns._ID), sel, arrayOf(path, "quicknote.txt"), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return collection.buildUpon().appendPath(cursor.getLong(0).toString()).build()
                    }
                }
            }
            val fallbackSel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val fallbackArgs = arrayOf("quicknote.txt", "${Environment.DIRECTORY_DOCUMENTS}/scanpaste%")
            context.contentResolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID), fallbackSel, fallbackArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return collection.buildUpon().appendPath(cursor.getLong(0).toString()).build()
                }
            }
            return null
        }
    }
}
