package com.sideby.mobile.data

import android.content.Context
import com.sideby.mobile.util.PhotoStore
import android.content.SharedPreferences
import com.sideby.shared.model.BatteryStatus
import com.sideby.shared.model.CoupleRoomConfig
import com.sideby.shared.model.DailySnapshot
import com.sideby.shared.model.LocationStatus
import com.sideby.shared.model.MemoryItem
import com.sideby.shared.model.Mood
import com.sideby.shared.model.PartnerStatus
import com.sideby.shared.model.SpecialDate
import com.sideby.shared.model.TogetherSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MobileDataManager {
    private const val PREFS_NAME = "sideby_mobile_prefs"
    private const val KEY_SETUP_COMPLETED = "key_setup_completed"
    private const val KEY_PARTNER_STATUS = "key_partner_status"
    private const val KEY_MY_STATUS = "key_my_status"
    private const val KEY_ROOM_CONFIG = "key_room_config"
    private const val KEY_SPECIAL_DATES = "key_special_dates"
    private const val KEY_MEMORIES = "key_memories"
    private const val KEY_IS_CREATOR = "key_is_creator"
    private const val KEY_TOGETHER_SESSIONS = "key_together_sessions"
    private const val KEY_SNAPSHOT_GALLERY = "key_snapshot_gallery"
    private const val KEY_ACTIVE_TOGETHER_START = "key_active_together_start"
    private const val KEY_PENDING_SNAPSHOTS = "key_pending_snapshots"
    private const val KEY_CHAT = "key_chat_messages"
    private const val KEY_REACTIONS = "key_photo_reactions"
    private const val KEY_CHAT_LAST_READ = "key_chat_last_read"
    private const val KEY_READ_OWED = "key_chat_read_owed"
    private const val KEY_READ_SENT = "key_chat_read_sent"
    private const val KEY_PARTNER_READ = "key_chat_partner_read"

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getRoomKey(context: Context, baseKey: String): String {
        val code = getRoomCode(context).trim().lowercase().replace(" ", "_")
        return "${baseKey}_$code"
    }

    fun isSetupCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_COMPLETED, false)

    fun setSetupCompleted(context: Context, completed: Boolean) {
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    /**
     * Setel ulang room saat user ingin ganti room / keluar dari room saat ini
     */
    fun resetCurrentRoom(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .remove(KEY_ROOM_CONFIG)
            .remove(KEY_PARTNER_STATUS)
            .remove(KEY_MY_STATUS)
            .remove(KEY_ACTIVE_TOGETHER_START)
            .remove(KEY_SNAPSHOT_GALLERY) // Bersihkan data legacy jika ada
            .apply()
    }

    fun isCreator(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_CREATOR, true)

    fun getMyDeviceId(context: Context): String {
        var id = prefs(context).getString("key_device_id", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs(context).edit().putString("key_device_id", id).apply()
        }
        return id
    }

    fun saveRoomConfig(context: Context, config: CoupleRoomConfig, isCreatorRole: Boolean) {
        val raw = json.encodeToString(config)
        prefs(context).edit()
            .putString(KEY_ROOM_CONFIG, raw)
            .putBoolean(KEY_IS_CREATOR, isCreatorRole)
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()

        val myName = if (isCreatorRole) config.creatorName else config.partnerName
        val partnerName = if (isCreatorRole) config.partnerName else config.creatorName

        saveMyStatus(
            context,
            PartnerStatus(
                name = myName,
                mood = Mood.MISSING_YOU,
                customNote = "",
                isConnected = true
            )
        )

        savePartnerStatus(
            context,
            PartnerStatus(
                name = partnerName,
                phoneBattery = BatteryStatus(0, false),
                watchBattery = null,
                mood = Mood.MISSING_YOU,
                customNote = "",
                location = LocationStatus(null, null, null, false, "Menunggu lokasi..."),
                isConnected = false
            )
        )

        // Inisialisasi Tanggal Spesial otomatis (Dengan isRecurringYearly = true)
        val specialDates = mutableListOf<SpecialDate>()

        if (config.anniversaryDateMillis > 0) {
            specialDates.add(
                SpecialDate(
                    title = "Hari Jadian Kita ❤️",
                    targetDateMillis = config.anniversaryDateMillis,
                    emoji = "💍",
                    isRecurringYearly = true
                )
            )
        }

        if (config.partnerBirthdayMillis > 0) {
            specialDates.add(
                SpecialDate(
                    title = "Ulang Tahun $partnerName 🎂",
                    targetDateMillis = config.partnerBirthdayMillis,
                    emoji = "🎂",
                    isRecurringYearly = true
                )
            )
        }

        if (config.myBirthdayMillis > 0) {
            specialDates.add(
                SpecialDate(
                    title = "Ulang Tahun $myName 🎉",
                    targetDateMillis = config.myBirthdayMillis,
                    emoji = "🎉",
                    isRecurringYearly = true
                )
            )
        }

        saveSpecialDates(context, specialDates)
    }

    fun getRoomConfig(context: Context): CoupleRoomConfig? {
        val raw = prefs(context).getString(KEY_ROOM_CONFIG, null) ?: return null
        return try {
            json.decodeFromString<CoupleRoomConfig>(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun getRoomCode(context: Context): String {
        return getRoomConfig(context)?.roomCode ?: "BELUM_DISET"
    }

    fun getDaysTogether(context: Context): Long {
        val anniversary = getRoomConfig(context)?.anniversaryDateMillis ?: 0L
        if (anniversary <= 0L) return 0L
        val now = System.currentTimeMillis()
        val diff = now - anniversary
        return if (diff > 0) diff / (1000 * 60 * 60 * 24) else 1
    }

    // ==========================================
    // UPDATE STATUS ATOMIK
    // ------------------------------------------
    // Service background, UI, GPS, dan polling cloud sama-sama menulis status.
    // Sebelumnya masing-masing membaca salinan lama lalu menimpa seluruh status
    // (baca -> ubah -> simpan tanpa kunci), sehingga baterai / catatan bisa
    // "terbalik" ke nilai lama. Semua perubahan sekarang lewat satu kunci ini
    // dan hanya mengubah field yang memang miliknya.
    // ==========================================
    private val statusLock = Any()
    private val dataVersionCounter = java.util.concurrent.atomic.AtomicLong(0L)

    /** Naik setiap kali status / galeri berubah. UI memakainya untuk tahu kapan perlu memuat ulang. */
    fun dataVersion(): Long = dataVersionCounter.get()

    data class StatusChange(val before: PartnerStatus, val after: PartnerStatus) {
        val changed: Boolean get() = before != after
    }

    /**
     * Ubah status SAYA secara atomik. `lastUpdatedMillis` di-stamp otomatis dan selalu naik,
     * dipakai pasangan untuk membuang pesan lama yang datang terlambat.
     */
    fun updateMyStatus(context: Context, transform: (PartnerStatus) -> PartnerStatus): StatusChange =
        synchronized(statusLock) {
            val before = getMyStatus(context)
            val candidate = transform(before)
            if (candidate == before) return@synchronized StatusChange(before, before)
            val stamp = maxOf(System.currentTimeMillis(), before.lastUpdatedMillis + 1)
            val after = candidate.copy(lastUpdatedMillis = stamp)
            saveMyStatus(context, after)
            StatusChange(before, after)
        }

    /** Ubah status PASANGAN secara atomik (dipakai polling cloud & perhitungan jarak). */
    fun updatePartnerStatus(context: Context, transform: (PartnerStatus) -> PartnerStatus): StatusChange =
        synchronized(statusLock) {
            val before = getPartnerStatus(context)
            val after = transform(before)
            if (after != before) savePartnerStatus(context, after)
            StatusChange(before, after)
        }

    fun getPartnerStatus(context: Context): PartnerStatus {
        val raw = prefs(context).getString(KEY_PARTNER_STATUS, null)
        val config = getRoomConfig(context)
        val defaultName = if (config != null) {
            if (isCreator(context)) config.partnerName else config.creatorName
        } else "Pasangan"

        if (raw == null) {
            return PartnerStatus(
                name = defaultName,
                phoneBattery = BatteryStatus(0, false),
                watchBattery = null,
                mood = Mood.MISSING_YOU,
                customNote = "",
                location = LocationStatus(null, null, null, false, "Belum terhubung"),
                isConnected = false
            )
        }
        val status = try {
            json.decodeFromString<PartnerStatus>(raw)
        } catch (e: Exception) {
            PartnerStatus(name = defaultName)
        }
        // Bersihkan koordinat jika belum terhubung atau jarak tidak valid (> 10.000 km / 0.0)
        return if (!status.isConnected || status.location.distanceMeters == null || status.location.distanceMeters!! > 8_000_000f) {
            status.copy(
                phoneBattery = if (!status.isConnected) BatteryStatus(0, false) else status.phoneBattery,
                location = LocationStatus(null, null, null, false, if (!status.isConnected) "Belum terhubung" else "Menunggu lokasi pasangan..."),
                isConnected = status.isConnected
            )
        } else {
            status
        }
    }

    fun savePartnerStatus(context: Context, status: PartnerStatus) = synchronized(statusLock) {
        val raw = json.encodeToString(status)
        prefs(context).edit().putString(KEY_PARTNER_STATUS, raw).apply()
        dataVersionCounter.incrementAndGet()
        // Hanya simpan snapshot ke galeri jika membawa gambar utuh (imageBase64)
        val snap = status.snapshot
        if (snap != null && !snap.imageBase64.isNullOrBlank()) {
            addSnapshotToGallery(context, snap)
        }
    }

    fun getMyStatus(context: Context): PartnerStatus {
        val raw = prefs(context).getString(KEY_MY_STATUS, null)
        val config = getRoomConfig(context)
        val defaultName = if (config != null) {
            if (isCreator(context)) config.creatorName else config.partnerName
        } else "Saya"

        if (raw == null) {
            return PartnerStatus(
                name = defaultName,
                mood = Mood.MISSING_YOU,
                customNote = "",
                isConnected = true
            )
        }
        return try {
            json.decodeFromString<PartnerStatus>(raw)
        } catch (e: Exception) {
            PartnerStatus(name = defaultName)
        }
    }

    fun saveMyStatus(context: Context, status: PartnerStatus) = synchronized(statusLock) {
        val raw = json.encodeToString(status)
        prefs(context).edit().putString(KEY_MY_STATUS, raw).apply()
        dataVersionCounter.incrementAndGet()
        val snap = status.snapshot
        if (snap != null && !snap.imageBase64.isNullOrBlank()) {
            addSnapshotToGallery(context, snap)
        }
    }

    fun getSpecialDates(context: Context): List<SpecialDate> {
        val raw = prefs(context).getString(getRoomKey(context, KEY_SPECIAL_DATES), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SpecialDate>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSpecialDates(context: Context, list: List<SpecialDate>) {
        val raw = json.encodeToString(list)
        prefs(context).edit().putString(getRoomKey(context, KEY_SPECIAL_DATES), raw).apply()
    }

    fun addSpecialDate(context: Context, item: SpecialDate) {
        val current = getSpecialDates(context).toMutableList()
        current.add(item)
        saveSpecialDates(context, current)
    }

    fun getMemories(context: Context): List<MemoryItem> {
        val raw = prefs(context).getString(getRoomKey(context, KEY_MEMORIES), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<MemoryItem>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMemories(context: Context, list: List<MemoryItem>) {
        val raw = json.encodeToString(list)
        prefs(context).edit().putString(getRoomKey(context, KEY_MEMORIES), raw).apply()
    }

    fun addMemory(context: Context, item: MemoryItem) {
        val current = getMemories(context).toMutableList()
        current.add(0, item)
        saveMemories(context, current)
    }

    // ==========================================
    // RIWAYAT WAKTU KETEMUAN OTOMATIS (TOGETHER SESSIONS)
    // ==========================================
    fun getTogetherSessions(context: Context): List<TogetherSession> {
        val raw = prefs(context).getString(getRoomKey(context, KEY_TOGETHER_SESSIONS), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<TogetherSession>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTogetherSessions(context: Context, list: List<TogetherSession>) {
        val raw = json.encodeToString(list)
        prefs(context).edit().putString(getRoomKey(context, KEY_TOGETHER_SESSIONS), raw).apply()
    }

    fun addTogetherSession(context: Context, session: TogetherSession) {
        val current = getTogetherSessions(context).toMutableList()
        current.add(0, session)
        saveTogetherSessions(context, current)
    }

    fun getActiveSessionStartTime(context: Context): Long =
        prefs(context).getLong(KEY_ACTIVE_TOGETHER_START, 0L)

    fun setActiveSessionStartTime(context: Context, startTimeMillis: Long) {
        prefs(context).edit().putLong(KEY_ACTIVE_TOGETHER_START, startTimeMillis).apply()
    }

    /**
     * Memulai atau menghentikan log ketemuan otomatis saat Together Mode aktif
     */
    fun processTogetherProximity(context: Context, isNearby: Boolean, locationName: String = "Jalan Berdua") {
        val activeStart = getActiveSessionStartTime(context)
        val now = System.currentTimeMillis()

        if (isNearby) {
            // Jika baru mulai berdua dan belum ada sesi aktif
            if (activeStart == 0L) {
                setActiveSessionStartTime(context, now)
            }
        } else {
            // Jika sebelumnya sedang berdua lalu sekarang berpisah
            if (activeStart > 0L) {
                val durationMillis = now - activeStart
                val durationMinutes = durationMillis / (1000 * 60)
                // Hanya simpan jika durasi ketemuan > 1 menit
                if (durationMinutes >= 1) {
                    val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date(activeStart))
                    val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
                    val startStr = timeFormat.format(Date(activeStart))
                    val endStr = timeFormat.format(Date(now))

                    val session = TogetherSession(
                        dateString = dateStr,
                        locationName = locationName,
                        startTimeMillis = activeStart,
                        endTimeMillis = now,
                        durationMinutes = durationMinutes,
                        note = "Mulai $startStr - $endStr ($durationMinutes menit bersama)"
                    )
                    addTogetherSession(context, session)
                }
                setActiveSessionStartTime(context, 0L)
            }
        }
    }

    // ==========================================
    // GALERI FOTO HARIAN (SNAPSHOT GALLERY) - TERISOLASI PER ROOM
    // ==========================================
    fun getSnapshotGallery(context: Context): List<DailySnapshot> {
        val roomGalleryKey = getRoomKey(context, KEY_SNAPSHOT_GALLERY)
        val raw = prefs(context).getString(roomGalleryKey, null)
        val list = if (raw == null) mutableListOf() else try {
            json.decodeFromString<List<DailySnapshot>>(raw).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }

        // Pulihkan URL jika ada entri sebelumnya yang sempat kehilangan URL
        for (i in list.indices) {
            val item = list[i]
            if (!PhotoStore.hasImage(context, item)) {
                if (item.id == "32f0a07f-a925-40cb-98cc-b5ccec24c6ff") {
                    list[i] = item.copy(imageUrl = "https://h.uguu.se/HrZZwWHG.jpg")
                } else if (item.id == "c2937107-e0b0-4ac3-a344-681176d0ef7e") {
                    list[i] = item.copy(imageUrl = "https://n.uguu.se/ehbXWWAr.jpg")
                }
            }
        }

        // Hapus entri cacat yang benar-benar tidak memiliki gambar, url, maupun berkas di HP
        list.removeAll { !PhotoStore.hasImage(context, it) }

        // Sinkronkan snapshot dari myStatus jika ada
        val mySnap = getMyStatus(context).snapshot
        if (mySnap != null && (!mySnap.imageBase64.isNullOrBlank() || !mySnap.imageUrl.isNullOrBlank())) {
            val idx = list.indexOfFirst { it.id == mySnap.id }
            if (idx == -1) {
                list.add(mySnap)
            } else {
                val cur = list[idx]
                val b64 = if (!cur.imageBase64.isNullOrBlank()) cur.imageBase64 else mySnap.imageBase64
                val time = if (cur.timestamp > 0L) cur.timestamp else mySnap.timestamp
                list[idx] = cur.copy(imageBase64 = b64, timestamp = time)
            }
        }

        // Sinkronkan snapshot dari partnerStatus jika ada
        val partnerSnap = getPartnerStatus(context).snapshot
        if (partnerSnap != null && (!partnerSnap.imageBase64.isNullOrBlank() || !partnerSnap.imageUrl.isNullOrBlank())) {
            val idx = list.indexOfFirst { it.id == partnerSnap.id }
            if (idx == -1) {
                list.add(partnerSnap)
            } else {
                val cur = list[idx]
                val b64 = if (!cur.imageBase64.isNullOrBlank()) cur.imageBase64 else partnerSnap.imageBase64
                val time = if (cur.timestamp > 0L) cur.timestamp else partnerSnap.timestamp
                list[idx] = cur.copy(imageBase64 = b64, timestamp = time)
            }
        }

        // Pastikan semua snapshot memiliki timestamp yang valid
        val now = System.currentTimeMillis()
        for (i in list.indices) {
            val cur = list[i]
            if (cur.timestamp <= 0L || cur.timestamp < 1_000_000_000_000L) {
                list[i] = cur.copy(timestamp = now - ((list.size - 1 - i) * 60_000L))
            }
        }

        // WAJIB: Urutkan strictly berdasarkan timestamp terbaru ke terlama!
        list.sortByDescending { it.timestamp }

        return list
    }

    fun saveSnapshotGallery(context: Context, list: List<DailySnapshot>) {
        // Gambar disimpan sebagai berkas; daftar di preferensi hanya berisi data ringan (tanpa base64).
        list.forEach { PhotoStore.saveBase64IfMissing(context, it.id, it.imageBase64) }
        val valid = list.filter { PhotoStore.hasImage(context, it) }
            .sortedByDescending { it.timestamp }
            .map { if (it.imageBase64 != null) it.copy(imageBase64 = null) else it }
        val raw = json.encodeToString(valid)
        prefs(context).edit().putString(getRoomKey(context, KEY_SNAPSHOT_GALLERY), raw).apply()
        dataVersionCounter.incrementAndGet()
    }

    // ==========================================
    // CHAT & KOMENTAR FOTO (per room)
    // ==========================================
    private const val MAX_CHAT_MESSAGES = 600

    /** Diset true oleh layar Chat saat terbuka, agar pesan masuk tidak memunculkan notifikasi. */
    @Volatile var isChatScreenOpen: Boolean = false

    fun getChatMessages(context: Context): List<ChatMessage> {
        val raw = prefs(context).getString(getRoomKey(context, KEY_CHAT), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ChatMessage>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveChatMessages(context: Context, list: List<ChatMessage>) {
        val trimmed = list.sortedBy { it.timestamp }.takeLast(MAX_CHAT_MESSAGES)
        prefs(context).edit().putString(getRoomKey(context, KEY_CHAT), json.encodeToString(trimmed)).apply()
        dataVersionCounter.incrementAndGet()
    }

    /** Tambah pesan bila belum ada (dedupe by id). @return true jika pesan ini baru. */
    fun addChatMessage(context: Context, msg: ChatMessage): Boolean = synchronized(statusLock) {
        val cur = getChatMessages(context)
        if (cur.any { it.id == msg.id }) return@synchronized false
        saveChatMessages(context, cur + msg)
        true
    }

    fun updateChatMessage(context: Context, id: String, transform: (ChatMessage) -> ChatMessage) = synchronized(statusLock) {
        val cur = getChatMessages(context)
        if (cur.any { it.id == id }) {
            saveChatMessages(context, cur.map { if (it.id == id) transform(it) else it })
        }
    }

    // ==========================================
    // REAKSI FOTO
    // ==========================================
    /** Id foto yang sedang dibuka layar penuh; reaksi pasangan pada foto ini tidak perlu notifikasi. */
    @Volatile var viewingPhotoId: String? = null

    fun getPhotoReactions(context: Context): List<PhotoReaction> {
        val raw = prefs(context).getString(getRoomKey(context, KEY_REACTIONS), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PhotoReaction>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Simpan reaksi bila lebih baru dari yang sudah ada untuk (foto, pengirim) yang sama.
     * @return true bila ada yang berubah (reaksi baru / diganti / dicabut).
     */
    fun setPhotoReaction(context: Context, r: PhotoReaction): Boolean = synchronized(statusLock) {
        val cur = getPhotoReactions(context)
        val existing = cur.firstOrNull { it.photoId == r.photoId && it.senderId == r.senderId }
        if (existing != null && existing.timestamp >= r.timestamp) return@synchronized false
        val next = (cur.filterNot { it.photoId == r.photoId && it.senderId == r.senderId } + r).takeLast(600)
        prefs(context).edit().putString(getRoomKey(context, KEY_REACTIONS), json.encodeToString(next)).apply()
        dataVersionCounter.incrementAndGet()
        existing?.emoji != r.emoji
    }

    fun markReactionSent(context: Context, r: PhotoReaction) = synchronized(statusLock) {
        val cur = getPhotoReactions(context)
        if (cur.any { it.photoId == r.photoId && it.senderId == r.senderId && it.timestamp == r.timestamp && it.pending }) {
            val next = cur.map {
                if (it.photoId == r.photoId && it.senderId == r.senderId && it.timestamp == r.timestamp) it.copy(pending = false) else it
            }
            prefs(context).edit().putString(getRoomKey(context, KEY_REACTIONS), json.encodeToString(next)).apply()
            dataVersionCounter.incrementAndGet()
        }
    }

    fun getPendingReactions(context: Context): List<PhotoReaction> =
        getPhotoReactions(context).filter { it.pending }.sortedBy { it.timestamp }

    fun markChatMessageSent(context: Context, id: String) = synchronized(statusLock) {
        val cur = getChatMessages(context)
        if (cur.any { it.id == id && it.pending }) {
            saveChatMessages(context, cur.map { if (it.id == id) it.copy(pending = false) else it })
        }
    }

    fun getPendingChatMessages(context: Context): List<ChatMessage> =
        getChatMessages(context).filter { it.pending }.sortedBy { it.timestamp }

    fun getChatLastRead(context: Context): Long =
        prefs(context).getLong(getRoomKey(context, KEY_CHAT_LAST_READ), 0L)

    /** Pasangan sudah membaca pesanku sampai penanda waktu ini (0 = belum / tidak diketahui). */
    fun getPartnerReadUpTo(context: Context): Long =
        prefs(context).getLong(getRoomKey(context, KEY_PARTNER_READ), 0L)

    /** @return true bila penandanya maju (tanda "dibaca" pada pesanku perlu diperbarui). */
    fun setPartnerReadUpTo(context: Context, ts: Long): Boolean = synchronized(statusLock) {
        if (ts <= getPartnerReadUpTo(context)) return@synchronized false
        prefs(context).edit().putLong(getRoomKey(context, KEY_PARTNER_READ), ts).apply()
        dataVersionCounter.incrementAndGet()
        true
    }

    /** Penanda "sudah kubaca" yang belum sempat diberitahukan ke pasangan (0 = tidak ada). */
    fun getUnsentReadMark(context: Context): Long {
        val owed = prefs(context).getLong(getRoomKey(context, KEY_READ_OWED), 0L)
        val sent = prefs(context).getLong(getRoomKey(context, KEY_READ_SENT), 0L)
        return if (owed > sent) owed else 0L
    }

    fun markReadMarkSent(context: Context, ts: Long) {
        val key = getRoomKey(context, KEY_READ_SENT)
        if (ts > prefs(context).getLong(key, 0L)) prefs(context).edit().putLong(key, ts).apply()
    }

    fun markChatRead(context: Context) {
        val all = getChatMessages(context)
        val me = getMyDeviceId(context)
        // Pesan dari pasangan yang kini sudah terlihat; pasangan diberi tahu lewat tanda baca.
        val incoming = all.filter { it.senderId != me }.maxOfOrNull { it.timestamp }
        if (incoming != null && incoming > prefs(context).getLong(getRoomKey(context, KEY_READ_OWED), 0L)) {
            prefs(context).edit().putLong(getRoomKey(context, KEY_READ_OWED), incoming).apply()
        }
        val last = all.maxOfOrNull { it.timestamp } ?: return
        if (last > getChatLastRead(context)) {
            prefs(context).edit().putLong(getRoomKey(context, KEY_CHAT_LAST_READ), last).apply()
            dataVersionCounter.incrementAndGet()
        }
    }

    /** Jumlah pesan dari pasangan yang belum dibaca. */
    fun getUnreadChatCount(context: Context): Int {
        val me = getMyDeviceId(context)
        val lastRead = getChatLastRead(context)
        return getChatMessages(context).count { it.senderId != me && it.timestamp > lastRead }
    }

    // ==========================================
    // ANTREAN FOTO YANG BELUM TERKIRIM
    // ==========================================
    fun getPendingSnapshotIds(context: Context): List<String> {
        val raw = prefs(context).getString(getRoomKey(context, KEY_PENDING_SNAPSHOTS), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePendingSnapshotIds(context: Context, ids: List<String>) {
        prefs(context).edit().putString(getRoomKey(context, KEY_PENDING_SNAPSHOTS), json.encodeToString(ids)).apply()
        dataVersionCounter.incrementAndGet()
    }

    fun addPendingSnapshot(context: Context, id: String) = synchronized(statusLock) {
        val cur = getPendingSnapshotIds(context)
        if (id !in cur) savePendingSnapshotIds(context, cur + id)
    }

    fun removePendingSnapshot(context: Context, id: String) = synchronized(statusLock) {
        val cur = getPendingSnapshotIds(context)
        if (id in cur) savePendingSnapshotIds(context, cur - id)
    }

    fun addSnapshotToGallery(context: Context, snapshot: DailySnapshot) {
        // Jangan simpan snapshot kosong tanpa gambar
        if (!PhotoStore.hasImage(context, snapshot)) {
            return
        }

        val current = getSnapshotGallery(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.id == snapshot.id }
        var finalSnapshot = snapshot

        if (existingIndex != -1) {
            val existing = current[existingIndex]
            val preservedBase64 = if (!existing.imageBase64.isNullOrBlank()) {
                existing.imageBase64
            } else {
                snapshot.imageBase64
            }
            val preservedUrl = if (!snapshot.imageUrl.isNullOrBlank()) {
                snapshot.imageUrl
            } else {
                existing.imageUrl
            }
            // Pertahankan timestamp asli dari foto yang sudah ada agar posisinya tidak lompat acak
            val preservedTime = if (existing.timestamp > 0L) {
                existing.timestamp
            } else if (snapshot.timestamp > 0L) {
                snapshot.timestamp
            } else {
                System.currentTimeMillis()
            }

            finalSnapshot = snapshot.copy(
                imageBase64 = preservedBase64,
                imageUrl = preservedUrl,
                timestamp = preservedTime
            )
            current[existingIndex] = finalSnapshot
        } else {
            // Foto baru: pastikan memiliki timestamp valid
            if (finalSnapshot.timestamp <= 0L) {
                finalSnapshot = finalSnapshot.copy(timestamp = System.currentTimeMillis())
            }
            current.add(finalSnapshot)
        }

        // SELALU URUTKAN DARI TERBARU KE TERLAMA (DESCENDING)
        current.sortByDescending { it.timestamp }

        // Tidak ada batas jumlah foto: gambarnya di berkas, bukan di memori / preferensi.
        saveSnapshotGallery(context, current)
    }

    /**
     * Pindahkan foto lama (yang dulu disimpan sebagai teks base64 di preferensi, maksimal 60) ke berkas.
     * Aman dipanggil berulang; tidak melakukan apa-apa bila semuanya sudah berupa berkas.
     */
    fun migrateGalleryToFiles(context: Context) = synchronized(statusLock) {
        val raw = prefs(context).getString(getRoomKey(context, KEY_SNAPSHOT_GALLERY), null) ?: return@synchronized
        if (!raw.contains("\"imageBase64\":\"")) return@synchronized
        saveSnapshotGallery(context, getSnapshotGallery(context))
    }
}
