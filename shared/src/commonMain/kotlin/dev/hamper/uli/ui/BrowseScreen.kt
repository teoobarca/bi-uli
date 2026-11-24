package dev.hamper.uli.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.Screen
import dev.hamper.uli.engine.CardState
import dev.hamper.uli.engine.Code
import dev.hamper.uli.engine.Question
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.Topic
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import dev.hamper.uli.ui.theme.monoStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(vm: AppViewModel, screen: Screen.Browse) {
    val progress = vm.progress
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val groups = remember { Topic.entries.map { t -> t to vm.questions.filter { it.topicEnum == t } } }
    var expanded by remember { mutableStateOf(screen.expanded) }
    val listState = rememberLazyListState()

    // Index of each topic header in the lazy list, to jump straight to a topic from Domov.
    LaunchedEffect(screen.topic) {
        val t = screen.topic ?: return@LaunchedEffect
        var index = 0
        for ((topic, qs) in groups) {
            if (topic == t) break
            index += 1 + qs.size
        }
        listState.scrollToItem(index)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::home) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Späť") }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Všetky otázky", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Zvládnuté ${Srs.masteredCount(vm.ids, progress)} zo ${vm.ids.size}. Ťukni a uvidíš odpoveď.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for ((topic, qs) in groups) {
                stickyHeader(key = "h-${topic.key}") {
                    val m = qs.count { progress.card(it.id).isMastered }
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(topic.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "$m/${qs.size}",
                            fontFamily = Rounded,
                            fontWeight = FontWeight.W700,
                            fontSize = 15.sp,
                            color = if (m == qs.size) Uli.colors.right else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                qs.forEachIndexed { i, q ->
                    item(key = q.id) {
                        BrowseRow(
                            q = q,
                            card = progress.card(q.id),
                            open = expanded == q.id,
                            showSk = progress.showSk,
                            shape = groupShape(i, qs.size, outer = 22.dp, inner = 6.dp),
                            onClick = { expanded = if (expanded == q.id) null else q.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseRow(q: Question, card: CardState, open: Boolean, showSk: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (open) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        // TalkBack hears where every question stands, the same three states the mark draws.
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = masteryLabel(card) },
    ) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                MasteryMark(card, Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    CodeText(
                        q.question,
                        if (open) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        english = true,
                    )
                    if (open && showSk && q.hasDistinctSk) {
                        Spacer(Modifier.height(4.dp))
                        CodeText(q.questionSk, MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("${q.id}", style = monoStyle(fontSize = 12.sp), color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 3.dp))
            }
            if (open) {
                Spacer(Modifier.height(14.dp))
                CorrectAnswers(q, codeOptions)
                Spacer(Modifier.height(14.dp))
                Explanation(q, emptyList())
            }
        }
    }
}

fun masteryLabel(card: CardState): String = when {
    card.isMastered -> "Zvládnutá"
    card.isNew -> "Nová"
    else -> "Rozrobená"
}

/** New: empty ring. Learning: amber dot. Mastered: green sun with a check. The row says it to TalkBack. */
@Composable
private fun MasteryMark(card: CardState, modifier: Modifier = Modifier) {
    val c = Uli.colors
    when {
        card.isMastered -> Box(modifier.size(22.dp).clip(Shapes.sunny).background(c.right), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = c.onRight, modifier = Modifier.size(14.dp))
        }
        card.isNew -> Box(modifier.size(22.dp).padding(2.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        else -> Box(modifier.size(22.dp).padding(2.dp).clip(Shapes.cookie6).background(c.learning))
    }
}
