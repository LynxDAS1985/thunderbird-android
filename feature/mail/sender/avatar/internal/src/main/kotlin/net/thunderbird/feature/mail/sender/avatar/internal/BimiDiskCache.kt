package net.thunderbird.feature.mail.sender.avatar.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class BimiDiskCache(
    cacheDir: Path,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = 20L * 1024 * 1024,
) {
    private val directory = cacheDir.resolve("bimi").resolve("v1")

    init {
        Files.createDirectories(directory)
        Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".tmp") }.forEach(Files::deleteIfExists)
        }
        removeOrphans()
    }

    fun read(key: String): Entry? {
        val base = hash(key)
        val data = directory.resolve("$base.svg")
        val metadata = directory.resolve("$base.meta")
        if (!Files.isRegularFile(data) || !Files.isRegularFile(metadata)) return null
        val fields = runCatching { Files.readString(metadata).trim().split(':').map(String::toLong) }.getOrNull()
            ?.takeIf { it.size == 2 } ?: return remove(data, metadata)
        if (fields[0] < 0 || fields[1] < fields[0]) return remove(data, metadata)
        val bytes = runCatching { Files.readAllBytes(data) }.getOrNull() ?: return remove(data, metadata)
        Files.setLastModifiedTime(data, java.nio.file.attribute.FileTime.fromMillis(now()))
        return Entry(data, bytes, expiresAt = fields[0], staleUntil = fields[1])
    }

    fun write(key: String, bytes: ByteArray, ttlMillis: Long, staleMillis: Long): Entry {
        require(ttlMillis > 0 && staleMillis >= ttlMillis)
        val base = hash(key)
        val data = directory.resolve("$base.svg")
        val metadata = directory.resolve("$base.meta")
        atomicWrite(data, bytes)
        atomicWrite(metadata, "${now() + ttlMillis}:${now() + staleMillis}".toByteArray())
        trim()
        return requireNotNull(read(key))
    }

    data class Entry(val path: Path, val bytes: ByteArray, val expiresAt: Long, val staleUntil: Long) {
        fun isFresh(now: Long) = now <= expiresAt
        fun isUsable(now: Long) = now <= staleUntil
    }

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(directory, target.fileName.toString(), ".tmp")
        try {
            Files.write(temporary, bytes)
            runCatching {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
                .getOrElse { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun trim() {
        val entries = Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".svg") }
                .map { data -> data to directory.resolve(data.fileName.toString().removeSuffix(".svg") + ".meta") }
                .filter { (_, metadata) -> Files.isRegularFile(metadata) }
                .toList()
        }
        var size = entries.sumOf { (data, metadata) -> Files.size(data) + Files.size(metadata) }
        entries.sortedBy { (data, _) -> Files.getLastModifiedTime(data).toMillis() }.forEach { (data, metadata) ->
            if (size > maxBytes) {
                size -= Files.size(data) + Files.size(metadata)
                Files.deleteIfExists(data)
                Files.deleteIfExists(metadata)
            }
        }
    }

    private fun removeOrphans() {
        val files = Files.list(directory).use { it.filter(Files::isRegularFile).toList() }
        files.forEach { file ->
            val name = file.fileName.toString()
            val counterpart = when {
                name.endsWith(".svg") -> directory.resolve(name.removeSuffix(".svg") + ".meta")
                name.endsWith(".meta") -> directory.resolve(name.removeSuffix(".meta") + ".svg")
                else -> null
            }
            if (counterpart == null || !Files.isRegularFile(counterpart)) Files.deleteIfExists(file)
        }
    }

    private fun remove(vararg paths: Path): Entry? {
        paths.forEach(Files::deleteIfExists)
        return null
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
