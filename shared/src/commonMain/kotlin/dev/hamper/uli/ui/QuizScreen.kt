package dev.hamper.uli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.QuizState
import dev.hamper.uli.engine.Code
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.Question
import dev.hamper.uli.ui.theme.QuestionStyle
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import dev.hamper.uli.ui.theme.monoStyle

@Composable
fun QuizScreen(vm: AppViewModel) {
    // Keep drawing the last state while the screen animates out.
    var last by remember { mutableStateOf(vm.quiz) }
    vm.quiz?.let { last = it }
    val s = last ?: return
    val buzz = rememberAnswerHaptic()

    fun haptic() {
        val now = vm.quiz ?: return
        if (now.answered) buzz(now.correct)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        RoundTopBar(s, onClose = vm::home, animate = !vm.shotMode)
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = s,
            contentKey = { it.round.index },
            transitionSpec = {
                val spatial = motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                (slideInHorizontally(spatial) { it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(spatial) { -it / 3 } + fadeOut(motion.fastEffectsSpec()))
            },
            modifier = Modifier.weight(1f),
            label = "question",
        ) { state ->
            // An outgoing question keeps rendering its own frozen state; its taps name their question, so a
            // tap that lands during the slide-out never answers the next one.
            val qid = state.round.queue[state.round.index]
            QuestionBody(
                q = vm.byId.getValue(qid),
                state = state,
                isCurrent = state.round.index == s.round.index,
                showSk = vm.progress.showSk,
                onToggleSk = vm::toggleSk,
                onPick = { i -> vm.pick(qid, i); haptic() },
                onSubmit = { vm.submit(qid); haptic() },
                onNext = vm::next,
                frozen = vm.shotMode,
                comesBack = if (state.answered) comesBack(vm, state) else null,
            )
        }
    }
}

/** When the card just answered shows up again: later in this round after a miss, or its next due time. */
private fun comesBack(vm: AppViewModel, state: QuizState): String? {
    val r = state.round
    val id = r.queue.getOrNull(r.index) ?: return null
    if (!state.correct) {
        val next = (r.index + 1 until r.queue.size).firstOrNull { r.queue[it] == id } ?: return null
        return comesBackAfter(next - r.index)
    }
    return comesBackIn(vm.progress.card(id).dueAt - vm.clock())
}

