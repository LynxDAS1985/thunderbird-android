package net.thunderbird.feature.mail.sender.avatar.internal

import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizer

object BimiSvgRasterizerFactory {
    fun create(): BimiSvgRasterizer = AndroidBimiSvgRasterizer()
}
