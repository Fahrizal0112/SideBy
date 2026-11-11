package com.sideby.mobile.ui

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.service.SidebyBackgroundSyncService
import com.sideby.mobile.sync.CloudSyncManager
import com.sideby.mobile.sync.SyncManager
import com.sideby.mobile.util.LocationHelper
import com.sideby.mobile.util.NotificationHelper
import com.sideby.mobile.widget.SidebyWidgetHelper
import com.sideby.shared.model.CoupleRoomConfig
import com.sideby.shared.model.DailySnapshot
import com.sideby.shared.model.MemoryItem
import com.sideby.shared.model.Mood
import com.sideby.shared.model.PartnerStatus
import com.sideby.shared.model.PokeMessage
import com.sideby.shared.model.PokeType
import com.sideby.shared.model.SpecialDate
import com.sideby.shared.model.TogetherSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.io.File
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.TextButton
import kotlin.random.Random
import com.sideby.mobile.data.ChatMessage
import com.sideby.mobile.update.UpdateInfo
import com.sideby.mobile.update.UpdateManager
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.mutableFloatStateOf
import com.sideby.mobile.util.VoiceNotes
import com.sideby.mobile.util.VoicePlayer
import com.sideby.mobile.util.VoiceRecorder
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

// ==========================================
// PALET WARNA INTIM & ROMANTIS (HANDCRAFTED)
// ==========================================
object SidebyColors {
    // Palet tenang: latar krem hangat, permukaan putih, satu warna aksen.
    val RosePrimary = Color(0xFFBF3A55)
    val RoseGlow = Color(0xFFD25D74)
    val CoralPeach = Color(0xFFD9806E)
    val SoftBlush = Color(0xFFF8EEEF)
    val DelicatePink = Color(0xFFF6F2F0)
    val CreamCanvas = Color(0xFFFCF7F3)
    val TextDeep = Color(0xFF211C1E)
    val TextMuted = Color(0xFF6B6367)
    val TextSubtle = Color(0xFF9A9195)
    val GlassBorder = Color(0xFFE8E2DE)
    val CardSurface = Color.White
    val TintRose = Color(0xFFFBE8EB)
    val TintMint = Color(0xFFE6F2EA)
    val TintSky = Color(0xFFE7EFF5)
    val TintPeach = Color(0xFFFBEADF)
    val BatteryOk = Color(0xFF2F8F5B)
    val BatteryWarn = Color(0xFFD38B1F)
    val BatteryLow = Color(0xFFC94545)
    val PastelMint = Color(0xFFEAF4EF)
    val PastelLavender = Color(0xFFF2F0F4)
    val PastelPeach = Color(0xFFF8F0EA)
    val PastelSky = Color(0xFFEDF3F7)

