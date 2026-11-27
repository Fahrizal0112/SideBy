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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
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
    val type: String, // "CONFIG", "STATUS", "POKE", "SNAPSHOT", "CHAT", "REACT", "READ", "ANNIV", "ANNIV_OPEN", "NAMES"
    val senderRole: String = "", // "CREATOR" or "PARTNER"
    val senderId: String = "", // Device UUID to avoid self-echo
    val configData: CoupleRoomConfig? = null,
    val statusData: PartnerStatus? = null,
    val pokeData: PokeMessage? = null,
    val snapshotData: DailySnapshot? = null,
    val chatData: com.sideby.mobile.data.ChatMessage? = null,
    val reactData: com.sideby.mobile.data.PhotoReaction? = null,
    val annivData: com.sideby.mobile.data.AnniversaryNote? = null,
    val names: com.sideby.mobile.data.NamesUpdate? = null,
    /** Untuk tipe "READ": semua pesanku dengan penanda waktu sampai sini sudah dibaca pasangan. */
    val readUpTo: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

object CloudSyncManager {

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_RELAY_PAYLOAD_BYTES = 3600

    // Multi-server relay redundancy agar komunikasi antar pasangan selalu lancar tanpa terpengaruh blokir ISP / limit
    // Server pertama = server milik sendiri (VM): lebih cepat dan menyimpan pesan & foto jauh lebih lama.
    // Dua server publik di bawahnya tetap dipakai sebagai cadangan, jadi aplikasi tetap jalan walau VM mati.
    private const val OWN_RELAY = "https://43-157-223-216.sslip.io"
    private val RELAY_SERVERS = listOf(
        "$OWN_RELAY/sideby-",
        "https://ntfy.sh/sideby-",
        "https://ntfy.envs.net/sideby-"
    )
    val relayServers: List<String> get() = RELAY_SERVERS

    /** Selama masih true, pesan juga disalin ke server publik, supaya HP pasangan yang belum diperbarui tetap menerimanya. */
    private const val MIRROR_TO_PUBLIC_RELAYS = true

    private fun isOwnRelay(serverBase: String) = serverBase.startsWith(OWN_RELAY)

    /** Seberapa jauh ke belakang pesan yang terlewat boleh diambil: VM menyimpan lama, server publik hanya ~12 jam. */
    private fun maxCatchUpSeconds(serverBase: String) = if (isOwnRelay(serverBase)) 90L * 24 * 3600 else 11 * 3600L

    /** Waktu terakhir VM gagal dihubungi; sebentar setelah itu server publik didahulukan agar tidak menunggu VM yang mati. */
    @Volatile private var ownRelayFailedAt = 0L
    private fun ownRelayLooksDown() = System.currentTimeMillis() - ownRelayFailedAt < 60_000L

    // Cegah pesan berulang dari cache ntfy (Deduplikasi Pesan)
    private val processedMessageIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val processedSnapshotIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

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
            // Hanya pesan bertajuk "Sideby CONFIG": riwayat di VM bisa panjang, jadi jangan unduh semuanya.
            val url = URL("${getRoomUrl(serverBase, roomCode)}/json?poll=1&since=all&title=Sideby%20CONFIG")
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

    /** Server yang terakhir berhasil dipakai dicoba lebih dulu pada pengiriman berikutnya. */
    @Volatile private var preferredUploadHost = 0
    @Volatile private var preferredRelay = 0

    private const val UPLOAD_HEDGE_MS = 3500L
    private const val RELAY_HEDGE_MS = 1500L

    suspend fun uploadSnapshotImage(imageBytes: ByteArray): String? =
        uploadFile(imageBytes, "snapshot.jpg", "image/jpeg")

