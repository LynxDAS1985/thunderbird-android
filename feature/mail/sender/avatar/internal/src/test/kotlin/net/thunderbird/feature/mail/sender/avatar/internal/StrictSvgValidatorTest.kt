package net.thunderbird.feature.mail.sender.avatar.internal

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class StrictSvgValidatorTest {
    private val testSubject = StrictSvgValidator()

    @Test
    fun `accepts small square self-contained SVG`() {
        assertThat(testSubject.isValid(svg("<path d='M0 0h10v10z'/>").bytes())).isTrue()
    }

    @Test
    fun `rejects DTD entities scripts and foreign objects`() {
        assertThat(testSubject.isValid("<!DOCTYPE svg><svg viewBox='0 0 10 10'/>".bytes())).isFalse()
        assertThat(testSubject.isValid("<!ENTITY x 'x'><svg viewBox='0 0 10 10'/>".bytes())).isFalse()
        assertThat(testSubject.isValid(svg("<script/> ").bytes())).isFalse()
        assertThat(testSubject.isValid(svg("<foreignObject/> ").bytes())).isFalse()
    }

    @Test
    fun `rejects external links URL values and event handlers`() {
        assertThat(testSubject.isValid(svg("<image href='https://evil.example/a.png'/>").bytes())).isFalse()
        assertThat(testSubject.isValid(svg("<path fill='url(https://evil.example/a)'/>").bytes())).isFalse()
        assertThat(testSubject.isValid(svg("<path onclick='steal()'/>").bytes())).isFalse()
        assertThat(testSubject.isValid(svg("<use href='#local'/>").bytes())).isFalse()
        assertThat(testSubject.isValid(svg("<?unsafe data?><path/>").bytes())).isFalse()
    }

    @Test
    fun `rejects non-square non-positive or missing viewBox`() {
        assertThat(testSubject.isValid("<svg viewBox='0 0 10 20'/>".bytes())).isFalse()
        assertThat(testSubject.isValid("<svg viewBox='0 0 0 0'/>".bytes())).isFalse()
        assertThat(testSubject.isValid("<svg/>".bytes())).isFalse()
    }

    @Test
    fun `rejects excessive depth element count and decompressed size`() {
        val deep = "<g>".repeat(65) + "</g>".repeat(65)
        val many = "<path/>".repeat(5001)
        val huge = ByteArray(SafeBimiTransport.MAX_SVG_BYTES + 1) { 'x'.code.toByte() }

        assertThat(testSubject.isValid(svg(deep).bytes())).isFalse()
        assertThat(testSubject.isValid(svg(many).bytes())).isFalse()
        assertThat(testSubject.isValid(huge)).isFalse()
    }

    private fun svg(body: String) = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'>$body</svg>"
    private fun String.bytes() = toByteArray(Charsets.UTF_8)
}
