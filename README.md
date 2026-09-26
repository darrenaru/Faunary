# Faunary

Faunary adalah jurnal pribadi untuk mencatat hewan yang kamu temui. Kamu memotret hewannya, AI di perangkat mengenali jenisnya, lalu titik GPS tersimpan dan temuan itu muncul di peta koleksimu.

## Setup

1. Buka proyek di Android Studio. Butuh JDK 17+; JBR bawaan Android Studio sudah cukup.
2. Isi `local.properties`. File ini tidak di-commit.
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   MAPBOX_ACCESS_TOKEN=pk.xxxxx
   ```
   Token Mapbox public bisa dibuat di https://account.mapbox.com/access-tokens.
3. (Opsional, untuk fitur online) Siapkan Supabase, lihat bagian **Backend** di bawah. Tanpa langkah ini aplikasi tetap berjalan penuh secara lokal.
4. Jalankan `./gradlew installDebug`. Kalau pakai perangkat MIUI, aktifkan *Install via USB* di Developer options.

## Stack

| Bagian | Teknologi |
|---|---|
| UI | Jetpack Compose + Material 3, font Inter & Fraunces |
| Arsitektur | MVVM (ViewModel + StateFlow), Hilt |
| Kamera | CameraX (`LifecycleCameraController`) |
| AI | ML Kit Object Detection menentukan kotak deteksi, lalu tiap kotak dipotong dan diberi label dengan ML Kit Image Labeling. Semua berjalan offline. |
| Peta | Mapbox Maps SDK v11 dengan style hangat (lihat `MapStyle.kt`) dan clustering marker |
| Lokasi | Fused Location Provider + Geocoder |
| Data | Room (`animal_sightings`); foto disimpan di penyimpanan internal aplikasi |
| Online | Supabase: login anonim, Postgres + PostGIS, Storage, Realtime; sinkronisasi lewat WorkManager |

## Backend (Supabase)

Setiap temuan otomatis dibagikan ke peta publik. Peta juga menampilkan temuan pengguna lain dan penjelajah yang sedang online. Lokasi live bersifat opt-in dan hanya dikirim selama aplikasi terbuka.

1. Buat proyek di https://supabase.com/dashboard.
2. Jalankan file-file di `supabase/migrations/` secara berurutan lewat SQL Editor.
3. Buka **Authentication → Sign In / Providers**, lalu aktifkan **Allow anonymous sign-ins**.
4. Tambahkan ke `local.properties`:
   ```properties
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_ANON_KEY=<anon / publishable key>
   ```
   Gunakan hanya kunci **anon/publishable**. Keamanan data diatur oleh Row Level Security. Kunci `service_role` jangan pernah ditaruh di aplikasi.

## Rilis & pembaruan otomatis

Aplikasi yang sudah terpasang mengecek `app-releases/latest.json` di Supabase saat dibuka dan setiap 12 jam. Pembaruan diunduh sebagai *patch* (hanya bagian yang berubah, biasanya beberapa MB). Secara default unduhan hanya berjalan lewat Wi-Fi, lalu diverifikasi SHA-256 dan dipasang setelah pengguna mengetuk **Pasang**.

```bash
# butuh kunci service_role hanya di mesin rilis, jangan pernah dimasukkan ke aplikasi
export FAUNARY_SERVICE_ROLE_KEY=...
python tools/release.py --notes "Catatan pembaruan"
```

Skrip ini menaikkan `version.properties`, membangun APK release per ABI (arm64-v8a dan armeabi-v7a, sekitar 25 MB), membuat patch dari 4 versi terakhir, lalu mengunggah semuanya.

**Penting:** setiap rilis harus ditandatangani dengan kunci yang sama (default `~/.android/debug.keystore`, bisa diganti lewat `SIGNING_*` di `local.properties`). Simpan cadangan file kunci tersebut. Kalau kunci hilang, semua pengguna harus uninstall sebelum bisa memperbarui.

## Struktur

```
app/src/main/java/com/faunary/app/
├── data/        Room entity/DAO, PhotoStorage, Settings
├── domain/      Kategori hewan, katalog label → nama Indonesia, statistik
├── ml/          AnimalDetector (ML Kit)
├── location/    GPS + reverse geocoding
├── remote/      Supabase: auth anonim, sinkronisasi, data komunitas, lokasi live
├── update/      Pembaruan otomatis: manifest, unduhan patch, verifikasi, PackageInstaller
└── ui/
    ├── map/       Peta koleksi (home), wrapper Mapbox, marker
    ├── camera/    Kamera in-app + impor dari galeri
    ├── review/    Hasil deteksi, koreksi label, lokasi, simpan
    ├── picker/    Tandai lokasi manual di peta
    ├── gallery/   Grid/list, cari, filter, urutkan
    ├── detail/    Foto + bounding box, edit, hapus, bagikan
    ├── journal/   Statistik & riwayat
    └── profile/   Tema, ambang AI, ekspor JSON
```

## Catatan

- Model dasar ML Kit mengenali kategori umum seperti Cat, Dog, Bird, dan Butterfly, tetapi belum bisa membedakan ras atau hewan endemik. Kalau AI salah atau tidak mendeteksi apa-apa, kamu tetap bisa memberi label sendiri. Setiap koreksi tercatat (`aiLabel` dibandingkan dengan `animalLabel`), dan persentase deteksi yang diterima tanpa koreksi ditampilkan di layar Jurnal.
- APK debug berukuran besar karena berisi native library untuk empat ABI dan kode yang belum di-minify. Untuk rilis, gunakan AAB (`./gradlew bundleRelease`) supaya Play Store memecahnya per ABI.
