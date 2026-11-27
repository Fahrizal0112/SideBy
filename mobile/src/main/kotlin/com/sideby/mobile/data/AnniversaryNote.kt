package com.sideby.mobile.data

import kotlinx.serialization.Serializable

/**
 * Catatan aniversary dari satu orang untuk pasangannya, per tahun. Satu catatan per (tahun, penulis).
 * Isinya baru bisa dibuka pasangan pada hari aniversary; setelah dibuka, penulis diberi tahu (openedAt).
 */
@Serializable
data class AnniversaryNote(
    val year: Int,
    val authorId: String,
    val authorName: String = "",
    val text: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    /** Waktu penerima (pasangan si penulis) membuka catatan ini; 0 = belum dibuka. */
    val openedAt: Long = 0L,
    /** Hanya lokal: catatanku yang belum berhasil terkirim. */
    val pending: Boolean = false,
    /** Hanya lokal: aku sudah membuka catatan pasangan, tetapi tanda "sudah dibuka" belum terkirim. */
    val receiptPending: Boolean = false
) {
    companion object {
        const val MAX_TEXT = 500
    }
}
