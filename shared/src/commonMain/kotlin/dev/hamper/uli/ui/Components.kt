package dev.hamper.uli.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.hamper.uli.engine.Code
import dev.hamper.uli.ui.theme.Uli
import dev.hamper.uli.ui.theme.monoStyle
import dev.hamper.uli.ui.theme.monoSpan

/** A shape that is [progress] of the way between two Material shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        val m = Matrix()
        m.scale(size.width, size.height)
        if (rotation != 0f) {
            m.translate(0.5f, 0.5f)
            m.rotateZ(rotation)
            m.translate(-0.5f, -0.5f)
        }
        path.transform(m)
        return Outline.Generic(path)
    }
}

class PolygonShape(polygon: RoundedPolygon, rotation: Float = 0f) : Shape {
    private val inner = MorphShape(Morph(polygon, polygon), 0f, rotation)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        inner.createOutline(size, layoutDirection, density)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object Shapes {
    val cookie12 by lazy { PolygonShape(MaterialShapes.Cookie12Sided) }
    val cookie9 by lazy { PolygonShape(MaterialShapes.Cookie9Sided) }
    val cookie6 by lazy { PolygonShape(MaterialShapes.Cookie6Sided) }
    val sunny by lazy { PolygonShape(MaterialShapes.Sunny) }
    val verySunny by lazy { PolygonShape(MaterialShapes.VerySunny) }
    val clover4 by lazy { PolygonShape(MaterialShapes.Clover4Leaf) }
    val clover8 by lazy { PolygonShape(MaterialShapes.Clover8Leaf) }
    val pill by lazy { PolygonShape(MaterialShapes.Pill) }
    val gem by lazy { PolygonShape(MaterialShapes.Gem) }
    val puffy by lazy { PolygonShape(MaterialShapes.Puffy) }
    val softBurst by lazy { PolygonShape(MaterialShapes.SoftBurst) }
    val flower by lazy { PolygonShape(MaterialShapes.Flower) }
    val ghostish by lazy { PolygonShape(MaterialShapes.Ghostish) }
    val arch by lazy { PolygonShape(MaterialShapes.Arch) }
    val pentagon by lazy { PolygonShape(MaterialShapes.Pentagon) }
}

/** Prose with commands, paths and flags set in mono. */
@Composable
fun rememberCodeText(text: String, english: Boolean): AnnotatedString {
    val bg = Uli.colors.codeBackground
    return remember(text, english, bg) {
        val spans = Code.spans(text, english)
        buildAnnotatedString {
            var pos = 0
            for (r in spans) {
                // Prose never breaks right after a slash either ("/bin/" + "csh").
                if (r.first > pos) append(Code.gluePaths(text.substring(pos, r.first)))
                withStyle(monoSpan(fontSize = 0.92.em(), background = bg, fontWeight = FontWeight.W500)) {
                    append(Code.unbreakable(text.substring(r.first, r.last + 1)))
                }
                pos = r.last + 1
            }
            if (pos < text.length) append(Code.gluePaths(text.substring(pos)))
        }
    }
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

@Composable
fun CodeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    english: Boolean = false,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        rememberCodeText(text, english),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The demo as a little terminal: prompts in amber, commands bright, output dimmed.
 * It is a code sample, so it is sized by width, not by the system font scale: the font is whatever makes
 * a [TERMINAL_COLUMNS]-column line (every demo is written to that width) fill the block. Only on a screen
 * so narrow that this would go below a readable 10 sp does a line soft-wrap, with a hanging indent.
 */
@Composable
fun Terminal(demo: String, modifier: Modifier = Modifier) {
    val c = Uli.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.terminal)
            .padding(top = 10.dp, bottom = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.terminalPrompt))
            Spacer(Modifier.width(8.dp))
            Text("bob@uli: ~", style = monoStyle(fontSize = 11.sp), color = c.terminalDim)
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            // JetBrains Mono advances 0.6 em per glyph. The size is worked out in dp and divided by the font
            // scale, so the system setting cannot push a 44-column line onto two rows.
            val fittedDp = (maxWidth.value / (TERMINAL_COLUMNS * 0.6f + 0.4f)).coerceIn(10f, 14f)
            // (toSp also undoes Android 14's non-linear scaling.)
            val style = with(LocalDensity.current) {
                monoStyle(
                    fontSize = fittedDp.dp.toSp(),
                    lineHeight = (fittedDp * 1.45f).dp.toSp(),
                ).copy(
                    // Soft wrap fallback: a continuation starts two columns in, so it still reads as one line.
                    textIndent = TextIndent(firstLine = 0.sp, restLine = (fittedDp * 0.6f * 2).dp.toSp()),
                )
            }
            Column {
                for (line in demo.lines()) {
                    val text = buildAnnotatedString {
                        if (line.startsWith("$ ")) {
                            withStyle(SpanStyle(color = c.terminalPrompt, fontWeight = FontWeight.W700)) { append("$ ") }
                            withStyle(SpanStyle(color = c.terminalText, fontWeight = FontWeight.W500)) { append(Code.gluePaths(line.removePrefix("$ "))) }
                        } else if (line == "$") {
                            withStyle(SpanStyle(color = c.terminalPrompt, fontWeight = FontWeight.W700)) { append("$") }
                        } else {
                            withStyle(SpanStyle(color = c.terminalDim)) { append(Code.gluePaths(line.replace("\t", "    "))) }
                        }
                    }
                    Text(text, style = style, softWrap = true)
                }
            }
        }
    }
}

