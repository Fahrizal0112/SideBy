package com.sideby.mobile.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.sync.CloudSyncManager
import com.sideby.mobile.sync.SyncManager
import com.sideby.shared.model.LocationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(context: Context, onLocationObtained: (Double, Double) -> Unit) {
        if (!hasLocationPermission(context)) return

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationObtained(location.latitude, location.longitude)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            onLocationObtained(lastLoc.latitude, lastLoc.longitude)
                        }
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun startContinuousLocationUpdates(
        context: Context,
        onLocationUpdated: (Double, Double) -> Unit
    ): LocationCallback? {
        if (!hasLocationPermission(context)) return null
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(3f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    onLocationUpdated(loc.latitude, loc.longitude)
                }
            }

            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            callback
        } catch (e: Exception) {
            null
        }
    }

    fun stopLocationUpdates(context: Context, callback: LocationCallback?) {
        if (callback == null) return
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.removeLocationUpdates(callback)
        } catch (_: Exception) {}
    }

    /**
     * Memperbarui lokasi GPS saya, menghitung jarak real-time dengan pasangan,
     * dan otomatis memicu notifikasi & log jika terdeteksi bersama (< 60m).
     */
    // GPS bisa datang dari beberapa sumber sekaligus (UI, service, loop). Diserialkan agar tidak
    // saling menimpa, dan pengiriman ke pasangan dibatasi supaya relay tidak dibanjiri.
    private val locationMutex = kotlinx.coroutines.sync.Mutex()
    private var lastSentLat = 0.0
    private var lastSentLon = 0.0
    private var lastSentAt = 0L

    suspend fun syncLocationAndProximity(context: Context, myLat: Double, myLon: Double): LocationStatus = withContext(Dispatchers.IO) {
        locationMutex.withLock {
            // Geocoder lambat; lakukan sebelum membaca/menulis status agar tidak memakai data basi.
            val locationName = getAreaName(context, myLat, myLon)

            var calculatedStatus = LocationStatus(myLat, myLon, null, false, "Menunggu koordinat pasangan...", locationName)

            // 1. Perbarui jarak di sisi pasangan secara atomik (hanya field lokasi)
            val partnerChange = MobileDataManager.updatePartnerStatus(context) { partner ->
                val pLat = partner.location.latitude
                val pLon = partner.location.longitude
                val calc = if (partner.isConnected && pLat != null && pLon != null &&
                    Math.abs(pLat) > 0.001 && Math.abs(pLon) > 0.001 &&
                    Math.abs(myLat) > 0.001 && Math.abs(myLon) > 0.001
                ) {
                    LocationStatus.calculate(myLat, myLon, pLat, pLon)
                } else {
                    val note = if (!partner.isConnected) "Belum terhubung" else "Menunggu koordinat pasangan..."
                    LocationStatus(myLat, myLon, null, false, note, locationName)
                }
                calculatedStatus = calc
                partner.copy(location = calc.copy(locationName = locationName))
            }
            val wasNearby = partnerChange.before.location.isNearby
            val isNowNearby = calculatedStatus.isNearby

            // 2. Perbarui lokasi saya secara atomik (hanya field lokasi)
            val myChange = MobileDataManager.updateMyStatus(context) {
                it.copy(location = LocationStatus(myLat, myLon, calculatedStatus.distanceMeters, isNowNearby, calculatedStatus.summary, locationName))
            }

            if (isNowNearby && !wasNearby) {
                withContext(Dispatchers.Main) {
                    NotificationHelper.showTogetherNotification(context, partnerChange.after.name)
                }
                MobileDataManager.processTogetherProximity(context, true, locationName)
            } else if (!isNowNearby && wasNearby) {
                MobileDataManager.processTogetherProximity(context, false, locationName)
            }

            // 3. Kirim ke pasangan hanya jika posisi bergeser > 15 m atau sudah > 60 detik
            val now = System.currentTimeMillis()
            val moved = FloatArray(1)
            android.location.Location.distanceBetween(lastSentLat, lastSentLon, myLat, myLon, moved)
            if (myChange.changed && (moved[0] > 15f || now - lastSentAt > 60_000L || isNowNearby != wasNearby)) {
                lastSentLat = myLat; lastSentLon = myLon; lastSentAt = now
                CloudSyncManager.sendMyStatus(context)
            }
            if (partnerChange.changed) {
                SyncManager.sendPartnerStatusToWatch(context, partnerChange.after)
                com.sideby.mobile.widget.SidebyWidgetHelper.updateAllWidgets(context)
            }

            calculatedStatus
        }
    }

    private fun getAreaName(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: "Sekitar Lokasi"
            } else {
                "Sekitar Lokasi"
            }
        } catch (e: Exception) {
            "Sekitar Lokasi"
        }
    }
}
