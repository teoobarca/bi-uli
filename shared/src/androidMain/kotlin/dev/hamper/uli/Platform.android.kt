package dev.hamper.uli

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException

@Composable
actual fun PredictiveBack(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit) {
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { onProgress(it.progress) }
            onBack()
        } catch (e: CancellationException) {
            // Gesture cancelled: stay.
        } finally {
            onProgress(0f)
        }
    }
}
