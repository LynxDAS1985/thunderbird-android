package net.thunderbird.feature.mail.sender.avatar.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.core.preference.avatar.SenderVerificationMethod
import net.thunderbird.core.preference.avatar.SenderVerificationResult
import net.thunderbird.feature.mail.sender.avatar.BimiLogoRequest
import net.thunderbird.feature.mail.sender.avatar.BimiLogoResult
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizationResult
import net.thunderbird.feature.mail.sender.avatar.BimiSvgRasterizer
import org.junit.Test

class DefaultBimiLogoRepositoryTest {
    private val message = SenderMessageIdentity("account", "folder", "message")
    private val validSvg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'><path/></svg>".toByteArray()

    @Test
    fun `does not call transport without exact DMARC verification`() = runTest {
        val transport = FakeTransport(response())
        val testSubject = repository(transport)
        val request = request(verification = SenderVerificationResult.NotChecked)

        val result = testSubject.load(request)

        assertThat(result).isEqualTo(BimiLogoResult.Rejected(BimiLogoResult.Reason.MESSAGE_NOT_DMARC_VERIFIED))
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `stores valid exact SVG and returns shield permission`() = runTest {
        val testSubject = repository(FakeTransport(response()))

        val result = testSubject.load(request())

        val available = result as BimiLogoResult.Available
        assertThat(available.source).isEqualTo(BimiLogoResult.Source.NETWORK)
        assertThat(available.mayShowVerifiedShield).isFalse()
        assertThat(available.image.key.startsWith("file:")).isTrue()
        assertThat(available.image.key.endsWith(".png")).isTrue()
    }

    @Test
    fun `permits shield only with exact DMARC and BIMI evidence`() = runTest {
        val verification = SenderVerificationResult.Verified(
            message,
            setOf(SenderVerificationMethod.DMARC_ALIGNMENT, SenderVerificationMethod.BIMI_ASSERTION),
        )

        val result = repository(FakeTransport(response())).load(request(verification))

        assertThat((result as BimiLogoResult.Available).mayShowVerifiedShield).isTrue()
    }

    @Test
    fun `rejects wrong status MIME oversized and dangerous SVG`() = runTest {
        val cases = listOf(
            response(code = 404) to BimiLogoResult.Reason.INVALID_RESPONSE,
            response(contentType = "image/png") to BimiLogoResult.Reason.INVALID_RESPONSE,
            response(body = ByteArray(SafeBimiTransport.MAX_SVG_BYTES + 1)) to BimiLogoResult.Reason.INVALID_SVG,
            response(
                body = "<svg viewBox='0 0 1 1'><script/></svg>".toByteArray(),
            ) to BimiLogoResult.Reason.INVALID_SVG,
        )

        cases.forEach { (response, reason) ->
            val result = repository(FakeTransport(response)).load(request())
            assertThat(result).isEqualTo(BimiLogoResult.Rejected(reason))
        }
    }

    @Test
    fun `uses fresh cache without network`() = runTest {
        var now = 1_000L
        val directory = Files.createTempDirectory("bimi-cache-test")
        repository(FakeTransport(response()), directory, { now }).load(request())
        val failingTransport = FakeTransport(failure = java.io.IOException("offline"))

        val result = repository(failingTransport, directory, { now + 1 }).load(request())

        val available = result as BimiLogoResult.Available
        assertThat(available.source).isEqualTo(BimiLogoResult.Source.FRESH_CACHE)
        assertThat(failingTransport.calls).isEqualTo(0)
    }

    @Test
    fun `uses cached PNG without rendering SVG again`() = runTest {
        val directory = Files.createTempDirectory("bimi-cache-test")
        val rasterizer = FakeRasterizer()
        repository(FakeTransport(response()), directory, rasterizer = rasterizer).load(request())

        repository(FakeTransport(response()), directory, rasterizer = rasterizer).load(request())

        assertThat(rasterizer.calls).isEqualTo(1)
    }

    @Test
    fun `rejects logo when safe rasterization fails`() = runTest {
        val rasterizer = FakeRasterizer(
            BimiSvgRasterizationResult.Rejected(
                BimiSvgRasterizationResult.Reason.RENDERING_FAILED,
            ),
        )

        val result = repository(FakeTransport(response()), rasterizer = rasterizer).load(request())

        assertThat(result).isEqualTo(BimiLogoResult.Rejected(BimiLogoResult.Reason.RASTERIZATION_FAILED))
    }

    @Test
    fun `stale cache never permits verified shield`() = runTest {
        var now = 1_000L
        val directory = Files.createTempDirectory("bimi-cache-test")
        repository(FakeTransport(response()), directory, { now }).load(request())
        now += 300_001
        val testSubject = repository(FakeTransport(failure = java.io.IOException("offline")), directory, { now })

        val result = testSubject.load(request())

        val available = result as BimiLogoResult.Available
        assertThat(available.source).isEqualTo(BimiLogoResult.Source.STALE_CACHE)
        assertThat(available.mayShowVerifiedShield).isFalse()
    }

    @Test
    fun `negative cache avoids repeated lookup until TTL expires`() = runTest {
        var now = 1_000L
        val directory = Files.createTempDirectory("bimi-cache-test")
        val resolver = FakeRecordResolver(BimiRecordLookupResult.NotFound)
        val testSubject = repository(FakeTransport(response()), directory, { now }, resolver)

        assertThat(testSubject.load(request()))
            .isEqualTo(BimiLogoResult.Rejected(BimiLogoResult.Reason.BIMI_RECORD_NOT_FOUND))
        assertThat(testSubject.load(request()))
            .isEqualTo(BimiLogoResult.Rejected(BimiLogoResult.Reason.BIMI_RECORD_NOT_FOUND))
        assertThat(resolver.calls).isEqualTo(1)

        now += 15 * 60 * 1000L + 1
        testSubject.load(request())
        assertThat(resolver.calls).isEqualTo(2)
    }

    @Test
    fun `invalid BIMI record is negatively cached but DNS errors are not`() = runTest {
        val invalidResolver = FakeRecordResolver(BimiRecordLookupResult.Invalid)
        val invalidRepository = repository(FakeTransport(response()), resolver = invalidResolver)

        invalidRepository.load(request())
        invalidRepository.load(request())

        assertThat(invalidResolver.calls).isEqualTo(1)

        val errorResolver = FakeRecordResolver(BimiRecordLookupResult.Error)
        val errorRepository = repository(FakeTransport(response()), resolver = errorResolver)
        errorRepository.load(request())
        errorRepository.load(request())
        assertThat(errorResolver.calls).isEqualTo(2)
    }

    private fun repository(
        transport: FakeTransport,
        directory: java.nio.file.Path = Files.createTempDirectory("bimi-cache-test"),
        now: () -> Long = { 1_000L },
        resolver: FakeRecordResolver = FakeRecordResolver(),
        rasterizer: BimiSvgRasterizer = FakeRasterizer(),
    ) = DefaultBimiLogoRepository(directory, resolver, transport, StrictSvgValidator(), now, rasterizer)

    private fun request(
        verification: SenderVerificationResult = SenderVerificationResult.Verified(
            message,
            setOf(SenderVerificationMethod.DMARC_ALIGNMENT),
        ),
    ) = BimiLogoRequest(message, "example.com", verification)

    private fun response(
        code: Int = 200,
        contentType: String = "image/svg+xml",
        body: ByteArray = validSvg,
    ) = BimiHttpResponse(code, contentType, body)

    private class FakeTransport(
        private val response: BimiHttpResponse? = null,
        private val failure: Exception? = null,
    ) : BimiTransport {
        var calls = 0

        override fun fetch(uri: java.net.URI): BimiHttpResponse {
            calls++
            failure?.let { throw it }
            return requireNotNull(response)
        }
    }

    private class FakeRecordResolver(
        private val result: BimiRecordLookupResult = BimiRecordLookupResult.Found(
            BimiRecord(java.net.URI("https://brand.example/logo.svg"), 300_000, dnssecAuthenticated = true),
        ),
    ) : BimiRecordResolver {
        var calls = 0

        override fun lookup(domain: String, selector: String): BimiRecordLookupResult {
            calls++
            return result
        }
    }

    private class FakeRasterizer(
        private val result: BimiSvgRasterizationResult = BimiSvgRasterizationResult.Success(
            png = byteArrayOf(1, 2, 3),
            width = 128,
            height = 128,
        ),
    ) : BimiSvgRasterizer {
        var calls = 0

        override fun rasterize(svg: ByteArray, sizePx: Int): BimiSvgRasterizationResult {
            calls++
            return result
        }
    }
}
