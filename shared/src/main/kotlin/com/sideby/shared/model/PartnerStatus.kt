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
    val daysRemaining: Long
        get() {
            val now = System.currentTimeMillis()
            val nowCal = Calendar.getInstance().apply { timeInMillis = now }
            val targetCal = Calendar.getInstance().apply { timeInMillis = targetDateMillis }

            if (!isRecurringYearly) {
                val diff = targetDateMillis - now
                return if (diff > 0) ceil(diff.toDouble() / (1000 * 60 * 60 * 24)).toLong() else 0
            }

            // Jika berulang tiap tahun (seperti Hari Jadian / Ulang Tahun)
            val nextCal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.MONTH, targetCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, targetCal.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }

            // Jika hari ulang tahun / jadian tahun ini sudah lewat
            if (nextCal.timeInMillis < now) {
                nextCal.add(Calendar.YEAR, 1)
            }

            // Cek apakah jatuh pada hari yang sama (Hari ini)
            val isToday = nowCal.get(Calendar.YEAR) == nextCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == nextCal.get(Calendar.DAY_OF_YEAR)
            if (isToday) return 0L

            val diff = nextCal.timeInMillis - now
            return if (diff > 0) ceil(diff.toDouble() / (1000 * 60 * 60 * 24)).toLong() else 0L
        }
}
