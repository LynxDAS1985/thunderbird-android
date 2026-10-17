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
        removeExpiredNegativeEntries()
        trim()
    }

    fun read(key: String): Entry? {
        val base = hash(key)
        val data = directory.resolve("$base.svg")
        val metadata = directory.resolve("$base.meta")
        val rasterized = rasterizedPath(key)
        if (!Files.isRegularFile(data) || !Files.isRegularFile(metadata)) return null
        val fields = runCatching { Files.readString(metadata).trim().split(':').map(String::toLong) }.getOrNull()
            ?.takeIf { it.size == 2 } ?: return remove(data, metadata, rasterized)
        if (fields[0] < 0 || fields[1] < fields[0]) return remove(data, metadata, rasterized)
        val bytes = runCatching { Files.readAllBytes(data) }.getOrNull() ?: return remove(data, metadata, rasterized)
        Files.setLastModifiedTime(data, java.nio.file.attribute.FileTime.fromMillis(now()))
        return Entry(data, rasterized, bytes, expiresAt = fields[0], staleUntil = fields[1])
    }

    fun write(key: String, bytes: ByteArray, ttlMillis: Long, staleMillis: Long): Entry {
        require(ttlMillis > 0 && staleMillis >= ttlMillis)
        val base = hash(key)
        val data = directory.resolve("$base.svg")
        val metadata = directory.resolve("$base.meta")
        Files.deleteIfExists(rasterizedPath(key))
        atomicWrite(data, bytes)
        atomicWrite(metadata, "${now() + ttlMillis}:${now() + staleMillis}".toByteArray())
        trim()
        return requireNotNull(read(key))
    }

    fun readNegative(key: String): NegativeEntry? {
        val metadata = negativePath(key)
        if (!Files.isRegularFile(metadata)) return null
        val entry = parseNegative(metadata) ?: return removeNegative(metadata)
        if (now() > entry.expiresAt) return removeNegative(metadata)
        Files.setLastModifiedTime(metadata, java.nio.file.attribute.FileTime.fromMillis(now()))
        return entry
    }

    fun writeNegative(key: String, reason: NegativeReason, ttlMillis: Long) {
        require(ttlMillis > 0)
        atomicWrite(negativePath(key), "${now() + ttlMillis}:${reason.name}".toByteArray())
        trim()
    }

    fun writeRasterized(entry: Entry, png: ByteArray): Path {
        atomicWrite(entry.rasterizedPath, png)
        trim()
        return entry.rasterizedPath
    }

    data class Entry(
        val path: Path,
        val rasterizedPath: Path,
        val bytes: ByteArray,
        val expiresAt: Long,
        val staleUntil: Long,
    ) {
        fun isFresh(now: Long) = now <= expiresAt
        fun isUsable(now: Long) = now <= staleUntil
    }

    data class NegativeEntry(val reason: NegativeReason, val expiresAt: Long)

    enum class NegativeReason { RECORD_NOT_FOUND, INVALID_RECORD }

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
        val positiveEntries = Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".svg") }
                .map { data -> data to directory.resolve(data.fileName.toString().removeSuffix(".svg") + ".meta") }
                .filter { (_, metadata) -> Files.isRegularFile(metadata) }
                .map { (data, metadata) ->
                    val png = directory.resolve(data.fileName.toString().removeSuffix(".svg") + ".png")
                    CacheFiles(
                        listOf(data, metadata, png).filter(Files::isRegularFile),
                        Files.getLastModifiedTime(data).toMillis(),
                    )
                }
                .toList()
        }
        val negativeEntries = Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(NEGATIVE_SUFFIX) }
                .map { file -> CacheFiles(listOf(file), Files.getLastModifiedTime(file).toMillis()) }
                .toList()
        }
        val entries = positiveEntries + negativeEntries
        var size = entries.sumOf { entry -> entry.files.sumOf(Files::size) }
        entries.sortedBy(CacheFiles::lastAccessedAt).forEach { entry ->
            if (size > maxBytes) {
                size -= entry.files.sumOf(Files::size)
                entry.files.forEach(Files::deleteIfExists)
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
                name.endsWith(".png") -> directory.resolve(name.removeSuffix(".png") + ".svg")
                name.endsWith(NEGATIVE_SUFFIX) -> file
                else -> null
            }
            if (counterpart == null || !Files.isRegularFile(counterpart)) Files.deleteIfExists(file)
        }
    }

    private fun remove(vararg paths: Path): Entry? {
        paths.forEach(Files::deleteIfExists)
        return null
    }

    private fun removeExpiredNegativeEntries() {
        Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(NEGATIVE_SUFFIX) }
                .forEach { file ->
                    val entry = parseNegative(file)
                    if (entry == null || now() > entry.expiresAt) Files.deleteIfExists(file)
                }
        }
    }

    private fun parseNegative(path: Path): NegativeEntry? {
        val fields = runCatching { Files.readString(path).trim().split(':') }.getOrNull()
            ?.takeIf { it.size == 2 } ?: return null
        val expiresAt = fields[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val reason = runCatching { NegativeReason.valueOf(fields[1]) }.getOrNull() ?: return null
        return NegativeEntry(reason, expiresAt)
    }

    private fun removeNegative(path: Path): NegativeEntry? {
        Files.deleteIfExists(path)
        return null
    }

    private fun negativePath(key: String): Path = directory.resolve("${hash(key)}$NEGATIVE_SUFFIX")

    private fun rasterizedPath(key: String): Path = directory.resolve("${hash(key)}.png")

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class CacheFiles(val files: List<Path>, val lastAccessedAt: Long)

    private companion object {
        const val NEGATIVE_SUFFIX = ".negative"
    }
}
