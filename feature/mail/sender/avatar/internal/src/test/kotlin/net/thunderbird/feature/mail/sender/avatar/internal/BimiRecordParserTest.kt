package net.thunderbird.feature.mail.sender.avatar.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class BimiRecordParserTest {
    @Test
    fun `parses one valid BIMI record`() {
        val result = BimiRecordParser.parseLogoUri("v=BIMI1; l=https://brand.example/logo.svg; a=self;")

        assertThat(result.toString()).isEqualTo("https://brand.example/logo.svg")
    }

    @Test
    fun `rejects unknown duplicate insecure and oversized values`() {
        assertThat(BimiRecordParser.parseLogoUri("v=BIMI1; x=value; l=https://brand.example/logo.svg")).isNull()
        assertThat(BimiRecordParser.parseLogoUri("v=BIMI1; v=BIMI1; l=https://brand.example/logo.svg")).isNull()
        assertThat(BimiRecordParser.parseLogoUri("v=BIMI1; l=http://brand.example/logo.svg")).isNull()
        assertThat(BimiRecordParser.parseLogoUri("v=BIMI1; l=https://brand.example/${"x".repeat(8192)}")).isNull()
    }
}
