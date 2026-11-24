package dev.hamper.uli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.Topic
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import dev.hamper.uli.ui.theme.monoStyle
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: AppViewModel, notice: (@Composable () -> Unit)? = null) {
    val progress = vm.progress
    val ids = vm.ids
    val readiness = Srs.readiness(ids, progress)
    val mastered = Srs.masteredCount(ids, progress)
    val now = vm.clock()
    // The true count: a Chyby round drills up to 10 of them, but the tile says how many there are.
    val drill = vm.allMistakeIds().size
    val preview = vm.nextRoundPreview()
    val openRound = vm.quiz
    val openExam = vm.exam
    val dueInRound = preview.count { Srs.isDue(progress.card(it), now) }
    val newInRound = preview.count { progress.card(it).isNew }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDiscardExam by remember { mutableStateOf(false) }
    var confirmDiscardRound by remember { mutableStateOf(false) }
    var confirmStartFresh by remember { mutableStateOf(false) }
    val lastExam = vm.examSummary?.takeIf { openExam == null || openExam.exam.index == 0 }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Header, a compact estimate against the 80 % line, the three modes as full-width rows, then the
            // topics. The list ends above the action area instead of running under it.
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Header(
                        subtitle = if (progress.cards.isEmpty()) "Úvod do Linuxu, zápočtový test" else todayLine(progress, now),
                        frozen = vm.shotMode,
                        onReset = { confirmReset = true },
                    )
                }
                vm.damagedProgress?.let { damaged ->
                    item {
                        DamagedProgressCard(
                            backupName = damaged.backup,
                            onRetry = vm::retryLoadProgress,
                            onStartFresh = { confirmStartFresh = true },
                        )
                    }
                }
                if (notice != null) item { notice() }
                if (openExam != null) {
                    item {
                        OpenExamCard(
                            answered = openExam.exam.index,
                            total = openExam.exam.ids.size,
                            onResume = vm::resumeExam,
                            onDiscard = { confirmDiscardExam = true },
                        )
                    }
                }
                item { ReadinessSummary(readiness, Srs.passChance(ids, progress), mastered, ids.size) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ModeRow(
                            title = "Skúška nanečisto",
                            detail = if (openExam != null) examProgress(openExam.exam.index, openExam.exam.ids.size) else "50 otázok",
                            icon = UliIcons.Timer,
                            badge = Shapes.clover4,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = if (openExam != null) vm::resumeExam else vm::startExam,
                            shape = groupShape(0, if (lastExam != null) 4 else 3),
                        )
                        // The last finished exam's review stays reachable until the next attempt gets an answer.
                        if (lastExam != null) {
                            LastExamRow(lastExam.result.correct, lastExam.result.total, lastExam.result.wrong.isNotEmpty(), vm::openLastExam, groupShape(1, 4))
                        }
                        ModeRow(
                            title = "Chyby",
                            detail = if (drill == 0) "Zatiaľ žiadne" else "$drill ${questionsWord(drill)}",
                            icon = UliIcons.Replay,
                            badge = Shapes.softBurst,
                            container = Uli.colors.wrongContainer,
                            content = Uli.colors.onWrongContainer,
                            // A Chyby round replaces the open round, so it asks first.
                            onClick = { if (openRound != null) confirmDiscardRound = true else vm.startMistakes() },
                            enabled = drill > 0,
                            shape = if (lastExam != null) groupShape(2, 4) else groupShape(1, 3),
                        )
                        ModeRow(
                            title = "Všetky otázky",
                            detail = "${ids.size} otázok",
                            icon = UliIcons.Stack,
                            badge = Shapes.cookie6,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { vm.openBrowse() },
                            shape = if (lastExam != null) groupShape(3, 4) else groupShape(2, 3),
                        )
                    }
                }
                item {
                    Text(
                        "Témy",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                }
                item {
                    val topics = Topic.entries
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        topics.forEachIndexed { i, t ->
                            val tIds = vm.questions.filter { it.topicEnum == t }.map { it.id }
                            val m = tIds.count { progress.card(it).isMastered }
                            val l = tIds.count { progress.card(it).let { c -> !c.isNew && !c.isMastered } }
                            TopicRow(t.title, m, l, tIds.size, groupShape(i, topics.size), onClick = { vm.openBrowse(t) })
                        }
                    }
                }
            }
            StatusBarScrim(list.canScrollBackward)
            // The list continues: a short fade where it meets the action area.
            val more by androidx.compose.animation.core.animateFloatAsState(if (list.canScrollForward) 1f else 0f, label = "more")
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(18.dp)
                    .graphicsLayer { alpha = more }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
            )
        }

        // The one big action has its own reserved area in the thumb zone; nothing scrolls under it.
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        ) {
            ContinueButton(
                title = when {
                    openRound != null -> "Pokračovať"
                    vm.isFresh -> "Začať"
                    else -> "Ďalšie kolo"
                },
                subtitle = when {
                    openRound != null -> "Rozrobené kolo, otázka ${openRound.round.displayNumber} z ${openRound.round.total}"
                    vm.isFresh -> "Prvých ${preview.size} otázok, od úvodu do Linuxu"
                    else -> roundSubtitle(dueInRound, newInRound, preview.size)
                },
                onClick = vm::continueOrStart,
            )
        }
    }

    if (confirmDiscardRound && openRound != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscardRound = false },
            title = { Text("Rozrobené kolo sa zahodí") },
            text = {
                Text(
                    "Si na otázke ${openRound.round.displayNumber} z ${openRound.round.total}. Chyby začnú nové kolo a toto sa zahodí. " +
                        "Odpovede, ktoré si už dal, ostanú započítané.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDiscardRound = false; vm.startMistakes() }) {
                    Text("Zahodiť a začať Chyby", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardRound = false }) { Text("Nechať kolo") } },
        )
    }

    if (confirmDiscardExam) {
        AlertDialog(
            onDismissRequest = { confirmDiscardExam = false },
            title = { Text("Zahodiť rozrobenú skúšku?") },
            text = { Text("Odpovede z tohto pokusu sa nezapočítajú. Postup v otázkach ostane.") },
            confirmButton = {
                TextButton(onClick = { vm.discardExam(); confirmDiscardExam = false }) {
                    Text("Zahodiť", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardExam = false }) { Text("Nechať") } },
        )
    }

    val damaged = vm.damagedProgress
    if (confirmStartFresh && damaged != null) {
        AlertDialog(
            onDismissRequest = { confirmStartFresh = false },
            title = { Text("Začať odznova?") },
            text = {
                Text(
                    "Poškodený súbor sa prepíše postupom, ktorý máš teraz v aplikácii. " +
                        (damaged.backup?.let { "Kópia $it ostane uložená." }
                            ?: "Zálohu sa nepodarilo vytvoriť, takže starý postup sa už nebude dať obnoviť."),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.acceptFreshProgress(); confirmStartFresh = false }) {
                    Text("Začať odznova", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmStartFresh = false }) { Text("Zrušiť") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Začať odznova?") },
            text = { Text("Vymaže sa celý tvoj postup: zvládnuté otázky, chyby aj odhad. Nedá sa to vrátiť.") },
            confirmButton = {
                TextButton(onClick = { vm.resetProgress(); confirmReset = false }) {
                    Text("Vymazať postup", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušiť") } },
        )
    }
}

/** An open mock exam, said the same way on the tile and the banner: how many are answered. */
private fun examProgress(answered: Int, total: Int) = "Hotovo $answered z $total"

/** "1 zvládnutá", "3 zvládnuté", "5 zvládnutých": a feminine adjective agreeing with a count of questions. */
private fun agree(n: Int, stem: String) = "$n $stem" + when (n) {
    1 -> "á"
    in 2..4 -> "é"
    else -> "ých"
}

fun questionsWord(n: Int) = when {
    n == 1 -> "otázka"
    n in 2..4 -> "otázky"
    else -> "otázok"
}

private fun roundSubtitle(due: Int, new: Int, size: Int): String = when {
    due > 0 && new > 0 -> "$size otázok: $due na zopakovanie, $new ${if (new in 2..4) "nové" else "nových"}"
    due > 0 -> "$size otázok na zopakovanie"
    new > 0 -> "$size otázok, z toho $new ${if (new in 2..4) "nové" else "nových"}"
    else -> "$size otázok na precvičenie"
}.replace("1 nových", "1 nová")

/**
 * The app's mark and name, with a line about today: a greeting for the time of day and how many questions
 * you have already answered today. The cursor in the mark blinks, like a terminal waiting for you.
 */
@Composable
private fun Header(subtitle: String, frozen: Boolean, onReset: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val blink by androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor").animateFloat(
        1f, 0f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes { durationMillis = 1060; 1f at 0; 1f at 529; 0f at 530; 0f at 1059 },
        ),
        label = "blink",
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Uli.colors.amber),
            contentAlignment = Alignment.Center,
        ) {
            Row {
                Text(">", style = monoStyle(fontSize = 17.sp, fontWeight = FontWeight.W700), color = Uli.colors.onAmber)
                Text(
                    "_",
                    style = monoStyle(fontSize = 17.sp, fontWeight = FontWeight.W700),
                    color = Uli.colors.onAmber,
                    modifier = Modifier.graphicsLayer { alpha = if (frozen) 1f else blink },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ULI", style = MaterialTheme.typography.titleLarge, lineHeight = 22.sp)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Viac") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Vymazať postup") }, onClick = { menu = false; onReset() })
            }
        }
    }
}

