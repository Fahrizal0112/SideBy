package com.sideby.mobile.sync

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sideby.mobile.data.MobileDataManager
import com.sideby.mobile.util.NotificationHelper
import com.sideby.mobile.widget.SidebyWidgetHelper
import com.sideby.shared.model.CoupleRoomConfig
import com.sideby.shared.model.DailySnapshot
import com.sideby.shared.model.PartnerStatus
import com.sideby.shared.model.PokeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Serializable
data class SyncPayload(
    val type: String, // "CONFIG", "STATUS", "POKE", "SNAPSHOT", "CHAT"
    val senderRole: String = "", // "CREATOR" or "PARTNER"
    val senderId: String = "", // Device UUID to avoid self-echo
    val configData: CoupleRoomConfig? = null,
    val statusData: PartnerStatus? = null,
    val pokeData: PokeMessage? = null,
    val snapshotData: DailySnapshot? = null,
    val chatData: com.sideby.mobile.data.ChatMessage? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object CloudSyncManager {

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_RELAY_PAYLOAD_BYTES = 3600

    // Multi-server relay redundancy agar komunikasi antar pasangan selalu lancar tanpa terpengaruh blokir ISP / limit
    private val RELAY_SERVERS = listOf(
        "https://ntfy.sh/sideby-",
        "https://ntfy.envs.net/sideby-"
    )

    // Cegah pesan berulang dari cache ntfy (Deduplikasi Pesan)
    private val processedMessageIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val processedSnapshotIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private var lastPollEpochSeconds: Long = 0L

    fun getRoomUrl(base: String, roomCode: String): String {
        val clean = roomCode.lowercase().trim().replace(" ", "-").removePrefix("sideby-")
        return "$base$clean"
    }

    /**
     * Broadcast konfigurasi room dari pembuat agar pacar bisa langsung menggunakannya
     */
    suspend fun broadcastRoomConfig(context: Context, config: CoupleRoomConfig): Boolean = withContext(Dispatchers.IO) {
        val myDeviceId = MobileDataManager.getMyDeviceId(context)
        val payload = SyncPayload(
            type = "CONFIG",
            senderRole = "CREATOR",
            senderId = myDeviceId,
            configData = config
        )
        val ok = sendToRelay(config.roomCode, payload)

        // Langsung kirim juga status awal Creator agar pacar langsung terhubung saat membuka app
        val myStatus = MobileDataManager.getMyStatus(context)
        val statusPayload = SyncPayload(
            type = "STATUS",
            senderRole = "CREATOR",
            senderId = myDeviceId,
            statusData = myStatus.copy(snapshot = null)
        )
        sendToRelay(config.roomCode, statusPayload)
        ok
    }

    /**
     * Mengambil konfigurasi room saat pacar memasukkan kode room
     */
    suspend fun fetchRoomConfig(roomCode: String): CoupleRoomConfig? = withContext(Dispatchers.IO) {
        for (serverBase in RELAY_SERVERS) {
            val cfg = fetchRoomConfigFromSingleServer(serverBase, roomCode)
            if (cfg != null) return@withContext cfg
        }
        null
    }

    private fun fetchRoomConfigFromSingleServer(serverBase: String, roomCode: String): CoupleRoomConfig? {
        return try {
            val url = URL("${getRoomUrl(serverBase, roomCode)}/json?poll=1&since=all")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 7000
            conn.readTimeout = 7000

            var latestConfig: CoupleRoomConfig? = null

            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { raw ->
                            try {
                                val root = JSONObject(raw)
                                val msg = root.optString("message")
                                if (msg.contains("\"type\":\"CONFIG\"")) {
                                    val payload = json.decodeFromString<SyncPayload>(msg)
                                    if (payload.configData != null) {
                                        latestConfig = payload.configData
                                    }
                                }
                            } catch (e: Exception) {
                                // Lewati log metadata ntfy
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            latestConfig
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Kirim pembaruan status ke cloud untuk pasangan
     */
    suspend fun sendMyStatus(context: Context): Boolean = withContext(Dispatchers.IO) {
        val roomCode = MobileDataManager.getRoomCode(context)
        val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
        val myDeviceId = MobileDataManager.getMyDeviceId(context)
        // Selalu kirim status TERBARU dari penyimpanan (bukan salinan lama milik pemanggil),
        // sehingga pengiriman yang saling menyalip tetap membawa data yang sama / lebih baru.
        val status = MobileDataManager.getMyStatus(context)

        // Hapus Base64 besar dari ping status rutin (agar payload selalu < 500 byte dan tidak kena limit 4KB)
        val snap = status.snapshot
        val lightweightStatus = if (snap?.imageBase64 != null) {
            status.copy(snapshot = snap.copy(imageBase64 = null))
        } else {
            status
        }

        val payload = SyncPayload(
            type = "STATUS",
            senderRole = role,
            senderId = myDeviceId,
            statusData = lightweightStatus
        )
        sendToRelay(roomCode, payload)
    }

    /**
     * Kirim sentuhan (Poke) - fitur ditiadakan
     */
    suspend fun sendPokeToPartner(context: Context, poke: PokeMessage): Boolean = withContext(Dispatchers.IO) {
        false
    }

    /**
     * Upload binary gambar snapshot ke hosting publik (ntfy.envs.net / ntfy.sh / Uguu)
     */
    suspend fun uploadSnapshotImage(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        // Coba 1: ntfy.envs.net (sangat stabil & cepat, tanpa batas harian ketat, direct PUT)
        val envsUrl = uploadToNtfyServer("https://ntfy.envs.net", imageBytes)
        if (!envsUrl.isNullOrBlank()) return@withContext envsUrl

        // Coba 2: Ntfy.sh attachment upload
        val ntfyUrl = uploadToNtfyServer("https://ntfy.sh", imageBytes)
        if (!ntfyUrl.isNullOrBlank()) return@withContext ntfyUrl

        // Coba 3: Uguu.se (fallback multipart upload)
        val uguuUrl = uploadToUguu(imageBytes)
        if (!uguuUrl.isNullOrBlank()) return@withContext uguuUrl

        null
    }

    private fun uploadToNtfyServer(serverBase: String, imageBytes: ByteArray): String? {
        return try {
            val topic = "sideby-uploads-" + UUID.randomUUID().toString().take(10)
            val url = URL("$serverBase/$topic")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Filename", "snapshot.jpg")
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            conn.outputStream.use { it.write(imageBytes) }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(responseText)
                val attachment = root.optJSONObject("attachment")
                attachment?.optString("url")
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun uploadToUguu(imageBytes: ByteArray): String? {
        return try {
            val boundary = "----SidebyBoundary" + System.currentTimeMillis()
            val url = URL("https://uguu.se/upload.php")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "Sideby/1.0")
            conn.connectTimeout = 12000
            conn.readTimeout = 12000

            val head = "--$boundary\r\nContent-Disposition: form-data; name=\"files[]\"; filename=\"snapshot.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8)
            val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

            conn.outputStream.use { os ->
                os.write(head)
                os.write(imageBytes)
                os.write(tail)
                os.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(responseText)
                val files = root.optJSONArray("files")
                files?.optJSONObject(0)?.optString("url")
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadImageBytes(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Sideby/1.0")
            if (conn.responseCode in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                bytes
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createTinyThumbnailBase64(fullBase64: String?): String? {
        if (fullBase64.isNullOrBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(fullBase64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxDim = 96
            val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
            val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 30, baos)
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Kirim foto harian ke pasangan. Dianggap berhasil HANYA jika foto sudah ter-upload (punya URL)
     * dan pesannya diterima relay. Jika upload gagal, tidak ada pesan setengah jadi (thumbnail saja)
     * yang dikirim; pemanggil cukup menaruh foto di antrean dan mencoba lagi nanti.
     */
    suspend fun sendSnapshotToPartner(context: Context, snapshot: DailySnapshot): Boolean = withContext(Dispatchers.IO) {
        val roomCode = MobileDataManager.getRoomCode(context)
        val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
        val myDeviceId = MobileDataManager.getMyDeviceId(context)

        var remoteUrl = snapshot.imageUrl
        if (remoteUrl.isNullOrBlank()) {
            val bytes = try {
                android.util.Base64.decode(snapshot.imageBase64 ?: "", android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                ByteArray(0)
            }
            if (bytes.isEmpty()) return@withContext false
            remoteUrl = uploadSnapshotImage(bytes)
            if (remoteUrl.isNullOrBlank()) return@withContext false
        }

        // Simpan URL ke data lokal supaya percobaan ulang tidak perlu upload lagi.
        val updatedLocal = snapshot.copy(imageUrl = remoteUrl)
        MobileDataManager.addSnapshotToGallery(context, updatedLocal)
        MobileDataManager.updateMyStatus(context) {
            if (it.snapshot?.id == updatedLocal.id) it.copy(snapshot = updatedLocal) else it
        }

        // Thumbnail kecil ikut dikirim agar penerima langsung punya pratinjau; foto penuh diunduh dari URL.
        // Relay (ntfy) menolak/mengubah pesan > 4096 byte menjadi lampiran sehingga penerima tidak bisa membacanya
        // dan fotonya "hilang" diam-diam. Pesan dijaga di bawah batas itu: thumbnail hanya ikut jika muat,
        // kalau tidak foto tetap sampai lewat URL.
        val tinyThumb = createTinyThumbnailBase64(snapshot.imageBase64)
        var payload = SyncPayload(
            type = "SNAPSHOT", senderRole = role, senderId = myDeviceId,
            snapshotData = updatedLocal.copy(imageBase64 = tinyThumb)
        )
        if (json.encodeToString(payload).toByteArray(Charsets.UTF_8).size > MAX_RELAY_PAYLOAD_BYTES) {
            payload = payload.copy(snapshotData = updatedLocal.copy(imageBase64 = null))
        }

        val ok = sendToRelay(roomCode, payload)
        if (ok) {
            // Jalur kedua: status berikutnya juga membawa URL foto, jadi foto tetap sampai
            // walau pesan SNAPSHOT-nya terlewat oleh pasangan.
            sendMyStatus(context)
        }
        ok
    }

    /** Kirim satu pesan chat. Sukses bila relay menerima. */
    suspend fun sendChatMessage(context: Context, msg: com.sideby.mobile.data.ChatMessage): Boolean = withContext(Dispatchers.IO) {
        val roomCode = MobileDataManager.getRoomCode(context)
        val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
        val wire = msg.copy(pending = false)
        sendToRelay(
            roomCode,
            SyncPayload(type = "CHAT", senderRole = role, senderId = MobileDataManager.getMyDeviceId(context), chatData = wire)
        )
    }

    private val chatFlushMutex = kotlinx.coroutines.sync.Mutex()

    /** Kirim ulang pesan chat yang tertunda, berurutan; berhenti di pesan pertama yang gagal. */
    suspend fun flushPendingChats(context: Context, wait: Boolean = false): Set<String> = withContext(Dispatchers.IO) {
        if (wait) chatFlushMutex.lock() else if (!chatFlushMutex.tryLock()) return@withContext emptySet()
        try {
            val sent = mutableSetOf<String>()
            for (msg in MobileDataManager.getPendingChatMessages(context)) {
                if (sendChatMessage(context, msg)) {
                    MobileDataManager.markChatMessageSent(context, msg.id)
                    sent.add(msg.id)
                } else break
            }
            sent
        } finally {
            chatFlushMutex.unlock()
        }
    }

    private val flushMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Kirim semua foto yang masih mengantre. Aman dipanggil dari UI maupun service:
     * hanya satu pengiriman berjalan, dan foto yang sudah terkirim langsung dikeluarkan dari antrean.
     * @param wait true = tunggu giliran (dipakai UI); false = lewati jika sedang berjalan (dipakai loop service).
     * @return id foto yang berhasil terkirim pada pemanggilan ini.
     */
    suspend fun flushPendingSnapshots(context: Context, wait: Boolean = false): Set<String> = withContext(Dispatchers.IO) {
        if (wait) flushMutex.lock() else if (!flushMutex.tryLock()) return@withContext emptySet()
        try {
            val sent = mutableSetOf<String>()
            val pending = MobileDataManager.getPendingSnapshotIds(context)
            if (pending.isEmpty()) return@withContext sent
            val gallery = MobileDataManager.getSnapshotGallery(context)
            for (id in pending) {
                val snap = gallery.firstOrNull { it.id == id }
                if (snap == null) {
                    MobileDataManager.removePendingSnapshot(context, id) // foto sudah tidak ada
                    continue
                }
                if (sendSnapshotToPartner(context, snap)) {
                    MobileDataManager.removePendingSnapshot(context, id)
                    sent.add(id)
                } else {
                    break // koneksi bermasalah; coba lagi di putaran berikutnya, urutan tetap terjaga
                }
            }
            sent
        } finally {
            flushMutex.unlock()
        }
    }

    private fun sendToRelay(roomCode: String, payload: SyncPayload): Boolean {
        var anySuccess = false
        val jsonString = json.encodeToString(payload)

        for (serverBase in RELAY_SERVERS) {
            try {
                val url = URL(getRoomUrl(serverBase, roomCode))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Title", "Sideby ${payload.type}")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                conn.disconnect()
                if (responseCode in 200..299) {
                    anySuccess = true
                    break // Sukses terkirim ke relay utama, tidak perlu duplikasi ke relay kedua
                }
            } catch (e: Exception) {
                // Lanjut coba server berikutnya
            }
        }
        return anySuccess
    }

    private val pollMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Mengambil pesan / update terbaru dari pasangan.
     * - Hanya satu polling berjalan sekaligus (UI & service sebelumnya bisa tumpang tindih).
     * - Semua relay dicek, karena pengirim bisa saja jatuh ke relay cadangan.
     * - Kursor waktu hanya maju bila polling berhasil, supaya update tidak terlewat saat sinyal putus.
     */
    suspend fun pollPartnerUpdates(context: Context): Boolean = withContext(Dispatchers.IO) {
        val roomCode = MobileDataManager.getRoomCode(context)
        if (roomCode.isBlank() || roomCode == "BELUM_DISET") return@withContext false
        if (!pollMutex.tryLock()) return@withContext false
        try {
            val pollStart = System.currentTimeMillis() / 1000
            // Kursor disimpan permanen. Sebelumnya setiap aplikasi dibuka ulang hanya mengambil 5 menit
            // terakhir, sehingga foto yang dikirim saat HP pasangan tertutup / diam di background hilang.
            val sync = context.getSharedPreferences("sideby_sync", Context.MODE_PRIVATE)
            val cursorKey = "last_poll_" + roomCode.lowercase().trim()
            val cursor = if (lastPollEpochSeconds > 0) lastPollEpochSeconds else sync.getLong(cursorKey, 0L)
            val sinceParam = if (cursor > 0) {
                "${(pollStart - cursor + 10).coerceIn(10, 11 * 3600L)}s" // relay menyimpan pesan ~12 jam
            } else {
                "5m"
            }

            var success = false
            for (serverBase in RELAY_SERVERS) {
                try {
                    val url = URL("${getRoomUrl(serverBase, roomCode)}/json?poll=1&since=$sinceParam")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000

                    if (conn.responseCode == 200) {
                        val lines = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readLines() }
                        conn.disconnect()
                        lines.forEach { processIncomingRelayJson(context, it) }
                        success = true
                    } else {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    // Lanjut ke relay berikutnya
                }
            }
            if (success) {
                lastPollEpochSeconds = pollStart
                sync.edit().putLong(cursorKey, pollStart).apply()
            }
            success
        } finally {
            pollMutex.unlock()
        }
    }

    private suspend fun processIncomingRelayJson(context: Context, rawLine: String) {
        try {
            val root = JSONObject(rawLine)
            val event = root.optString("event")
            if (event.isNotEmpty() && event != "message") return

            // 1. Deduplikasi pesan berdasarkan ID ntfy
            val ntfyId = root.optString("id")
            if (ntfyId.isNotEmpty()) {
                if (!processedMessageIds.add(ntfyId)) {
                    return // Pesan ini sudah pernah diproses, abaikan
                }
                if (processedMessageIds.size > 800) {
                    val iterator = processedMessageIds.iterator()
                    repeat(200) { if (iterator.hasNext()) { iterator.next(); iterator.remove() } }
                }
            }

            val messageContent = root.optString("message")
            if (messageContent.isBlank()) return

            val payload = try {
                json.decodeFromString<SyncPayload>(messageContent)
            } catch (e: Exception) {
                return
            }

            val myDeviceId = MobileDataManager.getMyDeviceId(context)
            val currentRole = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"

            // Cek apakah pesan berasal dari diri sendiri (berdasarkan senderId atau role)
            val isFromSelf = (payload.senderId.isNotBlank() && payload.senderId == myDeviceId) ||
                             (payload.senderId.isBlank() && payload.senderRole == currentRole)

            // Hanya proses pesan jika pengirim BUKAN diri sendiri
            if (!isFromSelf) {
                MobileDataManager.updatePartnerStatus(context) {
                    if (it.isConnected) it else it.copy(isConnected = true)
                }

                when (payload.type) {
                    "STATUS" -> {
                        payload.statusData?.let { incoming ->
                            // Penanda urutan dari pengirim. Pesan yang lebih lama dari yang sudah kita terima
                            // (datang terlambat / ter-replay dari cache relay) dibuang, tidak boleh menimpa data baru.
                            val incomingStamp = if (incoming.lastUpdatedMillis > 0L) incoming.lastUpdatedMillis else payload.timestamp

                            // Kerja lambat (unduh foto) dilakukan DI LUAR kunci data.
                            val incomingSnap = incoming.snapshot
                            var resolvedSnap: DailySnapshot? = null
                            if (incomingSnap != null) {
                                val inGallery = MobileDataManager.getSnapshotGallery(context)
                                    .find { it.id == incomingSnap.id && !it.imageBase64.isNullOrBlank() }
                                var base64 = incomingSnap.imageBase64.takeIf { !it.isNullOrBlank() } ?: inGallery?.imageBase64
                                val snapUrl = incomingSnap.imageUrl ?: inGallery?.imageUrl
                                if (base64.isNullOrBlank() && !snapUrl.isNullOrBlank()) {
                                    downloadImageBytes(snapUrl)?.let {
                                        base64 = android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                                    }
                                }
                                val snapTime = when {
                                    incomingSnap.timestamp > 0L -> incomingSnap.timestamp
                                    inGallery != null && inGallery.timestamp > 0L -> inGallery.timestamp
                                    incomingStamp > 0L -> incomingStamp
                                    else -> System.currentTimeMillis()
                                }
                                val full = incomingSnap.copy(imageBase64 = base64, imageUrl = snapUrl, timestamp = snapTime)
                                if (!full.imageBase64.isNullOrBlank() || !full.imageUrl.isNullOrBlank()) {
                                    resolvedSnap = full
                                }
                            }

                            // Satu pembaruan atomik: baca status terkini -> gabungkan -> simpan.
                            val change = MobileDataManager.updatePartnerStatus(context) { cur ->
                                if (cur.lastUpdatedMillis > 0L && incomingStamp < cur.lastUpdatedMillis) {
                                    return@updatePartnerStatus cur
                                }
                                val my = MobileDataManager.getMyStatus(context)
                                val myLat = my.location.latitude
                                val myLon = my.location.longitude
                                val pLat = incoming.location.latitude
                                val pLon = incoming.location.longitude
                                val finalLocation = if (myLat != null && myLon != null && pLat != null && pLon != null) {
                                    com.sideby.shared.model.LocationStatus.calculate(myLat, myLon, pLat, pLon)
                                } else {
                                    incoming.location
                                }
                                incoming.copy(
                                    // Baterai 0 / tidak diketahui jangan menimpa angka valid terakhir
                                    phoneBattery = if (incoming.phoneBattery.percentage > 0) incoming.phoneBattery else cur.phoneBattery,
                                    location = finalLocation,
                                    isConnected = true,
                                    snapshot = resolvedSnap ?: cur.snapshot,
                                    lastUpdatedMillis = incomingStamp
                                )
                            }

                            if (change.changed) {
                                val after = change.after
                                val wasNearby = change.before.location.isNearby
                                if (after.location.isNearby && !wasNearby) {
                                    NotificationHelper.showTogetherNotification(context, after.name)
                                    MobileDataManager.processTogetherProximity(context, true, after.location.locationName.ifEmpty { "Jalan Berdua" })
                                } else if (!after.location.isNearby && wasNearby) {
                                    MobileDataManager.processTogetherProximity(context, false)
                                }

                                val snap = resolvedSnap
                                if (snap != null) {
                                    MobileDataManager.addSnapshotToGallery(context, snap)
                                    if (snap.id != change.before.snapshot?.id && processedSnapshotIds.add(snap.id)) {
                                        NotificationHelper.showSnapshotNotification(
                                            context = context,
                                            partnerName = after.name,
                                            caption = snap.caption,
                                            imageBase64 = snap.imageBase64
                                        )
                                    }
                                }

                                SyncManager.sendPartnerStatusToWatch(context, after)
                                SidebyWidgetHelper.updateAllWidgets(context)
                            }
                        }
                    }
                    "CHAT" -> {
                        payload.chatData?.let { incoming ->
                            val text = incoming.text.take(com.sideby.mobile.data.ChatMessage.MAX_TEXT_LENGTH)
                            if (text.isBlank()) return@let
                            val msg = incoming.copy(text = text, pending = false)
                            if (MobileDataManager.addChatMessage(context, msg)) {
                                if (!MobileDataManager.isChatScreenOpen) {
                                    val name = MobileDataManager.getPartnerStatus(context).name
                                    NotificationHelper.showChatNotification(context, name, text, msg.photoId != null)
                                }
                            }
                        }
                    }
                    "POKE" -> {
                        // Fitur sentuhan haptic ditiadakan sesuai permintaan
                    }
                    "SNAPSHOT" -> {
                        payload.snapshotData?.let { snapshot ->
                            val validTime = if (snapshot.timestamp > 0L) snapshot.timestamp else if (payload.timestamp > 0L) payload.timestamp else System.currentTimeMillis()
                            var fullSnapshot = snapshot.copy(timestamp = validTime)
                            val photoUrl = fullSnapshot.imageUrl
                            if (!photoUrl.isNullOrBlank()) {
                                val downloadedBytes = downloadImageBytes(photoUrl)
                                if (downloadedBytes != null) {
                                    val b64 = android.util.Base64.encodeToString(downloadedBytes, android.util.Base64.NO_WRAP)
                                    fullSnapshot = fullSnapshot.copy(imageBase64 = b64)
                                }
                            }

                            // HANYA proses jika membawa gambar valid (base64 atau url)
                            if (!fullSnapshot.imageBase64.isNullOrBlank() || !fullSnapshot.imageUrl.isNullOrBlank()) {
                                val alreadyKnown = MobileDataManager.getSnapshotGallery(context).any { it.id == fullSnapshot.id }
                                val isBrandNew = !alreadyKnown && processedSnapshotIds.add(fullSnapshot.id)

                                val partnerName = MobileDataManager.updatePartnerStatus(context) {
                                    it.copy(snapshot = fullSnapshot, isConnected = true)
                                }.after.name
                                MobileDataManager.addSnapshotToGallery(context, fullSnapshot)

                                if (isBrandNew) {
                                    NotificationHelper.showSnapshotNotification(
                                        context = context,
                                        partnerName = partnerName,
                                        caption = fullSnapshot.caption,
                                        imageBase64 = fullSnapshot.imageBase64
                                    )
                                }
                                SidebyWidgetHelper.updateAllWidgets(context)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerLocalVibration(context: Context, poke: PokeMessage) {
        // Fitur sentuhan haptic ditiadakan
    }
}
