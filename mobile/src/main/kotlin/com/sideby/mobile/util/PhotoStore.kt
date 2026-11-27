package com.sideby.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import com.sideby.mobile.sync.CloudSyncManager
import com.sideby.shared.model.DailySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Penyimpanan foto di HP berupa berkas (filesDir/photos/<id>.jpg), bukan teks base64 di preferensi.
 * Dengan begini jumlah foto tidak dibatasi (ribuan pun muat), daftar foto tetap ringan, dan gambar
 * hanya dibaca dari disk saat benar-benar ditampilkan.
 */
object PhotoStore {
    private fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, id: String) = File(dir(context), "$id.jpg")

    // Daftar id foto yang ada di disk, disimpan di memori supaya pengecekan ribuan foto tetap cepat.
    private var ids: MutableSet<String>? = null

    private fun idSet(context: Context): MutableSet<String> = synchronized(this) {
        ids ?: (dir(context).list()?.filter { it.endsWith(".jpg") }?.map { it.removeSuffix(".jpg") }?.toMutableSet()
            ?: mutableSetOf()).also { ids = it }
    }

    fun has(context: Context, id: String): Boolean = synchronized(this) { idSet(context).contains(id) }

    /** Sebuah foto dianggap punya gambar bila ada isinya, alamat unduhannya, atau berkasnya di HP. */
    fun hasImage(context: Context, s: DailySnapshot): Boolean =
        !s.imageBase64.isNullOrBlank() || !s.imageUrl.isNullOrBlank() || has(context, s.id)

    fun saveBytes(context: Context, id: String, bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || id.isBlank()) return false
        return try {
            val target = file(context, id)
            val tmp = File(dir(context), "$id.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) return false
            }
            synchronized(this) { idSet(context).add(id) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Simpan dari base64 bila berkasnya belum ada. */
    fun saveBase64IfMissing(context: Context, id: String, base64: String?) {
        if (base64.isNullOrBlank() || has(context, id)) return
        try {
            saveBytes(context, id, Base64.decode(base64, Base64.DEFAULT))
        } catch (_: Exception) {
        }
    }

    fun readBytes(context: Context, id: String): ByteArray? =
        if (has(context, id)) try { file(context, id).readBytes() } catch (_: Exception) { null } else null

    /** Bytes JPEG sebuah foto: dari berkas bila ada, kalau tidak dari isi base64-nya. */
    fun bytesFor(context: Context, s: DailySnapshot): ByteArray? =
        readBytes(context, s.id) ?: s.imageBase64?.takeIf { it.isNotBlank() }?.let {
            try { Base64.decode(it, Base64.DEFAULT) } catch (_: Exception) { null }
        }

    // Foto yang pasti tidak bisa ditampilkan lagi (tidak ada di HP, dan sudah hilang dari server).
    private val goneIds = java.util.Collections.synchronizedSet(HashSet<String>())

    fun isGone(id: String): Boolean = goneIds.contains(id)

    // Cache bitmap kecil supaya menggulir galeri dan menggeser foto tidak mendekode ulang terus.
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun decodeFile(f: File, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Ambil gambar sebuah foto untuk ditampilkan. Urutan: berkas di HP, isi base64 (lalu disimpan jadi berkas),
     * terakhir diunduh dari server (lalu disimpan). Hasil didekode dengan ukuran sesuai [maxDim].
     */
    suspend fun load(context: Context, snap: DailySnapshot, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${snap.id}@$maxDim"
        cache.get(key)?.let { return@withContext it }
        val f = file(context, snap.id)
        var fresh = false
        if (!has(context, snap.id)) {
            val b64 = snap.imageBase64
            val url = snap.imageUrl
            if (!b64.isNullOrBlank()) {
                saveBase64IfMissing(context, snap.id, b64)
                fresh = true
            } else if (!url.isNullOrBlank()) {
                val bytes = CloudSyncManager.downloadImageBytes(url)
                if (bytes != null && saveBytes(context, snap.id, bytes)) fresh = true
                else if (CloudSyncManager.isImageGone(url)) goneIds.add(snap.id)
            } else {
                goneIds.add(snap.id) // tidak ada berkas, isi, maupun alamat: tidak mungkin dimuat
            }
        }
        if (!has(context, snap.id)) return@withContext null
        val bmp = decodeFile(f, maxDim)
        if (bmp == null && fresh) {
            // Isinya bukan gambar (mis. halaman galat dari server): buang supaya bisa dicoba lagi nanti.
            f.delete()
            synchronized(this@PhotoStore) { idSet(context).remove(snap.id) }
            goneIds.add(snap.id)
            return@withContext null
        }
        bmp?.also { cache.put(key, it); goneIds.remove(snap.id) }
    }
}
