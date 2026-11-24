package dev.hamper.uli.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.shape.CircleShape
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.PredictiveBack
import dev.hamper.uli.ExamState
import dev.hamper.uli.engine.Code
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.Question
import dev.hamper.uli.ui.theme.QuestionStyle
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import dev.hamper.uli.ui.theme.monoStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

fun formatPoints(p: Double): String = "${(p * 10).roundToInt() / 10},${(p * 10).roundToInt() % 10}".removeSuffix(",0")

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
fun ExamScreen(vm: AppViewModel) {
    var last by remember { mutableStateOf(vm.exam) }
    vm.exam?.let { last = it }
    val s = last ?: return
    var confirmQuit by remember { mutableStateOf(false) }
    // Nothing answered yet: leaving just drops the attempt, nothing to confirm or to keep.
    fun quit() {
        if (s.exam.index == 0) vm.leaveExam() else confirmQuit = true
    }
    PredictiveBack(enabled = true, onProgress = {}, onBack = ::quit)

    var now by remember { mutableLongStateOf(vm.clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = vm.clock()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { quit() }) { Icon(Icons.Filled.Close, contentDescription = "Ukončiť skúšku") }
            ExamBar((s.exam.index).toFloat() / s.exam.ids.size, Modifier.weight(1f).padding(horizontal = 6.dp))
            Spacer(Modifier.width(12.dp))
            Icon(UliIcons.Timer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                formatDuration(now - s.exam.startedAt),
                style = monoStyle(fontSize = 15.sp, fontWeight = FontWeight.W500),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = s,
            contentKey = { it.exam.index },
            transitionSpec = {
                val spatial = motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                (slideInHorizontally(spatial) { it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(spatial) { -it / 3 } + fadeOut(motion.fastEffectsSpec()))
            },
            modifier = Modifier.weight(1f),
            label = "exam",
        ) { st ->
            val id = st.exam.ids.getOrNull(st.exam.index) ?: return@AnimatedContent
            // The outgoing card stays visible while it slides out but no longer takes taps; the taps also name
            // their question, so one landing late never picks on the next question.
            val isCurrent = st.exam.index == s.exam.index
            ExamQuestion(
                q = vm.byId.getValue(id),
                st = st,
                isCurrent = isCurrent,
                number = st.exam.index + 1,
                total = st.exam.ids.size,
                showSk = vm.progress.showSk,
                onToggleSk = vm::toggleSk,
                onPick = { i -> vm.examPick(id, i) },
                onNext = { vm.examNext(id) },
                frozen = vm.shotMode,
            )
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Odísť zo skúšky?") },
            text = { Text("Pokus ostane uložený a dokončíš ho z Domova. Čas beží ďalej, ako na ostrej skúške.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false; vm.home() }) { Text("Odísť") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Zostať") } },
        )
    }
}

@Composable
private fun ExamBar(value: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size((size.width * value).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

@Composable
private fun ExamQuestion(
    q: Question,
    st: ExamState,
    isCurrent: Boolean,
    number: Int,
    total: Int,
    showSk: Boolean,
    onToggleSk: () -> Unit,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    frozen: Boolean,
) {
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    val multi = q.kind == Kind.Multi
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var compact by remember(q.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                FillColumn(
                    minHeight = viewport,
                    gap = 14.dp,
                    onOverflow = { compact = true },
                    top = {
                        QuestionCard(
                            q = q,
                            answered = false,
                            showSk = showSk,
                            onToggleSk = onToggleSk,
                            frozen = frozen,
                            compact = compact,
                            keepHintWhenCompact = multi,
                            label = {
                                Tag("Otázka $number z $total", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            },
                            hint = {
                                if (multi) {
                                    ChooseCounter(q.choose, st.selected.size, answered = false)
                                } else {
                                    Text(
                                        "Vyber a pokračuj. Výsledok uvidíš na konci.",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    },
                    bottom = {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            st.order.forEachIndexed { pos, i ->
                                OptionCard(
                                    text = q.options[i].text,
                                    code = i in codeOptions,
                                    letter = 'A' + pos,
                                    state = if (i in st.selected) OptState.Selected else OptState.Idle,
                                    multi = multi,
                                    onClick = if (isCurrent) ({ onPick(i) }) else null,
                                )
                            }
                        }
                    },
                )
            }
            ScrollEdges(scroll, MaterialTheme.colorScheme.background, onMore = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } })
        }
        Box(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            BigButton(
                text = if (number == total) "Odovzdať" else "Ďalej",
                enabled = isCurrent && st.selected.size == q.choose,
                onClick = onNext,
                trailing = number != total,
            )
        }
    }
}

@Composable
fun ExamEndScreen(vm: AppViewModel) {
    val summary = vm.examSummary ?: return
    val res = summary.result
    val c = Uli.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val accent = if (res.passed) c.right else c.wrong
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom + 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(
                    color = if (res.passed) c.rightContainer else c.wrongContainer,
                    contentColor = if (res.passed) c.onRightContainer else c.onWrongContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            "Skúška nanečisto",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (res.passed) c.onRightContainer else c.onWrongContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (res.passed) "Prešiel si" else "Ešte nie",
                                style = MaterialTheme.typography.displaySmall,
                                color = if (res.passed) c.onRightContainer else c.onWrongContainer,
                                modifier = Modifier.weight(1f),
                            )
                            ShapeBadge(
                                if (res.passed) Icons.Filled.Check else Icons.Filled.Close,
                                if (res.passed) Shapes.verySunny else Shapes.puffy,
                                accent,
                                if (res.passed) c.onRight else c.onWrong,
                                size = 56.dp,
                                iconSize = 28.dp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                formatPoints(res.points),
                                fontFamily = Rounded,
                                fontWeight = FontWeight.W800,
                                fontSize = 64.sp,
                                lineHeight = 64.sp,
                                color = if (res.passed) c.onRightContainer else c.onWrongContainer,
                            )
                            Text(
                                " / 80 b",
                                fontFamily = Rounded,
                                fontWeight = FontWeight.W700,
                                fontSize = 22.sp,
                                color = (if (res.passed) c.onRightContainer else c.onWrongContainer).copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PointsBar(res.points, accent, if (res.passed) c.onRightContainer else c.onWrongContainer)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Stat("${res.correct} z ${res.total}", "správne")
                            Stat(formatDuration(summary.durationMs), "čas")
                            Stat("64 b", "hranica")
                        }
                    }
                }
            }
            item { EstimateNote((Srs.readiness(vm.ids, vm.progress) * 100).roundToInt(), Srs.passChance(vm.ids, vm.progress)) }
            if (res.wrong.isNotEmpty()) {
                item {
                    Text(
                        "Kde si chyboval (${res.wrong.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(res.wrong) { i, id ->
                    ReviewItem(vm.byId.getValue(id), summary.exam.answers[id].orEmpty(), groupShape(i, res.wrong.size))
                }
            }
        }
        StatusBarScrim(list.canScrollBackward)
        val more = rememberMoreBelow(list)
        val scope = rememberCoroutineScope()
        BottomActions(
            primary = "Hotovo",
            onPrimary = vm::home,
            secondary = "Nová skúška",
            onSecondary = vm::startExam,
            modifier = Modifier.align(Alignment.BottomCenter),
            onMore = if (more) ({ scope.launch { list.animateScrollBy(list.layoutInfo.viewportSize.height * 0.6f) } }) else null,
        )
    }
}

