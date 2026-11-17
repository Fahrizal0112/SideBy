package com.sideby.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =====================================================================
// SISTEM DESAIN "LIQUID GLASS"
// Latar aurora berwarna yang bergerak pelan + panel kaca bening (isian tembus pandang,
// kilap di sudut atas, bingkai tipis bercahaya, bayangan lembut berwarna).
// =====================================================================

/** Tinggi ruang yang ditempati bilah navigasi melayang, agar isi halaman bisa memberi ruang di bawahnya. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

private val AuroraTop = Color(0xFFFFF1F6)
private val AuroraMid = Color(0xFFF3EDFF)
internal val AuroraBottom = Color(0xFFEAF3FF)

/** Latar belakang berwarna: tiga lapis gradasi + gumpalan warna yang bergeser sangat pelan. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraT"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(AuroraTop, AuroraMid, AuroraBottom)))
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }
        val a = (t * 2 * PI).toFloat()
        blob(Color(0xFFFF7FAE), w * (0.12f + 0.10f * sin(a)), h * (0.10f + 0.05f * cos(a)), w * 0.85f, 0.55f)
        blob(Color(0xFFA78BFA), w * (0.92f + 0.08f * cos(a * 2)), h * (0.34f + 0.06f * sin(a)), w * 0.95f, 0.50f)
        blob(Color(0xFF7CC4FF), w * (0.08f + 0.10f * cos(a)), h * (0.72f + 0.05f * sin(a * 2)), w * 0.95f, 0.50f)
        blob(Color(0xFFFFB38A), w * (0.88f + 0.07f * sin(a * 2)), h * (0.96f - 0.05f * cos(a)), w * 0.80f, 0.45f)
    }
}

private val RimBrush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.95f),
    0.35f to Color.White.copy(alpha = 0.28f),
    0.65f to Color.White.copy(alpha = 0.14f),
    1f to Color.White.copy(alpha = 0.75f)
)

/**
 * Gaya kaca: isian tembus pandang (lebih terang di sudut atas), kilau di bagian atas,
 * bingkai tipis bercahaya, dan bayangan lembut. [glossy] = kaca pekat berwarna (tombol, balon chat).
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Color.White,
    fillAlpha: Float = 0.42f,
    elevation: Dp = 10.dp,
    glossy: Boolean = false
): Modifier {
    val base = if (glossy) {
        Brush.linearGradient(
            listOf(
                lerp(tint, Color.White, 0.20f),
                tint,
                lerp(tint, Color(0xFF7B3FE4), 0.28f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                tint.copy(alpha = (fillAlpha + 0.22f).coerceAtMost(0.95f)),
                tint.copy(alpha = (fillAlpha - 0.08f).coerceAtLeast(0.04f))
            )
        )
    }
    val shadowColor = if (glossy) tint.copy(alpha = 0.9f) else Color(0xFF7A5CC8)
    return this
        .then(
            if (elevation > 0.dp) Modifier.shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = shadowColor,
                spotColor = shadowColor
            ) else Modifier
        )
        .clip(shape)
        .background(base)
        .drawBehind {
            // Kilau lengkung di bagian atas, seperti cahaya yang jatuh di permukaan kaca.
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = if (glossy) 0.38f else 0.30f), Color.Transparent),
                    endY = size.height * 0.55f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.12f, 0f)
            )
        }
        .border(1.dp, RimBrush, shape)
}

/** Pengganti `Surface`: panel kaca. Warna transparan dipakai apa adanya sebagai semburat; warna pekat diubah jadi kaca. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    color: Color = Color.White,
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
    shadowElevation: Dp = 8.dp,
    glossy: Boolean = false,
    content: @Composable () -> Unit
) {
    val fill = if (color.alpha < 0.999f) color.alpha else 0.42f
    Box(
        modifier = modifier.glass(
            shape = shape,
            tint = color.copy(alpha = 1f),
            fillAlpha = fill,
            elevation = shadowElevation,
            glossy = glossy
        ),
        propagateMinConstraints = true
    ) {
        CompositionLocalProvider(LocalContentColor provides if (glossy) Color.White else SidebyColors.TextDeep) {
            content()
        }
    }
}

// ---------------------------------------------------------------------
// TOMBOL
// ---------------------------------------------------------------------
data class GlassButtonColors(
    val container: Color,
    val content: Color,
    val disabledContainer: Color,
    val disabledContent: Color
)

object GlassButtonDefaults {
    fun buttonColors(
        containerColor: Color = SidebyColors.RosePrimary,
        contentColor: Color = Color.White,
        disabledContainerColor: Color = Color.White.copy(alpha = 0.45f),
        disabledContentColor: Color = SidebyColors.TextSubtle
    ) = GlassButtonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)

    fun outlinedButtonColors(
        contentColor: Color = SidebyColors.RosePrimary
    ) = GlassButtonColors(Color.White, contentColor, Color.White.copy(alpha = 0.45f), SidebyColors.TextSubtle)
}

@Composable
private fun GlassButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: GlassButtonColors,
    contentPadding: PaddingValues,
    forceSoft: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "glassButtonScale"
    )
    // Warna isian yang terang = kaca bening bersemburat; warna pekat = kaca berwarna mengilap.
    val soft = forceSoft || colors.container.luminance() > 0.55f
    val contentColor = if (enabled) colors.content else colors.disabledContent
    val look = when {
        !enabled -> Modifier.glass(shape, Color.White, 0.30f, 0.dp)
        soft -> Modifier.glass(shape, colors.container, if (forceSoft) 0.38f else 0.55f, 6.dp)
        else -> Modifier.glass(shape, colors.container, elevation = 12.dp, glossy = true)
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(look)
            .semantics { role = Role.Button }
            .pointerInput(enabled, onClick) {
                if (enabled) {
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
            .defaultMinSize(minHeight = 44.dp)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: GlassButtonColors = GlassButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) = GlassButtonBase(onClick, modifier, enabled, shape, colors, contentPadding, forceSoft = false, content = content)

/** Pengganti `OutlinedButton`: kaca bening dengan teks berwarna. */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(22.dp),
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
    colors: GlassButtonColors = GlassButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) = GlassButtonBase(onClick, modifier, enabled, shape, colors, contentPadding, forceSoft = true, content = content)