    /**
     * Unggah file (foto / voice note) ke hosting publik: ntfy.envs.net, ntfy.sh, lalu Uguu.
     * Server tidak lagi dicoba satu per satu sambil menunggu timeout. Server pertama (yang terakhir berhasil)
     * langsung jalan; bila gagal atau belum selesai dalam beberapa detik, server berikutnya ikut jalan, dan
     * yang pertama berhasil menang. Koneksi yang kalah diputus supaya tidak rebutan bandwidth.
     */
    suspend fun uploadFile(bytes: ByteArray, filename: String, mime: String): String? {
        val hostCount = 4
        // 0 = VM sendiri, 1 = ntfy.envs.net, 2 = ntfy.sh, 3 = uguu. VM didahulukan kecuali baru saja gagal.
        val others = (1 until hostCount).sortedBy { if (it == preferredUploadHost) -1 else it }
        val order = if (ownRelayLooksDown()) others + 0 else listOf(0) + others
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val connections = java.util.Collections.synchronizedList(mutableListOf<HttpURLConnection>())
        val track: (HttpURLConnection) -> Unit = { connections.add(it) }
        val result = CompletableDeferred<Pair<Int, String>?>()
        val failed = List(hostCount) { CompletableDeferred<Unit>() }
        val remaining = AtomicInteger(hostCount)

        order.forEachIndexed { pos, host ->
            scope.launch {
                if (pos > 0) withTimeoutOrNull(UPLOAD_HEDGE_MS * pos) { failed[pos - 1].await() }
                if (result.isCompleted) return@launch
                val url = try {
                    when (host) {
                        0 -> uploadToNtfyServer(OWN_RELAY, bytes, filename, mime, track)
                        1 -> uploadToNtfyServer("https://ntfy.envs.net", bytes, filename, mime, track)
                        2 -> uploadToNtfyServer("https://ntfy.sh", bytes, filename, mime, track)
                        else -> uploadToUguu(bytes, filename, mime, track)
                    }
                } catch (e: Exception) {
                    null
                }
                if (host == 0) { if (url.isNullOrBlank()) ownRelayFailedAt = System.currentTimeMillis() else ownRelayFailedAt = 0L }
                if (!url.isNullOrBlank()) result.complete(host to url)
                failed[pos].complete(Unit)
                if (remaining.decrementAndGet() == 0) result.complete(null)
            }
        }

        // Batas total: 30 dtk + waktu untuk kecepatan unggah minimal ~12 KB/dtk.
        val deadline = 30_000L + bytes.size / 12L
        try {
            val winner = withTimeoutOrNull(deadline) { result.await() }
            if (winner != null && winner.first != 0) preferredUploadHost = winner.first
            return winner?.second
        } finally {
            scope.cancel()
            synchronized(connections) {
                connections.forEach { try { it.disconnect() } catch (_: Exception) {} }
            }
        }
    }

