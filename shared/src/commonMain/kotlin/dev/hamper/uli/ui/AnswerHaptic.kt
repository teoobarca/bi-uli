package dev.hamper.uli.ui

import androidx.compose.runtime.Composable

/** Vibrates for a right (true) or wrong (false) answer; silent where the device has no haptics for apps (the web). */
@Composable
expect fun rememberAnswerHaptic(): (Boolean) -> Unit
