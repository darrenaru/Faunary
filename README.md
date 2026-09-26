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
3. Jalankan `./gradlew installDebug`. Kalau pakai perangkat MIUI, aktifkan *Install via USB* di Developer options.

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

## Struktur

```
app/src/main/java/com/faunary/app/
├── data/        Room entity/DAO, PhotoStorage, Settings
├── domain/      Kategori hewan, katalog label → nama Indonesia, statistik
├── ml/          AnimalDetector (ML Kit)
├── location/    GPS + reverse geocoding
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
