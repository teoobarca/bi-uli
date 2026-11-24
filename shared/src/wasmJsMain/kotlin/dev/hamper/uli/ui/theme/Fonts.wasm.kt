package dev.hamper.uli.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.hamper.uli.web.Files

internal actual fun fontFile(file: String, weight: FontWeight, variationSettings: FontVariation.Settings): Font {
    val axes = variationSettings.settings.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }
    return androidx.compose.ui.text.platform.Font("$file/$axes", { Files.bytes("$file.ttf") }, weight, variationSettings = variationSettings)
}
