package com.scanpaste.app

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class RabbitJumpActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val locale = Locale(LanguagePrefs.get(newBase))
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        applyOverrideConfiguration(config)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rabbit_jump)
        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.loadUrl("file:///android_asset/rabbitjump.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun back() = finish()

        @JavascriptInterface
        fun getBest(): String {
            return try {
                val uri = findUri("best.txt") ?: return ""
                contentResolver.openInputStream(uri)?.use {
                    String(it.readBytes(), Charsets.UTF_8).trim()
                } ?: ""
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun saveBest(data: String) {
            try {
                val uri = findOrCreateUri("best.txt") ?: return
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(data.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun getGame(): String {
            return try {
                val cached = gameCache
                if (cached != null) return cached

                val uri = findUri("game.txt")
                if (uri != null) {
                    val content = contentResolver.openInputStream(uri)?.use {
                        String(it.readBytes(), Charsets.UTF_8).trim()
                    } ?: ""
                    if (content.isNotBlank()) {
                        gameCache = content
                        return content
                    }
                }
                // File missing or empty: create the default one
                val default = "🐰\n🦊\nThe cute rabbit jumps over the lazy fox"
                val newUri = findOrCreateUri("game.txt") ?: return default
                contentResolver.openOutputStream(newUri, "wt")?.use {
                    it.write(default.toByteArray(Charsets.UTF_8))
                }
                gameCache = default
                default
            } catch (e: Exception) {
                "🐰\n🦊\nThe cute rabbit jumps over the lazy fox"
            }
        }

        private var gameCache: String? = null

        private fun findUri(fileName: String): android.net.Uri? {
            val sel = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            val a = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/scanpaste/", fileName)
            contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID), sel, a, null
            )?.use { c ->
                if (c.moveToFirst())
                    return MediaStore.Files.getContentUri("external").buildUpon()
                        .appendPath(c.getLong(0).toString()).build()
            }
            return null
        }

        private fun findOrCreateUri(fileName: String): android.net.Uri? {
            findUri(fileName)?.let { return it }
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOCUMENTS}/scanpaste")
            }
            return contentResolver.insert(MediaStore.Files.getContentUri("external"), v)
        }
    }
}
