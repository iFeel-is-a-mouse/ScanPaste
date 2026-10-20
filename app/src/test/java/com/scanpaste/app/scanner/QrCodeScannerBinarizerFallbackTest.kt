package com.scanpaste.app.scanner

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.Binarizer
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.common.BitArray
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Random
import org.junit.Test

/**
 * Unit tests for [QrCodeScanner.decodePixels] and its binarizer fallback.
 *
 * QR pixels are generated in-memory with ZXing's [QRCodeWriter], so no image
 * fixtures are needed and the tests run on the JVM.
 *
 * Coverage boundary: a real-world screen photo where Hybrid fails and Global
 * succeeds cannot be synthesized reliably here (that needs the moire fixture),
 * so the fallback is proven with an always-failing first binarizer plus a
 * generated QR decoded by GlobalHistogramBinarizer.
 */
class QrCodeScannerBinarizerFallbackTest {

    private val scanner = QrCodeScanner()

    @Test
    fun `decodes a generated QR code with the default binarizer chain`() {
        val image = qrImage(QR_TEXT, SIZE)

        val result = scanner.decodePixels(image.pixels, image.width, image.height)

        assertThat(result).isEqualTo(QR_TEXT)
    }

    @Test
    fun `falls back to global histogram when the first binarizer fails`() {
        val image = qrImage(QR_TEXT, SIZE)
        val attempts = mutableListOf<String>()
        val factories: List<(LuminanceSource) -> Binarizer> = listOf(
            { source -> attempts += "throwing"; ThrowingBinarizer(source) },
            { source -> attempts += "global"; GlobalHistogramBinarizer(source) }
        )

        val result = scanner.decodePixels(image.pixels, image.width, image.height, factories)

        assertThat(result).isEqualTo(QR_TEXT)
        assertThat(attempts).containsExactly("throwing", "global").inOrder()
    }

    @Test
    fun `tries hybrid then global and returns null when neither decodes`() {
        // Deterministic gray noise: no finder patterns, so neither binarizer can
        // produce a valid QR code.
        val random = Random(20261021L)
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) {
            val gray = 80 + random.nextInt(96)
            argb(gray, gray, gray)
        }
        val attempts = mutableListOf<String>()
        val factories: List<(LuminanceSource) -> Binarizer> = listOf(
            { source -> attempts += "hybrid"; HybridBinarizer(source) },
            { source -> attempts += "global"; GlobalHistogramBinarizer(source) }
        )

        val result = scanner.decodePixels(pixels, width, height, factories)

        assertThat(result).isNull()
        assertThat(attempts).containsExactly("hybrid", "global").inOrder()
    }

    private class ThrowingBinarizer(source: LuminanceSource) : Binarizer(source) {
        override fun getBlackMatrix(): BitMatrix =
            throw NotFoundException.getNotFoundInstance()

        override fun getBlackRow(y: Int, row: BitArray?): BitArray =
            throw NotFoundException.getNotFoundInstance()

        override fun createBinarizer(source: LuminanceSource): Binarizer = ThrowingBinarizer(source)
    }

    private class Image(val pixels: IntArray, val width: Int, val height: Int)

    private fun qrImage(text: String, size: Int): Image {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) argb(0, 0, 0) else argb(255, 255, 255)
            }
        }
        return Image(pixels, size, size)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private companion object {
        const val QR_TEXT = "https://b.51cto.com/app-download.html"
        const val SIZE = 300
    }
}
