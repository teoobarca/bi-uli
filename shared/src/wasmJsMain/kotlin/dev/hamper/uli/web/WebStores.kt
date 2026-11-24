package dev.hamper.uli.web

import dev.hamper.uli.data.Loaded
import dev.hamper.uli.data.ProgressStore
import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.data.SessionStore
import dev.hamper.uli.data.storeJson
import dev.hamper.uli.engine.ProgressData
import kotlinx.browser.localStorage

/** The same documents as the files on Android, in this browser's localStorage. Damaged ones are copied aside first. */
private fun <T> read(key: String, decode: (String) -> T): Loaded<T> {
    val text = try {
        localStorage.getItem(key) ?: return Loaded.Missing
    } catch (e: Throwable) {
        return Loaded.Corrupt(null, e)
    }
    return try {
        Loaded.Ok(decode(text))
    } catch (e: Throwable) {
        // one copy of the newest damage
        val copy = "$key.corrupt"
        Loaded.Corrupt(runCatching { localStorage.setItem(copy, text) }.map { copy }.getOrNull(), e)
    }
}

private fun write(key: String, text: String) {
    try {
        localStorage.setItem(key, text)
    } catch (e: Throwable) {
        // a JS error (a full or blocked storage) is no kotlin.Exception; callers handle those
        throw IllegalStateException("localStorage refused $key", e)
    }
}

internal class WebProgressStore : ProgressStore {
    override fun load(): Loaded<ProgressData> = read(KEY) { storeJson.decodeFromString<ProgressData>(it) }
    override fun save(data: ProgressData) = write(KEY, storeJson.encodeToString(data))

    private companion object { const val KEY = "uli.progress" }
}

internal class WebSessionStore : SessionStore {
    override fun load(): Loaded<SavedSession> = read(KEY) { storeJson.decodeFromString<SavedSession>(it) }
    override fun save(data: SavedSession) {
        if (data.isEmpty) localStorage.removeItem(KEY) else write(KEY, storeJson.encodeToString(data))
    }

    private companion object { const val KEY = "uli.session" }
}
