package net.thunderbird.feature.mail.sender.avatar.internal

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.nio.file.Files
import org.junit.Test

class BimiDiskCacheTest {
    @Test
    fun `write is readable and temporary files are removed on startup`() {
        val directory = Files.createTempDirectory("bimi-cache-test")
        val bimiDirectory = directory.resolve("bimi/v1")
        Files.createDirectories(bimiDirectory)
        val temporary = Files.writeString(bimiDirectory.resolve("abandoned.tmp"), "partial")

        val testSubject = BimiDiskCache(directory, now = { 100L })
        val entry = testSubject.write("key", byteArrayOf(1, 2, 3), ttlMillis = 10, staleMillis = 20)

        assertThat(Files.exists(temporary)).isFalse()
        assertThat(testSubject.read("key")).isNotNull()
        assertThat(entry.isFresh(110)).isTrue()
        assertThat(entry.isFresh(111)).isFalse()
        assertThat(entry.isUsable(120)).isTrue()
    }

    @Test
    fun `cache trims old files to configured maximum`() {
        var now = 1L
        val directory = Files.createTempDirectory("bimi-cache-test")
        val testSubject = BimiDiskCache(directory, now = { now }, maxBytes = 30)

        testSubject.write("first", ByteArray(20), 100, 200)
        now++
        testSubject.write("second", ByteArray(20), 100, 200)

        val total = Files.walk(directory).use { paths ->
            paths.filter(Files::isRegularFile).mapToLong(Files::size).sum()
        }
        assertThat(total <= 30).isTrue()
    }
}
