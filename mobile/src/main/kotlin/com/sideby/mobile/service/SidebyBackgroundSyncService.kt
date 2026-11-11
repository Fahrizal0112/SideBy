package com.sideby.mobile.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.sync.CloudSyncManager
import com.sideby.mobile.sync.SyncManager
import com.sideby.mobile.util.LocationHelper
import com.sideby.mobile.util.NotificationHelper
import com.sideby.mobile.widget.SidebyWidgetHelper
import com.sideby.shared.model.BatteryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Background Service untuk memastikan:
 * 1. Update status pasangan (baterai, mood, jarak) selalu diterima secara real-time di background
 * 2. Notifikasi heads-up langsung muncul saat pasangan mengirim foto / locket
 * 3. Jarak dan kedekatan (< 60m) terus dipantau secara otomatis
 * 4. Widget Homescreen & Lockscreen selalu ter-update otomatis
 */
class SidebyBackgroundSyncService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                serviceScope.launch {
                    checkAndBroadcastBattery()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {}
        startSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    private fun startAsForeground() {
        val partner = MobileDataManager.getPartnerStatus(this)
        val partnerName = if (partner.isConnected) partner.name else "Pasangan"
        val notification = NotificationHelper.getForegroundNotification(this, partnerName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
            }
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun startSyncLoop() {
        // Langganan langsung ke tiap relay: pesan chat tiba saat itu juga, tidak menunggu putaran polling.
        CloudSyncManager.relayServers.forEach { server ->
            serviceScope.launch {
                try {
                    CloudSyncManager.runRelayStream(applicationContext, server)
                } catch (_: Exception) {}
            }
        }
        serviceScope.launch {
            while (isActive) {
                try {
                    // 1. Ambil update terbaru dari pasangan sebagai jaring pengaman (jalan terpisah supaya
                    //    server yang lambat tidak menunda langkah-langkah lain di putaran ini).
                    serviceScope.launch { CloudSyncManager.pollPartnerUpdates(applicationContext) }

                    // 1b. Coba kirim ulang foto yang tertunda (non-blocking agar polling tidak tertahan)
                    if (MobileDataManager.getPendingSnapshotIds(applicationContext).isNotEmpty()) {
                        serviceScope.launch { CloudSyncManager.flushPendingSnapshots(applicationContext, wait = false) }
                    }

                    if (MobileDataManager.getPendingChatMessages(applicationContext).isNotEmpty()) {
                        serviceScope.launch { CloudSyncManager.flushPendingChats(applicationContext, wait = false) }
                    }

                    // 2. Baca baterai HP lokal dan kirim ke pasangan jika berubah
                    checkAndBroadcastBattery()

                    // 3. Pantau koordinat GPS secara otomatis jika ada izin
                    if (LocationHelper.hasLocationPermission(applicationContext)) {
                        LocationHelper.requestCurrentLocation(applicationContext) { lat, lon ->
                            serviceScope.launch {
                                LocationHelper.syncLocationAndProximity(applicationContext, lat, lon)
                                SidebyWidgetHelper.updateAllWidgets(applicationContext)
                            }
                        }
                    }

                    // 4. Perbarui widget layar kunci & beranda
                    SidebyWidgetHelper.updateAllWidgets(applicationContext)

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Polling cepat di background setiap 6 detik
                delay(6000L)
            }
        }
    }

    private suspend fun checkAndBroadcastBattery() {
        try {
            SyncManager.syncMyBattery(applicationContext)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, SidebyBackgroundSyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SidebyBackgroundSyncService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
