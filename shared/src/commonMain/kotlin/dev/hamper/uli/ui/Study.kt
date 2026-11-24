package dev.hamper.uli.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import dev.hamper.uli.engine.Code
import dev.hamper.uli.engine.Question
import dev.hamper.uli.ui.theme.Uli
import dev.hamper.uli.ui.theme.monoStyle

enum class OptState { Idle, Selected, Right, Wrong, Missed, Dim }

private val morphRight by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Sunny) }
private val morphWrong by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Puffy) }
private val morphPick by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided) }

/** A full-width answer card. The badge morphs into a sun when right and a puff when wrong. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OptionCard(
    text: String,
    code: Boolean,
    letter: Char,
    state: OptState,
    multi: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    note: String? = null,
    picked: Boolean = state == OptState.Selected || state == OptState.Wrong,
) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        when (state) {
            OptState.Idle, OptState.Dim -> scheme.surfaceContainerLow
            OptState.Selected -> scheme.secondaryContainer
            OptState.Right -> c.rightContainer
            OptState.Wrong -> c.wrongContainer
            OptState.Missed -> scheme.surfaceContainerLow
        },
        motion.defaultEffectsSpec(), label = "container",
    )
    val content = when (state) {
        OptState.Right -> c.onRightContainer
        OptState.Wrong -> c.onWrongContainer
        OptState.Selected -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }
    val corner by animateDpAsState(
        when (state) {
            OptState.Right, OptState.Wrong -> 30.dp
            OptState.Selected -> 26.dp
            else -> 20.dp
        },
        motion.defaultSpatialSpec(), label = "corner",
    )
    val border = when (state) {
        OptState.Missed -> BorderStroke(2.5.dp, c.right)
        OptState.Selected -> BorderStroke(2.dp, scheme.primary)
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // A short pop on the answer: right swells, wrong shakes.
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            OptState.Right -> {
                pop.snapTo(0.97f)
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 700f))
            }
            OptState.Wrong -> {
                for (x in listOf(-10f, 9f, -6f, 4f, 0f)) shake.animateTo(x, tween(45))
            }
            else -> {}
        }
    }
    // A tappable card stands on a thin ledge and sinks onto it under the thumb; once answered it settles flat.
    val ledge by animateDpAsState(if (onClick != null) LEDGE else 0.dp, motion.defaultSpatialSpec(), label = "ledge")
    val sink by animateDpAsState(if (pressed) ledge else 0.dp, motion.fastSpatialSpec(), label = "sink")
    val ledgeColor = if (state == OptState.Selected) scheme.primary.copy(alpha = 0.55f) else scheme.outlineVariant
    val shape = RoundedCornerShape(corner)

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                translationX = shake.value * density
            }
            .alpha(if (state == OptState.Dim) 0.45f else 1f)
            .drawBehind {
                if (ledge > 0.dp) {
                    val l = ledge.toPx()
                    val outline = shape.createOutline(Size(size.width, size.height - LEDGE.toPx()), layoutDirection, this)
                    translate(top = LEDGE.toPx() - (LEDGE.toPx() - l)) { drawOutline(outline, ledgeColor) }
                }
            }
            .padding(bottom = LEDGE),
    ) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        interactionSource = interaction,
        shape = shape,
        color = container,
        contentColor = content,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = sink.toPx() }
            // TalkBack hears a checkbox (pick several) or a radio button (pick one), checked when picked,
            // and after the answer what the card turned out to be.
            .semantics {
                role = if (multi) Role.Checkbox else Role.RadioButton
                if (multi) toggleableState = ToggleableState(picked) else selected = picked
                answerDescription(state, picked)?.let { stateDescription = it }
            },
    ) {
        Column {
            Row(
                Modifier.heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OptionBadge(letter, state, multi)
                Spacer(Modifier.width(14.dp))
                if (code) {
                    Text(
                        Code.gluePaths(text),
                        style = monoStyle(fontSize = 16.sp, fontWeight = FontWeight.W500, lineHeight = 22.sp),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // Plain options still set their paths and operators in mono, like prose does.
                    CodeText(text, MaterialTheme.typography.bodyLarge, english = true, modifier = Modifier.weight(1f))
                }
            }
            // Why this pick is wrong, right where the eye already is.
            androidx.compose.animation.AnimatedVisibility(
                visible = state == OptState.Wrong && note != null,
                enter = androidx.compose.animation.expandVertically(motion.defaultSpatialSpec()) + androidx.compose.animation.fadeIn(motion.defaultEffectsSpec()),
            ) {
                CodeText(
                    note.orEmpty(),
                    MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                    color = content,
                    modifier = Modifier.padding(start = 64.dp, end = 18.dp, bottom = 14.dp),
                )
            }
        }
    }
    }
}

private val LEDGE = 4.dp

private fun answerDescription(state: OptState, picked: Boolean): String? = when (state) {
    OptState.Right -> if (picked) "Tvoja odpoveď, správna" else "Správna odpoveď"
    OptState.Wrong -> "Tvoja odpoveď, nesprávna"
    OptState.Missed -> "Správna odpoveď, nevybraná"
    else -> null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionBadge(letter: Char, state: OptState, multi: Boolean) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val target = when (state) {
        OptState.Right, OptState.Wrong, OptState.Selected -> 1f
        else -> 0f
    }
    val morph = when (state) {
        OptState.Right -> morphRight
        OptState.Wrong -> morphWrong
        else -> morphPick
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f)) }
    val spin = (progress.value * 90f)
    val fill = when (state) {
        OptState.Right -> c.right
        OptState.Wrong -> c.wrong
        OptState.Selected -> scheme.primary
        else -> Color.Transparent
    }
    val onFill = when (state) {
        OptState.Right -> c.onRight
        OptState.Wrong -> c.onWrong
        OptState.Selected -> scheme.onPrimary
        else -> scheme.onSurfaceVariant
    }
    val shape = if (multi && state in setOf(OptState.Idle, OptState.Dim, OptState.Missed)) {
        RoundedCornerShape(10.dp)
    } else {
        MorphShape(morph, progress.value.coerceIn(0f, 1f), spin)
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (state in setOf(OptState.Idle, OptState.Dim)) Modifier.border(2.dp, scheme.outline, shape)
                else if (state == OptState.Missed) Modifier.border(2.5.dp, c.right, shape)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            // The card's state description says it; the icons stay silent.
            OptState.Right -> Icon(Icons.Filled.Check, null, tint = onFill, modifier = Modifier.size(20.dp))
            OptState.Wrong -> Icon(Icons.Filled.Close, null, tint = onFill, modifier = Modifier.size(20.dp))
            OptState.Missed -> Icon(Icons.Filled.Check, null, tint = c.right, modifier = Modifier.size(20.dp))
            OptState.Selected -> if (multi) {
                Icon(Icons.Filled.Check, null, tint = onFill, modifier = Modifier.size(20.dp))
            } else {
                Text(letter.toString(), color = onFill, fontWeight = FontWeight.W700, fontSize = 15.sp)
            }
            else -> Text(letter.toString(), color = onFill, fontWeight = FontWeight.W700, fontSize = 15.sp)
        }
    }
}

/**
 * Why, in reading order with one rhythm (16 dp): the note for a wrong pick, the explanation, the memory
 * hook and the terminal demo. Only the terminal keeps a box; the rest is plain type on the sheet.
 */
