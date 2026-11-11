package com.sideby.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sideby.mobile.data.ChatMessage
import com.sideby.mobile.data.MobileDataManager
import com.sideby.shared.model.DailySnapshot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.6f

/**
 * Gambar yang bisa dicubit untuk zoom, digeser saat diperbesar, dan diketuk dua kali untuk
 * memperbesar / mengembalikan. [onZoomedChange] memberi tahu pemanggil saat sedang diperbesar.
 */
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    onZoomedChange: (Boolean) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Ukuran gambar setelah "fit" ke dalam kotak, dipakai untuk membatasi geseran agar tidak keluar tepi.
    fun maxOffset(s: Float): Offset {
        if (box.width == 0 || box.height == 0) return Offset.Zero
        val fit = minOf(box.width / bitmap.width.toFloat(), box.height / bitmap.height.toFloat())
        val w = bitmap.width * fit * s
        val h = bitmap.height * fit * s
        return Offset(((w - box.width) / 2f).coerceAtLeast(0f), ((h - box.height) / 2f).coerceAtLeast(0f))
    }

    fun clamp(o: Offset, s: Float): Offset {
        val m = maxOffset(s)
        return Offset(o.x.coerceIn(-m.x, m.x), o.y.coerceIn(-m.y, m.y))
    }

    fun report() = onZoomedChange(scale > 1.02f)

    Box(
        modifier = modifier
            .onSizeChanged { box = it }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    val c = Offset(centroid.x - size.width / 2f, centroid.y - size.height / 2f)
                    val applied = newScale / scale
                    // Zoom mengikuti titik di antara dua jari, bukan pusat layar.
                    val moved = offset * applied + c * (1f - applied) + pan
                    scale = newScale
                    offset = if (newScale <= 1.001f) Offset.Zero else clamp(moved, newScale)
                    report()
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(onDoubleTap = { tap ->
                    val fromScale = scale
                    val fromOffset = offset
                    val zoomIn = scale < 1.5f
                    val toScale = if (zoomIn) DOUBLE_TAP_ZOOM else 1f
                    val c = Offset(tap.x - size.width / 2f, tap.y - size.height / 2f)
                    val ratio = toScale / fromScale
                    val toOffset = if (zoomIn) clamp(fromOffset * ratio + c * (1f - ratio), toScale) else Offset.Zero
                    scope.launch {
                        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) { t, _ ->
                            scale = fromScale + (toScale - fromScale) * t
                            offset = Offset(
                                fromOffset.x + (toOffset.x - fromOffset.x) * t,
                                fromOffset.y + (toOffset.y - fromOffset.y) * t
                            )
                            report()
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Foto",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

/**
 * Foto layar penuh berlatar gelap. Bisa di-zoom; komentar ada di bawah dan otomatis
 * menyingkir saat gambar sedang diperbesar supaya fotonya terlihat utuh.
 */
@Composable
fun PhotoViewerDialog(
    photo: DailySnapshot,
    gallery: List<DailySnapshot>,
    chat: List<ChatMessage>,
    partnerName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live = gallery.firstOrNull { it.id == photo.id } ?: photo
    val bitmap = rememberBitmap(live.imageBase64)
    val myId = remember { MobileDataManager.getMyDeviceId(context) }
    val comments = chat.filter { it.photoId == live.id }.sortedBy { it.timestamp }
    var commentInput by remember(photo.id) { mutableStateOf("") }
    var zoomed by remember(photo.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0C0D))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (live.senderName.isNotBlank()) {
                        Text(live.senderName, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID")).format(Date(live.timestamp)),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    ZoomableImage(bitmap = bitmap, modifier = Modifier.fillMaxSize(), onZoomedChange = { zoomed = it })
                } else {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                if (bitmap != null && !zoomed) {
                    Text(
                        "Cubit atau ketuk dua kali untuk memperbesar",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = !zoomed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "Komentar (${comments.size})",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SidebyColors.TextDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(modifier = Modifier.heightIn(max = 130.dp).verticalScroll(rememberScrollState())) {
                        if (comments.isEmpty()) {
                            Text("Belum ada komentar. Tulis yang pertama.", fontSize = 13.sp, color = SidebyColors.TextMuted)
                        }
                        comments.forEach { c ->
                            val mine = c.senderId == myId
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (mine) "Kamu" else c.senderName.ifBlank { partnerName },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (mine) SidebyColors.RosePrimary else SidebyColors.TextDeep
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (c.pending) "belum terkirim" else SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")).format(Date(c.timestamp)),
                                        fontSize = 11.sp,
                                        color = if (c.pending) SidebyColors.BatteryWarn else SidebyColors.TextSubtle
                                    )
                                }
                                Text(c.text, fontSize = 14.sp, color = SidebyColors.TextDeep, lineHeight = 20.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ChatInputBar(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = "Tulis komentar...",
                        onSend = {
                            val text = commentInput
                            commentInput = ""
                            scope.launch { sendChatMessage(context, text, live.id) }
                        }
                    )
                }
            }
        }
    }
}
