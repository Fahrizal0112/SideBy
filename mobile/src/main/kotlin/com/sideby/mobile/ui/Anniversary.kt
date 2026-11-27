package com.sideby.mobile.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sideby.mobile.data.AnniversaryNote
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =====================================================================
// CATATAN ANIVERSARY
// Sehari sebelum aniversary (H-1) muncul pop-up untuk menulis catatan untuk pasangan. Isinya terkunci
// sampai hari H; lalu pasangan membuka kartunya. Semua catatan terpajang per tahun, kartu kalian berdua
// digabung dalam satu bingkai.
// =====================================================================

object AnnivMath {
    private const val DAY = 86_400_000.0

    fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Awal hari aniversary pada tahun [year] (29 Februari jatuh ke 28 Februari di tahun biasa). */
    fun annivDate(configMillis: Long, year: Int): Long {
        val base = Calendar.getInstance().apply { timeInMillis = configMillis }
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, base.get(Calendar.MONTH), 1, 0, 0, 0)
        c.set(Calendar.DAY_OF_MONTH, minOf(base.get(Calendar.DAY_OF_MONTH), c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return c.timeInMillis
    }

    fun baseYear(configMillis: Long): Int = Calendar.getInstance().apply { timeInMillis = configMillis }.get(Calendar.YEAR)

    /** Aniversary yang paling dekat (hari ini ikut dihitung). */
    data class Target(val year: Int, val dateMillis: Long, val daysUntil: Int, val number: Int)

    fun target(configMillis: Long, now: Long): Target? {
        if (configMillis <= 0L) return null
        val today = startOfDay(now)
        var year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        var date = annivDate(configMillis, year)
        if (date < today) {
            year += 1
            date = annivDate(configMillis, year)
        }
        val days = Math.round((date - today) / DAY).toInt()
        return Target(year, date, days, year - baseYear(configMillis))
    }
}

private fun fmtDate(ms: Long): String = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(ms))

private fun yearLabel(number: Int): String = if (number > 0) "Aniversary ke-$number" else "Aniversary"

@Composable
private fun rememberAnnivNotes(): State<List<AnniversaryNote>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(MobileDataManager.getAnnivNotes(context)) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            val v = MobileDataManager.dataVersion()
            if (v != last) {
                last = v
                val fresh = withContext(Dispatchers.IO) { MobileDataManager.getAnnivNotes(context) }
                if (state.value != fresh) state.value = fresh
            }
            delay(800)
        }
    }
    return state
}

/** Kartu catatan: kaca bening bersemburat (mawar = catatanku, ungu = catatan pasangan). */
@Composable
private fun NoteCardFrame(mine: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = (if (mine) SidebyColors.RosePrimary else SidebyColors.Violet).copy(alpha = 0.16f),
        shadowElevation = 6.dp,
        content = content
    )
}

@Composable
private fun NoteText(text: String, big: Boolean = false) {
    Text(
        text = text,
        fontSize = if (big) 19.sp else 16.sp,
        lineHeight = if (big) 28.sp else 23.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        color = SidebyColors.TextDeep
    )
}