/**
 * The demo folded into one obviously tappable row ("Ukážka v termináli" and its first command); a tap opens the
 * full terminal in place and scrolls it into view, another tap folds it again.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TerminalPeek(demo: String, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Uli.colors
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(open) {
        if (open) {
            kotlinx.coroutines.delay(320)
            requester.bringIntoView()
        }
    }
    val first = demo.lines().firstOrNull { it.startsWith("$ ") }?.removePrefix("$ ") ?: demo.lines().first()
    Box(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = if (open) "Skryť ukážku" else "Ukázať ukážku", role = androidx.compose.ui.semantics.Role.Button) { onToggle() }
            .semantics { stateDescription = if (open) "Rozbalené" else "Zbalené" }
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        if (open) {
            Terminal(demo)
        } else {
            Row(
                Modifier.fillMaxWidth().background(c.terminal).heightIn(min = 60.dp).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ukážka v termináli", style = MaterialTheme.typography.labelLarge, color = c.terminalText)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.terminalPrompt, fontWeight = FontWeight.W700)) { append("$ ") }
                            withStyle(SpanStyle(color = c.terminalDim)) { append(first) }
                        },
                        style = monoStyle(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c.terminalText.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, null, tint = c.terminalText, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Every demo line is at most this many characters (ContentTest keeps it so). */
const val TERMINAL_COLUMNS = 44

/** A badge: an icon inside a Material shape. */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(iconSize))
    }
}

enum class Seg { Right, Wrong, Current, Retry, Upcoming }

/**
 * Round progress: one pill per card, colored by the first attempt. Right answers in a row fuse into one
 * bar (a streak you can see). The card you are on is a wave that runs from a faint tail into a bright
 * head, so it reads as moving forward even in a still frame; a correction runs the same way in red.
 */
@Composable
fun SegmentedProgress(segments: List<Seg>, modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = Uli.colors
    val primary = MaterialTheme.colorScheme.primary
    val wave = rememberInfiniteTransition(label = "wave")
    val phase by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    val shine = Color.White.copy(alpha = 0.28f)
    Row(modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { i, s ->
            val prevRight = s == Seg.Right && segments.getOrNull(i - 1) == Seg.Right
            val nextRight = s == Seg.Right && segments.getOrNull(i + 1) == Seg.Right
            if (i > 0 && !prevRight) Spacer(Modifier.width(4.dp))
            val color by animateColorAsState(
                when (s) {
                    Seg.Right -> c.right
                    Seg.Wrong, Seg.Retry -> c.wrong
                    Seg.Current -> primary
                    Seg.Upcoming -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "seg",
            )
            if (s == Seg.Current || s == Seg.Retry) {
                Canvas(Modifier.weight(2f).fillMaxHeight()) {
                    val stroke = 4.dp.toPx()
                    val amp = 2.6.dp.toPx()
                    val length = 11.dp.toPx()
                    val head = 4.5.dp.toPx()
                    val shift = (if (animate) phase else 0.25f) * length
                    val mid = size.height / 2
                    val end = size.width - head
                    val path = Path()
                    var x = stroke / 2
                    path.moveTo(x, mid + amp * sin((x + shift) / length * 2 * PI).toFloat())
                    while (x < end) {
                        x = (x + 1.dp.toPx()).coerceAtMost(end)
                        path.lineTo(x, mid + amp * sin((x + shift) / length * 2 * PI).toFloat())
                    }
                    drawPath(
                        path,
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.25f), color), startX = 0f, endX = end),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    val y = mid + amp * sin((end + shift) / length * 2 * PI).toFloat()
                    drawCircle(color, radius = head, center = Offset(end, y))
                }
            } else {
                val r = 4.dp
                val shape = RoundedCornerShape(
                    topStart = if (prevRight) 0.dp else r, bottomStart = if (prevRight) 0.dp else r,
                    topEnd = if (nextRight) 0.dp else r, bottomEnd = if (nextRight) 0.dp else r,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(shape)
                        .background(color),
                ) {
                    // A thin highlight on answered pills: filled, not just colored.
                    if (s == Seg.Right || s == Seg.Wrong) {
                        Box(Modifier.fillMaxWidth().padding(start = if (prevRight) 0.dp else 3.dp, end = if (nextRight) 0.dp else 3.dp, top = 1.5.dp).height(2.dp).clip(CircleShape).background(shine))
                    }
                }
            }
        }
    }
}

/** Small uppercase-free label pill. */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Grouped list item corners: big outside, tight between neighbours. */
fun groupShape(index: Int, count: Int, outer: androidx.compose.ui.unit.Dp = 24.dp, inner: androidx.compose.ui.unit.Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    return this.scale(s)
}

@Composable
fun OutlinedBox(border: Color, shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.border(BorderStroke(2.dp, border), shape).heightIn(min = 0.dp)) { content() }
}

/**
 * Behind the status bar on screens that scroll under it: invisible at rest, the paper color plus a short
 * fade once the content has moved, so the clock never sits on top of text.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.StatusBarScrim(visible: Boolean) {
    val bg = MaterialTheme.colorScheme.background
    val a by animateFloatAsState(if (visible) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "scrim")
    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().graphicsLayer { alpha = a }) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg))
        Box(Modifier.fillMaxWidth().height(16.dp).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))))
    }
}
