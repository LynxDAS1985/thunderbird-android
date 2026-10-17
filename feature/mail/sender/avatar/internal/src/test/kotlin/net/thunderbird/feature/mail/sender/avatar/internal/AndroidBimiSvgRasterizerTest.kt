package net.thunderbird.feature.mail.sender.avatar.internal

import android.graphics.BitmapFactory
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizationResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidBimiSvgRasterizerTest {
    private val testSubject = AndroidBimiSvgRasterizer()

    @Test
    fun `rasterize should return square transparent PNG at requested size`() {
        val result = testSubject.rasterize(SAFE_SVG, 64)

        assertThat(result).isInstanceOf<BimiSvgRasterizationResult.Success>()
        val success = result as BimiSvgRasterizationResult.Success
        val bitmap = BitmapFactory.decodeByteArray(success.png, 0, success.png.size)
        assertThat(success.mediaType).isEqualTo("image/png")
        assertThat(success.width).isEqualTo(64)
        assertThat(success.height).isEqualTo(64)
        assertThat(bitmap.width).isEqualTo(64)
        assertThat(bitmap.height).isEqualTo(64)
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(0)
    }

    @Test
    fun `rasterize should safely reject oversized encoded output`() {
        val result = testSubject.rasterize(SAFE_SVG, AndroidBimiSvgRasterizer.MAX_SIZE_PX)

        when (result) {
            is BimiSvgRasterizationResult.Success -> {
                assertThat(result.png.size).isLessThanOrEqualTo(AndroidBimiSvgRasterizer.MAX_PNG_BYTES)
                assertThat(result.width).isEqualTo(AndroidBimiSvgRasterizer.MAX_SIZE_PX)
            }

            is BimiSvgRasterizationResult.Rejected -> {
                assertThat(result.reason).isEqualTo(BimiSvgRasterizationResult.Reason.OUTPUT_TOO_LARGE)
            }
        }
    }

    @Test
    fun `rasterize should reject dimensions below minimum`() {
        val result = testSubject.rasterize(SAFE_SVG, AndroidBimiSvgRasterizer.MIN_SIZE_PX - 1)

        assertThat(result).isEqualTo(
            BimiSvgRasterizationResult.Rejected(BimiSvgRasterizationResult.Reason.INVALID_SIZE),
        )
    }

    @Test
    fun `rasterize should reject dimensions above maximum`() {
        val result = testSubject.rasterize(SAFE_SVG, AndroidBimiSvgRasterizer.MAX_SIZE_PX + 1)

        assertThat(result).isEqualTo(
            BimiSvgRasterizationResult.Rejected(BimiSvgRasterizationResult.Reason.INVALID_SIZE),
        )
    }

    @Test
    fun `rasterize should reject SVG that fails strict validation`() {
        val svgWithExternalImage = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
              <image href="https://example.org/tracker.png" width="10" height="10"/>
            </svg>
        """.trimIndent().encodeToByteArray()

        val result = testSubject.rasterize(svgWithExternalImage, 64)

        assertThat(result).isEqualTo(
            BimiSvgRasterizationResult.Rejected(BimiSvgRasterizationResult.Reason.INVALID_SVG),
        )
    }

    @Test
    fun `rasterize should reject malformed SVG`() {
        val result = testSubject.rasterize(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">".encodeToByteArray(),
            64,
        )

        assertThat(result).isEqualTo(
            BimiSvgRasterizationResult.Rejected(BimiSvgRasterizationResult.Reason.INVALID_SVG),
        )
    }

    private companion object {
        val SAFE_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="25" fill="#ff0000"/>
            </svg>
        """.trimIndent().encodeToByteArray()
    }
}
