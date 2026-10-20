package com.scanpaste.app.scanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.Binarizer
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.integration.android.IntentIntegrator
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

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
     * The image is bounded (20 MB), downsampled to at most [MAX_EDGE] pixels on
     * its longest edge, and decoded twice: once as-is, and once after a 3x3
     * median filter. The filter removes the pixel-grid noise that appears when
     * photographing a screen, which is what ZXing fails on in practice.
     *
     * Never throws: any failure (including OOM on huge images) returns null.
     * The caller owns [inputStream] and is responsible for closing it.
     *
     * @param inputStream Image stream from content resolver
     * @return Decoded text, or null if no QR code found or decode fails
     */
    fun decodeQrFromImage(inputStream: InputStream): String? {
        var bitmap: Bitmap? = null
        var filtered: Bitmap? = null
        return try {
            val bytes = readBytesLimited(inputStream, MAX_IMAGE_BYTES) ?: return null
            val sourceBitmap = decodeSampledBitmap(bytes) ?: return null
            bitmap = sourceBitmap

            decodeQrFromBitmap(sourceBitmap)?.let { return it }

            // The raw decode failed: retry once on a median-filtered copy.
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val pixels = IntArray(width * height)
            sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            // Free the source before creating the filtered copy to cap peak memory.
            bitmap.recycle()
            bitmap = null

            val filteredPixels = medianFilter3x3(pixels, width, height)
            filtered = Bitmap.createBitmap(filteredPixels, width, height, Bitmap.Config.ARGB_8888)
            decodeQrFromBitmap(filtered)
        } catch (e: Exception) {
            // Decode/IO failure: no QR code is recoverable from this image.
            null
        } catch (e: OutOfMemoryError) {
            // Very large bitmaps can still exhaust the heap despite downsampling.
            null
        } finally {
            bitmap?.recycle()
            filtered?.recycle()
        }
    }

    /**
     * Reads at most [limit] bytes. Returns null as soon as the stream exceeds it,
     * so a huge or malicious image cannot exhaust memory.
     */
    private fun readBytesLimited(inputStream: InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /**
     * Decodes bounds first, then decodes with an inSampleSize that keeps the
     * longest edge at or below [MAX_EDGE]. ZXing does not need more resolution
     * than that for a screen photo, and the smaller bitmap keeps both the decode
     * and the median filter cheap.
     */
    private fun decodeSampledBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longestEdge / sampleSize > MAX_EDGE) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return decodePixels(pixels, width, height)
    }

    /**
     * Tries every binarizer in [binarizerFactories] against the same pixels and
     * returns the first successful decode.
     *
     * [HybridBinarizer] (local, block-based thresholding) handles ordinary photos
     * but fails on low-contrast screen photos with moire patterns; in practice a
     * global histogram threshold still resolves those, so it is the fallback.
     *
     * Kept free of Android types so it can be unit tested on the JVM.
     *
     * @param pixels ARGB pixels, row-major, at least width * height entries
     * @param binarizerFactories Ordered attempts; first non-null decode wins
     * @return Decoded text, or null if every binarizer fails
     */
    internal fun decodePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        binarizerFactories: List<(LuminanceSource) -> Binarizer> = DEFAULT_BINARIZERS
    ): String? {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(pixels.size >= width * height) { "pixels smaller than width * height" }

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )

        for (factory in binarizerFactories) {
            // Fresh source/reader per attempt: binarizers may keep per-instance
            // state, and a failed decode must not leak into the next attempt.
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(factory(source))
            try {
                val result = MultiFormatReader().apply { setHints(hints) }.decode(binaryBitmap)
                if (result != null) return result.text
            } catch (e: ReaderException) {
                // Expected for the wrong binarizer on a hard image; try the next one.
            }
        }
        return null
    }

    /**
     * Pure 3x3 median filter over ARGB pixels.
     *
     * Each pixel is converted to luma (0.299R + 0.587G + 0.114B), the 3x3
     * neighbourhood is median-filtered with clamped (edge-replicated) borders,
     * and the result is returned as opaque grayscale ARGB.
     *
     * Kept free of Android types so it can be unit tested on the JVM.
     *
     * @param pixels ARGB pixels, row-major, at least width * height entries
     * @return filtered grayscale ARGB pixels of size width * height
     */
    fun medianFilter3x3(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(pixels.size >= width * height) { "pixels smaller than width * height" }

        val size = width * height
        // Precompute the luma plane once instead of re-deriving it for every
        // neighbour lookup (9x less work per pixel).
        val luma = IntArray(size)
        for (i in 0 until size) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            luma[i] = (299 * r + 587 * g + 114 * b) / 1000
        }

        val xMinus = IntArray(width) { max(0, it - 1) }
        val xPlus = IntArray(width) { min(width - 1, it + 1) }
        val yMinus = IntArray(height) { max(0, it - 1) }
        val yPlus = IntArray(height) { min(height - 1, it + 1) }

        val window = IntArray(9)
        val output = IntArray(size)
        for (y in 0 until height) {
            val rowUp = yMinus[y] * width
            val rowMid = y * width
            val rowDown = yPlus[y] * width
            for (x in 0 until width) {
                val left = xMinus[x]
                val right = xPlus[x]
                window[0] = luma[rowUp + left]
                window[1] = luma[rowUp + x]
                window[2] = luma[rowUp + right]
                window[3] = luma[rowMid + left]
                window[4] = luma[rowMid + x]
                window[5] = luma[rowMid + right]
                window[6] = luma[rowDown + left]
                window[7] = luma[rowDown + x]
                window[8] = luma[rowDown + right]

                // Insertion sort of exactly 9 entries; cheaper than allocating
                // and sorting a fresh array for every pixel.
                for (i in 1 until 9) {
                    val value = window[i]
                    var j = i - 1
                    while (j >= 0 && window[j] > value) {
                        window[j + 1] = window[j]
                        j--
                    }
                    window[j + 1] = value
                }

                val gray = window[4]
                output[rowMid + x] = GRAYSCALE_ALPHA or (gray shl 16) or (gray shl 8) or gray
            }
        }
        return output
    }

    private companion object {
        /** Hard cap on a picked image; anything larger is rejected unread. */
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

        /** Longest edge after downsampling. */
        const val MAX_EDGE = 2048

        const val READ_BUFFER_BYTES = 16 * 1024
        const val GRAYSCALE_ALPHA = 0xFF shl 24

        /** Decode order: local/adaptive threshold first, global histogram as fallback. */
        val DEFAULT_BINARIZERS: List<(LuminanceSource) -> Binarizer> = listOf(
            { source -> HybridBinarizer(source) },
            { source -> GlobalHistogramBinarizer(source) }
        )
    }
}
