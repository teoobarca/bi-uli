package dev.hamper.uli.data

import androidx.core.util.AtomicFile
import dev.hamper.uli.engine.ProgressData
import java.io.File
import java.io.FileNotFoundException

/**
 * Reads [name] in [dir]. A missing file is [Loaded.Missing]; a file that cannot be read or parsed is copied to
 * `<stem>.corrupt-<timestamp>.json` first and reported as [Loaded.Corrupt], never silently treated as empty.
 */
internal fun <T> readJson(dir: File, name: String, now: () -> Long, decode: (String) -> T): Loaded<T> {
    val base = File(dir, name)
    val file = AtomicFile(base)
    val bytes = try {
        file.readFully()
    } catch (e: FileNotFoundException) {
        return Loaded.Missing
    } catch (e: Exception) {
        return Loaded.Corrupt(backup(dir, base, now())?.name, e)
    }
    return try {
        Loaded.Ok(decode(bytes.decodeToString()))
    } catch (e: Exception) {
        Loaded.Corrupt(backup(dir, base, now())?.name, e)
    }
}

private fun backup(dir: File, base: File, now: Long): File? = runCatching {
    // The same damaged file read on every start is kept once, not once per start.
    val bytes = base.readBytes()
    dir.listFiles { f -> f.name.startsWith("${base.nameWithoutExtension}.corrupt-") }
        ?.firstOrNull { it.readBytes().contentEquals(bytes) }
        ?.let { return@runCatching it }
    val copy = File(dir, "${base.nameWithoutExtension}.corrupt-$now.json")
    base.copyTo(copy, overwrite = true)
}.getOrNull()

internal fun writeJson(dir: File, name: String, text: String) {
    val file = AtomicFile(File(dir, name))
    val out = file.startWrite()
    try {
        out.write(text.encodeToByteArray())
        file.finishWrite(out)
    } catch (e: Exception) {
        file.failWrite(out)
        throw e
    }
}

/** Progress as one small JSON file in filesDir, replaced atomically on every write. */
class FileProgressStore(private val dir: File, private val now: () -> Long = System::currentTimeMillis) : ProgressStore {
    override fun load(): Loaded<ProgressData> =
        readJson(dir, NAME, now) { storeJson.decodeFromString<ProgressData>(it) }

    @Synchronized
    override fun save(data: ProgressData) = writeJson(dir, NAME, storeJson.encodeToString(data))

    companion object {
        const val NAME = "progress.json"
    }
}

/** session.json next to progress.json, replaced atomically like the progress file. */
class FileSessionStore(private val dir: File, private val now: () -> Long = System::currentTimeMillis) : SessionStore {
    override fun load(): Loaded<SavedSession> =
        readJson(dir, NAME, now) { storeJson.decodeFromString<SavedSession>(it) }

    @Synchronized
    override fun save(data: SavedSession) {
        if (data.isEmpty) {
            AtomicFile(File(dir, NAME)).delete()
            return
        }
        writeJson(dir, NAME, storeJson.encodeToString(data))
    }

    companion object {
        const val NAME = "session.json"
    }
}