@Composable
private fun RoundTopBar(s: QuizState, onClose: () -> Unit, animate: Boolean) {
    val r = s.round
    // A correction is a current card too: its red pill becomes the moving head until it is answered.
    val retrying = r.isRetry && !s.answered
    val segments = r.ids.map { id ->
        when {
            retrying && id == r.current -> Seg.Retry
            r.firstAttempt[id] == true -> Seg.Right
            r.firstAttempt[id] == false -> Seg.Wrong
            id == r.current -> Seg.Current
            else -> Seg.Upcoming
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Zavrieť kolo") }
        SegmentedProgress(segments, Modifier.weight(1f).padding(horizontal = 6.dp), animate = animate)
        Spacer(Modifier.width(10.dp))
        // Corrections do not pretend to be the next card: they say how many missed cards are still waiting,
        // a number that only falls as you fix them.
        val left = r.toFix(s.answered)
        Text(
            when {
                !r.isRetry -> "${r.displayNumber}/${r.total}"
                left == 0 -> "Opravené"
                else -> "Oprava · ešte $left"
            },
            fontFamily = Rounded,
            fontWeight = FontWeight.W700,
            fontSize = if (r.isRetry) 15.sp else 17.sp,
            color = when {
                !r.isRetry -> MaterialTheme.colorScheme.onSurfaceVariant
                left == 0 -> Uli.colors.right
                else -> Uli.colors.wrong
            },
            maxLines = 1,
            modifier = Modifier.semantics {
                contentDescription = when {
                    !r.isRetry -> "Otázka ${r.displayNumber} z ${r.total}"
                    left == 0 -> "Oprava, všetko opravené"
                    else -> "Oprava, na opravu ostáva $left"
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun QuestionBody(
    q: Question,
    state: QuizState,
    isCurrent: Boolean,
    showSk: Boolean,
    onToggleSk: () -> Unit,
    onPick: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    frozen: Boolean,
    comesBack: String? = null,
) {
    val answered = state.answered && isCurrent
    val selected = if (isCurrent) state.selected else emptySet()
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    val multi = q.kind == Kind.Multi
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    // When the question and its options do not fit (long text, SK line, big font scale), the card drops
    // its decoration so the options get the room. Decided once per question, so nothing jumps later.
    var compact by remember(q.id) { mutableStateOf(false) }
    // A right answer's verdict grows into whatever room the sheet leaves, so the win fills the frame instead
    // of a gap. Measured once, after the sheet has settled (no feedback loop), then animated into place.
    // A wrong answer has no hero: its spare room becomes empty sheet under the why, so the options and the sheet
    // close up under the question instead of leaving a band above the options.
    val heroOn = answered
    var demoOpen by remember(q.id) { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val room = remember(q.id) { IntArray(1) { Int.MIN_VALUE } } // spare px under everything, as if the hero had no extra height
    var heroTarget by remember(q.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val heroPx by androidx.compose.animation.core.animateIntAsState(
        heroTarget,
        if (frozen) tween(0) else spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "hero",
    )
    LaunchedEffect(heroOn, demoOpen) {
        if (!heroOn) { heroTarget = 0; return@LaunchedEffect }
        // Wait until the sheet and the options that drop out have stopped moving (the room stops changing).
        var last = Int.MIN_VALUE
        var still = 0
        var frames = 0
        while (still < 8 && frames < 150) {
            androidx.compose.runtime.withFrameNanos { }
            frames++
            // Not laid out yet (a cold start can take a few frames): nothing to measure.
            if (room[0] == Int.MIN_VALUE) continue
            if (room[0] == last) still++ else { still = 0; last = room[0] }
        }
        if (room[0] == Int.MIN_VALUE) return@LaunchedEffect
        heroTarget = if (state.correct) room[0].coerceIn(0, with(density) { 360.dp.roundToPx() }) else room[0].coerceAtLeast(0)
    }

    // Before the answer the question card takes the spare height; after it, the feedback sheet does.
    val cardShare by animateFloatAsState(if (answered) 0f else 1f, motion.defaultSpatialSpec(), label = "share")

    // After the answer, start from the top: the question, then the right option and your pick.
    LaunchedEffect(answered) {
        if (answered && scroll.value > 0) scroll.animateScrollTo(0)
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                // The question card takes whatever height is left, so the options always sit at the
                // bottom, in thumb reach. When everything does not fit, the whole screen scrolls.
                FillColumn(
                    minHeight = viewport,
                    gap = 12.dp,
                    topShare = cardShare,
                    spareBetween = true,
                    onOverflow = { if (!answered && isCurrent) compact = true },
                    onSpare = { d -> room[0] = d + heroPx },
                    top = {
                        QuestionCard(
                            q = q,
                            answered = answered,
                            showSk = showSk,
                            onToggleSk = onToggleSk,
                            frozen = frozen,
                            compact = compact,
                            keepHintWhenCompact = multi,
                            label = {
                                // A correction says so in the top bar (red head, "Oprava 1 z 2"), not twice.
                                Tag(q.topicEnum.title, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            },
                            hint = {
                                when {
                                    multi -> ChooseCounter(q.choose, selected.size, answered)
                                    q.kind == Kind.TrueFalse -> Hint("Pravda, alebo nepravda? Ťukni.")
                                    else -> Hint("Ťukni na odpoveď")
                                }
                            },
                        )
                    },
                    bottom = {
                        Column {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.order.forEachIndexed { pos, i ->
                                    val opt = q.options[i]
                                    val st = when {
                                        !answered -> if (i in selected) OptState.Selected else OptState.Idle
                                        opt.correct && i in selected -> OptState.Right
                                        opt.correct && !multi -> OptState.Right
                                        opt.correct -> OptState.Missed
                                        i in selected -> OptState.Wrong
                                        else -> OptState.Dim
                                    }
                                    // Once answered only what matters stays: the right one(s) and your pick,
                                    // so the why fits under them without scrolling.
                                    AnimatedVisibility(
                                        visible = st != OptState.Dim,
                                        enter = fadeIn(),
                                        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                                    ) {
                                        Entrance(pos, enabled = isCurrent && !answered && !frozen) {
                                            OptionCard(
                                                text = opt.text,
                                                code = i in codeOptions,
                                                letter = 'A' + pos,
                                                state = st,
                                                multi = multi,
                                                onClick = if (answered || !isCurrent) null else ({ onPick(i) }),
                                                note = opt.note,
                                                picked = i in selected,
                                            )
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = answered,
                                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                FeedbackPanel(
                                    q = q,
                                    correct = state.correct,
                                    streak = state.round.streak,
                                    comesBack = comesBack,
                                    frozen = frozen,
                                    hero = with(density) { heroPx.toDp() },
                                    demoOpen = demoOpen,
                                    onDemoToggle = { demoOpen = !demoOpen },
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            if (!answered) Spacer(Modifier.height(if (multi) 8.dp else 12.dp))
                        }
                    },
                )
            }
            ScrollEdges(
                scroll = scroll,
                color = if (answered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
                onMore = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                moreLabel = if (answered) "Pokračuje nižšie" else "Ďalšie možnosti",
            )
        }
        // Single choice has no bottom button: the options still end above the gesture bar, never under it.
        if (!answered && !multi) Spacer(Modifier.navigationBarsPadding())

        // Pinned bottom: the check button (multi) or, once answered, "Ďalej" on the panel's color.
        AnimatedVisibility(
            visible = answered,
            enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            ) {
                val last = state.round.index + 1 >= state.round.queue.size
                BigButton(if (last) "Dokončiť kolo" else "Ďalej", onClick = onNext, trailing = true)
            }
        }
        if (multi && !answered) {
            Box(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                BigButton(
                    text = "Skontrolovať",
                    enabled = selected.size == q.choose,
                    onClick = onSubmit,
                )
            }
        }
    }
}

/**
 * A column at least [minHeight] tall. The leftover space goes to [top] while [topShare] is 1 (the question
 * card grows, so [bottom] hugs the lower edge, in thumb reach). What [top] gives back as [topShare] falls
 * opens as a gap above [bottom] when [spareBetween] (the answer and its sheet stay down on the pinned
 * button, sized to their content) or below it otherwise. Taller content just scrolls, and [onOverflow]
 * hears about it; [onSpare] hears how much room is left (negative when short).
 */
@Composable
fun FillColumn(
    minHeight: Dp,
    gap: Dp,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    topShare: Float = 1f,
    spareBetween: Boolean = false,
    onOverflow: (() -> Unit)? = null,
    exactTop: Boolean = false,
    onSpare: ((Int) -> Unit)? = null,
) {
    Layout(
        content = {
            Box(propagateMinConstraints = true) { top() }
            Box { bottom() }
        },
    ) { m, c ->
        val w = c.maxWidth
        val b = m[1].measure(Constraints(minWidth = w, maxWidth = w))
        val g = gap.roundToPx()
        val natural = m[0].minIntrinsicHeight(w)
        val min = minHeight.roundToPx()
        if (natural + g + b.height > min) onOverflow?.invoke()
        val spare = (min - b.height - g - natural).coerceAtLeast(0)
        val th = natural + (spare * topShare).toInt()
        // exactTop bounds the top too, so a weighted child inside it can take the spare height.
        val t = m[0].measure(Constraints(minWidth = w, maxWidth = w, minHeight = th, maxHeight = if (exactTop) th else Constraints.Infinity))
        val rest = (min - t.height - g - b.height).coerceAtLeast(0)
        val between = if (spareBetween) rest else 0
        // Signed: room left over (or missing) under everything, for content that can grow into it.
        onSpare?.invoke(min - (t.height + g + b.height))
        layout(w, t.height + g + b.height + rest) {
            t.place(0, 0)
            b.place(0, t.height + g + between)
        }
    }
}

/**
 * Scroll affordance for a screen that does not fit: a soft fade at the top once scrolled, a fade at the
 * bottom while there is more, and (before the answer) a pill that jumps to the remaining options.
 */
@Composable
fun BoxScope.ScrollEdges(scroll: ScrollState, color: Color, onMore: (() -> Unit)?, moreLabel: String = "Ďalšie možnosti") {
    val motion = MaterialTheme.motionScheme
    val top by animateFloatAsState(if (scroll.value > 0) 1f else 0f, motion.fastEffectsSpec(), label = "topEdge")
    val more = scroll.canScrollForward
    val bottom by animateFloatAsState(if (more) 1f else 0f, motion.fastEffectsSpec(), label = "bottomEdge")
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(18.dp)
            .graphicsLayer { alpha = top }
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0f)))),
    )
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
            .graphicsLayer { alpha = bottom }
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0f), color))),
    )
    if (onMore != null) {
        AnimatedVisibility(
            visible = more,
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f),
            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        ) {
            Surface(
                onClick = onMore,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.heightIn(min = 44.dp).padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(moreLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Options rise in one after another when a question arrives. Until an option is in place it takes no taps:
 * the bottom one rises where "Ďalej" just was, and a quick second tap there must not answer it.
 */
@Composable
private fun Entrance(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay(70L + 55L * index)
            p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                alpha = p.value.coerceIn(0f, 1f)
                translationY = (1f - p.value) * 28.dp.toPx()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Initial pass, before the option's click sees it: a press while still rising is
                        // swallowed, so that whole tap is ignored. Moves pass through, scrolling still works.
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (p.value < SETTLED) e.changes.forEach { if (it.changedToDownIgnoreConsumed()) it.consume() }
                    }
                }
            },
    ) { content() }
}

/** An option counts as in place (and takes taps) once it has risen this far. */
private const val SETTLED = 0.9f

/**
 * The question on a warm card that fills the space above the options, signed with the app's mark:
 * an amber cookie with a blinking terminal cursor that turns slowly. Alive, but nothing extra to read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    q: Question,
    answered: Boolean,
    showSk: Boolean,
    onToggleSk: () -> Unit,
    frozen: Boolean,
    label: @Composable () -> Unit,
    hint: @Composable () -> Unit,
    compact: Boolean = false,
    keepHintWhenCompact: Boolean = false,
) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.primary == c.amber
    val card = if (dark) scheme.primaryContainer.copy(alpha = 0.30f) else c.amber.copy(alpha = 0.17f)
    // Only the answer animates the size; going compact snaps, so a question never shrinks in front of you.
    val open by animateFloatAsState(if (answered) 0f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "qsize")
    val qSize = 19f + ((if (compact) 20f else 23f) - 19f) * open
    Surface(
        color = card,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 14.dp, top = if (answered) 16.dp else 14.dp, bottom = if (answered) 16.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Once answered the card steps back to just the question.
                AnimatedVisibility(!answered, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.padding(bottom = if (compact) 10.dp else 16.dp), verticalAlignment = Alignment.Top) {
                        FlowRow(
                            Modifier.weight(1f).padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) { label() }
                        Spacer(Modifier.width(8.dp))
                        SkToggle(showSk, onToggleSk)
                    }
                }
                CodeText(
                    q.question,
                    QuestionStyle.copy(fontSize = qSize.sp, lineHeight = (qSize * 1.35f).sp),
                    english = true,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp),
                )
                AnimatedVisibility(showSk && q.hasDistinctSk, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    CodeText(
                        q.questionSk,
                        MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, end = 6.dp),
                    )
                }
            }
            if (!compact || keepHintWhenCompact) {
                AnimatedVisibility(!answered, enter = fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.padding(top = if (compact) 10.dp else 18.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).padding(bottom = 4.dp)) { hint() }
                        if (!compact) PromptMark(frozen)
                    }
                }
            }
        }
    }
}

/** The app's mark: ">_" on an amber cookie. The cookie turns slowly and the cursor blinks. */
@Composable
private fun PromptMark(frozen: Boolean, size: Dp = 68.dp) {
    val c = Uli.colors
    val t = rememberInfiniteTransition(label = "mark")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
    val blink by t.animateFloat(
        1f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1060; 1f at 0; 1f at 529; 0f at 530; 0f at 1059 }),
        label = "blink",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (frozen) 12f else spin }
                .clip(Shapes.cookie12)
                .background(c.amber),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(">", style = monoStyle(fontSize = 22.sp, fontWeight = FontWeight.W700), color = c.onAmber)
            Text(
                "_",
                style = monoStyle(fontSize = 22.sp, fontWeight = FontWeight.W700),
                color = c.onAmber,
                modifier = Modifier.graphicsLayer { alpha = if (frozen) 1f else blink },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(UliIcons.Touch, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The Slovak line on or off: a real toggle for TalkBack ("Slovenský preklad, zapnuté"). */
@Composable
fun SkToggle(on: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        checked = on,
        onCheckedChange = { onToggle() },
        shape = CircleShape,
        color = if (on) scheme.primary else scheme.surface.copy(alpha = 0.6f),
        contentColor = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.semantics {
            contentDescription = "Slovenský preklad otázky"
            stateDescription = if (on) "Zapnuté" else "Vypnuté"
        },
    ) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(UliIcons.Translate, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            // Never "S / K": the chip keeps its width and the topic tags wrap instead.
            Text("SK", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
fun ChooseCounter(choose: Int, picked: Int, answered: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Vyber $choose",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        repeat(choose) { i ->
            val filled = i < picked
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(if (filled) 16.dp else 12.dp)
                    .clip(if (filled) Shapes.cookie6 else CircleShape)
                    .background(if (filled) scheme.primary else scheme.surfaceContainerHighest),
            )
        }
        if (!answered) {
            Spacer(Modifier.width(4.dp))
            Text("$picked z $choose", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonShapes(RoundedCornerShape(32.dp), RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Text(text, fontFamily = Rounded, fontWeight = FontWeight.W700, fontSize = 20.sp)
        if (trailing) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(22.dp))
        }
    }
}

/**
 * The why, as a sheet in clearly separated blocks: the verdict (and when the card comes back), the one fact
 * to keep, why the right answer is right, then the terminal. Why your own pick is wrong sits on that pick,
 * above. Body text is full size; only the labels are small.
 */
@Composable
private fun FeedbackPanel(
    q: Question,
    correct: Boolean,
    streak: Int,
    comesBack: String?,
    frozen: Boolean,
    hero: Dp,
    demoOpen: Boolean,
    onDemoToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
            Verdict(correct, streak, comesBack, frozen, if (correct) hero else 0.dp)
            q.hook?.let {
                Spacer(Modifier.height(14.dp))
                Hook(it)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Prečo",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            CodeText(
                q.explanation,
                MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp),
                color = scheme.onSurface,
            )
            q.demo?.let {
                Spacer(Modifier.height(18.dp))
                // After a miss there is already a lot to read: the terminal waits as one row until it is wanted.
                if (correct) Terminal(it) else TerminalPeek(it, open = demoOpen, onToggle = onDemoToggle)
            }
            q.disputedNote?.let {
                Spacer(Modifier.height(14.dp))
                Disputed(it)
            }
            if (!correct) Spacer(Modifier.height(hero))
        }
    }
}

