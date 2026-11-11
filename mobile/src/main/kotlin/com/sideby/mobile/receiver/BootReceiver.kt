package com.sideby.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.service.SidebyBackgroundSyncService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val roomCode = MobileDataManager.getRoomCode(context)
            if (roomCode.isNotBlank() && roomCode != "BELUM_DISET") {
                SidebyBackgroundSyncService.start(context)
            }
        }
    }
}
