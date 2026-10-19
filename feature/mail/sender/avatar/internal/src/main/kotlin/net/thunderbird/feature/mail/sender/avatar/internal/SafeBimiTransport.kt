package net.thunderbird.feature.mail.sender.avatar.internal

import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun interface HostResolver {
    fun lookup(host: String): List<InetAddress>
}

internal class PublicAddressDns(private val hostResolver: HostResolver) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = hostResolver.lookup(hostname).also { addresses ->
        if (!BimiSecurity.addressesArePublic(addresses)) throw BimiTransportException.BlockedAddress
    }
}

internal data class BimiHttpResponse(val code: Int, val contentType: String?, val body: ByteArray)

internal fun interface BimiTransport {
    fun fetch(uri: URI): BimiHttpResponse
}

internal class SafeBimiTransport(
    private val hostResolver: HostResolver = HostResolver { InetAddress.getAllByName(it).toList() },
    private val client: OkHttpClient = defaultClient(hostResolver),
) : BimiTransport {
    override fun fetch(uri: URI): BimiHttpResponse {
        var current = uri
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val safeUri = BimiSecurity.validateUrl(current.toString()) ?: throw BimiTransportException.InvalidUrl
            val addresses = hostResolver.lookup(requireNotNull(safeUri.host))
            if (!BimiSecurity.addressesArePublic(addresses)) throw BimiTransportException.BlockedAddress

            val request = Request.Builder().url(safeUri.toString()).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    if (redirectCount == MAX_REDIRECTS) throw BimiTransportException.TooManyRedirects
                    val location = response.header("Location") ?: throw BimiTransportException.InvalidResponse
                    current = runCatching { safeUri.resolve(location.trim()) }
                        .getOrElse { throw BimiTransportException.InvalidResponse }
                    return@repeat
                }
                val contentLength = response.body.contentLength()
                if (contentLength > MAX_SVG_BYTES) throw BimiTransportException.ResponseTooLarge
                return BimiHttpResponse(
                    code = response.code,
                    contentType = response.header("Content-Type"),
                    body = response.body.byteStream().readLimited(MAX_SVG_BYTES),
                )
            }
        }
        throw BimiTransportException.TooManyRedirects
    }

    private fun java.io.InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            total += count
            if (total > maxBytes) throw BimiTransportException.ResponseTooLarge
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_REDIRECTS = 3
        const val MAX_SVG_BYTES = 256 * 1024

        private fun defaultClient(hostResolver: HostResolver) = OkHttpClient.Builder()
            .dns(PublicAddressDns(hostResolver))
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

internal sealed class BimiTransportException : IOException() {
    data object InvalidUrl : BimiTransportException()
    data object BlockedAddress : BimiTransportException()
    data object TooManyRedirects : BimiTransportException()
    data object InvalidResponse : BimiTransportException()
    data object ResponseTooLarge : BimiTransportException()
}