/**
 * progress.json could not be read: say so plainly, say whether a copy exists (only when it really does), and
 * let the user choose.
 */
@Composable
private fun DamagedProgressCard(backupName: String?, onRetry: () -> Unit, onStartFresh: () -> Unit) {
    val c = Uli.colors
    Surface(color = c.wrongContainer, contentColor = c.onWrongContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)) {
            Text(
                if (backupName != null) "Nepodarilo sa načítať tvoj postup, záloha je uložená" else "Nepodarilo sa načítať tvoj postup ani uložiť zálohu",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                (if (backupName != null) "Kópia súboru je v aplikácii ako $backupName. " else "") +
                    "Pôvodný súbor sa neprepíše. Medzitým sa učíš s prázdnym postupom.\n" +
                    "Kým nevyberieš, nový postup sa neukladá.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRetry) { Text("Skúsiť znova", color = c.onWrongContainer) }
                TextButton(onClick = onStartFresh) { Text("Začať odznova", color = c.onWrongContainer) }
            }
        }
    }
}

/**
 * The estimate as a compact summary: the number on the amber cookie, what it is, how many questions are
 * mastered, and the same number against the 80 % pass line.
 */
@Composable
private fun ReadinessSummary(readiness: Double, chance: Double, mastered: Int, total: Int) {
    val pct = (readiness * 100).roundToInt()
    // Green only when passing is likely, not when the mean touches 80 % (that is a coin flip).
    val above = chance >= Srs.LIKELY_PASS
    val c = Uli.colors
    val spin = remember { Animatable(-40f) }
    LaunchedEffect(Unit) { spin.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 120f)) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = spin.value }
                            .clip(Shapes.cookie12)
                            .background(if (above) c.right else c.amber),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$pct",
                            fontFamily = Rounded,
                            fontWeight = FontWeight.W800,
                            fontSize = 34.sp,
                            lineHeight = 34.sp,
                            color = if (above) c.onRight else c.onAmber,
                        )
                        Text(
                            "%",
                            fontFamily = Rounded,
                            fontWeight = FontWeight.W700,
                            fontSize = 16.sp,
                            color = if (above) c.onRight else c.onAmber,
                            modifier = Modifier.padding(bottom = 4.dp, start = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Odhad na skúške", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Zvládnuté: $mastered ${zo(total)} $total otázok",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Šanca prejsť: ${chanceText(chance)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                        color = if (above) c.right else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ExamMeter(readiness.toFloat(), above)
        }
    }
}

