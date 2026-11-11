package com.sideby.mobile.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String
)

/**
 * Pengecekan & pemasangan update lewat GitHub Releases.
 * Android tidak mengizinkan pemasangan senyap: aplikasi mengunduh APK lalu membuka
 * layar pemasang sistem, dan pengguna mengetuk "Pasang".
 */
object UpdateManager {

    private const val REPO = "Fahrizal0112/SideBy"
    private const val APK_ASSET_NAME = "Sideby-Phone.apk"
    private const val PREFS = "sideby_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Bandingkan "1.10" vs "1.9" secara numerik per bagian. */
    internal fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split('.').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** True bila sudah waktunya cek otomatis (dibatasi 6 jam sekali). */
    fun shouldAutoCheck(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > AUTO_CHECK_INTERVAL_MS
    }

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        object Failed : CheckResult()
    }

    suspend fun checkForUpdate(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Sideby-App")
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext CheckResult.Failed
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val root = JSONObject(body)
            val tag = root.optString("tag_name")
            val assets = root.optJSONArray("assets") ?: return@withContext CheckResult.Failed
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == APK_ASSET_NAME) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                }
            }
            if (tag.isBlank() || url.isNullOrBlank()) return@withContext CheckResult.Failed

            if (isNewer(tag, installedVersionName(context))) {
                CheckResult.Available(
                    UpdateInfo(tag.removePrefix("v"), url, size, root.optString("body").trim())
                )
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Failed
        }
    }

    /** Unduh APK ke cache. @return file APK, atau null jika gagal. */
    suspend fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "Sideby-${info.versionName}.apk")

                val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Sideby-App")
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    return@withContext null
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                var read = 0L
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                conn.disconnect()
                // Sanity check: file APK (zip) minimal beberapa MB, dan tidak terpotong.
                if (target.length() < 1_000_000L || (total > 0 && target.length() != total)) {
                    target.delete()
                    null
                } else target
            } catch (e: Exception) {
                null
            }
        }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Buka pengaturan "Install aplikasi tidak dikenal" untuk Sideby. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Buka pemasang sistem untuk APK yang sudah diunduh. */
    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
