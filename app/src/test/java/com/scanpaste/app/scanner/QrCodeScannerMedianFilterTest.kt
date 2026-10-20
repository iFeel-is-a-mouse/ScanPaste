package com.scanpaste.app.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [QrCodeScanner.medianFilter3x3].
 *
 * The filter is pure (no Android types), so it runs on the JVM.
 */
class QrCodeScannerMedianFilterTest {

    private val scanner = QrCodeScanner()

    @Test
    fun `uniform image stays unchanged`() {
        val pixels = IntArray(9) { argb(100, 100, 100) }

        val filtered = scanner.medianFilter3x3(pixels, 3, 3)

        assertThat(filtered.size).isEqualTo(9)
        assertThat(filtered.toList()).containsExactlyElementsIn(List(9) { argb(100, 100, 100) })
    }

    @Test
    fun `single bright pixel is removed by the median`() {
        // 3x3 black image with a white centre pixel (salt noise).
        val pixels = IntArray(9) { argb(0, 0, 0) }
        pixels[4] = argb(255, 255, 255)

        val filtered = scanner.medianFilter3x3(pixels, 3, 3)

        val black = argb(0, 0, 0)
        assertThat(filtered[4]).isEqualTo(black)
        assertThat(filtered.toList()).containsExactlyElementsIn(List(9) { black })
    }

    @Test
    fun `edges are clamped and grayscale is opaque`() {
        // 2x1 image: pure red and pure blue.
        val pixels = intArrayOf(argb(255, 0, 0), argb(0, 0, 255))

        val filtered = scanner.medianFilter3x3(pixels, 2, 1)

        assertThat(filtered.size).isEqualTo(2)
        // Red luma 76, blue luma 29. With edge replication each pixel sees
        // itself 6 times and its neighbour 3 times, so the median is its own
        // value in both cases.
        assertThat(filtered[0]).isEqualTo(argb(76, 76, 76))
        assertThat(filtered[1]).isEqualTo(argb(29, 29, 29))
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