@Composable
fun Explanation(
    q: Question,
    wrongNotes: List<String>,
    modifier: Modifier = Modifier,
    showHook: Boolean = true,
    prose: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
    proseColor: Color = MaterialTheme.colorScheme.onSurface,
    gap: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val c = Uli.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (note in wrongNotes) {
            Surface(color = c.wrongContainer.copy(alpha = 0.55f), contentColor = c.onWrongContainer, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Prečo nie tvoja odpoveď", style = MaterialTheme.typography.labelMedium, color = c.wrong)
                    Spacer(Modifier.height(2.dp))
                    CodeText(note, MaterialTheme.typography.bodyMedium)
                }
            }
        }
        CodeText(q.explanation, prose, color = proseColor)
        if (showHook) q.hook?.let { Hook(it) }
        q.demo?.let { Terminal(it) }
        q.disputedNote?.let { Disputed(it) }
    }
}

/** "Na skúške vs. v praxi": one tap shows what real Linux does where the exam key disagrees. */
@Composable
fun Disputed(note: String) {
    var open by remember(note) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (open) 20.dp else 24.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dcorner")
    Surface(
        onClick = { open = !open },
        shape = RoundedCornerShape(corner),
        color = if (open) scheme.surfaceContainerHigh else Color.Transparent,
        contentColor = scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = if (open) 14.dp else 6.dp)) {
            Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(UliIcons.Balance, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Na skúške vs. v praxi", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Skryť" else "Ukázať",
                    modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f),
                )
            }
            if (open) {
                Spacer(Modifier.height(4.dp))
                CodeText(note, MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.padding(end = 6.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Na skúške platí kľúč, odpovedz podľa neho.",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The memory hook: one bold line on a soft amber block, signed with the amber flower. */
@Composable
fun Hook(text: String) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.primary == c.amber
    Surface(
        color = if (dark) scheme.primaryContainer.copy(alpha = 0.32f) else c.amber.copy(alpha = 0.20f),
        contentColor = scheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 13.dp), verticalAlignment = Alignment.Top) {
            ShapeBadge(UliIcons.Lightbulb, Shapes.flower, c.amber, c.onAmber, size = 30.dp, iconSize = 17.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Zapamätaj si", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                CodeText(text, MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp), color = scheme.onSurface)
            }
        }
    }
}

/** Correct answers for study mode, with code options in mono. */
@Composable
fun CorrectAnswers(q: Question, codeOptions: Set<Int>) {
    val c = Uli.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in q.correctIndices.sorted()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.rightContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Shapes.sunny).background(c.right), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = c.onRight, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                val t = q.options[i].text
                if (i in codeOptions) {
                    Text(Code.gluePaths(t), style = monoStyle(fontSize = 15.sp, fontWeight = FontWeight.W500), color = c.onRightContainer)
                } else {
                    CodeText(t, MaterialTheme.typography.bodyMedium, english = true, color = c.onRightContainer)
                }
            }
        }
    }
}

@Composable
fun WrongAnswers(q: Question, picked: Set<Int>, codeOptions: Set<Int>) {
    val c = Uli.colors
    val wrong = picked.filter { it in q.options.indices && !q.options[it].correct }.sorted()
    val missing = q.correctIndices.isNotEmpty() && picked.isEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in wrong) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.wrongContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Shapes.puffy).background(c.wrong), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, null, tint = c.onWrong, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                val t = q.options[i].text
                if (i in codeOptions) {
                    Text(Code.gluePaths(t), style = monoStyle(fontSize = 15.sp, fontWeight = FontWeight.W500), color = c.onWrongContainer)
                } else {
                    CodeText(t, MaterialTheme.typography.bodyMedium, english = true, color = c.onWrongContainer)
                }
            }
        }
        if (missing) Text("Bez odpovede", style = MaterialTheme.typography.bodyMedium, color = c.wrong)
    }
}
