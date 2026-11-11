package com.sideby.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Gerak kecil yang bikin aplikasi terasa hidup, bukan pamer efek:
// semua memakai pegas (spring) supaya ada pantulan halus, dan tidak ada yang bergerak terus-menerus
// selain detak hati dan gerak mengambang pelan.

/** Tombol / kartu yang mengempis sedikit saat ditekan lalu memantul kembali. */
fun Modifier.bounceClick(
    pressedScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier = composed2 {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounceClick"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onClick() }
            )
        }
}

/**
 * Efek tekan-memantul untuk komponen yang sudah punya onClick sendiri (Button, dsb.).
 * Hanya mengamati sentuhan, tidak mengambil alih atau menghabiskan event-nya.
 */
fun Modifier.pressBounce(pressedScale: Float = 0.95f): Modifier = composed2 {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressBounce"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
                pressed = false
            }
        }
}

/** Kartu muncul satu per satu: naik sedikit, membesar dari agak kecil, dan memudar masuk. */
fun Modifier.popIn(index: Int = 0, stepMillis: Int = 70): Modifier = composed2 {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * stepMillis).toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** Mengambang pelan naik-turun, seperti balon. */
fun Modifier.floating(amplitudeDp: Float = 4f, periodMillis: Int = 2600): Modifier = composed2 {
    val t = rememberInfiniteTransition(label = "floating")
    val y by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatingY"
    )
    this.graphicsLayer { translationY = y * amplitudeDp.dp.toPx() }
}

/** Detak jantung: dua denyut cepat lalu jeda. */
fun Modifier.heartbeat(periodMillis: Int = 1500): Modifier = composed2 {
    val t = rememberInfiniteTransition(label = "heartbeat")
    val s by t.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMillis
                1f at 0
                1.22f at (periodMillis * 0.12f).toInt() using FastOutSlowInEasing
                1f at (periodMillis * 0.26f).toInt() using FastOutSlowInEasing
                1.14f at (periodMillis * 0.38f).toInt() using FastOutSlowInEasing
                1f at (periodMillis * 0.52f).toInt() using FastOutSlowInEasing
                1f at periodMillis
            }
        ),
        label = "heartbeatScale"
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Goyang kiri-kanan sekali tiap [trigger] berubah (mis. saat diketuk). */
@Composable
fun rememberWobble(trigger: Int): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        a.snapTo(0f)
        a.animateTo(
            0f,
            keyframes {
                durationMillis = 520
                0f at 0
                -12f at 70
                10f at 160
                -7f at 250
                4f at 340
                0f at 520
            }
        )
    }
    return a.value
}

// -----------------------------------------------------------------------------
// Hati melayang
// -----------------------------------------------------------------------------

internal class Burst(val id: Int, val origin: Offset, val count: Int)

@Stable
class HeartBurstState {
    internal val bursts = mutableStateListOf<Burst>()
    private var nextId = 0
    var hostOrigin by mutableStateOf(Offset.Zero)
        internal set

    /** Lepaskan hati dari titik [center] (koordinat layar). */
    fun fire(center: Offset, count: Int = 8) {
        // Batasi supaya ketukan beruntun tidak menumpuk puluhan animasi.
        if (bursts.size >= 4) bursts.removeAt(0)
        bursts.add(Burst(nextId++, center, count))
    }
}

val LocalHeartBurst = compositionLocalOf<HeartBurstState?> { null }

/** Bungkus seluruh layar; hati digambar di atas semua konten tanpa menghalangi sentuhan. */
@Composable
fun HeartBurstHost(content: @Composable () -> Unit) {
    val state = remember { HeartBurstState() }
    CompositionLocalProvider(LocalHeartBurst provides state) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { state.hostOrigin = it.positionInRoot() }
        ) {
            content()
            state.bursts.toList().forEach { b ->
                androidx.compose.runtime.key(b.id) {
                    BurstLayer(
                        origin = b.origin - state.hostOrigin,
                        count = b.count,
                        onDone = { state.bursts.remove(b) }
                    )
                }
            }
        }
    }
}

