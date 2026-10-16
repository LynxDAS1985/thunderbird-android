package net.thunderbird.feature.mail.sender.avatar

interface BimiSvgRasterizer {
    fun rasterize(svg: ByteArray, sizePx: Int): BimiSvgRasterizationResult
}

sealed interface BimiSvgRasterizationResult {
    data class Success(
        val png: ByteArray,
        val width: Int,
        val height: Int,
    ) : BimiSvgRasterizationResult {
        val mediaType: String = "image/png"
    }

    data class Rejected(val reason: Reason) : BimiSvgRasterizationResult

    enum class Reason {
        INVALID_SIZE,
        INVALID_SVG,
        RENDERING_FAILED,
        OUTPUT_TOO_LARGE,
    }
}
