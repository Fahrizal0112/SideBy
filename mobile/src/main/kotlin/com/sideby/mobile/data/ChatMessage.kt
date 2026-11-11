package com.sideby.mobile.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Satu pesan chat. Bisa berdiri sendiri, atau menjadi komentar / balasan untuk sebuah foto (photoId).
 * Teks dibatasi pendek supaya pesan relay tetap di bawah batas ukuran (4 KB).
 */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String = "",
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Id foto yang sedang dibalas / dikomentari (null = chat biasa). */
    val photoId: String? = null,
    /** Hanya dipakai lokal: true = pesan saya yang belum berhasil terkirim. */
    val pending: Boolean = false
) {
    companion object {
        const val MAX_TEXT_LENGTH = 400
    }
}