/**
 * The exam score and the estimate on Domov answer different questions, so they can differ (80/80 here,
 * 56 % there). Said right under the score, with the estimate itself, so the two screens never contradict.
 */
@Composable
private fun EstimateNote(pct: Int, chance: Double) {
    val c = Uli.colors
    val above = chance >= Srs.LIKELY_PASS
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(Shapes.cookie12).background(if (above) c.right else c.amber))
                Text(
                    "$pct %",
                    fontFamily = Rounded,
                    fontWeight = FontWeight.W800,
                    fontSize = 14.sp,
                    color = if (above) c.onRight else c.onAmber,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Odhad na skúške je teraz $pct %", style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp))
                Text(
                    "Rastie, keď odpoveď vydrží aj o pár hodín a dní. Jedna dobrá skúška ho len posunie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontFamily = Rounded, fontWeight = FontWeight.W700, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColorAlpha())
    }
}

@Composable
private fun LocalContentColorAlpha(): Color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.7f)

@Composable
private fun PointsBar(points: Double, fill: Color, ink: Color) {
    val v = (points / Exam.MAX_POINTS).toFloat()
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val h = 12.dp.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(ink.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(fill, Offset(0f, y), Size((size.width * v).coerceAtLeast(h), h), r)
        val x = size.width * 0.8f
        drawRoundRect(ink, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
    }
}

/** A missed question: what you picked, what is right, and why (tap to open). */
@Composable
fun ReviewItem(q: Question, picked: Set<Int>?, shape: androidx.compose.ui.graphics.Shape, startExpanded: Boolean = false) {
    var open by remember { mutableStateOf(startExpanded) }
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    Surface(
        onClick = { open = !open },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Rozbalené" else "Zbalené" },
    ) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                CodeText(
                    q.question,
                    MaterialTheme.typography.titleMedium,
                    english = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                // The whole row opens it; the pill only says so.
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = CircleShape) {
                    Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (open) "Skryť" else "Prečo", style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f))
                    }
                }
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                // Only indices this question has (a damaged save must never crash the review).
                val picks = picked?.filter { it in q.options.indices }?.toSet()
                if (picks != null) {
                    WrongAnswers(q, picks, codeOptions)
                    Spacer(Modifier.height(6.dp))
                }
                CorrectAnswers(q, codeOptions)
                Spacer(Modifier.height(14.dp))
                val notes = picks.orEmpty().filter { !q.options[it].correct }.mapNotNull { q.options[it].note }
                Explanation(q, notes)
            }
        }
    }
}

/**
 * True while the list's last item is not fully above the bottom actions (the list's bottom content padding):
 * then the fold cuts a card and a "more" pill says so.
 */
@Composable
fun rememberMoreBelow(list: androidx.compose.foundation.lazy.LazyListState): Boolean {
    val more by remember(list) {
        androidx.compose.runtime.derivedStateOf {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index < info.totalItemsCount - 1 || last.offset + last.size > info.viewportEndOffset - info.afterContentPadding + 1
        }
    }
    return more
}

@Composable
fun BottomActions(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    // A solid bar with a short fade above it: the list ends cleanly instead of showing through. When the fold
    // cuts a card, a pill on the fade says the list goes on.
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = onMore != null,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                MorePill("Pokračuje nižšie", onClick = { onMore?.invoke() })
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BigButton(primary, onClick = onPrimary, trailing = true)
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 2.dp)) {
                Text(secondary, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** A small dark pill with a down arrow: there is more below, tap to get there. */
@Composable
fun MorePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(Modifier.heightIn(min = 40.dp).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