// ---------------------------------------------------------------------
// KOLOM ISIAN
// ---------------------------------------------------------------------
@Composable
fun glassFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SidebyColors.RosePrimary,
    unfocusedBorderColor = Color.White.copy(alpha = 0.95f),
    focusedContainerColor = Color.White.copy(alpha = 0.62f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.38f),
    focusedLabelColor = SidebyColors.RosePrimary,
    unfocusedLabelColor = SidebyColors.TextMuted,
    cursorColor = SidebyColors.RosePrimary,
    focusedTextColor = SidebyColors.TextDeep,
    unfocusedTextColor = SidebyColors.TextDeep,
    focusedPlaceholderColor = SidebyColors.TextSubtle,
    unfocusedPlaceholderColor = SidebyColors.TextSubtle,
    focusedSupportingTextColor = SidebyColors.TextMuted,
    unfocusedSupportingTextColor = SidebyColors.TextMuted
)

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: Shape = RoundedCornerShape(18.dp),
    textStyle: TextStyle = TextStyle.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: TextFieldColors = glassFieldColors()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        shape = shape,
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        colors = colors
    )
}

// ---------------------------------------------------------------------
// BILAH NAVIGASI MELAYANG
// ---------------------------------------------------------------------
data class GlassNavItem(val label: String, val outlined: ImageVector, val filled: ImageVector)

/** Tinggi tetap bilah (tanpa inset sistem). Dipakai juga untuk menghitung ruang kosong di bawah isi halaman. */
val GlassNavBarHeight = 68.dp
val GlassNavBarOuterPadding = 12.dp

@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    badgeIndex: Int = -1,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = GlassNavBarOuterPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassNavBarHeight)
                .glass(RoundedCornerShape(34.dp), Color.White, fillAlpha = 0.50f, elevation = 18.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val itemWidth = maxWidth / items.size
                val indicatorX by animateDpAsState(
                    targetValue = itemWidth * selected,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    label = "navIndicator"
                )
                // Gelembung kaca yang meluncur mengikuti tab terpilih.
                Box(
                    modifier = Modifier
                        .offset(x = indicatorX)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .glass(RoundedCornerShape(28.dp), SidebyColors.RosePrimary, fillAlpha = 0.20f, elevation = 0.dp)
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selected
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.18f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "navIconScale"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(index) { detectTapGestures { onSelect(index) } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box {
                                Icon(
                                    imageVector = if (isSelected) item.filled else item.outlined,
                                    contentDescription = item.label,
                                    tint = if (isSelected) SidebyColors.RosePrimary else SidebyColors.TextMuted,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                )
                                if (index == badgeIndex && badgeCount > 0 && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-5).dp)
                                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                            .clip(CircleShape)
                                            .background(SidebyColors.RosePrimary)
                                            .border(1.5.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (badgeCount > 9) "9+" else "$badgeCount",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                item.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) SidebyColors.RosePrimary else SidebyColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Lingkaran kaca kecil untuk ikon / avatar. */
@Composable
fun GlassOrb(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.glass(CircleShape, tint, fillAlpha = 0.50f, elevation = 6.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}
