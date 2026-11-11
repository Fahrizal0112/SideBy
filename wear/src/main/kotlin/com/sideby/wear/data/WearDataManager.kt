package com.sideby.wear.data

import android.content.Context
import android.content.SharedPreferences
import com.sideby.shared.model.PartnerStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WearDataManager {
    private const val PREFS_NAME = "sideby_wear_prefs"
    private const val KEY_PARTNER_STATUS = "key_partner_status"

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPartnerStatus(context: Context): PartnerStatus {
        val raw = prefs(context).getString(KEY_PARTNER_STATUS, null) ?: return PartnerStatus()
        return try {
            json.decodeFromString<PartnerStatus>(raw)
        } catch (e: Exception) {
            PartnerStatus()
        }
    }

    fun savePartnerStatus(context: Context, status: PartnerStatus) {
        val raw = json.encodeToString(status)
        prefs(context).edit().putString(KEY_PARTNER_STATUS, raw).apply()
    }
}
