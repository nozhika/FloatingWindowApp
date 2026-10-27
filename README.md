# Floating Window App (Android)

Aplikasi Android sederhana yang menampilkan **floating window / bubble** di atas aplikasi lain, mirip gelembung chat Messenger. Bubble bisa diseret (drag) ke mana saja di layar dan ditutup lewat tombol X.

## Struktur Proyek

```
FloatingWindowApp/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/floatingwindow/
│       │   ├── MainActivity.java          # UI utama: tombol start/stop
│       │   └── FloatingWindowService.java # Service yang menampilkan bubble
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── layout_floating_widget.xml
│           ├── drawable/bubble_background.xml
│           └── values/ (strings, colors, themes)
├── build.gradle
├── settings.gradle
├── gradle.properties
└── .github/workflows/android-build.yml   # CI build otomatis
```

## Cara Kerja

1. `MainActivity` menampilkan dua tombol: **Tampilkan Floating Window** dan **Hentikan Floating Window**.
2. Saat tombol start ditekan, aplikasi mengecek izin `SYSTEM_ALERT_WINDOW` (izin "Display over other apps"). Jika belum diberikan, pengguna diarahkan ke halaman Settings untuk mengaktifkannya.
3. Setelah izin diberikan, `FloatingWindowService` dijalankan sebagai **foreground service** (wajib di Android 8+) dan menambahkan `layout_floating_widget.xml` ke `WindowManager` sebagai overlay.
4. Bubble bisa diseret bebas di layar (via `OnTouchListener` yang menghitung delta posisi sentuhan), dan ditutup dengan menekan ikon X di pojoknya.

## Build Lokal

Proyek ini **tidak menyertakan Gradle Wrapper binary** (`gradle-wrapper.jar`) karena dibuat di lingkungan tanpa akses ke Google Maven. Untuk build secara lokal, Anda punya 2 opsi:

**Opsi A — Buka di Android Studio (paling mudah)**
Android Studio otomatis membuatkan wrapper saat proyek dibuka pertama kali. Cukup `File > Open` folder ini.

**Opsi B — Pakai Gradle yang sudah terpasang di komputer**
```bash
gradle wrapper --gradle-version 8.4   # generate gradlew sekali
./gradlew assembleDebug
```
APK hasil build ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Build Otomatis via GitHub Actions

Workflow di `.github/workflows/android-build.yml` akan otomatis build APK debug & release (unsigned) setiap kali ada push/PR ke branch `main`, atau bisa dipicu manual lewat tab **Actions > Run workflow**.

Workflow ini memakai `gradle/actions/setup-gradle` untuk menjalankan Gradle langsung (tidak bergantung pada wrapper lokal), jadi tetap berjalan normal meski repo tidak menyertakan `gradlew`.

Setelah build selesai, APK bisa diunduh dari halaman run workflow tersebut, di bagian **Artifacts** (`app-debug-apk` dan `app-release-apk`).

> Catatan: APK release yang dihasilkan **belum ditandatangani (unsigned)**, jadi hanya untuk testing — belum siap dipublikasikan ke Play Store tanpa proses signing tambahan.

## Izin yang Digunakan

| Izin | Kegunaan |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Menampilkan window di atas aplikasi lain |
| `FOREGROUND_SERVICE` | Menjalankan service floating window di foreground |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Diperlukan Android 14+ untuk tipe foreground service "specialUse" |
