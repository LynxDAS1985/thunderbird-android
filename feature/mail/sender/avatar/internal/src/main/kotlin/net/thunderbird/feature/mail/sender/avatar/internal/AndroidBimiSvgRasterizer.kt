package net.thunderbird.feature.mail.sender.avatar.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizationResult
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizer

internal class AndroidBimiSvgRasterizer(
    private val validator: StrictSvgValidator = StrictSvgValidator(),
) : BimiSvgRasterizer {
    override fun rasterize(svg: ByteArray, sizePx: Int): BimiSvgRasterizationResult {
        if (sizePx !in MIN_SIZE_PX..MAX_SIZE_PX || sizePx.toLong() * sizePx > MAX_PIXELS) {
            return rejected(BimiSvgRasterizationResult.Reason.INVALID_SIZE)
        }
        if (!validator.isValid(svg)) return rejected(BimiSvgRasterizationResult.Reason.INVALID_SVG)

        val parsedSvg = runCatching { SVG.getFromInputStream(ByteArrayInputStream(svg)) }
            .getOrElse { return rejected(BimiSvgRasterizationResult.Reason.INVALID_SVG) }
        val bitmap = runCatching { Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888) }
            .getOrElse { return rejected(BimiSvgRasterizationResult.Reason.RENDERING_FAILED) }

        return try {
            val rendered = runCatching {
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                parsedSvg.renderToCanvas(canvas, RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
                encodePng(bitmap)
            }.getOrElse {
                return rejected(BimiSvgRasterizationResult.Reason.RENDERING_FAILED)
            }

            if (rendered.size > MAX_PNG_BYTES) {
                rejected(BimiSvgRasterizationResult.Reason.OUTPUT_TOO_LARGE)
            } else {
                BimiSvgRasterizationResult.Success(png = rendered, width = sizePx, height = sizePx)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        return output.toByteArray()
    }

    private fun rejected(reason: BimiSvgRasterizationResult.Reason) = BimiSvgRasterizationResult.Rejected(reason)

    internal companion object {
        const val MIN_SIZE_PX = 16
        const val MAX_SIZE_PX = 512
        const val MAX_PIXELS = 512L * 512L
        const val MAX_PNG_BYTES = 1024 * 1024
        private const val PNG_QUALITY = 100
    }
}
