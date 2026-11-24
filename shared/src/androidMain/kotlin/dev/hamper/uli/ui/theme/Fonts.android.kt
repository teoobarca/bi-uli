package dev.hamper.uli.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.hamper.uli.shared.R

internal actual fun fontFile(file: String, weight: FontWeight, variationSettings: FontVariation.Settings): Font = Font(
    when (file) {
        "google_sans_flex" -> R.font.google_sans_flex
        "jetbrains_mono" -> R.font.jetbrains_mono
        else -> error("no font $file")
    },
    weight = weight,
    variationSettings = variationSettings,
)
