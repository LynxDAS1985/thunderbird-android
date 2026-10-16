package net.thunderbird.feature.mail.sender.avatar.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import java.net.InetAddress
import java.net.URI
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class SafeBimiTransportTest {
    private val publicAddress = InetAddress.getByName("93.184.216.34")

    @Test
    fun `follows no more than three HTTPS redirects`() {
        var calls = 0
        val transport = transport(
            Interceptor { chain ->
                calls++
                response(chain, 302, headers = mapOf("Location" to "https://brand.example/$calls.svg"))
            },
        )

        assertFailure { transport.fetch(URI("https://brand.example/logo.svg")) }
            .isInstanceOf<BimiTransportException.TooManyRedirects>()
        assertThat(calls).isEqualTo(4)
    }

    @Test
    fun `rejects redirect to HTTP before another request`() {
        var calls = 0
        val transport = transport(
            Interceptor { chain ->
                calls++
                response(chain, 302, headers = mapOf("Location" to "http://brand.example/logo.svg"))
            },
        )

        assertFailure { transport.fetch(URI("https://brand.example/logo.svg")) }
            .isInstanceOf<BimiTransportException.InvalidUrl>()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `rejects streamed response above decompressed size limit`() {
        val body = ByteArray(SafeBimiTransport.MAX_SVG_BYTES + 1).toResponseBody()
        val transport = transport(Interceptor { chain -> response(chain, 200, body = body) })

        assertFailure { transport.fetch(URI("https://brand.example/logo.svg")) }
            .isInstanceOf<BimiTransportException.ResponseTooLarge>()
    }

    @Test
    fun `DNS adapter rejects mixed public and private answers`() {
        val dns = PublicAddressDns(
            HostResolver {
                listOf(publicAddress, InetAddress.getByName("192.168.1.1"))
            },
        )

        assertFailure { dns.lookup("brand.example") }.isInstanceOf<BimiTransportException.BlockedAddress>()
    }

    private fun transport(interceptor: Interceptor): SafeBimiTransport {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(interceptor)
            .build()
        return SafeBimiTransport(HostResolver { listOf(publicAddress) }, client)
    }

    private fun response(
        chain: Interceptor.Chain,
        code: Int,
        headers: Map<String, String> = emptyMap(),
        body: okhttp3.ResponseBody = ByteArray(0).toResponseBody(),
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .message("test")
        .code(code)
        .body(body)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
}
