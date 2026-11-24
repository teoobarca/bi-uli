package dev.hamper.uli.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * On an iPhone in Safari: how to get the app on the home screen (a page cannot do it itself) and why now. Safari
 * deletes a site's storage after 7 days without a visit, and the home-screen app starts with storage of its own.
 */
@Composable
internal fun HomeScreenHint(onHide: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp)) {
            Text("Daj si ULI na plochu", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "V Safari ťukni na Zdieľať a potom na Pridať na plochu. Sprav to ešte pred učením: appka na ploche " +
                    "začína od nuly a v Safari sa postup po 7 dňoch bez návštevy zmaže.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onHide) { Text("Skryť", color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
}
