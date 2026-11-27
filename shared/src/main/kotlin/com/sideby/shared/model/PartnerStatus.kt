package com.sideby.shared.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID
import kotlin.math.ceil

@Serializable
data class BatteryStatus(
    val percentage: Int = 0,
    val isCharging: Boolean = false
) {
    val displayString: String
        get() = if (percentage <= 0) "--" else "$percentage%${if (isCharging) " ⚡" else ""}"
}

@Serializable
enum class Mood(val emoji: String, val label: String) {
    MISSING_YOU("❤️", "Kangen kamu"),
    WORKING("💼", "Lagi kerja / sibuk"),
    ON_THE_WAY("🛵", "Di jalan (OTW)"),
    COFFEE("☕", "Lagi ngopi / santai"),
    EATING("🍜", "Lagi makan"),
    RESTING("😴", "Istirahat / tidur"),
    HAPPY("✨", "Lagi happy"),
    SICK("🤒", "Kurang enak badan"),
    CALL_ME("📞", "Telepon aku ya");

    companion object {
        fun fromName(name: String?): Mood = entries.firstOrNull { it.name == name } ?: MISSING_YOU
    }
}

@Serializable
data class LocationStatus(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceMeters: Float? = null,
    val isNearby: Boolean = false,
    val summary: String = "Menunggu lokasi pasangan...",
    val locationName: String = ""
) {
    companion object {
        fun calculate(lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?): LocationStatus {
            if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
                return LocationStatus(null, null, null, false, "Menunggu lokasi pasangan...", "")
            }
            // Cegah koordinat 0.0 (Null Island / koordinat kosong) yang menyebabkan jarak 11.000+ km
            if ((lat1 == 0.0 && lon1 == 0.0) || (lat2 == 0.0 && lon2 == 0.0) ||
                (Math.abs(lat1) < 0.001 && Math.abs(lon1) < 0.001) ||
                (Math.abs(lat2) < 0.001 && Math.abs(lon2) < 0.001)
            ) {
                return LocationStatus(null, null, null, false, "Menunggu lokasi pasangan...", "")
            }
            val r = 6371000.0 // Radius bumi dalam meter
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            val dist = (r * c).toFloat()
            val isNearby = dist <= 60f
            val summary = when {
                isNearby -> "Together ❤️"
                dist < 1000f -> "${dist.toInt()} m dari kamu"
                else -> String.format("%.1f km dari kamu", dist / 1000f)
            }
            return LocationStatus(lat2, lon2, dist, isNearby, summary, "")
        }
    }
}

@Serializable
data class DailySnapshot(
    val id: String = UUID.randomUUID().toString(),
    val imageBase64: String? = null,
    val imageUrl: String? = null,
    val caption: String = "",
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PartnerStatus(
    val id: String = "",
    val name: String = "Pasangan",
    val phoneBattery: BatteryStatus = BatteryStatus(0, false),
    val watchBattery: BatteryStatus? = null,
    val mood: Mood = Mood.MISSING_YOU,
    val customNote: String = "",
    val location: LocationStatus = LocationStatus(null, null, null, false, "Menunggu lokasi pasangan..."),
    val snapshot: DailySnapshot? = null,
    val isConnected: Boolean = false,
    val lastUpdatedMillis: Long = 0L
)

@Serializable
data class CoupleRoomConfig(
    val roomCode: String,
    val creatorName: String,          // Nama pembuat room (panggilan pacar ke kamu)
    val partnerName: String,          // Nama pacar (panggilan kamu ke pacar)
    val anniversaryDateMillis: Long,  // Tanggal jadian
    val myBirthdayMillis: Long,       // Tanggal lahir pembuat
    val partnerBirthdayMillis: Long,  // Tanggal lahir pasangan
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val dateMillis: Long,
    val imageBase64: String? = null
)

@Serializable
data class TogetherSession(
    val id: String = UUID.randomUUID().toString(),
    val dateString: String,
    val locationName: String = "Jalan Berdua",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Long,
    val note: String = ""
)

@Serializable
data class SpecialDate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDateMillis: Long,
    val emoji: String = "🎉",
    val isRecurringYearly: Boolean = true
) {
    /**
     * Sisa hari menurut kalender: besok = 1, hari ini = 0. Dihitung dari awal hari ke awal hari,
     * jadi jam saat ini tidak membuat hasilnya lebih satu hari.
     */
    val daysRemaining: Long
        get() {
            fun startOfDay(millis: Long): Calendar = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dayMillis = 24.0 * 60 * 60 * 1000
            val today = startOfDay(System.currentTimeMillis())

            if (!isRecurringYearly) {
                val diff = startOfDay(targetDateMillis).timeInMillis - today.timeInMillis
                return if (diff > 0) Math.round(diff / dayMillis) else 0L
            }

            // Berulang tiap tahun (seperti Hari Jadian / Ulang Tahun): cari tanggal terdekat yang belum lewat.
            val target = Calendar.getInstance().apply { timeInMillis = targetDateMillis }
            fun occurrence(year: Int): Long {
                val c = Calendar.getInstance()
                c.clear()
                c.set(year, target.get(Calendar.MONTH), 1, 0, 0, 0)
                // 29 Februari jatuh ke 28 Februari di tahun biasa.
                c.set(Calendar.DAY_OF_MONTH, minOf(target.get(Calendar.DAY_OF_MONTH), c.getActualMaximum(Calendar.DAY_OF_MONTH)))
                return c.timeInMillis
            }
            var next = occurrence(today.get(Calendar.YEAR))
            if (next < today.timeInMillis) next = occurrence(today.get(Calendar.YEAR) + 1)
            return Math.round((next - today.timeInMillis) / dayMillis)
        }
}
