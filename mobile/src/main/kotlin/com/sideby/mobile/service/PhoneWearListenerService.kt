package com.sideby.mobile.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sideby.shared.constants.DataLayerConstants
import com.sideby.shared.model.PokeMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class PhoneWearListenerService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == DataLayerConstants.PATH_POKE) {
            // Fitur sentuhan haptic telah ditiadakan sesuai permintaan
        }
    }

    private fun vibratePhone(poke: PokeMessage) {
        // Fitur sentuhan haptic telah ditiadakan
    }
}
