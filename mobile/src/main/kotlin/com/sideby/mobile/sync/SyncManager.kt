package com.sideby.mobile.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.android.gms.wearable.Wearable
import com.sideby.shared.constants.DataLayerConstants
import com.sideby.shared.model.BatteryStatus
import com.sideby.shared.model.PartnerStatus
import com.sideby.shared.model.PokeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SyncManager {

    private val json = Json { ignoreUnknownKeys = true }

    fun readCurrentPhoneBattery(context: Context): BatteryStatus {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        if (intent == null) return BatteryStatus(-1, false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Satu-satunya cara membaca baterai (UI & service memakai fungsi ini),
        // supaya nilainya tidak selisih 1% antar pembaca dan saling menimpa.
        val percentage = if (level >= 0 && scale > 0) {
            Math.round(level * 100f / scale)
        } else {
            -1
        }

        return BatteryStatus(percentage.coerceIn(-1, 100), isCharging)
    }

    /**
     * Baca baterai lalu simpan secara atomik; kirim ke pasangan hanya bila benar-benar berubah.
     * Hanya menyentuh field baterai, catatan / mood / lokasi tidak ikut tertimpa.
     */
    suspend fun syncMyBattery(context: Context) {
        val current = readCurrentPhoneBattery(context)
        if (current.percentage < 0) return
        val change = com.sideby.mobile.data.MobileDataManager.updateMyStatus(context) {
            if (it.phoneBattery == current) it else it.copy(phoneBattery = current)
        }
        if (change.changed) {
            CloudSyncManager.sendMyStatus(context)
            com.sideby.mobile.widget.SidebyWidgetHelper.updateAllWidgets(context)
        }
    }

    suspend fun sendPartnerStatusToWatch(context: Context, status: PartnerStatus): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                val messageClient = Wearable.getMessageClient(context)
                val payload = json.encodeToString(status).toByteArray(Charsets.UTF_8)

                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, DataLayerConstants.PATH_PARTNER_STATUS, payload).await()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    suspend fun sendPokeToWatch(context: Context, poke: PokeMessage): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                val messageClient = Wearable.getMessageClient(context)
                val payload = json.encodeToString(poke).toByteArray(Charsets.UTF_8)

                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, DataLayerConstants.PATH_POKE, payload).await()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
