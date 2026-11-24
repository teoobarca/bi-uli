package dev.hamper.uli

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import dev.hamper.uli.web.SystemBack

@Composable
actual fun PredictiveBack(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit) {
    val back by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val remove = if (enabled) SystemBack.add { back() } else null
        onDispose { remove?.invoke() }
    }
}
