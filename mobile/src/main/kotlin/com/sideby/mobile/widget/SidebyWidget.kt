package com.sideby.mobile.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mendeteksi apakah widget sedang dipasang di Layar Kunci (Keyguard / Lockscreen)
 */
private fun isKeyguardWidget(context: Context, id: GlanceId): Boolean {
    return try {
        val appWidgetId = (id as? AppWidgetId)?.appWidgetId ?: return false
        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
        category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    } catch (_: Exception) {
        false
    }
}

/**
 * WIDGET 1: Status, Baterai & Jarak Real-Time (Home Screen & Auto Lock Screen)
 */
class SidebyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val partner = MobileDataManager.getPartnerStatus(context)
        val isKeyguard = isKeyguardWidget(context, id)

        provideContent {
            if (isKeyguard) {
                // Tampilan Khusus Lock Screen: Background Clear / Transparan & Hanya Jarak
                val (icon, distText) = getDistanceDisplay(partner)
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color.Transparent))
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = icon,
                        style = TextStyle(fontSize = 16.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = distText,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color.White)
                        ),
                        maxLines = 1
                    )
                }
            } else {
                // Tampilan Standar Home Screen (Card Lengkap)
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFFFFF0F5)))
                        .cornerRadius(18.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Baris Atas: Nama & Jarak / Status Bersama
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sideby • ${partner.name}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color(0xFFE91E63))
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )

                        val distanceBadgeText = when {
                            !partner.isConnected -> "Belum terhubung"
                            partner.location.isNearby -> "❤️ Sedang Bersama"
                            partner.location.distanceMeters != null && partner.location.distanceMeters!! in 1f..10_000_000f -> "📍 ${partner.location.summary}"
                            else -> "Menunggu lokasi..."
                        }

                        Box(
                            modifier = GlanceModifier
                                .background(
                                    ColorProvider(
                                        if (partner.isConnected && partner.location.isNearby) Color(0xFFFFD1DC) else Color(0xFFFFEEF2)
                                    )
                                )
                                .cornerRadius(8.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = distanceBadgeText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color(0xFFE91E63))
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Baris Bawah: Emoji Mood & Status Baterai
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val moodEmoji = if (partner.isConnected) partner.mood.emoji else "⏳"
                        val moodText = if (partner.isConnected) partner.mood.label else "Menunggu Pasangan Bergabung"
                        val batteryText = if (partner.isConnected) {
                            "🔋 Baterai HP ${partner.phoneBattery.displayString}"
                        } else {
                            "Bagikan kode room ke pasangan"
                        }

                        Text(
                            text = moodEmoji,
                            style = TextStyle(fontSize = 26.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Column {
                            Text(
                                text = moodText,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(Color(0xFF2B2B2B))
                                )
                            )
                            Text(
                                text = batteryText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = ColorProvider(Color(0xFF666666))
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class SidebyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SidebyWidget()
}

/**
 * WIDGET 2: Foto Harian / Snapshot Locket Pasangan (Home Screen Widget)
 * Selalu menampilkan foto paling terakhir dari galeri berdua tanpa pernah hilang saat refresh.
 */
class SidebySnapshotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val partner = MobileDataManager.getPartnerStatus(context)
        val gallery = MobileDataManager.getSnapshotGallery(context)
        val myStatus = MobileDataManager.getMyStatus(context)

        val allCandidates = (gallery + listOfNotNull(partner.snapshot, myStatus.snapshot))
            .distinctBy { it.id }
            .filter { !it.imageBase64.isNullOrBlank() || !it.imageUrl.isNullOrBlank() }

        // Pilih foto dengan timestamp paling terbaru secara deterministik
        val latestWithImage = allCandidates.filter { !it.imageBase64.isNullOrBlank() }.maxByOrNull { it.timestamp }
        val snapshot = latestWithImage ?: allCandidates.maxByOrNull { it.timestamp }

        val imageBase64 = snapshot?.imageBase64
            ?: (snapshot?.let { snap -> gallery.find { it.id == snap.id && !it.imageBase64.isNullOrBlank() }?.imageBase64 })
        val bitmap = imageBase64?.let { decodeBase64ToBitmap(it) }

        val headerTitle = if (partner.isConnected && snapshot == partner.snapshot) {
            "📸 Locket • ${partner.name}"
        } else {
            "📸 Locket Berdua"
        }

        val timeText = if ((snapshot?.timestamp ?: 0L) > 0L) {
            val sdf = SimpleDateFormat("HH:mm", Locale("id", "ID"))
            " • ${sdf.format(Date(snapshot!!.timestamp))}"
        } else ""
        val captionDisplay = (snapshot?.caption?.ifEmpty { "Momen hari ini ❤️" } ?: "Momen hari ini ❤️") + timeText

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFFFFF0F5)))
                    .cornerRadius(20.dp)
                    .padding(10.dp)
                    .clickable(
                        // Ketuk foto locket = buka aplikasi langsung ke foto itu, layar penuh dan bisa di-zoom.
                        actionStartActivity(
                            Intent(context, MainActivity::class.java).apply {
                                snapshot?.id?.let { putExtra(MainActivity.EXTRA_OPEN_PHOTO_ID, it) }
                            }
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Locket
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerTitle,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color(0xFFE91E63))
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = partner.mood.emoji,
                        style = TextStyle(fontSize = 14.sp)
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Foto Terakhir Pasangan
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Foto Pasangan",
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .cornerRadius(14.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = captionDisplay,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(Color(0xFF333333))
                        ),
                        maxLines = 1
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(ColorProvider(Color.White))
                            .cornerRadius(14.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💌",
                                style = TextStyle(fontSize = 24.sp)
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "Belum ada foto hari ini",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = ColorProvider(Color(0xFF888888))
                                )
                            )
                            Text(
                                text = "Ketuk untuk buka Sideby",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = ColorProvider(Color(0xFFAAAAAA))
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

class SidebySnapshotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SidebySnapshotWidget()
}

/**
 * WIDGET 3: Khusus Layar Kunci Samsung (Samsung One UI Lock Screen & AOD)
 * Background clear / transparan persis desain modern Samsung One UI, hanya menampilkan jarak dengan pasangan.
 */
class SidebyLockDistanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val partner = MobileDataManager.getPartnerStatus(context)
        val (icon, distText) = getDistanceDisplay(partner)

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color.Transparent))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = icon,
                    style = TextStyle(fontSize = 15.sp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = distText,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White)
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

class SidebyLockDistanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SidebyLockDistanceWidget()
}

/**
 * Format string jarak yang bersih dan presisi untuk Layar Kunci
 */
private fun getDistanceDisplay(partner: com.sideby.shared.model.PartnerStatus): Pair<String, String> {
    return when {
        !partner.isConnected -> "📍" to "Belum terhubung"
        partner.location.isNearby -> "❤️" to "Bersama"
        partner.location.distanceMeters != null && partner.location.distanceMeters!! in 1f..10_000_000f -> {
            val dist = partner.location.distanceMeters!!
            val text = if (dist < 1000f) {
                "${dist.toInt()} m"
            } else {
                String.format(Locale.US, "%.1f km", dist / 1000f)
            }
            "📍" to text
        }
        else -> "📍" to "Menunggu lokasi"
    }
}

object SidebyWidgetHelper {
    suspend fun updateAllWidgets(context: Context) {
        try {
            SidebyWidget().updateAll(context)
            SidebySnapshotWidget().updateAll(context)
            SidebyLockDistanceWidget().updateAll(context)
        } catch (_: Exception) {}
    }
}
