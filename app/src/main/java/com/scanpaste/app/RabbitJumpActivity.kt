package com.scanpaste.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Rabbit Jump easter egg.
 *
 * The game is an HTML asset; everything it shows is pushed in from Kotlin:
 * - [AndroidBridge.getUiStrings] carries every user-visible label for the
 *   current app language (strings.xml / values-zh), so the HTML holds no copy.
 * - [AndroidBridge.getGame] carries the sprite + pangram configuration from
 *   game.txt, falling back to the localized default.
 *
 * Both save files live in the same app-private directory as the quick note.
 */
class RabbitJumpActivity : AppCompatActivity() {

    private var webView: WebView? = null

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
        webView = findViewById<WebView>(R.id.webview).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(AndroidBridge(applicationContext), BRIDGE_NAME)
            loadUrl("file:///android_asset/rabbitjump.html")
        }
    }

    override fun onDestroy() {
        webView?.let { view ->
            // The JS bridge holds an Application context, but the WebView
            // itself keeps native resources: remove the interface and destroy
            // it explicitly instead of waiting for GC.
            view.removeJavascriptInterface(BRIDGE_NAME)
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    /**
     * Builds the label bundle for the WebView.
     *
     * Uses the Activity context on purpose: attachBaseContext applies the
     * in-app language override to these resources, while applicationContext
     * would fall back to the system locale.
     */
    private fun uiStringsJson(): String {
        val language = LanguagePrefs.get(this)
        return RabbitJumpFiles.jsonObject(
            linkedMapOf(
                "lang" to if (language == LanguagePrefs.LANG_ZH) "zh-CN" else "en",
                "title" to getString(R.string.rabbit_title),
                "back" to getString(R.string.rabbit_back),
                "jump" to getString(R.string.rabbit_jump),
                "bestEmpty" to getString(R.string.rabbit_best_empty),
                "bestFormat" to getString(R.string.rabbit_best_format),
                "level" to getString(R.string.rabbit_level),
                "newRecord" to getString(R.string.rabbit_new_record),
                "levelClear" to getString(R.string.rabbit_level_clear),
                "retry" to getString(R.string.rabbit_retry),
                "hintEarly" to getString(R.string.rabbit_hint_early),
                "hintLate" to getString(R.string.rabbit_hint_late),
                "hintPerfect" to getString(R.string.rabbit_hint_perfect),
                "hintGreat" to getString(R.string.rabbit_hint_great),
                "hintNice" to getString(R.string.rabbit_hint_nice),
                "hintRisky" to getString(R.string.rabbit_hint_risky),
                "speedInfo" to getString(R.string.rabbit_speed_info),
                "pangram" to getString(R.string.rabbit_default_pangram)
            )
        )
    }

    inner class AndroidBridge(private val storageContext: Context) {

        @JavascriptInterface
        fun back() {
            runOnUiThread { finish() }
        }

        /** All UI copy for the active language, as a JSON object. */
        @JavascriptInterface
        fun getUiStrings(): String = try {
            uiStringsJson()
        } catch (e: Exception) {
            // The game stays usable with empty labels rather than crashing.
            Log.e(TAG, "Could not build UI strings", e)
            "{}"
        }

        /** game.txt content; the localized default is created on first run. */
        @JavascriptInterface
        fun getGame(): String = try {
            RabbitJumpStorage.loadGame(
                storageContext,
                getString(R.string.rabbit_default_pangram)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not load game.txt, using in-memory default", e)
            RabbitJumpFiles.defaultGameText(getString(R.string.rabbit_default_pangram))
        }

        @JavascriptInterface
        fun getBest(): String = try {
            RabbitJumpStorage.readBest(storageContext)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read best.txt", e)
            ""
        }

        /**
         * @return true when best.txt was written. JS does not need the value,
         * but failures are logged instead of being swallowed.
         */
        @JavascriptInterface
        fun saveBest(data: String): Boolean = try {
            RabbitJumpStorage.writeBest(storageContext, data)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save best.txt", e)
            false
        }
    }

    private companion object {
        const val TAG = "RabbitJumpActivity"
        const val BRIDGE_NAME = "Android"
    }
}
