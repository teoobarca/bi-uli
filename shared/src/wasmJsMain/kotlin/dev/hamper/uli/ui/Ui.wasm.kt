@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.hamper.uli.ui

import androidx.compose.runtime.Composable

/** iOS Safari has no vibration; the browser stays quiet. */
@Composable
actual fun rememberAnswerHaptic(): (Boolean) -> Unit = {}

private fun hourOf(ms: Double): Int = js("new Date(ms).getHours()")
private fun midnightOf(ms: Double): Double = js("new Date(ms).setHours(0, 0, 0, 0)")

actual fun localDay(now: Long): LocalDay = LocalDay(hourOf(now.toDouble()), midnightOf(now.toDouble()).toLong())
