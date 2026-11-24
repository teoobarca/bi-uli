@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.hamper.uli.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.Host
import dev.hamper.uli.UliApp
import dev.hamper.uli.ui.theme.UliTheme
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.get
import org.w3c.dom.set

/** ULI in the browser: the same screens, engine and saved documents as on Android. */
fun main() {
    MainScope().launch {
        try {
            start()
        } catch (e: Throwable) {
            jsFailed(e.toString()) // the splash says it in words (resources/web.js)
            throw e
        }
    }
}

private const val HINT_HIDDEN = "uli.hintHidden"

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun start() {
    Files.load("questions.json", "google_sans_flex.ttf", "jetbrains_mono.ttf")
    val vm = AppViewModel(Files.bytes("questions.json").decodeToString(), WebProgressStore(), WebSessionStore(), WebHost)
    // ?shot=<name> opens a fixed state (Shots) for screenshots, only on this machine
    if (window.location.hostname == "127.0.0.1") shotParam()?.let(vm::applyShot)
    jsOnBack(SystemBack::press)
    ComposeViewport(document.getElementById("app")!!) {
        var hint by remember { mutableStateOf(jsHint() && runCatching { localStorage[HINT_HIDDEN] == null }.getOrDefault(false)) }
        UliTheme {
            UliApp(
                vm,
                notice = if (hint) ({ HomeScreenHint { hint = false; runCatching { localStorage[HINT_HIDDEN] = "1" } } }) else null,
            )
        }
        // the splash goes only once the first screen is drawn
        LaunchedEffect(Unit) { withFrameNanos {}; withFrameNanos {}; jsReady() }
    }
    jsPersist() // asks the browser to keep this site's storage
}

private fun shotParam(): String? = js("new URLSearchParams(location.search).get('shot')")

private object WebHost : Host {
    override val io = Dispatchers.Main // localStorage is synchronous; one thread keeps the order
    override fun now() = jsNow().toLong()
    override fun uptime() = jsUptime().toLong()

    /** Kept per tab: a reload comes back to the same screen, a new start begins on Domov. */
    override var savedScreen: String?
        get() = runCatching { sessionStorage["uli.screen"] }.getOrNull()
        set(v) { runCatching { if (v != null) sessionStorage["uli.screen"] = v } }

    override fun log(message: String, error: Throwable) = jsLog("$message: $error")
    override fun saveFailed() = jsSaveFailed()
}
