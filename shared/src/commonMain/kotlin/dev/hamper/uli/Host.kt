package dev.hamper.uli

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineDispatcher

/** What [AppViewModel] asks of the device it runs on. */
interface Host {
    /** Runs the writes, one at a time in order. */
    val io: CoroutineDispatcher

    /** Wall clock, epoch ms. */
    fun now(): Long

    /** Monotonic ms. */
    fun uptime(): Long

    /** The screen to come back to after the process (or the page) dies. */
    var savedScreen: String?

    fun log(message: String, error: Throwable)

    /** Tells the user, once, that the progress could not be saved. Called on any thread. */
    fun saveFailed()
}

/** Back by gesture or button while [enabled]; [onProgress] (0..1) follows Android's predictive gesture and ends at 0. */
@Composable
expect fun PredictiveBack(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit)