    val BackgroundBrush = Brush.verticalGradient(listOf(CreamCanvas, CreamCanvas))
    val PrimaryButtonBrush = Brush.horizontalGradient(listOf(RosePrimary, RosePrimary))
    val HeroCardBrush = Brush.linearGradient(listOf(Color.White, Color.White))
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_PHOTO_ID = "open_photo_id"
        const val TAB_CHAT = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MobileDataManager.isSetupCompleted(this)) {
            SidebyBackgroundSyncService.start(this)
        }

        setContent {
            SidebyTheme {
                val context = LocalContext.current
                var isConfigured by remember { mutableStateOf(MobileDataManager.isSetupCompleted(context)) }

                if (!isConfigured) {
                    SidebySetupWizardScreen(
                        onSetupCompleted = {
                            isConfigured = true
                            SidebyBackgroundSyncService.start(context)
                        }
                    )
                } else {
                    SidebyMainApp(
                        initialTab = intent?.getIntExtra(EXTRA_OPEN_TAB, 0) ?: 0,
                        initialPhotoId = intent?.getStringExtra(EXTRA_OPEN_PHOTO_ID),
                        onResetSetup = {
                            MobileDataManager.resetCurrentRoom(context)
                            SidebyBackgroundSyncService.stop(context)
                            isConfigured = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SidebyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SidebyColors.RosePrimary,
            onPrimary = Color.White,
            secondary = SidebyColors.RoseGlow,
            background = SidebyColors.CreamCanvas,
            surface = SidebyColors.CardSurface,
            onSurface = SidebyColors.TextDeep,
            onSurfaceVariant = SidebyColors.TextMuted,
            outline = SidebyColors.GlassBorder,
            surfaceVariant = SidebyColors.DelicatePink
        ),
        content = content
    )
}

// ==========================================
// ONBOARDING / SETUP WIZARD (CLEAN & GORGEOUS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebySetupWizardScreen(
    onSetupCompleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedSetupTab by remember { mutableIntStateOf(0) }

    var roomCode by remember { mutableStateOf("SIDEBY-${Random.nextInt(1000, 9999)}") }
    var myName by remember { mutableStateOf("") }
    var partnerName by remember { mutableStateOf("") }
    var anniversaryMillis by remember { mutableLongStateOf(0L) }
    var myBirthdayMillis by remember { mutableLongStateOf(0L) }
    var partnerBirthdayMillis by remember { mutableLongStateOf(0L) }

    var joinRoomCode by remember { mutableStateOf("") }
    var isJoiningLoading by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SidebyColors.BackgroundBrush)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Brand Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SidebyColors.PrimaryButtonBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Sideby",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SidebyColors.TextDeep,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Ruang privat untuk berdua",
                            fontSize = 13.sp,
                            color = SidebyColors.TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Custom Tab Switcher (Floating Pill Style)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedSetupTab == 0) SidebyColors.PrimaryButtonBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable { selectedSetupTab = 0 }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Buat Room",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (selectedSetupTab == 0) Color.White else SidebyColors.TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedSetupTab == 1) SidebyColors.PrimaryButtonBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable { selectedSetupTab = 1 }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Gabung Room",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (selectedSetupTab == 1) Color.White else SidebyColors.TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (selectedSetupTab == 0) {
                    // MODE PEMBUAT ROOM
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("1. Kode Room Kalian", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SidebyColors.TextDeep)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = roomCode,
                                    onValueChange = { roomCode = it.uppercase() },
                                    label = { Text("Kode Unik") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SidebyColors.RosePrimary,
                                        unfocusedBorderColor = SidebyColors.GlassBorder
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = { roomCode = "SIDEBY-${Random.nextInt(1000, 9999)}" },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.SoftBlush)
                                ) {
                                    Text("Acak", color = SidebyColors.RosePrimary, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text("2. Panggilan Sayang", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SidebyColors.TextDeep)
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = myName,
                                onValueChange = { myName = it },
                                label = { Text("Nama Panggilan Kamu") },
                                supportingText = { Text("Nama yang akan tampil di HP pasanganmu") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SidebyColors.RosePrimary,
                                    unfocusedBorderColor = SidebyColors.GlassBorder
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = partnerName,
                                onValueChange = { partnerName = it },
                                label = { Text("Nama Panggilan Pasangan") },
                                supportingText = { Text("Nama pasangan yang akan tampil di HP kamu") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SidebyColors.RosePrimary,
                                    unfocusedBorderColor = SidebyColors.GlassBorder
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Text("3. Tanggal-Tanggal Spesial", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SidebyColors.TextDeep)
                            Spacer(modifier = Modifier.height(10.dp))

                            DatePickerField(
                                label = "Tanggal Jadian Kita",
                                selectedMillis = anniversaryMillis,
                                onDateSelected = { anniversaryMillis = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DatePickerField(
                                label = "Tanggal Lahir Pasangan",
                                selectedMillis = partnerBirthdayMillis,
                                onDateSelected = { partnerBirthdayMillis = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DatePickerField(
                                label = "Tanggal Lahir Kamu",
                                selectedMillis = myBirthdayMillis,
                                onDateSelected = { myBirthdayMillis = it }
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Button(
                                onClick = {
                                    if (myName.isBlank() || partnerName.isBlank()) {
                                        Toast.makeText(context, "Silakan isi nama panggilan terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val config = CoupleRoomConfig(
                                        roomCode = roomCode,
                                        creatorName = myName,
                                        partnerName = partnerName,
                                        anniversaryDateMillis = anniversaryMillis,
                                        myBirthdayMillis = myBirthdayMillis,
                                        partnerBirthdayMillis = partnerBirthdayMillis
                                    )

                                    MobileDataManager.saveRoomConfig(context, config, isCreatorRole = true)

                                    coroutineScope.launch {
                                        CloudSyncManager.broadcastRoomConfig(context, config)
                                    }

                                    Toast.makeText(context, "Room $roomCode berhasil dibuat!", Toast.LENGTH_SHORT).show()
                                    onSetupCompleted()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary)
                            ) {
                                Text(
                                    text = "Buat room",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    // MODE GABUNG ROOM (PACAR TINGGAL PAKAI)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Gabung ke Room Pacar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = SidebyColors.TextDeep
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pacar kamu sudah menyiapkan room? Cukup masukkan kodenya. Seluruh tanggal jadian, ulang tahun, dan panggilan akan otomatis tersinkronisasi!",
                                fontSize = 13.sp,
                                color = SidebyColors.TextMuted,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedTextField(
                                value = joinRoomCode,
                                onValueChange = { joinRoomCode = it.uppercase() },
                                label = { Text("Kode Room") },
                                supportingText = { Text("Masukkan kode room yang diberikan pasanganmu") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SidebyColors.RosePrimary,
                                    unfocusedBorderColor = SidebyColors.GlassBorder
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (joinRoomCode.isBlank()) {
                                        Toast.makeText(context, "Silakan masukkan kode room!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isJoiningLoading = true
                                    coroutineScope.launch {
                                        val fetchedConfig = CloudSyncManager.fetchRoomConfig(joinRoomCode)
                                        isJoiningLoading = false

                                        if (fetchedConfig != null) {
                                            MobileDataManager.saveRoomConfig(context, fetchedConfig, isCreatorRole = false)
                                                                                        CloudSyncManager.sendMyStatus(context)
                                            Toast.makeText(context, "Berhasil terhubung ke room ${fetchedConfig.roomCode}!", Toast.LENGTH_SHORT).show()
                                            onSetupCompleted()
                                        } else {
                                            val fallback = CoupleRoomConfig(
                                                roomCode = joinRoomCode,
                                                creatorName = "Pasangan",
                                                partnerName = "Saya",
                                                anniversaryDateMillis = 0L,
                                                myBirthdayMillis = 0L,
                                                partnerBirthdayMillis = 0L
                                            )
                                            MobileDataManager.saveRoomConfig(context, fallback, isCreatorRole = false)
                                                                                        CloudSyncManager.sendMyStatus(context)
                                            Toast.makeText(context, "Terhubung ke room $joinRoomCode!", Toast.LENGTH_SHORT).show()
                                            onSetupCompleted()
                                        }
                                    }
                                },
                                enabled = !isJoiningLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary)
                            ) {
                                if (isJoiningLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Menghubungkan...")
                                } else {
                                    Text(
                                        text = "Gabung",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatePickerField(
    label: String,
    selectedMillis: Long,
    onDateSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        if (selectedMillis > 0) timeInMillis = selectedMillis
    }

    val displayString = if (selectedMillis > 0) {
        SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(selectedMillis))
    } else {
        "Pilih tanggal..."
    }

    OutlinedTextField(
        value = displayString,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        trailingIcon = {
            Icon(
                Icons.Default.DateRange,
                contentDescription = "Pilih Tanggal",
                tint = SidebyColors.RosePrimary,
                modifier = Modifier.clickable {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val cal = Calendar.getInstance()
                            cal.set(year, month, dayOfMonth, 0, 0, 0)
                            onDateSelected(cal.timeInMillis)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val cal = Calendar.getInstance()
                        cal.set(year, month, dayOfMonth, 0, 0, 0)
                        onDateSelected(cal.timeInMillis)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SidebyColors.RosePrimary,
            unfocusedBorderColor = SidebyColors.GlassBorder
        )
    )
}

// ==========================================
// APLIKASI UTAMA (MAIN APP DENGAN DESAIN BARU)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebyMainApp(
    initialTab: Int = 0,
    initialPhotoId: String? = null,
    onResetSetup: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(initialTab) }
    var unreadChat by remember { mutableIntStateOf(0) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    // Cek update otomatis saat aplikasi dibuka (maksimal tiap 6 jam), tanpa mengganggu bila gagal / sudah terbaru.
    LaunchedEffect(Unit) {
        if (UpdateManager.shouldAutoCheck(context)) {
            val r = UpdateManager.checkForUpdate(context)
            if (r is UpdateManager.CheckResult.Available) pendingUpdate = r.info
        }
    }
    pendingUpdate?.let { UpdateDialog(it) { pendingUpdate = null } }

    // Dibuka dari widget locket: tampilkan foto itu langsung, layar penuh.
    var widgetPhotoId by remember { mutableStateOf(initialPhotoId) }
    widgetPhotoId?.let { id ->
        val loaded by produceState<Triple<List<DailySnapshot>, List<ChatMessage>, Boolean>>(
            initialValue = Triple(emptyList(), emptyList(), false), id
        ) {
            value = withContext(Dispatchers.IO) {
                Triple(MobileDataManager.getSnapshotGallery(context), MobileDataManager.getChatMessages(context), true)
            }
        }
        val (gallery, chat, ready) = loaded
        val snap = gallery.firstOrNull { it.id == id }
            ?: MobileDataManager.getMyStatus(context).snapshot?.takeIf { it.id == id }
            ?: MobileDataManager.getPartnerStatus(context).snapshot?.takeIf { it.id == id }
        if (snap != null) {
            PhotoViewerDialog(
                photo = snap,
                gallery = gallery,
                chat = chat,
                partnerName = remember { MobileDataManager.getPartnerStatus(context).name },
                onDismiss = { widgetPhotoId = null }
            )
        } else if (ready) {
            widgetPhotoId = null
        }
    }

    LaunchedEffect(currentTab) {
        var lastVersion = -1L
        while (true) {
            val v = MobileDataManager.dataVersion()
            if (v != lastVersion) {
                lastVersion = v
                if (currentTab == 1 && MobileDataManager.isChatScreenOpen) withContext(Dispatchers.IO) { MobileDataManager.markChatRead(context) }
                val n = withContext(Dispatchers.IO) { MobileDataManager.getUnreadChatCount(context) }
                if (unreadChat != n) unreadChat = n
            }
            delay(1000)
        }
    }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (LocationHelper.hasLocationPermission(context)) {
            LocationHelper.requestCurrentLocation(context) { lat, lon ->
                coroutineScope.launch {
                    LocationHelper.syncLocationAndProximity(context, lat, lon)
                }
            }
        }
        SidebyBackgroundSyncService.start(context)
    }

    // Continuous Real-Time GPS Tracking Callback
    DisposableEffect(Unit) {
        val callback = LocationHelper.startContinuousLocationUpdates(context) { lat, lon ->
            coroutineScope.launch {
                LocationHelper.syncLocationAndProximity(context, lat, lon)
            }
        }
        onDispose {
            LocationHelper.stopLocationUpdates(context, callback)
        }
    }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannel(context)

        val perms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())

        while (true) {
            CloudSyncManager.pollPartnerUpdates(context)

            LocationHelper.requestCurrentLocation(context) { lat, lon ->
                coroutineScope.launch {
                    LocationHelper.syncLocationAndProximity(context, lat, lon)
                }
            }
            delay(5000)
        }
    }

    HeartBurstHost {
    Scaffold(
        containerColor = SidebyColors.CreamCanvas,
        topBar = {
            val myName = remember { MobileDataManager.getMyStatus(context).name }
            val days = remember { MobileDataManager.getDaysTogether(context) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Halo, $myName",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = SidebyColors.TextDeep,
                        maxLines = 1
                    )
                    Text(
                        text = if (days > 0) "Hari ke-$days bersama" else "Room ${MobileDataManager.getRoomCode(context)}",
                        fontSize = 13.sp,
                        color = SidebyColors.TextMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SidebyColors.TintRose,
                    modifier = Modifier.heartBurstClick(count = 9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = SidebyColors.RosePrimary, modifier = Modifier.size(14.dp).heartbeat())
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(MobileDataManager.getRoomCode(context), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SidebyColors.RosePrimary)
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = SidebyColors.GlassBorder)
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        Triple("Berdua", Icons.Outlined.Home, Icons.Filled.Home),
                        Triple("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
                        Triple("Jarak", Icons.Outlined.Place, Icons.Filled.Place),
                        Triple("Momen", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
                        Triple("Setelan", Icons.Outlined.Settings, Icons.Filled.Settings)
                    )
                    items.forEachIndexed { index, (label, outlined, filled) ->
                        NavigationBarItem(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            icon = {
                                BadgedBox(badge = {
                                    if (index == 1 && unreadChat > 0 && currentTab != 1) {
                                        Badge(containerColor = SidebyColors.RosePrimary) { Text(if (unreadChat > 9) "9+" else "$unreadChat") }
                                    }
                                }) {
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (currentTab == index) 1.22f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
                                        label = "navIconScale"
                                    )
                                    Icon(
                                        if (currentTab == index) filled else outlined,
                                        contentDescription = label,
                                        modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                    )
                                }
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SidebyColors.RosePrimary,
                                selectedTextColor = SidebyColors.RosePrimary,
                                indicatorColor = SidebyColors.SoftBlush,
                                unselectedIconColor = SidebyColors.TextMuted,
                                unselectedTextColor = SidebyColors.TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pindah tab: konten baru menyelinap dari arah tab yang dituju, yang lama memudar ke sisi sebaliknya.
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { it / 7 * dir }
                    ) + fadeIn(tween(220, delayMillis = 60))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(180),
                            targetOffsetX = { -it / 10 * dir }
                        ) + fadeOut(tween(140)))
                },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreenTab()
                    1 -> ChatScreenTab()
                    2 -> RadarScreenTab()
                    3 -> MemoriesScreenTab()
                    4 -> SettingsScreenTab(onResetSetup = onResetSetup)
                }
            }
        }
    }
    } // HeartBurstHost
}

// ==========================================
// TAB 1: BERDUA
// ==========================================

/** Dekode gambar di luar main thread, dan hanya ulang bila isi gambarnya berubah. */
@Composable
internal fun rememberBitmap(base64: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, base64) {
        value = if (base64.isNullOrBlank()) null
        else withContext(Dispatchers.Default) { decodeBase64ToBitmap(base64) }
    }
    return state.value
}

private fun relativeTime(millis: Long, now: Long): String {
    if (millis <= 0L) return ""
    val diff = (now - millis).coerceAtLeast(0L)
    val min = diff / 60_000L
    return when {
        min < 1 -> "baru saja"
        min < 60 -> "$min menit lalu"
        min < 60 * 24 -> "${min / 60} jam lalu"
        else -> "${min / (60 * 24)} hari lalu"
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().popIn(index),
        shape = RoundedCornerShape(20.dp),
        color = SidebyColors.CardSurface,
        border = BorderStroke(1.dp, SidebyColors.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressColor: Color = iconColor
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = tint) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep, maxLines = 1)
            Text(label, fontSize = 12.sp, color = SidebyColors.TextMuted, maxLines = 1)
            if (progress != null) {
                // Bar baterai mengisi dengan pantulan, dan bergeser halus saat persennya berubah.
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessVeryLow),
                    label = "batteryBar"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0.04f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(progressColor)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreenTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var partner by remember { mutableStateOf(MobileDataManager.getPartnerStatus(context)) }
    var myStatus by remember { mutableStateOf(MobileDataManager.getMyStatus(context)) }
    var galleryList by remember { mutableStateOf<List<DailySnapshot>>(emptyList()) }
    var pendingIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Input yang sedang diedit user tidak boleh ikut tertimpa saat data di belakang layar berubah.
    var selectedMood by remember { mutableStateOf(myStatus.mood) }
    var noteInput by remember { mutableStateOf(myStatus.customNote) }
    var isSaving by remember { mutableStateOf(false) }

    var showGalleryDialog by remember { mutableStateOf(false) }
    var selectedFullPhoto by remember { mutableStateOf<DailySnapshot?>(null) }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Muat ulang hanya ketika data benar-benar berubah (versi naik), dan di luar main thread.
    // Sebelumnya seluruh status + galeri di-decode ulang di main thread tiap 1,5 detik.
    LaunchedEffect(Unit) {
        var lastVersion = -1L
        var lastBatteryCheck = 0L
        while (true) {
            val version = MobileDataManager.dataVersion()
            if (version != lastVersion) {
                lastVersion = version
                val (p, m, g) = withContext(Dispatchers.IO) {
                    Triple(
                        MobileDataManager.getPartnerStatus(context),
                        MobileDataManager.getMyStatus(context),
                        MobileDataManager.getSnapshotGallery(context)
                    )
                }
                val chat = withContext(Dispatchers.IO) { MobileDataManager.getChatMessages(context) }
                if (chatMessages != chat) chatMessages = chat
                val pend = MobileDataManager.getPendingSnapshotIds(context)
                if (pendingIds != pend) pendingIds = pend
                if (partner != p) partner = p
                if (myStatus != m) myStatus = m
                if (galleryList != g) galleryList = g
            }
            val t = System.currentTimeMillis()
            now = t
            if (t - lastBatteryCheck > 15_000L) {
                lastBatteryCheck = t
                withContext(Dispatchers.IO) { SyncManager.syncMyBattery(context) }
            }
            delay(1000)
        }
    }

    val processPhotoUri: (Uri) -> Unit = { uri ->
        coroutineScope.launch {
            Toast.makeText(context, "Mengirim foto...", Toast.LENGTH_SHORT).show()
            val base64 = encodeImageUriToBase64(context, uri)
            if (base64 != null) {
                val snapshot = DailySnapshot(
                    imageBase64 = base64,
                    caption = "",
                    senderName = myStatus.name,
                    timestamp = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    MobileDataManager.updateMyStatus(context) { it.copy(snapshot = snapshot) }
                    MobileDataManager.addSnapshotToGallery(context, snapshot)
                    // Masuk antrean dulu; terhapus dari antrean hanya setelah benar-benar terkirim.
                    MobileDataManager.addPendingSnapshot(context, snapshot.id)
                }
                SidebyWidgetHelper.updateAllWidgets(context)

                val sentIds = CloudSyncManager.flushPendingSnapshots(context, wait = true)
                Toast.makeText(
                    context,
                    if (snapshot.id in sentIds) "Foto terkirim"
                    else "Foto belum terkirim. Akan dicoba lagi otomatis saat koneksi kembali.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Foto tidak bisa dibaca", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) processPhotoUri(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && tempCameraUri != null) processPhotoUri(tempCameraUri!!)
    }

    val openCamera: () -> Unit = {
        try {
            val file = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Kamera tidak bisa dibuka", Toast.LENGTH_SHORT).show()
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
        else Toast.makeText(context, "Izin kamera diperlukan untuk memotret", Toast.LENGTH_SHORT).show()
    }
    val launchCamera: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Foto terbaru = timestamp paling besar. Tidak lagi memprioritaskan "yang sudah punya base64",
    // karena itu membuat foto di layar berpindah-pindah saat unduhan selesai.
    val latestSnapshot = remember(galleryList, partner.snapshot, myStatus.snapshot) {
        (galleryList + listOfNotNull(partner.snapshot, myStatus.snapshot))
            .filter { !it.imageBase64.isNullOrBlank() || !it.imageUrl.isNullOrBlank() }
            .groupBy { it.id }
            .map { (_, versions) -> versions.maxByOrNull { it.imageBase64?.length ?: 0 }!! }
            .maxByOrNull { it.timestamp }
    }
    val latestBitmap = rememberBitmap(latestSnapshot?.imageBase64)

    LaunchedEffect(latestSnapshot?.id, latestSnapshot?.imageUrl, latestSnapshot?.imageBase64?.length) {
        val snap = latestSnapshot ?: return@LaunchedEffect
        val url = snap.imageUrl
        if (snap.imageBase64.isNullOrBlank() && !url.isNullOrBlank()) {
            val bytes = CloudSyncManager.downloadImageBytes(url)
            if (bytes != null) {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                withContext(Dispatchers.IO) { MobileDataManager.addSnapshotToGallery(context, snap.copy(imageBase64 = b64)) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!partner.isConnected) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SidebyColors.SoftBlush
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = SidebyColors.RosePrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Menunggu ${partner.name} bergabung", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SidebyColors.TextDeep)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Minta ${partner.name} memasukkan kode room ${MobileDataManager.getRoomCode(context)} di aplikasinya.",
                            fontSize = 13.sp,
                            color = SidebyColors.TextMuted
                        )
                    }
                }
            }
        }

        // ---- Pasangan ----
        Surface(
            modifier = Modifier.fillMaxWidth().popIn(0),
            shape = RoundedCornerShape(20.dp),
            color = SidebyColors.TintRose
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar mengambang pelan; diketuk = goyang + hati melayang. Emoji mood berganti dengan efek pantul.
                    var avatarTaps by remember { mutableIntStateOf(0) }
                    val wobble = rememberWobble(avatarTaps)
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .floating(amplitudeDp = 3f)
                            .graphicsLayer { rotationZ = wobble }
                            .clip(CircleShape)
                            .background(Color.White)
                            .heartBurstClick(count = 7) { avatarTaps++ },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = if (partner.isConnected) partner.mood.emoji else partner.name.firstOrNull()?.uppercase() ?: "?",
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(tween(120))) togetherWith
                                    (scaleOut(tween(120)) + fadeOut(tween(100)))
                            },
                            label = "partnerEmoji"
                        ) { face -> Text(face, fontSize = 28.sp) }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            partner.name,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = SidebyColors.TextDeep,
                            maxLines = 1
                        )
                        val recent = partner.isConnected && partner.lastUpdatedMillis > 0L && now - partner.lastUpdatedMillis < 10 * 60_000L
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Titik "aktif" berdenyut pelan supaya terasa hidup.
                            val dotPulse = rememberInfiniteTransition(label = "activeDot")
                            val dotScale by dotPulse.animateFloat(
                                initialValue = 1f,
                                targetValue = if (recent) 1.5f else 1f,
                                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "dotScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                                    .clip(CircleShape)
                                    .background(if (recent) SidebyColors.BatteryOk else SidebyColors.TextSubtle)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val updated = relativeTime(partner.lastUpdatedMillis, now)
                            Text(
                                text = when {
                                    !partner.isConnected -> "Belum terhubung"
                                    recent -> "Aktif sekarang"
                                    updated.isNotEmpty() -> "Terakhir aktif $updated"
                                    else -> "Terhubung"
                                },
                                fontSize = 13.sp,
                                color = SidebyColors.TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mood + catatan sebagai balon pesan
                Surface(shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp), color = Color.White) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        if (partner.isConnected) {
                            Text(partner.mood.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SidebyColors.RosePrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Catatan baru naik dari bawah menggantikan yang lama.
                            AnimatedContent(
                                targetState = partner.customNote,
                                transitionSpec = {
                                    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                        (fadeOut(tween(120)))
                                },
                                label = "partnerNote"
                            ) { note ->
                                Text(
                                    text = if (note.isNotBlank()) "\u201C$note\u201D" else "Belum ada catatan",
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = if (note.isNotBlank()) FontStyle.Italic else FontStyle.Normal,
                                    fontSize = 16.sp,
                                    lineHeight = 23.sp,
                                    color = if (note.isNotBlank()) SidebyColors.TextDeep else SidebyColors.TextSubtle
                                )
                            }
                        } else {
                            Text(
                                "Status akan muncul setelah ${partner.name} terhubung.",
                                fontSize = 14.sp,
                                color = SidebyColors.TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val phone = partner.phoneBattery
                val hasBattery = partner.isConnected && phone.percentage > 0
                val batteryColor = when {
                    phone.percentage <= 15 -> SidebyColors.BatteryLow
                    phone.percentage <= 35 -> SidebyColors.BatteryWarn
                    else -> SidebyColors.BatteryOk
                }
                val distanceText = when {
                    !partner.isConnected -> "-"
                    partner.location.isNearby -> "Bersama"
                    partner.location.distanceMeters != null && partner.location.distanceMeters!! in 1f..10_000_000f ->
                        partner.location.summary.removeSuffix(" dari kamu")
                    else -> "-"
                }
                val watch = partner.watchBattery
                // Angka baterai menghitung naik / turun, bukan loncat.
                val shownBattery by animateIntAsState(
                    targetValue = if (hasBattery) phone.percentage else 0,
                    animationSpec = tween(700, easing = FastOutSlowInEasing),
                    label = "batteryNumber"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                    StatTile(
                        icon = if (phone.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
                        label = if (hasBattery && phone.isCharging) "Baterai HP, mengisi" else "Baterai HP",
                        value = if (hasBattery) "$shownBattery%" else "-",
                        tint = SidebyColors.TintMint,
                        iconColor = if (hasBattery) batteryColor else SidebyColors.TextMuted,
                        progress = if (hasBattery) phone.percentage / 100f else null,
                        progressColor = batteryColor,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    StatTile(
                        icon = Icons.Outlined.Place,
                        label = "Jarak",
                        value = distanceText,
                        tint = SidebyColors.TintSky,
                        iconColor = Color(0xFF3F7FA8),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    if (partner.isConnected && watch != null) {
                        StatTile(
                            icon = Icons.Outlined.Watch,
                            label = "Jam",
                            value = if (watch.percentage > 0) "${watch.percentage}%" else "-",
                            tint = SidebyColors.TintPeach,
                            iconColor = SidebyColors.CoralPeach,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        // ---- Foto ----
        SectionCard(index = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foto terbaru", fontFamily = FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep, modifier = Modifier.weight(1f))
                TextButton(onClick = { showGalleryDialog = true }) {
                    Text(
                        if (galleryList.isNotEmpty()) "Galeri (${galleryList.size})" else "Galeri",
                        color = SidebyColors.RosePrimary,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (latestSnapshot != null && latestBitmap != null) {
                // Foto baru "digantung" dan berayun beberapa kali sebelum diam miring tipis.
                val swing = remember { Animatable(-8f) }
                var photoTaps by remember { mutableIntStateOf(0) }
                val photoWobble = rememberWobble(photoTaps)
                LaunchedEffect(latestSnapshot.id) {
                    swing.snapTo(-8f)
                    swing.animateTo(-1.2f, spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessVeryLow))
                }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                rotationZ = swing.value + photoWobble * 0.4f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            }
                            .bounceClick(pressedScale = 0.97f) { photoTaps++; selectedFullPhoto = latestSnapshot },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                        shadowElevation = 3.dp
                    ) {
                        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
                            Image(
                                bitmap = latestBitmap,
                                contentDescription = "Foto terbaru",
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val who = latestSnapshot.senderName.ifBlank { null }
                            val whenText = relativeTime(latestSnapshot.timestamp, now)
                            val isPending = latestSnapshot.id in pendingIds
                            Text(
                                text = if (isPending) {
                                    if (now - latestSnapshot.timestamp < 25_000L) "Mengunggah..." else "Belum terkirim, menunggu koneksi"
                                }
                                else listOfNotNull(who, whenText.ifEmpty { null }).joinToString(", "),
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp,
                                color = if (isPending) SidebyColors.BatteryWarn else SidebyColors.TextMuted
                            )
                        }
                    }
                }
                val commentCount = chatMessages.count { it.photoId == latestSnapshot.id }
                TextButton(onClick = { selectedFullPhoto = latestSnapshot }, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = SidebyColors.RosePrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (commentCount > 0) "$commentCount komentar" else "Balas foto ini",
                        color = SidebyColors.RosePrimary,
                        fontSize = 13.sp
                    )
                }
            } else if (latestSnapshot != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SidebyColors.DelicatePink),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SidebyColors.RosePrimary, strokeWidth = 2.dp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SidebyColors.DelicatePink),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Belum ada foto", fontSize = 14.sp, color = SidebyColors.TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showPhotoSourceDialog = true },
                modifier = Modifier.fillMaxWidth().height(46.dp).pressBounce(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.TintRose, contentColor = SidebyColors.RosePrimary)
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kirim foto", fontWeight = FontWeight.SemiBold)
            }
        }

        // ---- Status saya ----
        SectionCard(index = 2) {
            Text("Status kamu", fontFamily = FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Yang kamu pilih di sini muncul di layar ${partner.name}.", fontSize = 13.sp, color = SidebyColors.TextMuted)
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Mood.entries.forEach { mood ->
                    val chipScale by animateFloatAsState(
                        targetValue = if (selectedMood == mood) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "chipScale"
                    )
                    FilterChip(
                        modifier = Modifier.graphicsLayer { scaleX = chipScale; scaleY = chipScale },
                        selected = selectedMood == mood,
                        onClick = { selectedMood = mood },
                        label = { Text("${mood.emoji} ${mood.label}", fontSize = 13.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SidebyColors.TintRose,
                            selectedLabelColor = SidebyColors.RosePrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedMood == mood,
                            borderColor = SidebyColors.GlassBorder,
                            selectedBorderColor = SidebyColors.RosePrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = noteInput,
                onValueChange = { if (it.length <= 80) noteInput = it },
                label = { Text("Catatan singkat (opsional)") },
                supportingText = { Text("${noteInput.length}/80") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SidebyColors.RosePrimary,
                    unfocusedBorderColor = SidebyColors.GlassBorder,
                    focusedLabelColor = SidebyColors.RosePrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            val dirty = selectedMood != myStatus.mood || noteInput.trim() != myStatus.customNote
            val burstHost = LocalHeartBurst.current
            var saveCenter by remember { mutableStateOf(Offset.Zero) }
            Button(
                onClick = {
                    isSaving = true
                    val mood = selectedMood
                    val note = noteInput.trim()
                    coroutineScope.launch {
                        // Hanya mood & catatan yang diubah. Baterai, lokasi, dan foto dibiarkan apa adanya.
                        val change = withContext(Dispatchers.IO) {
                            MobileDataManager.updateMyStatus(context) { it.copy(mood = mood, customNote = note) }
                        }
                        noteInput = note
                        myStatus = change.after
                        val sent = CloudSyncManager.sendMyStatus(context)
                        SyncManager.sendPartnerStatusToWatch(context, MobileDataManager.getPartnerStatus(context))
                        isSaving = false
                        if (sent) burstHost?.fire(saveCenter, 10)
                        Toast.makeText(
                            context,
                            if (sent) "Status terkirim" else "Status tersimpan, belum terkirim. Cek koneksi internet.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = dirty && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onGloballyPositioned {
                        val pos = it.positionInRoot()
                        saveCenter = Offset(pos.x + it.size.width / 2f, pos.y + it.size.height / 2f)
                    }
                    .pressBounce(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SidebyColors.RosePrimary,
                    disabledContainerColor = SidebyColors.GlassBorder
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (dirty) "Simpan status" else "Sudah tersimpan", fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }

    // ---- Pilih sumber foto ----
    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Kirim foto", fontWeight = FontWeight.SemiBold) },
            text = { Text("Foto akan langsung dikirim ke ${partner.name}.", color = SidebyColors.TextMuted) },
            confirmButton = {
                TextButton(onClick = { showPhotoSourceDialog = false; launchCamera() }) {
                    Text("Ambil foto", color = SidebyColors.RosePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false; photoPickerLauncher.launch("image/*") }) {
                    Text("Pilih dari galeri", color = SidebyColors.RosePrimary)
                }
            },
            containerColor = Color.White
        )
    }

    // ---- Galeri ----
    if (showGalleryDialog) {

        Dialog(
            onDismissRequest = { showGalleryDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.82f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Galeri (${galleryList.size})", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SidebyColors.TextDeep, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showGalleryDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = SidebyColors.TextDeep)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (galleryList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Belum ada foto.", fontSize = 14.sp, color = SidebyColors.TextMuted)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(galleryList, key = { it.id }) { item ->
                                val bmp = rememberBitmap(item.imageBase64)
                                LaunchedEffect(item.id, item.imageBase64?.length, item.imageUrl) {
                                    val url = item.imageUrl
                                    if (item.imageBase64.isNullOrBlank() && !url.isNullOrBlank()) {
                                        val bytes = CloudSyncManager.downloadImageBytes(url)
                                        if (bytes != null) {
                                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                            withContext(Dispatchers.IO) { MobileDataManager.addSnapshotToGallery(context, item.copy(imageBase64 = b64)) }
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SidebyColors.DelicatePink)
                                        .clickable { selectedFullPhoto = item },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(bitmap = bmp, contentDescription = "Foto", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SidebyColors.RosePrimary, strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    // ---- Foto layar penuh (zoom) + komentar ----
    selectedFullPhoto?.let { photo ->
        PhotoViewerDialog(
            photo = photo,
            gallery = galleryList,
            chat = chatMessages,
            partnerName = partner.name,
            onDismiss = { selectedFullPhoto = null }
        )
    }
}

// ==========================================
// UPDATE APLIKASI
// ==========================================
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needPermission by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        containerColor = Color.White,
        title = { Text("Versi ${info.versionName} tersedia", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "Kamu memakai versi ${UpdateManager.installedVersionName(context)}." +
                        if (info.sizeBytes > 0) " Ukuran unduhan ${"%.1f".format(info.sizeBytes / 1_048_576f)} MB." else "",
                    fontSize = 14.sp,
                    color = SidebyColors.TextMuted
                )
                if (info.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(info.notes, fontSize = 14.sp, color = SidebyColors.TextDeep, lineHeight = 20.sp)
                }
                progress?.let { p ->
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = SidebyColors.RosePrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Mengunduh ${(p * 100).toInt()}%", fontSize = 12.sp, color = SidebyColors.TextMuted)
                }
                if (needPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Izinkan Sideby memasang aplikasi di layar yang baru terbuka, lalu kembali ke sini dan ketuk Lanjutkan.",
                        fontSize = 13.sp,
                        color = SidebyColors.BatteryWarn
                    )
                }
                error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, fontSize = 13.sp, color = SidebyColors.BatteryLow)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = progress == null,
                colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary),
                onClick = {
                    if (!UpdateManager.canInstallPackages(context)) {
                        needPermission = true
                        UpdateManager.openInstallPermissionSettings(context)
                        return@Button
                    }
                    needPermission = false
                    error = null
                    progress = 0f
                    scope.launch {
                        val apk = UpdateManager.downloadApk(context, info) { progress = it }
                        progress = null
                        if (apk == null) {
                            error = "Unduhan gagal. Cek koneksi internet lalu coba lagi."
                        } else {
                            UpdateManager.launchInstaller(context, apk)
                            onDismiss()
                        }
                    }
                }
            ) { Text(if (needPermission) "Lanjutkan" else "Unduh & pasang") }
        },
        dismissButton = {
            TextButton(enabled = progress == null, onClick = onDismiss) { Text("Nanti", color = SidebyColors.TextMuted) }
        }
    )
}

@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<UpdateInfo?>(null) }

    SectionCard {
        Text("Pembaruan aplikasi", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SidebyColors.TextDeep)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            message ?: "Cek apakah ada versi Sideby yang lebih baru.",
            fontSize = 13.sp,
            color = SidebyColors.TextMuted
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                checking = true
                message = null
                scope.launch {
                    when (val r = UpdateManager.checkForUpdate(context)) {
                        is UpdateManager.CheckResult.Available -> {
                            available = r.info
                            message = "Versi ${r.info.versionName} tersedia."
                        }
                        UpdateManager.CheckResult.UpToDate -> message = "Kamu sudah memakai versi terbaru."
                        UpdateManager.CheckResult.Failed -> message = "Tidak bisa mengecek sekarang. Coba lagi nanti."
                    }
                    checking = false
                }
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SidebyColors.GlassBorder)
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SidebyColors.RosePrimary, strokeWidth = 2.dp)
            } else {
                Text("Cek pembaruan", color = SidebyColors.RosePrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
    available?.let { UpdateDialog(it) { available = null } }
}

// ==========================================
// CHAT
// ==========================================
internal suspend fun sendChatMessage(context: Context, text: String, photoId: String?) {
    val clean = text.trim().take(ChatMessage.MAX_TEXT_LENGTH)
    if (clean.isBlank()) return
    val me = MobileDataManager.getMyStatus(context)
    val msg = ChatMessage(
        senderId = MobileDataManager.getMyDeviceId(context),
        senderName = me.name,
        text = clean,
        photoId = photoId,
        pending = true
    )
    // Simpan lokal dulu (langsung tampil), lalu kirim; jika gagal, service mencoba lagi otomatis.
    withContext(Dispatchers.IO) { MobileDataManager.addChatMessage(context, msg) }
    CloudSyncManager.flushPendingChats(context, wait = true)
}

/** Simpan rekaman lalu kirim. Gagal terkirim = tetap di antrean dan dicoba lagi otomatis, seperti chat biasa. */
internal suspend fun sendVoiceMessage(context: Context, recording: File, millis: Long) {
    val me = MobileDataManager.getMyStatus(context)
    val msg = ChatMessage(
        senderId = MobileDataManager.getMyDeviceId(context),
        senderName = me.name,
        // Teks ini hanya terlihat di aplikasi versi lama yang belum bisa memutar voice note.
        text = "[Pesan suara]",
        audioMillis = millis,
        pending = true
    )
    withContext(Dispatchers.IO) {
        recording.copyTo(VoiceNotes.file(context, msg.id), overwrite = true)
        recording.delete()
        MobileDataManager.addChatMessage(context, msg)
    }
    CloudSyncManager.flushPendingChats(context, wait = true)
}

@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    /** Bila diisi, tombol mikrofon muncul saat kolom teks kosong (untuk voice note). */
    onVoice: ((File, Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }

    // Rekaman yang ditinggalkan (pindah tab / layar ditutup) dibuang, bukan terkirim diam-diam.
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    val finishRecording: () -> Unit = {
        recording = false
        val result = recorder.stop()
        if (result == null) {
            Toast.makeText(context, "Rekaman terlalu singkat", Toast.LENGTH_SHORT).show()
        } else {
            onVoice?.invoke(result.first, result.second)
        }
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        while (recorder.isRecording) {
            elapsed = recorder.elapsedMillis()
            // Naik cepat, turun pelan, supaya batang suaranya enak dilihat.
            val target = recorder.level()
            level = if (target > level) target else level * 0.8f + target * 0.2f
            if (elapsed >= VoiceNotes.MAX_MILLIS - 200) {
                finishRecording()
                break
            }
            delay(60)
        }
    }

    val startRecording: () -> Unit = {
        if (recorder.start()) {
            elapsed = 0L
            level = 0f
            recording = true
        } else {
            Toast.makeText(context, "Mikrofon tidak bisa dipakai sekarang", Toast.LENGTH_SHORT).show()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Izin mikrofon diperlukan untuk voice note", Toast.LENGTH_SHORT).show()
    }
    val requestRecording: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    if (recording) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SidebyColors.GlassBorder)
                    .bounceClick(pressedScale = 0.88f) {
                        recording = false
                        recorder.cancel()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Batalkan rekaman", tint = SidebyColors.TextMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = SidebyColors.TintRose
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val blink = rememberInfiniteTransition(label = "recDot")
                    val dotAlpha by blink.animateFloat(
                        initialValue = 1f, targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                        label = "recDotAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer { alpha = dotAlpha }
                            .clip(CircleShape)
                            .background(SidebyColors.BatteryLow)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val sec = (elapsed / 1000).toInt()
                    Text(
                        "%d:%02d".format(sec / 60, sec % 60),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = SidebyColors.TextDeep
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Batang suara bergerak mengikuti volume bicara.
                    Row(
                        modifier = Modifier.weight(1f).height(26.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bars = 18
                        repeat(bars) { i ->
                            val wave = (kotlin.math.sin((elapsed / 130.0) + i * 0.7) + 1.0).toFloat() / 2f
                            val h = (4f + 22f * (level * (0.35f + 0.65f * wave))).coerceIn(4f, 26f)
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(h.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SidebyColors.RosePrimary.copy(alpha = 0.75f))
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SidebyColors.RosePrimary)
                    .bounceClick(pressedScale = 0.86f) { finishRecording() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim voice note", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        return
    }

    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= ChatMessage.MAX_TEXT_LENGTH) onValueChange(it) },
            placeholder = { Text(placeholder, color = SidebyColors.TextSubtle) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SidebyColors.RosePrimary,
                unfocusedBorderColor = SidebyColors.GlassBorder,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        val enabled = value.isNotBlank()
        val showMic = !enabled && onVoice != null
        val sendScope = rememberCoroutineScope()
        val fly = remember { Animatable(0f) }
        // Tombol yang aktif membesar sedikit; saat ditekan pesawat kertasnya terbang keluar lalu muncul lagi.
        val buttonScale by animateFloatAsState(
            targetValue = if (enabled || showMic) 1f else 0.88f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "sendScale"
        )
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(48.dp)
                .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                .clip(CircleShape)
                .background(
                    when {
                        enabled -> SidebyColors.RosePrimary
                        showMic -> SidebyColors.TintRose
                        else -> SidebyColors.GlassBorder
                    }
                )
                .then(
                    if (enabled) Modifier.bounceClick(pressedScale = 0.86f) {
                        onSend()
                        sendScope.launch {
                            fly.snapTo(0f)
                            fly.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                            fly.snapTo(-1f)
                            fly.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
                        }
                    } else if (showMic) Modifier.bounceClick(pressedScale = 0.86f) { requestRecording() }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showMic) {
                Icon(Icons.Filled.Mic, contentDescription = "Rekam voice note", tint = SidebyColors.RosePrimary, modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            translationX = fly.value * 30.dp.toPx()
                            translationY = -fly.value * 30.dp.toPx()
                            alpha = 1f - kotlin.math.abs(fly.value).coerceAtMost(1f) * 0.9f
                        }
                )
            }
        }
    }
}

@Composable
fun ChatScreenTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val myId = remember { MobileDataManager.getMyDeviceId(context) }
    val partnerName = remember { MobileDataManager.getPartnerStatus(context).name }

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var photos by remember { mutableStateOf<List<DailySnapshot>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var openedPhoto by remember { mutableStateOf<DailySnapshot?>(null) }
    val listState = rememberLazyListState()
    DisposableEffect(Unit) { onDispose { VoicePlayer.stop() } }

    // "Chat terbuka" hanya benar saat layar benar-benar terlihat (RESUMED). Jika kamu menekan Home / mengunci
    // layar, pesan masuk harus tetap memunculkan notifikasi dan tidak ditandai terbaca.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var screenVisible by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { MobileDataManager.isChatScreenOpen = true; screenVisible = true }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { MobileDataManager.isChatScreenOpen = false; screenVisible = false }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            MobileDataManager.isChatScreenOpen = true
            screenVisible = true
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MobileDataManager.isChatScreenOpen = false
        }
    }

    // Tandai terbaca hanya ketika layar terlihat, termasuk saat kembali ke aplikasi.
    LaunchedEffect(screenVisible, messages.size) {
        if (screenVisible) withContext(Dispatchers.IO) { MobileDataManager.markChatRead(context) }
    }

    LaunchedEffect(Unit) {
        var lastVersion = -1L
        while (true) {
            val v = MobileDataManager.dataVersion()
            if (v != lastVersion) {
                lastVersion = v
                val chat = withContext(Dispatchers.IO) { MobileDataManager.getChatMessages(context) }
                if (messages != chat) messages = chat
                // Galeri (berat) hanya dimuat ulang bila ada foto yang dirujuk tapi belum kita punya.
                val needed = chat.mapNotNull { it.photoId }.toSet()
                if (needed.any { id -> photos.none { it.id == id } }) {
                    photos = withContext(Dispatchers.IO) { MobileDataManager.getSnapshotGallery(context) }
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    openedPhoto?.let { p ->
        PhotoViewerDialog(
            photo = p,
            gallery = photos,
            chat = messages,
            partnerName = partnerName,
            onDismiss = { openedPhoto = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = SidebyColors.RosePrimary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp).floating(amplitudeDp = 5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Belum ada pesan", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SidebyColors.TextDeep)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Mulai dengan menyapa $partnerName, atau balas fotonya dari halaman Berdua.",
                        fontSize = 14.sp,
                        color = SidebyColors.TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                    val prev = messages.getOrNull(index - 1)
                    if (prev == null || !isSameDay(prev.timestamp, msg.timestamp)) {
                        Text(
                            text = dayLabel(msg.timestamp),
                            fontSize = 12.sp,
                            color = SidebyColors.TextSubtle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    val bubblePhoto = msg.photoId?.let { id -> photos.firstOrNull { it.id == id } }
                    ChatBubble(
                        msg = msg,
                        mine = msg.senderId == myId,
                        photo = bubblePhoto,
                        onPhotoClick = { bubblePhoto?.let { openedPhoto = it } }
                    )
                }
            }
        }

        Surface(color = SidebyColors.CreamCanvas) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "Tulis pesan untuk $partnerName",
                    onSend = {
                        val text = input
                        input = ""
                        coroutineScope.launch { sendChatMessage(context, text, null) }
                    },
                    onVoice = { file, ms -> coroutineScope.launch { sendVoiceMessage(context, file, ms) } }
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, mine: Boolean, photo: DailySnapshot?, onPhotoClick: () -> Unit = {}) {
    val bubbleShape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    // Hanya pesan yang baru masuk (beberapa detik terakhir) yang "meletus" muncul dari sudut balonnya;
    // riwayat lama tampil biasa supaya tidak heboh saat membuka chat.
    val fresh = remember(msg.id) { System.currentTimeMillis() - msg.timestamp < 4_000L }
    val pop = remember(msg.id) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(msg.id) {
        if (fresh) pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = pop.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
                scaleX = 0.5f + 0.5f * p
                scaleY = 0.5f + 0.5f * p
                alpha = (p * 3f).coerceIn(0f, 1f)
            },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = if (mine) SidebyColors.RosePrimary else Color.White,
            border = if (mine) null else BorderStroke(1.dp, SidebyColors.GlassBorder),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (msg.photoId != null) {
                    val bmp = rememberBitmap(photo?.imageBase64)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mine) Color.White.copy(alpha = 0.2f) else SidebyColors.DelicatePink)
                            .clickable(enabled = photo != null, onClick = onPhotoClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(bitmap = bmp, contentDescription = "Foto yang dibalas", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = if (mine) Color.White else SidebyColors.TextSubtle)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (msg.audioMillis > 0L) {
                    VoiceBubbleContent(msg = msg, mine = mine)
                } else {
                    Text(
                        msg.text,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = if (mine) Color.White else SidebyColors.TextDeep
                    )
                }
            }
        }
        Text(
            text = if (msg.pending) {
                if (System.currentTimeMillis() - msg.timestamp < 25_000L) "Mengirim..." else "Belum terkirim, menunggu koneksi"
            } else SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(msg.timestamp)),
            fontSize = 11.sp,
            color = if (msg.pending) SidebyColors.BatteryWarn else SidebyColors.TextSubtle,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private fun formatClock(ms: Long): String {
    val sec = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}

@Composable
private fun VoiceBubbleContent(msg: ChatMessage, mine: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember(msg.id) { mutableStateOf(false) }
    val isThis = VoicePlayer.playingId == msg.id
    val playing = isThis && VoicePlayer.isPlaying
    val progress = if (isThis && VoicePlayer.durationMs > 0L) {
        (VoicePlayer.positionMs.toFloat() / VoicePlayer.durationMs).coerceIn(0f, 1f)
    } else 0f
    val strong = if (mine) Color.White else SidebyColors.RosePrimary
    val faint = if (mine) Color.White.copy(alpha = 0.4f) else SidebyColors.RosePrimary.copy(alpha = 0.25f)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 200.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (mine) Color.White.copy(alpha = 0.22f) else SidebyColors.TintRose)
                .bounceClick(pressedScale = 0.88f) {
                    if (downloading) return@bounceClick
                    scope.launch {
                        var f = VoiceNotes.file(context, msg.id)
                        if (!f.exists() || f.length() == 0L) {
                            downloading = true
                            f = VoiceNotes.ensureLocal(context, msg) ?: f
                            downloading = false
                        }
                        if (f.exists() && f.length() > 0L) VoicePlayer.toggle(msg.id, f)
                        else Toast.makeText(context, "Voice note belum bisa diunduh. Cek koneksi internet.", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (downloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = strong, strokeWidth = 2.dp)
            } else {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Jeda" else "Putar",
                    tint = strong,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            // Gelombang tiruan yang tetap sama untuk setiap pesan; bagian yang sudah diputar berwarna pekat.
            val heights = remember(msg.id) {
                val r = Random(msg.id.hashCode())
                List(26) { 0.25f + 0.75f * r.nextFloat() }
            }
            Canvas(modifier = Modifier.width(150.dp).height(28.dp)) {
                val n = heights.size
                val step = size.width / n
                val barW = step * 0.55f
                heights.forEachIndexed { i, h ->
                    val x = i * step + (step - barW) / 2f
                    val barH = size.height * h
                    val played = (i + 0.5f) / n <= progress
                    drawRoundRect(
                        color = if (played) strong else faint,
                        topLeft = Offset(x, (size.height - barH) / 2f),
                        size = androidx.compose.ui.geometry.Size(barW, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
                    )
                }
            }
            Text(
                text = if (isThis) "${formatClock(VoicePlayer.positionMs)} / ${formatClock(msg.audioMillis)}" else formatClock(msg.audioMillis),
                fontSize = 11.sp,
                color = if (mine) Color.White.copy(alpha = 0.85f) else SidebyColors.TextMuted
            )
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(millis, now) -> "Hari ini"
        isSameDay(millis, now - 24L * 3600_000L) -> "Kemarin"
        else -> SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(millis))
    }
}

// ==========================================
// TAB 2: RADAR & JARAK REAL-TIME (OTOMATIS)
// ==========================================
@Composable
fun RadarScreenTab() {
    val context = LocalContext.current
    var partner by remember { mutableStateOf(MobileDataManager.getPartnerStatus(context)) }

    LaunchedEffect(Unit) {
        var lastVersion = -1L
        while (true) {
            val v = MobileDataManager.dataVersion()
            if (v != lastVersion) {
                lastVersion = v
                val fresh = withContext(Dispatchers.IO) { MobileDataManager.getPartnerStatus(context) }
                if (partner != fresh) partner = fresh
            }
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val hasDistance = partner.isConnected && partner.location.distanceMeters != null &&
            partner.location.distanceMeters!! in 1f..10_000_000f
    val headline = when {
        !partner.isConnected -> "Belum terhubung"
        partner.location.isNearby -> "Sedang bersama"
        hasDistance -> partner.location.summary
        else -> "Menunggu lokasi"
    }
    val detail = when {
        !partner.isConnected -> "Jarak muncul setelah ${partner.name} bergabung ke room."
        partner.location.isNearby -> "Kamu dan ${partner.name} berada di tempat yang sama."
        hasDistance -> "Diperbarui otomatis selama aplikasi berjalan."
        else -> "Pastikan izin lokasi aktif di kedua HP."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(index = 0) {
            Text("Jarak dengan ${partner.name}", fontSize = 13.sp, color = SidebyColors.TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(headline, fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail, fontSize = 14.sp, color = SidebyColors.TextMuted, lineHeight = 20.sp)
        }

        SectionCard(index = 1) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(SidebyColors.DelicatePink),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2
                        listOf(0.33f, 0.66f, 1f).forEach {
                            drawCircle(color = SidebyColors.GlassBorder, radius = radius * it - 1f, style = Stroke(width = 2f))
                        }
                    }
                    // Riak halus memancar dari posisimu, seperti sinyal yang mencari pasangan.
                    RippleRings(color = SidebyColors.RosePrimary, modifier = Modifier.fillMaxSize())
                    if (partner.isConnected && partner.location.isNearby) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(SidebyColors.RosePrimary.copy(alpha = 0.12f))
                        )
                    }
                    Box(
                        modifier = Modifier.size(40.dp).heartbeat(2200).clip(CircleShape).background(SidebyColors.RosePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Kamu", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    if (partner.isConnected) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .align(if (partner.location.isNearby) Alignment.BottomCenter else Alignment.TopEnd)
                                .padding(if (partner.location.isNearby) 14.dp else 22.dp)
                                .floating(amplitudeDp = 4f, periodMillis = 2200)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.5.dp, SidebyColors.RosePrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(partner.mood.emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
            Text(
                text = "Gambar ini hanya ilustrasi, posisi pasangan tidak digambar sesuai arah sebenarnya.",
                fontSize = 12.sp,
                color = SidebyColors.TextSubtle,
                lineHeight = 16.sp
            )
        }

        SectionCard(index = 2) {
            Text("Cara kerjanya", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SidebyColors.TextDeep)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Lokasi dibaca otomatis di latar belakang. Jika kalian berada kurang dari 60 meter, HP memberi notifikasi dan mencatat waktu ketemuan di tab Momen.",
                fontSize = 13.sp,
                color = SidebyColors.TextMuted,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ==========================================
// TAB 3: MOMEN & COUNTDOWN (LOG OTOMATIS)
// ==========================================
@Composable
fun MemoriesScreenTab() {
    val context = LocalContext.current
    val daysTogether = MobileDataManager.getDaysTogether(context)
    var specialDates by remember { mutableStateOf(MobileDataManager.getSpecialDates(context)) }
    var memories by remember { mutableStateOf(MobileDataManager.getMemories(context)) }
    var togetherSessions by remember { mutableStateOf(MobileDataManager.getTogetherSessions(context)) }
    val partner = MobileDataManager.getPartnerStatus(context)

    var showAddDateDialog by remember { mutableStateOf(false) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showAddSessionDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 1. HERO MILESTONE KISAH KITA
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SidebyColors.RosePrimary)
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (daysTogether > 0) "$daysTogether" else "-",
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (daysTogether > 0) "hari bersama" else "Tanggal jadian belum diisi",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }

        // BANNER KENCAN AKTIF
        if (partner.location.isNearby || MobileDataManager.getActiveSessionStartTime(context) > 0L) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = SidebyColors.SoftBlush,
                    border = BorderStroke(1.dp, SidebyColors.GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = SidebyColors.RosePrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sedang bersama",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SidebyColors.RosePrimary
                            )
                            Text(
                                text = "Waktu ketemuan dicatat otomatis ke riwayat.",
                                fontSize = 11.sp,
                                color = SidebyColors.TextMuted
                            )
                        }
                    }
                }
            }
        }

        // 2. RIWAYAT WAKTU KETEMUAN OTOMATIS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Riwayat ketemuan", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
                OutlinedButton(
                    onClick = { showAddSessionDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Catat", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SidebyColors.RosePrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (togetherSessions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SidebyColors.GlassBorder)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Belum ada riwayat kencan. Log ini otomatis mencatat jam mulai & selesai setiap kali kalian bertemu (< 60m), atau tekan '+ Catat' untuk mencatat kencan hari ini.",
                            fontSize = 12.sp,
                            color = SidebyColors.TextMuted,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        items(togetherSessions) { session ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SidebyColors.SoftBlush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = SidebyColors.RosePrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.locationName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SidebyColors.TextDeep)
                        Text(session.note, fontSize = 12.sp, color = SidebyColors.TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(session.dateString, fontSize = 10.sp, color = SidebyColors.TextSubtle)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SidebyColors.SoftBlush
                    ) {
                        Text(
                            text = "${session.durationMinutes} mnt",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SidebyColors.RosePrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 3. COUNTDOWN SPESIAL
        item {
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hitung mundur", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
                OutlinedButton(
                    onClick = { showAddDateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SidebyColors.RosePrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (specialDates.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SidebyColors.GlassBorder)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text("Belum ada tanggal spesial. Tekan '+ Tambah' untuk membuat countdown baru.", fontSize = 12.sp, color = SidebyColors.TextMuted)
                    }
                }
            }
        }

        items(specialDates) { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.emoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SidebyColors.TextDeep)
                        Text("Momen spesial berdua", fontSize = 12.sp, color = SidebyColors.TextMuted)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SidebyColors.SoftBlush
                    ) {
                        Text(
                            text = if (item.daysRemaining == 0L) "Hari ini" else "${item.daysRemaining} hari lagi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SidebyColors.RosePrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 4. MEMORY LOG (CATATAN KENANGAN)
        item {
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kenangan", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
                OutlinedButton(
                    onClick = { showAddMemoryDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Tulis", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SidebyColors.RosePrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (memories.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SidebyColors.GlassBorder)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text("Belum ada kenangan yang dicatat. Tekan '+ Kenangan' untuk menulis cerita indah kalian berdua.", fontSize = 12.sp, color = SidebyColors.TextMuted)
                    }
                }
            }
        }

        items(memories) { mem ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, SidebyColors.GlassBorder),
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(mem.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SidebyColors.TextDeep)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(mem.description, fontSize = 13.sp, color = SidebyColors.TextMuted, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(mem.dateMillis)),
                        fontSize = 11.sp,
                        color = SidebyColors.TextSubtle
                    )
                }
            }
        }
    }

    // Dialog Tambah Kencan Manual
    if (showAddSessionDialog) {
        var placeInput by remember { mutableStateOf("") }
        var durationInput by remember { mutableStateOf("120") }
        AlertDialog(
            onDismissRequest = { showAddSessionDialog = false },
            title = { Text("Catat Kencan / Ketemuan", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = placeInput,
                        onValueChange = { placeInput = it },
                        label = { Text("Lokasi Pertemuan") },
                        supportingText = { Text("Nama tempat kalian bertemu") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text("Durasi Kebersamaan") },
                        supportingText = { Text("Total waktu bersama dalam menit") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val durationMin = durationInput.toLongOrNull() ?: 60L
                        val now = System.currentTimeMillis()
                        val startTime = now - (durationMin * 60 * 1000)
                        val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date(now))
                        val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
                        val startStr = timeFormat.format(Date(startTime))
                        val endStr = timeFormat.format(Date(now))

                        val session = TogetherSession(
                            dateString = dateStr,
                            locationName = placeInput.ifBlank { "Jalan Berdua" },
                            startTimeMillis = startTime,
                            endTimeMillis = now,
                            durationMinutes = durationMin,
                            note = "Mulai $startStr - $endStr ($durationMin menit bersama)"
                        )
                        MobileDataManager.addTogetherSession(context, session)
                        togetherSessions = MobileDataManager.getTogetherSessions(context)
                        showAddSessionDialog = false
                        Toast.makeText(context, "Kencan berhasil dicatat!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddSessionDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog Tambah Tanggal Spesial
    if (showAddDateDialog) {
        var titleInput by remember { mutableStateOf("") }
        var emojiInput by remember { mutableStateOf("🎉") }
        var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)) }

        AlertDialog(
            onDismissRequest = { showAddDateDialog = false },
            title = { Text("Tambah Tanggal Spesial", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Judul Acara / Momen") },
                        supportingText = { Text("Keterangan tanggal spesial kalian") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DatePickerField(
                        label = "Tanggal Acara",
                        selectedMillis = dateMillis,
                        onDateSelected = { dateMillis = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = emojiInput,
                        onValueChange = { emojiInput = it },
                        label = { Text("Ikon Emoji") },
                        supportingText = { Text("Pilih satu emoji perwakilan acara") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            val newItem = SpecialDate(
                                title = titleInput,
                                targetDateMillis = dateMillis,
                                emoji = emojiInput.ifEmpty { "🎉" }
                            )
                            MobileDataManager.addSpecialDate(context, newItem)
                            specialDates = MobileDataManager.getSpecialDates(context)
                            showAddDateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDateDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog Tambah Kenangan
    if (showAddMemoryDialog) {
        var memTitle by remember { mutableStateOf("") }
        var memDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Tulis Kenangan Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = memTitle,
                        onValueChange = { memTitle = it },
                        label = { Text("Judul Kenangan") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = memDesc,
                        onValueChange = { memDesc = it },
                        label = { Text("Cerita Momen Ini") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (memTitle.isNotBlank()) {
                            val newMem = MemoryItem(
                                title = memTitle,
                                description = memDesc,
                                dateMillis = System.currentTimeMillis()
                            )
                            MobileDataManager.addMemory(context, newMem)
                            memories = MobileDataManager.getMemories(context)
                            showAddMemoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddMemoryDialog = false }) { Text("Batal") }
            }
        )
    }
}

// ==========================================
// TAB 4: PENGATURAN (ELEGANT & CLEAN)
// ==========================================
@Composable
fun SettingsScreenTab(
    onResetSetup: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config = MobileDataManager.getRoomConfig(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, SidebyColors.GlassBorder),
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Room", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = config?.roomCode ?: "--",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SidebyColors.RosePrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Panggilan Kamu: ${if (MobileDataManager.isCreator(context)) config?.creatorName else config?.partnerName}",
                    fontSize = 13.sp,
                    color = SidebyColors.TextMuted
                )
                Text(
                    text = "Panggilan Pasangan: ${if (MobileDataManager.isCreator(context)) config?.partnerName else config?.creatorName}",
                    fontSize = 13.sp,
                    color = SidebyColors.TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            config?.let {
                                CloudSyncManager.broadcastRoomConfig(context, it)
                                Toast.makeText(context, "Konfigurasi room disinkronkan kembali!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Sinkronkan ulang", fontWeight = FontWeight.Bold, color = SidebyColors.RosePrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, SidebyColors.GlassBorder),
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Jam tangan Wear OS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Kirim getaran tes ke jam tangan Wear OS kamu via Bluetooth.",
                    fontSize = 12.sp,
                    color = SidebyColors.TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val msg = PokeMessage(PokeType.HEARTBEAT, "Uji Coba")
                            val sent = SyncManager.sendPokeToWatch(context, msg)
                            if (sent) {
                                Toast.makeText(context, "Sinyal getar terkirim ke jam tangan!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Jam tangan belum terhubung via Bluetooth", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SidebyColors.RosePrimary)
                ) {
                    Text("Kirim getaran tes", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UpdateCard()

        Spacer(modifier = Modifier.height(16.dp))

        // Reset Setup
        OutlinedButton(
            onClick = { onResetSetup() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
        ) {
            Text("Keluar dari room", fontWeight = FontWeight.Bold)
        }

        // Versi aplikasi (diambil dari APK yang terpasang, jadi selalu sesuai build)
        val versionLabel = remember {
            try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
                "Versi ${info.versionName} (build $code)"
            } catch (e: Exception) {
                "Versi tidak diketahui"
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = versionLabel,
            fontSize = 12.sp,
            color = SidebyColors.TextSubtle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// Helpers for image conversion with auto EXIF rotation correction
private suspend fun encodeImageUriToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        // 1. Baca metadata EXIF untuk mendeteksi rotasi foto kamera HP (Samsung / Android portrait)
        val rotationDegrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }

        // 2. Decode bitmap dari URI
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return@withContext null

        // 3. Putar bitmap jika posisinya miring (koreksi rotasi portrait / landscape agar selalu tegak lurus)
        val orientedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } else {
            bitmap
        }

        // 4. Ubah ukuran secara proporsional (max dimensi 800px) agar gambar tajam dan file tetap ringan
        val maxDimension = 1280
        val width = orientedBitmap.width
        val height = orientedBitmap.height
        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }

        val targetWidth = if (scale < 1.0f) (width * scale).toInt().coerceAtLeast(1) else width
        val targetHeight = if (scale < 1.0f) (height * scale).toInt().coerceAtLeast(1) else height

        val scaled = if (targetWidth != width || targetHeight != height) {
            val rescaled = Bitmap.createScaledBitmap(orientedBitmap, targetWidth, targetHeight, true)
            if (rescaled != orientedBitmap) orientedBitmap.recycle()
            rescaled
        } else {
            orientedBitmap
        }

        // 5. Kompresi JPEG kualitas 75%
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        scaled.recycle()

        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun decodeBase64ToBitmap(base64: String): ImageBitmap? {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
