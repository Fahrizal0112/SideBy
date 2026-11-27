package com.sideby.mobile.data

import kotlinx.serialization.Serializable

/** Perubahan nama dari satu HP: nama baru si pengirim, dan nama baru untuk penerimanya. */
@Serializable
data class NamesUpdate(
    val senderName: String,
    val receiverName: String,
    val updatedAt: Long
)
