package com.remotepool.storage

import com.remotepool.models.Pool
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Pool file storage: one pool = one `<poolName>.json` file.
 * The pool name is the key; duplication is excluded.
 *
 * Blocking and enabling methods from Dispatchers.IO.
 */
class PoolStore(private val baseDir: File) {

    /** Creates a new pool. If a pool with the same name already exists, it abandons [IOException]. */
    @Synchronized
    @Throws(IOException::class)
    fun create(pool: Pool): Pool {
        val file = fileFor(pool.poolName)
        ensureDir()
        if (!file.createNewFile())
            throw IOException("Pool \"${pool.poolName}\" already exists")
        try {
            writeAtomically(file, pool.toJson())
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        return pool
    }

    @Synchronized
    @Throws(IOException::class)
    fun delete(name: String) {
        val file = fileFor(name)
        if (!file.delete())
            throw IOException("Couldn't delete pool \"$name\"")
    }

    @Synchronized
    fun enumerate(): Set<String> =
        baseDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXTENSION) }
            ?.map { it.name.removeSuffix(EXTENSION) }
            ?.toSet()
            ?: emptySet()

    @Synchronized
    @Throws(IOException::class)
    fun load(name: String): Pool {
        val file = fileFor(name)
        if (!file.isFile)
            throw FileNotFoundException("Pool \"$name\" not found")
        return Pool.fromJson(file.readText(StandardCharsets.UTF_8))
    }

    @Synchronized
    @Throws(IOException::class)
    fun save(pool: Pool): Pool {
        val file = fileFor(pool.poolName)
        if (!file.isFile)
            throw FileNotFoundException("Pool \"${pool.poolName}\" not found")
        writeAtomically(file, pool.toJson())
        return pool
    }

    /**
     * Renames the pool: file and the poolName field within it.
     * Secrets in SecretsManager are bound to the old name and are not migrated.
     */
    @Synchronized
    @Throws(IOException::class)
    fun rename(name: String, replacement: String): Pool {
        val file = fileFor(name)
        val replacementFile = fileFor(replacement)
        if (!file.isFile)
            throw FileNotFoundException("Pool \"$name\" not found")
        if (!replacementFile.createNewFile())
            throw IOException("Pool \"$replacement\" already exists")
        try {
            val renamed = load(name).withName(replacement)
            writeAtomically(replacementFile, renamed.toJson())
            if (!file.delete())
                throw IOException("Couldn't delete old pool \"$name\"")
            return renamed
        } catch (e: Throwable) {
            replacementFile.delete()
            throw e
        }
    }

    private fun ensureDir() {
        if (!baseDir.isDirectory && !baseDir.mkdirs())
            throw IOException("Couldn't create directory ${baseDir.path}")
    }

    private fun fileFor(name: String): File {
        require(!isNameInvalid(name)) { "Invalid pool name: \"$name\"" }
        return File(baseDir, name + EXTENSION)
    }

    /** We write to a temporary file and rename it so that a crash doesn't leave behind truncated JSON. */
    private fun writeAtomically(target: File, json: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(json, StandardCharsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Couldn't write ${target.name}")
        }
    }

    companion object {
        private const val EXTENSION = ".json"
        private const val MAX_NAME_LENGTH = 100

        /** Имя идёт в имя файла, поэтому отсекаем всё, что может выйти за пределы каталога. */
        fun isNameInvalid(name: String): Boolean =
            name.isBlank() ||
                    name.length > MAX_NAME_LENGTH ||
                    name == "." || name == ".." ||
                    name.any { it == '/' || it == '\\' || it == '\u0000' }
    }
}