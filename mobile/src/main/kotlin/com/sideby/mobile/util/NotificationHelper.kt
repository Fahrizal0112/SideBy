package com.sideby.mobile.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.sideby.mobile.R
import com.sideby.mobile.ui.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID_TOGETHER = "channel_sideby_together_v2"
    private const val CHANNEL_ID_SNAPSHOT = "channel_sideby_snapshot_v2"
    private const val CHANNEL_ID_FOREGROUND = "channel_sideby_foreground"
    private const val CHANNEL_ID_CHAT = "channel_sideby_chat"
    private const val NOTIFICATION_ID_CHAT = 3001

    private const val NOTIFICATION_ID_TOGETHER = 1001
    private const val NOTIFICATION_ID_SNAPSHOT = 2001
    const val NOTIFICATION_ID_FOREGROUND = 4001

    private const val COLOR_LOVE_PINK = 0xFFFF2D6F.toInt()

    private var lastTogetherNotificationTimeMillis = 0L

    fun getChimeSoundUri(context: Context): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/sideby_love_chime")
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Bersihkan channel lama dan channel poke yang telah ditiadakan
            try {
                manager.deleteNotificationChannel("channel_sideby_together")
                manager.deleteNotificationChannel("channel_sideby_snapshot")
                manager.deleteNotificationChannel("channel_sideby_poke")
                manager.deleteNotificationChannel("channel_sideby_poke_v2")
            } catch (_: Exception) {}

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val chimeUri = getChimeSoundUri(context)

            // Channel 1: Sedang Bersama (Suara Chime Melodi Romantis + Getar Detak Jantung)
            val togetherChannel = NotificationChannel(
                CHANNEL_ID_TOGETHER,
                "❤️ Notifikasi Sedang Bersama",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Melodi romantis saat kamu dan pasangan terdeteksi di lokasi yang sama (< 60m)"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 250, 100, 400)
                setSound(chimeUri, audioAttributes)
                enableLights(true)
                lightColor = COLOR_LOVE_PINK
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel 2: Foto & Locket Pasangan (Suara Chime Lembut + Foto Penuh)
            val snapshotChannel = NotificationChannel(
                CHANNEL_ID_SNAPSHOT,
                "📸 Foto & Locket Pasangan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nada notifikasi khusus saat pasangan mengirimkan foto harian atau locket"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 120, 80, 120, 80, 250)
                setSound(chimeUri, audioAttributes)
                enableLights(true)
                lightColor = COLOR_LOVE_PINK
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel 3: Foreground Service (Senyap & Halus)
            val foregroundChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "Layanan Latar Belakang Sideby",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Memastikan sinkronisasi baterai, jarak, dan foto selalu berjalan tepat waktu"
                setShowBadge(false)
            }

            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHAT,
                "Chat & komentar foto",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pesan dan komentar foto dari pasangan"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(chatChannel)
            manager.createNotificationChannel(togetherChannel)
            manager.createNotificationChannel(snapshotChannel)
            manager.createNotificationChannel(foregroundChannel)
        }
    }

    fun showTogetherNotification(context: Context, partnerName: String) {
        val now = System.currentTimeMillis()
        if (now - lastTogetherNotificationTimeMillis < 15 * 60 * 1000) return
        lastTogetherNotificationTimeMillis = now

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_TOGETHER,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TOGETHER)
            .setSmallIcon(R.drawable.ic_heart)
            .setColor(COLOR_LOVE_PINK)
            .setSubText("Sideby • Radar Cinta")
            .setContentTitle("❤️ Sedang Bersama $partnerName!")
            .setContentText("Kalian berdua terdeteksi berada di lokasi yang sama (< 60m). Waktu kencan mulai dicatat secara otomatis! 🥰")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Kalian berdua terdeteksi berada di lokasi yang sama (< 60m). Waktu kebersamaan kalian mulai dicatat secara otomatis ke Momen! ❤️")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(getChimeSoundUri(context))
            .setVibrate(longArrayOf(0, 150, 100, 250, 100, 400))
            .setLights(COLOR_LOVE_PINK, 1000, 1000)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_heart,
                "Buka Momen Cinta 🥰",
                pendingIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_TOGETHER, notification)
    }

    fun showChatNotification(context: Context, partnerName: String, text: String, isPhotoComment: Boolean) {
        createNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_CHAT)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID_CHAT, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAT)
            .setSmallIcon(R.drawable.ic_heart)
            .setColor(COLOR_LOVE_PINK)
            .setContentTitle(if (isPhotoComment) "$partnerName mengomentari fotomu" else partnerName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_CHAT, notification)
    }

    fun showSnapshotNotification(context: Context, partnerName: String, caption: String, imageBase64: String?) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_SNAPSHOT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bitmap = imageBase64?.let { decodeBase64(it) }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SNAPSHOT)
            .setSmallIcon(R.drawable.ic_heart)
            .setColor(COLOR_LOVE_PINK)
            .setSubText("Sideby • Momen Cinta ❤️")
            .setContentTitle("📸 $partnerName Mengirimkan Foto!")
            .setContentText(caption.ifEmpty { "Ketuk untuk melihat foto baru berdua ❤️" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(getChimeSoundUri(context))
            .setVibrate(longArrayOf(0, 120, 80, 120, 80, 250))
            .setLights(COLOR_LOVE_PINK, 1000, 1000)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_heart,
                "Lihat Foto Berdua 📸",
                pendingIntent
            )

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(caption.ifEmpty { "Foto baru dari $partnerName ❤️" })
            )
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SNAPSHOT, builder.build())
    }

    fun getForegroundNotification(context: Context, partnerName: String): Notification {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_FOREGROUND,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_FOREGROUND)
            .setSmallIcon(R.drawable.ic_heart)
            .setColor(COLOR_LOVE_PINK)
            .setContentTitle("❤️ Sideby Aktif di Latar Belakang")
            .setContentText("Terhubung dengan $partnerName • Sinkronisasi jarak, baterai & foto aktif")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun decodeBase64(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
