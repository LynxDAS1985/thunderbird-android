package net.thunderbird.feature.mail.sender.avatar.internal

import java.nio.file.Path
import net.thunderbird.feature.mail.sender.avatar.BimiLogoRepository

object BimiLogoRepositoryFactory {
    fun create(cacheDirectory: Path): BimiLogoRepository = DefaultBimiLogoRepository(cacheDirectory)
}
