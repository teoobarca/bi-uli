package dev.hamper.uli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.RoundKind
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RoundEndScreen(vm: AppViewModel) {
    val s = vm.roundSummary ?: return
    val c = Uli.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val ratio = s.score.toFloat() / s.total
    val good = ratio >= 0.8f
    val readiness = (Srs.readiness(vm.ids, vm.progress) * 100).roundToInt()
    val chance = Srs.passChance(vm.ids, vm.progress)
    val likely = chance >= Srs.LIKELY_PASS
    val mastered = Srs.masteredCount(vm.ids, vm.progress)

    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 24.dp, bottom = bottom + 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ScoreBadge(s.score, s.total, good) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                    Text(
                        when {
                            s.score == s.total -> "Bez chyby"
                            good -> "Výborne"
                            ratio >= 0.6f -> "Dobré kolo"
                            else -> "Ešte to dáš"
                        },
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        roundEndNote(s.kind, s.ids, s.mistakes, vm.progress, vm.clock()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            item {
                // One quiet card with the two numbers that moved, instead of a row of colored tiles.
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 16.dp)) {
                        // Mastery needs 3 right answers spread over time, so a first clean round masters nothing yet:
                        // then show how many cards moved up instead of a discouraging "+0".
                        if (s.newlyMastered > 0 || s.climbed == 0) {
                            Stat(
                                value = "+${s.newlyMastered}",
                                label = "novo zvládnuté",
                                detail = "spolu $mastered zo ${vm.ids.size}",
                                color = if (s.newlyMastered > 0) c.right else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Stat(
                                value = "+${s.climbed}",
                                label = "o krok bližšie",
                                detail = "zvládnutá po 3. správnej",
                                color = c.right,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Box(Modifier.fillMaxHeight().padding(vertical = 4.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Stat(
                            value = "$readiness %",
                            label = "odhad na skúške",
                            detail = "šanca prejsť ${chanceText(chance)}",
                            color = if (likely) c.right else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (s.mistakes.isNotEmpty()) {
                item {
                    Text(
                        "Na zopakovanie (${s.mistakes.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 18.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(s.mistakes) { i, id ->
                    ReviewItem(vm.byId.getValue(id), null, groupShape(i, s.mistakes.size))
                }
            }
        }
        if (good) Celebration(vm.shotMode, Modifier.align(Alignment.TopCenter).padding(top = top + 24.dp))
        StatusBarScrim(list.canScrollBackward)
        val more = rememberMoreBelow(list)
        val scope = rememberCoroutineScope()
        BottomActions(
            primary = "Ďalšie kolo",
            onPrimary = vm::startRound,
            secondary = "Domov",
            onSecondary = vm::home,
            modifier = Modifier.align(Alignment.BottomCenter),
            onMore = if (more) ({ scope.launch { list.animateScrollBy(list.layoutInfo.viewportSize.height * 0.6f) } }) else null,
        )
    }
}

/** The score sun: big enough to celebrate, small enough that two review cards fit above the actions. */
private val BADGE = 172.dp

@Composable
private fun ScoreBadge(score: Int, total: Int, good: Boolean) {
    val c = Uli.colors
    val spin = remember { Animatable(-120f) }
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { spin.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 90f)) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    Box(Modifier.size(BADGE).graphicsLayer { scaleX = scale.value; scaleY = scale.value }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = spin.value }
                .clip(Shapes.verySunny)
                .background(if (good) c.amber else MaterialTheme.colorScheme.secondaryContainer),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$score",
                fontFamily = Rounded,
                fontWeight = FontWeight.W900,
                fontSize = 68.sp,
                lineHeight = 68.sp,
                color = if (good) c.onAmber else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "/$total",
                fontFamily = Rounded,
                fontWeight = FontWeight.W700,
                fontSize = 26.sp,
                color = (if (good) c.onAmber else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, detail: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Rounded, fontWeight = FontWeight.W800, fontSize = 28.sp, lineHeight = 30.sp, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** A one-shot burst of Material shapes from behind the score, gone in about a second. */
@Composable
private fun Celebration(frozen: Boolean, modifier: Modifier) {
    val t = remember { Animatable(if (frozen) 0.5f else 0f) }
    LaunchedEffect(Unit) { if (!frozen) t.animateTo(1f, tween(1150, easing = FastOutSlowInEasing)) }
    if (t.value >= 1f) return
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(c.amber, c.right, scheme.tertiary, c.learning, scheme.primary, c.wrong)
    val shapes = listOf(Shapes.sunny, Shapes.clover4, Shapes.cookie6, Shapes.gem, Shapes.flower, Shapes.pill)
    Box(modifier.size(BADGE)) {
        for (i in 0 until 18) {
            // Fan out over the upper arc from the badge's rim, then fall a little.
            val angle = 200.0 + i * (140.0 / 17) + (i % 3) * 4.0
            val dist = 80 + (40 + (i % 4) * 22) * t.value
            val rad = angle * kotlin.math.PI / 180
            val x = (cos(rad) * dist).toFloat()
            val y = (sin(rad) * dist).toFloat() + 60f * t.value * t.value
            val size = (12 + (i % 3) * 6).dp
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(size)
                    .graphicsLayer {
                        translationX = x * density
                        translationY = y * density
                        rotationZ = t.value * (180f + i * 25f)
                        alpha = 1f - t.value * t.value
                        val s = 0.4f + 0.8f * (1f - (t.value - 0.5f) * (t.value - 0.5f) * 2f)
                        scaleX = s
                        scaleY = s
                    }
                    .clip(shapes[i % shapes.size])
                    .background(colors[i % colors.size]),
            )
        }
    }
}
