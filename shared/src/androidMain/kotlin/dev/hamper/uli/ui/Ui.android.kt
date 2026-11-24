package dev.hamper.uli.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import java.time.Instant
import java.time.ZoneId

@Composable
actual fun rememberAnswerHaptic(): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) { { ok -> view.performHapticFeedback(if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT) } }
}

actual fun localDay(now: Long): LocalDay = localDay(now, ZoneId.systemDefault())

fun localDay(now: Long, zone: ZoneId): LocalDay {
    val t = Instant.ofEpochMilli(now).atZone(zone)
    return LocalDay(t.hour, t.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli())
}
