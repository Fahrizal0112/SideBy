package com.sideby.shared.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class PokeType(val emoji: String, val title: String, val description: String) {
    HEARTBEAT("❤️", "Detak Jantung", "Denyut ritmis denyut nadi cinta"),
    HUG("🫂", "Peluk Jauh", "Getaran hangat dan panjang di pergelangan"),
    KISS("💋", "Kecup Manis", "Tiga getaran lembut"),
    NUDGE("👉", "Colek", "Dua ketukan singkat"),
    DRINK_WATER("💧", "Ingat Minum", "Ketukan ritmis pengingat minum"),
    MISS_YOU("💌", "Kangen Banget", "Pola getar denyut lembut berulang");

    val vibrationPattern: LongArray
        get() = when (this) {
            HEARTBEAT -> longArrayOf(0, 120, 100, 240, 300, 120, 100, 240)
            HUG -> longArrayOf(0, 500, 150, 500, 150, 700)
            KISS -> longArrayOf(0, 100, 80, 100, 80, 140)
            NUDGE -> longArrayOf(0, 80, 80, 120)
            DRINK_WATER -> longArrayOf(0, 100, 100, 100, 100, 100)
            MISS_YOU -> longArrayOf(0, 200, 100, 200, 200, 400)
        }
}

@Serializable
data class PokeMessage(
    val type: PokeType,
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString()
)