// ---------------------------------------------------------------------
// Dialog menulis catatan
// ---------------------------------------------------------------------
@Composable
private fun AnniversaryWriteDialog(
    target: AnnivMath.Target,
    partnerName: String,
    existing: AnniversaryNote?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.80f),
            shadowElevation = 20.dp
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("💌", fontSize = 34.sp, modifier = Modifier.floating(amplitudeDp = 3f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (target.daysUntil == 1) "Besok ${yearLabel(target.number).lowercase()} kalian!" else "Catatan untuk $partnerName",
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = SidebyColors.TextDeep
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tulis catatan untuk $partnerName. Isinya dirahasiakan dan baru bisa dibuka $partnerName pada ${fmtDate(target.dateMillis)}.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = SidebyColors.TextMuted
                )
                Spacer(modifier = Modifier.height(14.dp))
                GlassTextField(
                    value = text,
                    onValueChange = { if (it.length <= AnniversaryNote.MAX_TEXT) text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("Tulis apa yang ingin kamu sampaikan...") },
                    supportingText = { Text("${text.length}/${AnniversaryNote.MAX_TEXT}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    maxLines = 10
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassOutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) { Text("Nanti", fontWeight = FontWeight.Bold) }
                    GlassButton(
                        onClick = {
                            val saved = MobileDataManager.saveMyAnnivNote(context, target.year, text)
                            if (saved == null) {
                                Toast.makeText(context, "Tulis dulu catatannya", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch { CloudSyncManager.flushPendingAnniv(context, wait = true) }
                                Toast.makeText(context, "Catatan tersimpan, terkunci sampai hari aniversary", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1.4f).height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Dialog membuka catatan pasangan (kartu terbalik)
// ---------------------------------------------------------------------
@Composable
private fun NoteRevealDialog(note: AnniversaryNote, partnerName: String, cfgMillis: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var opened by remember(note.year, note.authorId) { mutableStateOf(note.openedAt > 0L) }
    val flip = remember(note.year, note.authorId) { Animatable(if (note.openedAt > 0L) 1f else 0f) }
    LaunchedEffect(opened) {
        flip.animateTo(if (opened) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    val number = note.year - AnnivMath.baseYear(cfgMillis)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.92f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationY = 180f * flip.value
                        cameraDistance = 14f * density.density
                    }
            ) {
                if (flip.value < 0.5f) {
                    // Sisi depan: amplop tertutup.
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(pressedScale = 0.97f) {
                                if (!opened) {
                                    opened = true
                                    MobileDataManager.markPartnerAnnivOpened(context, note.year, note.authorId)
                                    scope.launch { CloudSyncManager.flushPendingAnniv(context, wait = true) }
                                }
                            },
                        shape = RoundedCornerShape(34.dp),
                        color = SidebyColors.RosePrimary,
                        glossy = true,
                        shadowElevation = 18.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 44.dp, horizontal = 24.dp)
                        ) {
                            Text("💌", fontSize = 64.sp, modifier = Modifier.floating(amplitudeDp = 5f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Dari ${note.authorName.ifBlank { partnerName }}",
                                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                            )
                            Text(yearLabel(number), fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Ketuk untuk membuka", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                } else {
                    // Sisi belakang: isi catatan (diputar balik supaya tidak terbaca terbalik).
                    Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(34.dp),
                            color = Color.White.copy(alpha = 0.85f),
                            shadowElevation = 18.dp
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp).padding(24.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = SidebyColors.RosePrimary, modifier = Modifier.size(18.dp).heartbeat())
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${note.authorName.ifBlank { partnerName }} • ${yearLabel(number)}",
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SidebyColors.RosePrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                NoteText(note.text, big = true)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            GlassOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) { Text(if (opened) "Tutup" else "Nanti", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------------
// Pop-up saat aplikasi dibuka
// ---------------------------------------------------------------------
/** Pasang sekali di layar utama: pop-up menulis catatan di H-1, dan pop-up membuka catatan pasangan di hari H. */
@Composable
fun AnniversaryPopups() {
    val context = LocalContext.current
    val notes by rememberAnnivNotes()
    val cfg = remember { MobileDataManager.getRoomConfig(context)?.anniversaryDateMillis ?: 0L }
    val myId = remember { MobileDataManager.getMyDeviceId(context) }
    val partnerName = remember { MobileDataManager.getPartnerStatus(context).name }
    val now = remember { System.currentTimeMillis() }
    val target = remember(cfg) { AnnivMath.target(cfg, now) }
    var writeDismissed by remember { mutableStateOf(false) }
    var revealDismissed by remember { mutableStateOf(false) }

    // H-1 dan aku belum menulis catatan.
    if (target != null && target.daysUntil == 1 && !writeDismissed &&
        notes.none { it.year == target.year && it.authorId == myId }
    ) {
        AnniversaryWriteDialog(target, partnerName, existing = null, onDismiss = { writeDismissed = true })
        return
    }

    // Hari H (atau sesudahnya) dan catatan dari pasangan belum kubuka.
    val toOpen = notes.firstOrNull {
        it.authorId != myId && it.openedAt == 0L && it.text.isNotBlank() &&
            now >= AnnivMath.annivDate(cfg, it.year)
    }
    if (cfg > 0L && toOpen != null && !revealDismissed) {
        NoteRevealDialog(toOpen, partnerName, cfg, onDismiss = { revealDismissed = true })
    }
}

// ---------------------------------------------------------------------
// Bagian di tab Momen: status + pajangan per tahun
// ---------------------------------------------------------------------
@Composable
fun AnniversarySection() {
    val context = LocalContext.current
    val notes by rememberAnnivNotes()
    val cfg = remember { MobileDataManager.getRoomConfig(context)?.anniversaryDateMillis ?: 0L }
    val myId = remember { MobileDataManager.getMyDeviceId(context) }
    val partnerName = remember { MobileDataManager.getPartnerStatus(context).name }
    val now = remember { System.currentTimeMillis() }
    val target = remember(cfg) { AnnivMath.target(cfg, now) }

    var writing by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf<AnniversaryNote?>(null) }
    var viewing by remember { mutableStateOf<AnniversaryNote?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Catatan aniversary", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep)

        if (target == null) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), shadowElevation = 6.dp) {
                Text(
                    "Tanggal jadian belum diisi, jadi catatan aniversary belum bisa dipakai.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = SidebyColors.TextMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
        }

        val myNote = notes.firstOrNull { it.year == target.year && it.authorId == myId }
        val partnerNote = notes.firstOrNull { it.year == target.year && it.authorId != myId }

        // ---- Kartu status aniversary berikutnya ----
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = SidebyColors.TintRose,
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (target.daysUntil) {
                        0 -> "Hari ini ${yearLabel(target.number).lowercase()} kalian!"
                        1 -> "Besok ${yearLabel(target.number).lowercase()} kalian"
                        else -> "${yearLabel(target.number)} dalam ${target.daysUntil} hari"
                    },
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SidebyColors.TextDeep
                )
                Text(fmtDate(target.dateMillis), fontSize = 13.sp, color = SidebyColors.TextMuted)

                // Catatanku
                if (myNote != null) {
                    Text(
                        when {
                            myNote.openedAt > 0L -> "Catatanmu sudah dibuka $partnerName (${fmtDate(myNote.openedAt)})."
                            else -> "Catatanmu tersimpan dan dirahasiakan. Kamu masih bisa mengubahnya sampai dibuka."
                        },
                        fontSize = 13.sp, lineHeight = 19.sp, color = SidebyColors.TextDeep
                    )
                    if (myNote.openedAt == 0L) {
                        GlassOutlinedButton(
                            onClick = { writing = true },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(23.dp)
                        ) { Text("Ubah catatanku", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    GlassButton(
                        onClick = { writing = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tulis catatan untuk $partnerName", fontWeight = FontWeight.Bold)
                    }
                }

                // Catatan pasangan (terkunci sampai hari H)
                Text(
                    when {
                        partnerNote == null -> "$partnerName belum menulis catatan."
                        target.daysUntil > 0 -> "$partnerName sudah menulis catatan untukmu. Terbuka pada ${fmtDate(target.dateMillis)}."
                        partnerNote.openedAt == 0L -> "$partnerName menulis catatan untukmu. Buka sekarang!"
                        else -> "Kamu sudah membuka catatan dari $partnerName."
                    },
                    fontSize = 13.sp, lineHeight = 19.sp, color = SidebyColors.TextDeep
                )
                if (partnerNote != null && target.daysUntil == 0 && partnerNote.openedAt == 0L) {
                    GlassButton(
                        onClick = { revealing = partnerNote },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) { Text("💌  Buka catatan dari $partnerName", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // ---- Pajangan ----
        Text("Pajangan catatan", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep, modifier = Modifier.padding(top = 6.dp))
        val years = notes.map { it.year }.distinct().sortedDescending()
        if (years.isEmpty()) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), shadowElevation = 6.dp) {
                Text(
                    "Belum ada catatan. Setelah kalian saling menulis, kartunya terpajang di sini setiap tahun, kartu kalian berdua dalam satu bingkai.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = SidebyColors.TextMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        years.forEach { year ->
            val mine = notes.firstOrNull { it.year == year && it.authorId == myId }
            val theirs = notes.firstOrNull { it.year == year && it.authorId != myId }
            val dateMillis = AnnivMath.annivDate(cfg, year)
            val reached = now >= dateMillis
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = Color.White.copy(alpha = 0.40f),
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = SidebyColors.RosePrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${yearLabel(year - AnnivMath.baseYear(cfg))} • ${fmtDate(dateMillis)}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SidebyColors.TextDeep
                        )
                    }
                    // Kartuku
                    if (mine != null) {
                        NoteCardFrame(mine = true, modifier = Modifier.bounceClick(0.98f) { viewing = mine }) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Dariku", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SidebyColors.RosePrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                NoteText(mine.text)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    when {
                                        mine.openedAt > 0L -> "Dibuka $partnerName, ${fmtDate(mine.openedAt)}"
                                        reached -> "Belum dibuka $partnerName"
                                        else -> "Rahasia sampai ${fmtDate(dateMillis)}"
                                    },
                                    fontSize = 11.sp, color = SidebyColors.TextMuted
                                )
                            }
                        }
                    }
                    // Kartu pasangan
                    if (theirs != null) {
                        val open = theirs.openedAt > 0L
                        NoteCardFrame(
                            mine = false,
                            modifier = Modifier.bounceClick(0.98f) {
                                when {
                                    open -> viewing = theirs
                                    reached -> revealing = theirs
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Dari ${theirs.authorName.ifBlank { partnerName }}",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SidebyColors.Violet
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (open) {
                                    NoteText(theirs.text)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = SidebyColors.TextMuted, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (reached) "Ketuk untuk membuka" else "Terkunci sampai ${fmtDate(dateMillis)}",
                                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SidebyColors.TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    } else if (reached) {
                        Text("$partnerName tidak menulis catatan tahun ini.", fontSize = 12.sp, color = SidebyColors.TextSubtle)
                    }
                }
            }
        }
    }

    if (writing && target != null) {
        AnniversaryWriteDialog(target, partnerName, existing = notes.firstOrNull { it.year == target.year && it.authorId == myId }, onDismiss = { writing = false })
    }
    revealing?.let { NoteRevealDialog(it, partnerName, cfg, onDismiss = { revealing = null }) }
    viewing?.let { n ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 20.dp
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp).padding(24.dp)) {
                    Text(
                        if (n.authorId == myId) "Catatanku" else "Dari ${n.authorName.ifBlank { partnerName }}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (n.authorId == myId) SidebyColors.RosePrimary else SidebyColors.Violet
                    )
                    Text(
                        "${yearLabel(n.year - AnnivMath.baseYear(cfg))} • ${fmtDate(AnnivMath.annivDate(cfg, n.year))}",
                        fontSize = 12.sp, color = SidebyColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NoteText(n.text, big = true)
                    Spacer(modifier = Modifier.height(18.dp))
                    GlassOutlinedButton(
                        onClick = { viewing = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Tutup", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