private val heartColors = listOf(
    Color(0xFFBF3A55), Color(0xFFD25D74), Color(0xFFE8899A), Color(0xFFD9806E), Color(0xFFF2A7B3)
)

@Composable
private fun BurstLayer(origin: Offset, count: Int, onDone: () -> Unit) {
    val hearts = remember {
        List(count) { i ->
            val spread = (i - (count - 1) / 2f)
            HeartSpec(
                dx = spread * 16f + Random.nextFloat() * 14f - 7f,
                rise = 120f + Random.nextFloat() * 110f,
                sizeDp = 14f + Random.nextFloat() * 14f,
                sway = 10f + Random.nextFloat() * 14f,
                phase = Random.nextFloat() * 6.28f,
                delayMs = i * 55L,
                color = heartColors[Random.nextInt(heartColors.size)]
            )
        }
    }
    hearts.forEach { FloatingHeart(origin, it) }
    LaunchedEffect(Unit) {
        delay(2200)
        onDone()
    }
}

private class HeartSpec(
    val dx: Float,
    val rise: Float,
    val sizeDp: Float,
    val sway: Float,
    val phase: Float,
    val delayMs: Long,
    val color: Color
)

@Composable
private fun FloatingHeart(origin: Offset, spec: HeartSpec) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(spec.delayMs)
        p.animateTo(1f, tween(1500, easing = LinearEasing))
    }
    val density = LocalDensity.current
    val sizePx = with(density) { spec.sizeDp.dp.toPx() }
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = spec.color,
        modifier = Modifier
            .offset { IntOffset((origin.x - sizePx / 2).toInt(), (origin.y - sizePx / 2).toInt()) }
            .size(spec.sizeDp.dp)
            .graphicsLayer {
                val t = p.value
                val ease = 1f - (1f - t) * (1f - t)
                translationY = -spec.rise.dp.toPx() * ease
                translationX = spec.dx.dp.toPx() * ease + sin(spec.phase + t * 2f * PI.toFloat()) * spec.sway.dp.toPx()
                // Muncul membesar dengan pantulan di awal, lalu memudar di akhir.
                val pop = if (t < 0.18f) t / 0.18f else 1f
                val s = 0.3f + 0.7f * pop + 0.18f * sin(pop * PI.toFloat())
                scaleX = s
                scaleY = s
                rotationZ = sin(spec.phase + t * 4f) * 14f
                alpha = if (t == 0f) 0f else (1f - ((t - 0.55f) / 0.45f).coerceIn(0f, 1f))
            }
    )
}

/**
 * Bounce saat ditekan + lepaskan hati dari titik tengah elemen ini.
 * Bila host belum dipasang, hanya memantul.
 */
fun Modifier.heartBurstClick(count: Int = 8, onClick: () -> Unit = {}): Modifier = composed2 {
    val host = LocalHeartBurst.current
    val haptic = LocalHapticFeedback.current
    var center by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned {
            val pos = it.positionInRoot()
            center = Offset(pos.x + it.size.width / 2f, pos.y + it.size.height / 2f)
        }
        .bounceClick(pressedScale = 0.88f) {
            host?.fire(center, count)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Untuk memicu hati dari kode (mis. setelah status tersimpan). */
@Composable
fun rememberBurstAt(): (Offset, Int) -> Unit {
    val host = LocalHeartBurst.current
    return { o, c -> host?.fire(o, c) }
}

// -----------------------------------------------------------------------------
// Riak lingkaran (radar)
// -----------------------------------------------------------------------------

@Composable
fun RippleRings(color: Color, modifier: Modifier = Modifier, rings: Int = 3) {
    val t = rememberInfiniteTransition(label = "ripple")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "ripplePhase"
    )
    Canvas(modifier = modifier) {
        val maxR = size.minDimension / 2f
        for (i in 0 until rings) {
            val p = (phase + i / rings.toFloat()) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.35f),
                radius = maxR * (0.15f + 0.85f * p),
                style = Stroke(width = 2.5f)
            )
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Modifier.composed2(crossinline factory: @Composable Modifier.() -> Modifier): Modifier =
    composed { factory() }
