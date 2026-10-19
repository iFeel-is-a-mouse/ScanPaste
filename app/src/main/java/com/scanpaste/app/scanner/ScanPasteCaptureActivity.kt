package com.scanpaste.app.scanner

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.scanpaste.app.LanguagePrefs
import java.util.Locale

/**
 * Custom CaptureActivity: portrait-only with repositioned prompt text
 * that sits above the bottom navigation bar area.
 */
class ScanPasteCaptureActivity : CaptureActivity() {

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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        addPromptOverlay()
    }

    private fun addPromptOverlay() {
        val barcodeView = findViewById<DecoratedBarcodeView>(
            com.google.zxing.client.android.R.id.zxing_barcode_scanner
        )
        val prompt = TextView(this).apply {
            text = getString(com.scanpaste.app.R.string.scanner_prompt)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setPadding(32, 12, 32, 12)
        }
        // Position above the bottom — about 20% from bottom edge
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply {
            bottomMargin = 160  // px offset from bottom
        }
        barcodeView.addView(prompt, params)
    }
}