    private fun uploadToNtfyServer(
        serverBase: String, bytes: ByteArray, filename: String, mime: String, track: (HttpURLConnection) -> Unit
    ): String? {
        val topic = "sideby-uploads-" + UUID.randomUUID().toString().take(10)
        val conn = URL("$serverBase/$topic").openConnection() as HttpURLConnection
        track(conn)
        return try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Filename", filename)
            conn.setRequestProperty("Content-Type", mime)
            conn.connectTimeout = 8000
            conn.readTimeout = 25000
            // Streaming dengan panjang tetap: tidak menampung salinan kedua di memori, dan tersambung lebih cepat.
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(responseText).optJSONObject("attachment")?.optString("url")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    private fun uploadToUguu(
        bytes: ByteArray, filename: String, mime: String, track: (HttpURLConnection) -> Unit
    ): String? {
        val conn = URL("https://uguu.se/upload.php").openConnection() as HttpURLConnection
        track(conn)
        return try {
            val boundary = "----SidebyBoundary" + System.currentTimeMillis()
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "Sideby/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 25000

            val head = "--$boundary\r\nContent-Disposition: form-data; name=\"files[]\"; filename=\"$filename\"\r\nContent-Type: $mime\r\n\r\n".toByteArray(Charsets.UTF_8)
            val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(head.size + bytes.size + tail.size)
            conn.outputStream.use { os ->
                os.write(head)
                os.write(bytes)
                os.write(tail)
                os.flush()
            }
            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(responseText).optJSONArray("files")?.optJSONObject(0)?.optString("url")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
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
            // Foto yang mengantre disimpan sebagai berkas; isi base64 hanya ada pada foto yang baru diambil.
            val bytes = com.sideby.mobile.util.PhotoStore.bytesFor(context, snapshot) ?: ByteArray(0)
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
        val tinyThumb = createTinyThumbnailBase64(
            snapshot.imageBase64 ?: com.sideby.mobile.util.PhotoStore.bytesFor(context, snapshot)
                ?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        )
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
        var wire = msg.copy(pending = false)
        // Voice note: unggah dulu (sekali saja; URL-nya disimpan supaya percobaan ulang tidak mengunggah lagi).
        if (msg.audioMillis > 0L && msg.audioUrl.isNullOrBlank()) {
            val file = com.sideby.mobile.util.VoiceNotes.file(context, msg.id)
            if (!file.exists() || file.length() == 0L) return@withContext true // rekaman hilang; jangan macetkan antrean
            val url = uploadFile(file.readBytes(), "voice.m4a", "audio/mp4") ?: return@withContext false
            MobileDataManager.updateChatMessage(context, msg.id) { it.copy(audioUrl = url) }
            wire = wire.copy(audioUrl = url)
        }
        sendToRelay(
            roomCode,
            SyncPayload(type = "CHAT", senderRole = role, senderId = MobileDataManager.getMyDeviceId(context), chatData = wire)
        )
    }

    /** Kirim satu reaksi foto. Sukses bila relay menerima. */
    suspend fun sendReaction(context: Context, r: com.sideby.mobile.data.PhotoReaction): Boolean = withContext(Dispatchers.IO) {
        val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
        sendToRelay(
            MobileDataManager.getRoomCode(context),
            SyncPayload(type = "REACT", senderRole = role, senderId = MobileDataManager.getMyDeviceId(context), reactData = r.copy(pending = false))
        )
    }

    private val namesFlushMutex = kotlinx.coroutines.sync.Mutex()

    /** Ganti nama sendiri dan nama pasangan: terapkan di HP ini, lalu kabari pasangan (diulang otomatis bila gagal). */
    suspend fun renameBoth(context: Context, myName: String, partnerName: String) = withContext(Dispatchers.IO) {
        val update = com.sideby.mobile.data.NamesUpdate(
            senderName = myName.trim(),
            receiverName = partnerName.trim(),
            updatedAt = maxOf(System.currentTimeMillis(), MobileDataManager.getNamesUpdatedAt(context) + 1)
        )
        MobileDataManager.applyNames(context, update.senderName, update.receiverName, update.updatedAt)
        MobileDataManager.setPendingNames(context, update)
        flushPendingNames(context)
        // Nama baruku juga ikut di status berikutnya (HP pasangan yang belum diperbarui pun melihatnya).
        sendMyStatus(context)
    }

    suspend fun flushPendingNames(context: Context) = withContext(Dispatchers.IO) {
        if (!namesFlushMutex.tryLock()) return@withContext
        try {
            val u = MobileDataManager.getPendingNames(context) ?: return@withContext
            val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
            val ok = sendToRelay(
                MobileDataManager.getRoomCode(context),
                SyncPayload(type = "NAMES", senderRole = role, senderId = MobileDataManager.getMyDeviceId(context), names = u)
            )
            if (ok) MobileDataManager.clearPendingNames(context, u)
        } finally {
            namesFlushMutex.unlock()
        }
    }

    private val annivFlushMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Kirim catatan aniversary yang tertunda, dan tanda "sudah kubuka" untuk catatan pasangan.
     * Berhenti di yang pertama gagal; service mencoba lagi di putaran berikutnya.
     */
    suspend fun flushPendingAnniv(context: Context, wait: Boolean = false) = withContext(Dispatchers.IO) {
        if (wait) annivFlushMutex.lock() else if (!annivFlushMutex.tryLock()) return@withContext
        try {
            val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
            val me = MobileDataManager.getMyDeviceId(context)
            val room = MobileDataManager.getRoomCode(context)
            for (n in MobileDataManager.getPendingAnnivNotes(context)) {
                val ok = sendToRelay(
                    room,
                    SyncPayload(type = "ANNIV", senderRole = role, senderId = me, annivData = n.copy(pending = false, receiptPending = false))
                )
                if (ok) MobileDataManager.markAnnivNoteSent(context, n) else return@withContext
            }
            for (n in MobileDataManager.getPendingAnnivReceipts(context)) {
                // Isi catatan tidak ikut dikirim ulang; pasangan hanya perlu tahu kapan catatannya dibuka.
                val ok = sendToRelay(
                    room,
                    SyncPayload(type = "ANNIV_OPEN", senderRole = role, senderId = me, annivData = n.copy(text = "", pending = false, receiptPending = false))
                )
                if (ok) MobileDataManager.markAnnivReceiptSent(context, n) else return@withContext
            }
        } finally {
            annivFlushMutex.unlock()
        }
    }

    private val readFlushMutex = kotlinx.coroutines.sync.Mutex()

    /** Beri tahu pasangan bahwa pesannya sudah kubaca. Bila gagal, dicoba lagi oleh service. */
    suspend fun flushReadReceipt(context: Context) = withContext(Dispatchers.IO) {
        if (!readFlushMutex.tryLock()) return@withContext
        try {
            val mark = MobileDataManager.getUnsentReadMark(context)
            if (mark <= 0L) return@withContext
            val role = if (MobileDataManager.isCreator(context)) "CREATOR" else "PARTNER"
            val ok = sendToRelay(
                MobileDataManager.getRoomCode(context),
                SyncPayload(type = "READ", senderRole = role, senderId = MobileDataManager.getMyDeviceId(context), readUpTo = mark)
            )
            if (ok) MobileDataManager.markReadMarkSent(context, mark)
        } finally {
            readFlushMutex.unlock()
        }
    }

    private val reactFlushMutex = kotlinx.coroutines.sync.Mutex()

    /** Kirim ulang reaksi yang tertunda, berurutan; berhenti di yang pertama gagal. */
    suspend fun flushPendingReactions(context: Context, wait: Boolean = false) = withContext(Dispatchers.IO) {
        if (wait) reactFlushMutex.lock() else if (!reactFlushMutex.tryLock()) return@withContext
        try {
            for (r in MobileDataManager.getPendingReactions(context)) {
                if (sendReaction(context, r)) MobileDataManager.markReactionSent(context, r) else break
            }
        } finally {
            reactFlushMutex.unlock()
        }
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

    /**
     * Kirim ke relay dengan "hedging": relay utama langsung dicoba; bila gagal atau belum menjawab dalam
     * 1,5 dtk, relay cadangan ikut dicoba. Yang pertama berhasil menentukan hasilnya. Sebelumnya relay dicoba
     * berurutan dengan timeout 6 dtk masing-masing, sehingga satu server lambat menahan setiap pesan.
     * Kalau kedua relay sempat menerima, penerima membuang duplikatnya lewat id pesan.
     */
    private suspend fun sendToRelay(roomCode: String, payload: SyncPayload): Boolean {
        val body = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val count = RELAY_SERVERS.size
        // VM (indeks 0) didahulukan; server publik berikutnya diurutkan menurut yang terakhir berhasil.
        val others = (1 until count).sortedBy { if (it == preferredRelay) -1 else it }
        val order = if (ownRelayLooksDown()) others + 0 else listOf(0) + others
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val result = CompletableDeferred<Boolean>()
        val failed = List(count) { CompletableDeferred<Unit>() }
        val remaining = AtomicInteger(count)

        order.forEachIndexed { pos, idx ->
            scope.launch {
                if (pos > 0) withTimeoutOrNull(RELAY_HEDGE_MS) { failed[pos - 1].await() }
                if (result.isCompleted) return@launch
                val ok = postToRelay(RELAY_SERVERS[idx], roomCode, payload.type, body)
                if (idx == 0) ownRelayFailedAt = if (ok) 0L else System.currentTimeMillis()
                if (ok) {
                    if (idx != 0) preferredRelay = idx
                    result.complete(true)
                    // Salin ke server publik di latar belakang (tidak ditunggu), supaya HP yang belum diperbarui tetap menerima.
                    if (idx == 0 && MIRROR_TO_PUBLIC_RELAYS) {
                        CoroutineScope(Dispatchers.IO).launch {
                            for (i in 1 until count) postToRelay(RELAY_SERVERS[i], roomCode, payload.type, body)
                        }
                    }
                }
                failed[pos].complete(Unit)
                if (remaining.decrementAndGet() == 0) result.complete(false)
            }
        }
        try {
            return withTimeoutOrNull(15_000L) { result.await() } ?: false
        } finally {
            scope.cancel()
        }
    }

    private fun postToRelay(serverBase: String, roomCode: String, type: String, body: ByteArray): Boolean {
        return try {
            val conn = URL(getRoomUrl(serverBase, roomCode)).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Title", "Sideby $type")
            conn.connectTimeout = 5000
            conn.readTimeout = 6000
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
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
            // Semua relay dicek bersamaan (bukan berurutan), dan masing-masing punya kursor sendiri:
            // relay yang gagal tidak membuat pesan di relay itu terlewat, dan relay lambat tidak menahan yang lain.
            coroutineScope {
                RELAY_SERVERS.map { server -> async { pollOneRelay(context, server, roomCode) } }.awaitAll()
            }.any { it }
        } finally {
            pollMutex.unlock()
        }
    }

    private fun serverTag(serverBase: String) = serverBase.removePrefix("https://").substringBefore("/")

    private suspend fun pollOneRelay(context: Context, serverBase: String, roomCode: String): Boolean {
        val pollStart = System.currentTimeMillis() / 1000
        val room = roomCode.lowercase().trim()
        val sync = context.getSharedPreferences("sideby_sync", Context.MODE_PRIVATE)
        val cursorKey = "last_poll_${room}_${serverTag(serverBase)}"
        // Kursor lama (satu untuk semua relay) dipakai sekali sebagai titik awal agar tidak ada pesan terlewat saat update.
        val cursor = sync.getLong(cursorKey, sync.getLong("last_poll_$room", 0L))
        val sinceParam = if (cursor > 0) {
            "${(pollStart - cursor + 10).coerceIn(10, maxCatchUpSeconds(serverBase))}s" // server publik menyimpan ~12 jam, VM jauh lebih lama
        } else {
            "5m"
        }
        return try {
            val conn = URL("${getRoomUrl(serverBase, roomCode)}/json?poll=1&since=$sinceParam").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val lines = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readLines() }
                conn.disconnect()
                lines.forEach { processIncomingRelayJson(context, it) }
                sync.edit().putLong(cursorKey, pollStart).apply()
                true
            } else {
                conn.disconnect()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Langganan langsung ke satu relay (koneksi tetap terbuka). Pesan masuk dikirim server saat itu juga,
     * jadi chat tidak lagi menunggu giliran polling tiap beberapa detik. Polling biasa tetap berjalan sebagai
     * jaring pengaman; pesan ganda dibuang lewat deduplikasi.
     */
    suspend fun runRelayStream(context: Context, serverBase: String) {
        var backoff = 3000L
        while (currentCoroutineContext().isActive) {
            val roomCode = MobileDataManager.getRoomCode(context)
            if (roomCode.isBlank() || roomCode == "BELUM_DISET") {
                delay(5000L)
                continue
            }
            var conn: HttpURLConnection? = null
            // Bacaan yang menggantung tidak bisa dibatalkan coroutine; putus koneksinya saat dibatalkan.
            val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { try { conn?.disconnect() } catch (_: Exception) {} }
            try {
                val sync = context.getSharedPreferences("sideby_sync", Context.MODE_PRIVATE)
                val key = "stream_${roomCode.lowercase().trim()}_${serverTag(serverBase)}"
                val now = System.currentTimeMillis() / 1000
                val cursor = sync.getLong(key, 0L)
                val since = if (cursor > 0) "${(now - cursor + 10).coerceIn(10, maxCatchUpSeconds(serverBase))}s" else "10s"
                val c = URL("${getRoomUrl(serverBase, roomCode)}/json?since=$since").openConnection() as HttpURLConnection
                conn = c
                c.requestMethod = "GET"
                c.connectTimeout = 8000
                c.readTimeout = 100_000 // server mengirim keepalive tiap ~45 dtk
                if (c.responseCode == 200) {
                    backoff = 3000L
                    BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { reader ->
                        while (currentCoroutineContext().isActive) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            processIncomingRelayJson(context, line)
                            val t = try { JSONObject(line).optLong("time", 0L) } catch (_: Exception) { 0L }
                            if (t > 0L) sync.edit().putLong(key, t).apply()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // sambungan putus / tidak ada sinyal: coba lagi setelah jeda
            } finally {
                cancelHandle.dispose()
                try { conn?.disconnect() } catch (_: Exception) {}
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
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
                                    .find { it.id == incomingSnap.id }
                                var base64 = incomingSnap.imageBase64.takeIf { !it.isNullOrBlank() } ?: inGallery?.imageBase64
                                val snapUrl = incomingSnap.imageUrl ?: inGallery?.imageUrl
                                // Foto yang sudah ada di HP tidak diunduh lagi.
                                if (base64.isNullOrBlank() && !snapUrl.isNullOrBlank() &&
                                    !com.sideby.mobile.util.PhotoStore.has(context, incomingSnap.id)
                                ) {
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
                                    // Status yang dikirim sebelum nama diganti tidak boleh mengembalikan nama lama.
                                    name = if (incomingStamp < MobileDataManager.getNamesUpdatedAt(context)) cur.name else incoming.name,
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
                            val isVoice = !incoming.audioUrl.isNullOrBlank() && incoming.audioMillis > 0L
                            val text = incoming.text.take(com.sideby.mobile.data.ChatMessage.MAX_TEXT_LENGTH)
                                .ifBlank { if (isVoice) "Pesan suara" else "" }
                            if (text.isBlank()) return@let
                            val msg = incoming.copy(text = text, pending = false)
                            if (MobileDataManager.addChatMessage(context, msg)) {
                                if (!MobileDataManager.isChatScreenOpen) {
                                    val name = MobileDataManager.getPartnerStatus(context).name
                                    NotificationHelper.showChatNotification(
                                        context, name,
                                        if (isVoice) "Pesan suara" else text,
                                        msg.photoId != null
                                    )
                                }
                                // Unduh rekaman di latar belakang supaya langsung bisa diputar saat chat dibuka.
                                if (isVoice) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        com.sideby.mobile.util.VoiceNotes.ensureLocal(context, msg)
                                    }
                                }
                            }
                        }
                    }
                    "NAMES" -> {
                        payload.names?.let { n ->
                            if (n.updatedAt > MobileDataManager.getNamesUpdatedAt(context) &&
                                n.senderName.isNotBlank() && n.receiverName.isNotBlank()
                            ) {
                                // Dari sudut pandang HP ini: namaku = nama untuk penerima, nama pasangan = nama si pengirim.
                                MobileDataManager.applyNames(context, n.receiverName, n.senderName, n.updatedAt)
                                SidebyWidgetHelper.updateAllWidgets(context)
                                sendMyStatus(context)
                            }
                        }
                    }
                    "ANNIV" -> {
                        payload.annivData?.let { MobileDataManager.mergeIncomingAnnivNote(context, it) }
                    }
                    "ANNIV_OPEN" -> {
                        // Catatan yang dibuka adalah catatanku (penulisnya aku).
                        payload.annivData?.let {
                            if (it.authorId == myDeviceId) MobileDataManager.applyAnnivOpenReceipt(context, it.year, it.openedAt)
                        }
                    }
                    "READ" -> {
                        if (payload.readUpTo > 0L) MobileDataManager.setPartnerReadUpTo(context, payload.readUpTo)
                    }
                    "REACT" -> {
                        payload.reactData?.let { incoming ->
                            if (incoming.photoId.isBlank() || incoming.senderId == myDeviceId) return@let
                            val r = incoming.copy(pending = false)
                            if (MobileDataManager.setPhotoReaction(context, r) && r.emoji.isNotBlank() &&
                                MobileDataManager.viewingPhotoId != r.photoId
                            ) {
                                val name = MobileDataManager.getPartnerStatus(context).name
                                val myName = MobileDataManager.getMyStatus(context).name
                                val ownPhoto = MobileDataManager.getSnapshotGallery(context)
                                    .firstOrNull { it.id == r.photoId }?.senderName == myName
                                NotificationHelper.showChatNotification(
                                    context, name,
                                    "bereaksi ${r.emoji} pada ${if (ownPhoto) "fotomu" else "foto"}",
                                    isPhotoComment = false,
                                    photoId = r.photoId
                                )
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
                            if (!photoUrl.isNullOrBlank() && !com.sideby.mobile.util.PhotoStore.has(context, fullSnapshot.id)) {
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
