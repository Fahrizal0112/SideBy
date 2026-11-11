package com.sideby.mobile.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sideby.mobile.data.ChatMessage
import com.sideby.mobile.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Penyimpanan berkas voice note di HP (milik pengirim maupun hasil unduhan dari pasangan). */
object VoiceNotes {
    const val MAX_MILLIS = 120_000L
    const val MIN_MILLIS = 700L

    private fun dir(context: Context) = File(context.filesDir, "voice").apply { mkdirs() }

    fun file(context: Context, messageId: String) = File(dir(context), "$messageId.m4a")

    /** Pastikan rekaman ada di HP; bila belum, unduh dari server. null = belum bisa. */
    suspend fun ensureLocal(context: Context, msg: ChatMessage): File? = withContext(Dispatchers.IO) {
        val f = file(context, msg.id)
        if (f.exists() && f.length() > 0L) return@withContext f
        val url = msg.audioUrl ?: return@withContext null
        val bytes = CloudSyncManager.downloadImageBytes(url) ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        f.writeBytes(bytes)
        f
    }
}

/** Perekam suara: AAC mono 32 kbps, sekitar 4 KB per detik, jadi 2 menit hanya ~480 KB. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        cancel()
        val out = File(context.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(22050)
            r.setAudioEncodingBitRate(32000)
            r.setMaxDuration(VoiceNotes.MAX_MILLIS.toInt())
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) {}
            out.delete()
            false
        }
    }

    fun elapsedMillis(): Long = if (recorder == null) 0L else System.currentTimeMillis() - startedAt

    /** 0f..1f, untuk indikator suara langsung. */
    fun level(): Float = try {
        ((recorder?.maxAmplitude ?: 0) / 14000f).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0f
    }

    /** Hentikan dan kembalikan hasilnya; null bila terlalu singkat / gagal. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val f = file
        val duration = elapsedMillis()
        recorder = null
        file = null
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        }
        try { r.release() } catch (_: Exception) {}
        if (!ok || f == null || duration < VoiceNotes.MIN_MILLIS || f.length() == 0L) {
            f?.delete()
            return null
        }
        return f to duration
    }

    fun cancel() {
        val r = recorder
        val f = file
        recorder = null
        file = null
        if (r != null) {
            try { r.stop() } catch (_: Exception) {}
            try { r.release() } catch (_: Exception) {}
        }
        f?.delete()
    }
}

/** Satu pemutar untuk seluruh aplikasi: memutar satu voice note pada satu waktu, status bisa dibaca Compose. */
object VoicePlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            try {
                if (p.isPlaying) positionMs = p.currentPosition.toLong()
            } catch (_: Exception) {}
            if (isPlaying) handler.postDelayed(this, 80L)
        }
    }

    fun toggle(id: String, file: File) {
        val p = player
        if (playingId == id && p != null) {
            if (isPlaying) {
                try { p.pause() } catch (_: Exception) {}
                isPlaying = false
            } else {
                try { p.start() } catch (_: Exception) {}
                isPlaying = true
                handler.post(ticker)
            }
            return
        }
        stop()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            player = mp
            playingId = id
            durationMs = mp.duration.toLong()
            positionMs = 0L
            mp.start()
            isPlaying = true
            handler.post(ticker)
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
        playingId = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
    }
}