/** "z 50", but "zo 70" (sedemdesiat), "zo 40", "zo 60": the preposition follows the spoken number. */
private fun zo(n: Int) = if (n.toString().first() in "467") "zo" else "z"

/** Estimate on a 0 to 100 track with the pass line at 80 %. */
@Composable
private fun ExamMeter(value: Float, likely: Boolean) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 90f)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val passZone = Uli.colors.right.copy(alpha = 0.18f)
    val fill = if (likely) Uli.colors.right else MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurface
    Column {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "hranica 80 %",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(0.8f).padding(end = 6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val h = 14.dp.toPx()
            val y = (size.height - h) / 2
            val r = CornerRadius(h / 2, h / 2)
            drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
            drawRoundRect(passZone, Offset(size.width * 0.8f, y), Size(size.width * 0.2f, h), r)
            val w = (size.width * anim.value).coerceAtLeast(h)
            drawRoundRect(fill, Offset(0f, y), Size(w, h), r)
            val x = size.width * 0.8f
            drawRoundRect(line, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
private fun MasteryBar(mastered: Int, learning: Int, total: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val learn = Uli.colors.learning
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val gap = 3.dp.toPx()
        val wm = size.width * mastered / total
        val wl = size.width * learning / total
        if (learning > 0) drawRoundRect(learn, Offset((wm + if (mastered > 0) gap else 0f), 0f), Size((wl - gap).coerceAtLeast(size.height), size.height), r)
        if (mastered > 0) drawRoundRect(primary, Offset.Zero, Size(wm.coerceAtLeast(size.height), size.height), r)
    }
}

/** One mode as a full-width row: its colored shape, a 17 sp label and the count, all one target. */
@Composable
private fun ModeRow(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: androidx.compose.ui.graphics.Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp).alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeBadge(icon, badge, container, content, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(detail, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A slim row under Skúška nanečisto: the last finished exam's score, a tap opens its result and mistakes. */
@Composable
private fun LastExamRow(correct: Int, total: Int, hasMistakes: Boolean, onClick: () -> Unit, shape: androidx.compose.ui.graphics.Shape) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(start = 70.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Posledná skúška: $correct/$total · " + if (hasMistakes) "pozrieť chyby" else "pozrieť výsledok",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * One topic: its name, the bar (mastered solid, in progress light, new as the bare track) and a caption
 * whose dots are the bar's own colors, so the bar explains itself without a legend elsewhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicRow(title: String, mastered: Int, learning: Int, total: Int, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val done = mastered == total
    Surface(
        onClick = onClick,
        shape = shape,
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.heightIn(min = 56.dp).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (done) {
                    ShapeBadge(Icons.Filled.Check, Shapes.verySunny, Uli.colors.right, Uli.colors.onRight, size = 22.dp, iconSize = 14.dp)
                } else {
                    Text("$total ${questionsWord(total)}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            MasteryBar(mastered, learning, total)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    done -> LegendItem(scheme.primary, "Všetky zvládnuté")
                    mastered == 0 && learning == 0 -> LegendItem(scheme.surfaceContainerHighest, "Všetko nové", hollow = true)
                    else -> {
                        LegendItem(scheme.primary, agree(mastered, "zvládnut"))
                        if (learning > 0) LegendItem(Uli.colors.learning, agree(learning, "rozroben"))
                        val new = total - mastered - learning
                        if (new > 0) LegendItem(scheme.surfaceContainerHighest, agree(new, "nov"), hollow = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String, hollow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(color)
                .then(if (hollow) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContinueButton(title: String, subtitle: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = ButtonShapes(RoundedCornerShape(36.dp), RoundedCornerShape(18.dp)),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(start = 28.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        modifier = Modifier.fillMaxWidth().height(84.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Rounded, fontWeight = FontWeight.W800, fontSize = 24.sp, lineHeight = 28.sp)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
        }
        Box(
            Modifier.size(60.dp).clip(Shapes.cookie9).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
    }
}

/** A mock exam left half-way: continue it (the obvious action) or throw it away. */
@Composable
private fun OpenExamCard(answered: Int, total: Int, onResume: () -> Unit, onDiscard: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(UliIcons.Timer, Shapes.clover4, scheme.tertiary, scheme.onTertiary, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rozrobená skúška", style = MaterialTheme.typography.titleMedium)
                    Text(examProgress(answered, total), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer.copy(alpha = 0.8f))
                }
                TextButton(onClick = onDiscard) { Text("Zahodiť", color = scheme.onTertiaryContainer) }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
                modifier = Modifier.fillMaxWidth().padding(end = 10.dp).height(52.dp),
            ) {
                Text("Pokračovať v skúške", fontFamily = Rounded, fontWeight = FontWeight.W700, fontSize = 17.sp)
            }
        }
    }
}
