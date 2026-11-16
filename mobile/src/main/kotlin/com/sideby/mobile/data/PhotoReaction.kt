package com.sideby.mobile.data

import kotlinx.serialization.Serializable

/**
 * Reaksi satu orang pada satu foto. Tiap orang hanya punya satu reaksi per foto; yang paling baru menang.
 * [emoji] kosong berarti reaksinya dicabut.
 */
@Serializable
data class PhotoReaction(
    val photoId: String,
    val senderId: String,
    val senderName: String = "",
    val emoji: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    /** Hanya dipakai lokal: true = reaksi saya yang belum berhasil terkirim. */
    val pending: Boolean = false
) {
    companion object {
        val CHOICES = listOf("❤️", "👍", "😂", "😮", "😢")
    }
}
