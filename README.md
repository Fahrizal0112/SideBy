# Sideby ❤️ (Android & Wear OS Couple Companion)

Aplikasi privat khusus berdua yang menghubungkan kamu dan pasanganmu melalui **Android Smartphone** dan **Wear OS Smartwatch**.

---

## 📁 Struktur Modul

Project ini menggunakan arsitektur **Multi-Module**:

```text
loveproject/
├── shared/     # [Modul Bersama] Data model (PartnerStatus, Mood, BatteryStatus, Poke, Snapshot, SpecialDate, MemoryItem)
├── mobile/     # [Aplikasi HP] Jetpack Compose, 2 Glance Widgets (Lockscreen & Homescreen), Battery Monitor, Data Sync
└── wear/       # [Aplikasi Jam] Wear Compose, Complication Watch Face, Quick Poke Tile, Haptic Engine
```

---

## 🌟 Fitur Utama

### 1. Widgets di HP (Android Glance)
* **Widget 1: Status, Baterai & Jarak Real-Time (Lock Screen & Home Screen):**
  * Bisa dipasang di **Lock Screen** dan **Home Screen**.
  * Menampilkan:
    * Nama & status mood pasangan.
    * Persentase baterai HP pasangan.
    * **Status Jarak Real-Time:** Menampilkan jarak (misal `"📍 1.2 km dari kamu"` atau `"📍 350 m"`) dan otomatis berubah menjadi **`"❤️ Sedang Bersama"`** ketika kalian berdua berada di lokasi yang sama (< 60m).
* **Widget 2: Foto Locket Harian (Home Screen Widget):**
  * Menampilkan foto harian (*Daily Snapshot*) terbaru yang dikirim oleh pasangan langsung di layar utama HP ala aplikasi Locket.
  * Dilengkapi caption manis dan bisa langsung diketuk untuk membuka Sideby dan membalas foto.

### 2. Aplikasi HP (Android Phone)
* **Tab 1: Berdua (Live Status & Snapshot):**
  * Kartu status live, upload foto harian berdua, dan 6 sentuhan haptic (*Detak Jantung, Peluk Jauh, Kecup Manis, Colek, Ingat Minum, Kangen Banget*).
* **Tab 2: Radar Jarak & Private Mini-Map:**
  * Radar konsentris dengan gelombang denyut cinta yang menghitung jarak dan posisi pasangan.
* **Tab 3: Momen & Kenangan:**
  * Counter hari jadian (*Days Together*), hitungan mundur ke tanggal spesial, dan timeline catatan kenangan (*Memory Log*).
* **Tab 4: Pengaturan & Pairing:**
  * Kode room pasangan (cukup masukkan kode yang sama di kedua HP) dan tes sinyal jam tangan Wear OS.

### 3. Wear OS Smartwatch
* **Watch Face Complication (`PartnerComplicationService`):**
  * Menampilkan baterai HP pasangan dan status mood langsung di watch face (`SHORT_TEXT`, `RANGED_VALUE`, `LONG_TEXT`).
* **Quick Poke Tile (`QuickPokeTileService`):**
  * Geser ke kiri dari layar jam untuk mengirim pesan/sentuhan getar instan.
* **Haptic Touch Engine (`WearDataListenerService`):**
  * Jam bergetar di pergelangan tangan dengan ritme khusus saat pasangan mengirim sentuhan.
* **Wear Compose Round App (`MainActivity`):**
  * Tampilan ringkas status pasangan dengan tombol sentuhan 1-tap.

---

## 📦 Unduh & Update

APK terbaru ada di halaman [Releases](https://github.com/Fahrizal0112/SideBy/releases/latest) (`Sideby-Phone.apk`).
Setelah terpasang, aplikasi mengecek versi baru sendiri saat dibuka dan lewat **Setelan → Pembaruan aplikasi**.
Android tetap meminta kamu mengetuk "Pasang" tiap update, dan izin "Install aplikasi tidak dikenal" cukup diberikan sekali.

Merilis versi baru: naikkan `versionCode` dan `versionName` di `mobile/build.gradle.kts`, lalu jalankan `./release.sh "catatan perubahan"`.
