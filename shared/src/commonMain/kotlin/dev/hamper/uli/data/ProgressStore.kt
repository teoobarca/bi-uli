package dev.hamper.uli.data

import dev.hamper.uli.engine.ProgressData
import kotlinx.serialization.json.Json

/** What reading saved data found. Only missing data means "nothing yet"; anything else is kept safe. */
sealed interface Loaded<out T> {
    data class Ok<T>(val data: T) : Loaded<T>
    data object Missing : Loaded<Nothing>

    /** The data is there but unreadable. [backup] is the name of a copy of it (null if even copying failed). */
    data class Corrupt(val backup: String?, val error: Throwable) : Loaded<Nothing>
}

interface ProgressStore {
    fun load(): Loaded<ProgressData>

    /** Throws when the write fails; the caller decides how to tell the user. */
    fun save(data: ProgressData)
}

/** How progress and sessions are written, in files on Android and in the browser's storage on the web. */
val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Screenshot sessions run on seeded data and never touch the real progress file. */
class MemoryProgressStore(private var data: ProgressData = ProgressData()) : ProgressStore {
    override fun load(): Loaded<ProgressData> = Loaded.Ok(data)
    override fun save(data: ProgressData) {
        this.data = data
    }
}