/**
 * The verdict, big enough to read from a still frame. A right answer gets a sun that pops in with a small
 * burst of shapes that stays around it, and the streak; a wrong one a puff. Under it, when the card comes back.
 * [extra] is spare height under the sheet: a right answer with enough of it becomes a centered celebration.
 */
@Composable
private fun Verdict(correct: Boolean, streak: Int, comesBack: String?, frozen: Boolean, extra: Dp = 0.dp) {
    val c = Uli.colors
    val accent = if (correct) c.right else c.wrong
    // The box height comes from [extra] alone, so switching layouts inside never feeds back into it; it is a
    // minimum, so large font sizes still get every line.
    val rowHeight = if (correct) 76.dp else 56.dp
    val total = rowHeight + extra
    // The centered layout is the badge (76 dp at scale 1) plus about 80 dp of text; it scales up to 1.45.
    val big = correct && total >= 158.dp
    val scale = ((total.value - 82f) / 76f).coerceIn(1f, 1.45f)
    Box(Modifier.fillMaxWidth().heightIn(min = rowHeight + extra), contentAlignment = Alignment.Center) {
        if (big) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VerdictBadge(correct = true, frozen = frozen, scale = scale)
                Text("Správne", style = MaterialTheme.typography.headlineLarge, color = accent, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (comesBack != null) {
                        Text(comesBack, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (streak >= 2) {
                        Spacer(Modifier.width(10.dp))
                        Streak(streak)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Some room but not enough for the centered layout: the sun and its burst grow into it.
                VerdictBadge(correct, frozen, scale = if (correct) (total / rowHeight).coerceIn(1f, 1.6f) else 1f)
                Spacer(Modifier.width(if (correct) 14.dp else 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (correct) "Správne" else "Nesprávne",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent,
                        maxLines = 1,
                    )
                    if (comesBack != null) {
                        Text(comesBack, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (correct && streak >= 2) {
                    Spacer(Modifier.width(8.dp))
                    Streak(streak)
                }
            }
        }
    }
}

/** "5 za sebou" on an amber pill with a small sun: the streak, only when there is one. */
@Composable
private fun Streak(n: Int) {
    val c = Uli.colors
    Surface(color = c.amber, contentColor = c.onAmber, shape = CircleShape) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(Shapes.sunny).background(c.onAmber), contentAlignment = Alignment.Center) {
                Text("$n", fontFamily = Rounded, fontWeight = FontWeight.W800, fontSize = 11.sp, lineHeight = 11.sp, color = c.amber)
            }
            Spacer(Modifier.width(6.dp))
            Text("za sebou", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun VerdictBadge(correct: Boolean, frozen: Boolean, scale: Float = 1f) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val pop = remember { Animatable(if (frozen) 1f else 0f) }
    LaunchedEffect(Unit) { if (!frozen) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    val burst = remember { Animatable(if (frozen) 1f else 0f) }
    LaunchedEffect(Unit) { if (!frozen && correct) burst.animateTo(1f, tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val colors = listOf(c.amber, scheme.tertiary, c.right, c.learning, c.amber, scheme.tertiary, c.right, c.amber)
    val shapes = listOf(Shapes.cookie6, Shapes.clover4, Shapes.sunny, Shapes.gem, Shapes.flower, Shapes.cookie6, Shapes.clover4, Shapes.gem)
    val k = scale
    Box(Modifier.size(if (correct) 76.dp * k else 56.dp), contentAlignment = Alignment.Center) {
        if (correct) {
            // Eight small shapes fly out from behind the sun and stay around it, so the frame still reads as a win.
            for (i in 0 until 8) {
                val angle = (-100.0 + i * 45.0 + (i % 2) * 12.0) * kotlin.math.PI / 180
                val dist = (18f + 18f * burst.value + (i % 3) * 2f) * k
                val size = (6 + (i % 3) * 2).dp * k
                Box(
                    Modifier
                        .size(size)
                        .graphicsLayer {
                            translationX = (kotlin.math.cos(angle) * dist).toFloat() * density
                            translationY = (kotlin.math.sin(angle) * dist).toFloat() * density
                            rotationZ = burst.value * (90f + i * 30f)
                            val sc = burst.value.coerceIn(0f, 1f)
                            scaleX = sc
                            scaleY = sc
                        }
                        .clip(shapes[i])
                        .background(colors[i]),
                )
            }
        }
        Box(
            Modifier
                .size(if (correct) 46.dp * k else 44.dp)
                .graphicsLayer {
                    val sc = 0.6f + 0.4f * pop.value
                    scaleX = sc
                    scaleY = sc
                    rotationZ = (1f - pop.value) * -60f
                }
                .clip(if (correct) Shapes.verySunny else Shapes.puffy)
                .background(if (correct) c.right else c.wrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (correct) Icons.Filled.Check else Icons.Filled.Close,
                null,
                tint = if (correct) c.onRight else c.onWrong,
                modifier = Modifier.size(24.dp * k),
            )
        }
    }
}
