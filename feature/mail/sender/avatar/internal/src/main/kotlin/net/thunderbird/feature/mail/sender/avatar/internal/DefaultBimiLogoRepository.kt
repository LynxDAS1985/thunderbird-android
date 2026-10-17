package net.thunderbird.feature.mail.sender.avatar.internal

import java.nio.file.FileSystemException
import java.nio.file.Path
import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderVerificationMethod
import net.thunderbird.core.preference.avatar.SenderVerificationResult
import net.thunderbird.feature.mail.sender.avatar.BimiLogoRepository
import net.thunderbird.feature.mail.sender.avatar.BimiLogoRequest
import net.thunderbird.feature.mail.sender.avatar.BimiLogoResult
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizationResult
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizer

internal class DefaultBimiLogoRepository(
    cacheDirectory: Path,
    private val recordResolver: BimiRecordResolver = MiniDnsBimiRecordResolver(),
    private val transport: BimiTransport = SafeBimiTransport(),
    private val validator: StrictSvgValidator = StrictSvgValidator(),
    private val now: () -> Long = System::currentTimeMillis,
    private val rasterizer: BimiSvgRasterizer = AndroidBimiSvgRasterizer(),
) : BimiLogoRepository {
    private val cache = BimiDiskCache(cacheDirectory, now)

    override fun load(request: BimiLogoRequest): BimiLogoResult {
        if (!BimiSecurity.acceptsVerifiedMessage(
                request,
            )
        ) {
            return rejected(BimiLogoResult.Reason.MESSAGE_NOT_DMARC_VERIFIED)
        }
        val domain = BimiSecurity.normalizeDomain(request.alignedDomain)
            ?: return rejected(BimiLogoResult.Reason.INVALID_DOMAIN)
        val selector = BimiSecurity.normalizeSelector(request.selector)
            ?: return rejected(BimiLogoResult.Reason.INVALID_SELECTOR)
        val negativeCacheKey = "$domain|$selector"
        cache.readNegative(negativeCacheKey)?.let { entry ->
            return rejected(entry.reason.toResultReason())
        }
        val record = when (val result = recordResolver.lookup(domain, selector)) {
            is BimiRecordLookupResult.Found -> result.record

            BimiRecordLookupResult.NotFound -> {
                cache.writeNegative(
                    negativeCacheKey,
                    BimiDiskCache.NegativeReason.RECORD_NOT_FOUND,
                    NEGATIVE_TTL_MILLIS,
                )
                return rejected(BimiLogoResult.Reason.BIMI_RECORD_NOT_FOUND)
            }

            BimiRecordLookupResult.Invalid -> {
                cache.writeNegative(
                    negativeCacheKey,
                    BimiDiskCache.NegativeReason.INVALID_RECORD,
                    NEGATIVE_TTL_MILLIS,
                )
                return rejected(BimiLogoResult.Reason.INVALID_BIMI_RECORD)
            }

            BimiRecordLookupResult.Error -> return rejected(BimiLogoResult.Reason.DNS_ERROR)
        }
        val uri = BimiSecurity.validateUrl(record.logoUri.toString())
            ?: return rejected(BimiLogoResult.Reason.INVALID_URL)
        val key = "$domain|$uri"
        val cached = cache.read(key)
        if (cached?.isFresh(now()) == true && validator.isValid(cached.bytes)) {
            return rasterized(cached, mayShowShield(request), BimiLogoResult.Source.FRESH_CACHE)
        }

        return try {
            val response = transport.fetch(uri)
            if (response.code != 200 ||
                !response.contentType?.substringBefore(';')?.trim().equals("image/svg+xml", ignoreCase = true)
            ) {
                return staleOrRejected(cached, BimiLogoResult.Reason.INVALID_RESPONSE)
            }
            if (!validator.isValid(response.body)) return staleOrRejected(cached, BimiLogoResult.Reason.INVALID_SVG)
            val ttl = record.ttlMillis.coerceIn(MIN_TTL_MILLIS, MAX_TTL_MILLIS)
            val entry = cache.write(key, response.body, ttl, STALE_TTL_MILLIS)
            rasterized(entry, mayShowShield(request), BimiLogoResult.Source.NETWORK)
        } catch (exception: BimiTransportException.BlockedAddress) {
            staleOrRejected(cached, BimiLogoResult.Reason.BLOCKED_ADDRESS)
        } catch (exception: BimiTransportException.InvalidUrl) {
            staleOrRejected(cached, BimiLogoResult.Reason.INVALID_URL)
        } catch (exception: BimiTransportException.ResponseTooLarge) {
            staleOrRejected(cached, BimiLogoResult.Reason.INVALID_SVG)
        } catch (exception: BimiTransportException.InvalidResponse) {
            staleOrRejected(cached, BimiLogoResult.Reason.INVALID_RESPONSE)
        } catch (exception: BimiTransportException.TooManyRedirects) {
            staleOrRejected(cached, BimiLogoResult.Reason.INVALID_RESPONSE)
        } catch (exception: FileSystemException) {
            staleOrRejected(cached, BimiLogoResult.Reason.CACHE_ERROR)
        } catch (exception: Exception) {
            staleOrRejected(cached, BimiLogoResult.Reason.NETWORK_ERROR)
        }
    }

    private fun staleOrRejected(cached: BimiDiskCache.Entry?, reason: BimiLogoResult.Reason): BimiLogoResult =
        if (cached?.isUsable(now()) == true && validator.isValid(cached.bytes)) {
            rasterized(cached, false, BimiLogoResult.Source.STALE_CACHE)
        } else {
            rejected(reason)
        }

    private fun rasterized(
        entry: BimiDiskCache.Entry,
        shield: Boolean,
        source: BimiLogoResult.Source,
    ): BimiLogoResult {
        if (java.nio.file.Files.isRegularFile(entry.rasterizedPath)) {
            return available(entry.rasterizedPath, shield, source)
        }

        return when (val result = rasterizer.rasterize(entry.bytes, RASTER_SIZE_PX)) {
            is BimiSvgRasterizationResult.Success -> {
                val path = cache.writeRasterized(entry, result.png)
                available(path, shield, source)
            }

            is BimiSvgRasterizationResult.Rejected -> rejected(BimiLogoResult.Reason.RASTERIZATION_FAILED)
        }
    }

    private fun available(path: Path, shield: Boolean, source: BimiLogoResult.Source) =
        BimiLogoResult.Available(
            image = SenderAvatarImage(path.toUri().toString()),
            mayShowVerifiedShield = shield,
            source = source,
        )

    private fun rejected(reason: BimiLogoResult.Reason) = BimiLogoResult.Rejected(reason)

    private fun mayShowShield(request: BimiLogoRequest): Boolean {
        val verification = request.verification as? SenderVerificationResult.Verified ?: return false
        return SenderVerificationMethod.BIMI_ASSERTION in verification.methods
    }

    private fun BimiDiskCache.NegativeReason.toResultReason(): BimiLogoResult.Reason = when (this) {
        BimiDiskCache.NegativeReason.RECORD_NOT_FOUND -> BimiLogoResult.Reason.BIMI_RECORD_NOT_FOUND
        BimiDiskCache.NegativeReason.INVALID_RECORD -> BimiLogoResult.Reason.INVALID_BIMI_RECORD
    }

    private companion object {
        const val MIN_TTL_MILLIS = 5 * 60 * 1000L
        const val MAX_TTL_MILLIS = 24 * 60 * 60 * 1000L
        const val STALE_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
        const val NEGATIVE_TTL_MILLIS = 15 * 60 * 1000L
        const val RASTER_SIZE_PX = 128
    }
}
