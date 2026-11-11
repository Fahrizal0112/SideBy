package com.sideby.wear.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sideby.shared.constants.DataLayerConstants
import com.sideby.shared.model.PartnerStatus
import com.sideby.shared.model.PokeMessage
import com.sideby.wear.data.WearDataManager
import kotlinx.serialization.json.Json

class WearDataListenerService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            DataLayerConstants.PATH_POKE -> {
                try {
                    val rawJson = String(messageEvent.data, Charsets.UTF_8)
                    val poke = json.decodeFromString<PokeMessage>(rawJson)
                    vibrateForPoke(poke)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            DataLayerConstants.PATH_PARTNER_STATUS -> {
                try {
                    val rawJson = String(messageEvent.data, Charsets.UTF_8)
                    val status = json.decodeFromString<PartnerStatus>(rawJson)
                    WearDataManager.savePartnerStatus(applicationContext, status)

                    // Minta Wear OS untuk merefresh komplikasi di Watch Face
                    val requester = ComplicationDataSourceUpdateRequester.create(
                        applicationContext,
                        ComponentName(applicationContext, PartnerComplicationService::class.java)
                    )
                    requester.requestUpdateAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun vibrateForPoke(poke: PokeMessage) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            val pattern = poke.type.vibrationPattern
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        }
    }
}
