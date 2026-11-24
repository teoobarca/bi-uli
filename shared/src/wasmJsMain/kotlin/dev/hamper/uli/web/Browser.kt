@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.hamper.uli.web

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.fetch.Response
import kotlin.js.Promise

// The calls into resources/web.js (window.uliWeb), which says what each one does.

internal fun jsOnBack(cb: () -> Unit): Unit = js("window.uliWeb.onBack(cb)")
internal fun jsReady(): Unit = js("window.uliWeb.ready()")
internal fun jsFailed(message: String): Unit = js("window.uliWeb.failed(message)")
internal fun jsSaveFailed(): Unit = js("window.uliWeb.saveFailed()")
internal fun jsHint(): Boolean = js("window.uliWeb.needsHomeScreen()")
internal fun jsPersist(): Promise<JsAny?> = js("window.uliWeb.persist()")
internal fun jsLog(message: String): Unit = js("console.warn(message)")
internal fun jsNow(): Double = js("Date.now()")
internal fun jsUptime(): Double = js("performance.now()")

/** Files next to index.html, fetched before the first frame (the browser reads nothing synchronously). */
internal object Files {
    private val loaded = HashMap<String, ByteArray>()

    fun bytes(name: String): ByteArray = loaded[name] ?: error("$name was not preloaded")

    suspend fun load(vararg names: String) {
        names.map { it to window.fetch(it) }.forEach { (name, request) ->
            val r: Response = request.await()
            check(r.ok) { "Failed to fetch $name: ${r.status}" }
            val buffer: ArrayBuffer = r.arrayBuffer().await()
            loaded[name] = Int8Array(buffer).toByteArray()
        }
    }
}

/** The browser's back (the iOS edge swipe, the back button): the newest enabled handler takes it. */
internal object SystemBack {
    private val handlers = ArrayList<() -> Unit>()

    /** Registers [handler]; call the result to take it away. */
    fun add(handler: () -> Unit): () -> Unit {
        handlers.add(handler)
        return { handlers.remove(handler) }
    }

    fun press() {
        handlers.lastOrNull()?.invoke()
    }
}
