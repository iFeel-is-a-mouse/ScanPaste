package com.scanpaste.app.scanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.integration.android.IntentIntegrator
import java.io.InputStream

/**
 * Encapsulates ZXing QR code scanning via standalone CaptureActivity and gallery image decoding.
 */
class QrCodeScanner {

    /**
     * Creates an Intent to launch ZXing CaptureActivity for live camera QR scanning.
     */
    fun createScanIntent(activity: Activity): Intent {
        val integrator = IntentIntegrator(activity).apply {
            setCaptureActivity(ScanPasteCaptureActivity::class.java)
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            // Prompt is handled by custom overlay in ScanPasteCaptureActivity
            setPrompt("")
            setBeepEnabled(false)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        return integrator.createScanIntent()
    }

    /**
     * Parses the result returned by ZXing CaptureActivity.
     */
    fun parseResult(resultCode: Int, data: Intent?): String? {
        val result = IntentIntegrator.parseActivityResult(
            IntentIntegrator.REQUEST_CODE, resultCode, data
        )
        return result?.contents
    }

    /**
     * Decodes a QR code from a gallery image.
     *
     * @param inputStream Image stream from content resolver
     * @return Decoded text, or null if no QR code found or decode fails
     */
    fun decodeQrFromImage(inputStream: InputStream): String? {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            decodeQrFromBitmap(bitmap)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        // Convert bitmap to RGB int array for ZXing
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        return try {
            val result = MultiFormatReader().apply {
                setHints(mapOf(
                    com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to
                            listOf(com.google.zxing.BarcodeFormat.QR_CODE)
                ))
            }.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}
