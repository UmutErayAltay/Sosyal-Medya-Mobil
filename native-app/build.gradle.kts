// Top-level build file — TWA projesindeki (repo kökü) build.gradle'dan TAMAMEN
// bağımsız. Bu proje kendi Gradle wrapper'ını (8.11.1, root ile aynı — bu
// sandbox'ta zaten cache'li olduğu için ekstra indirme gerekmiyor) kullanır.
//
// Kotlin 2.0.21 -> 2.1.20: Faz 4 sonu — gerçek Supabase Realtime (bkz.
// app/build.gradle.kts'teki realtime-kt bağımlılığı) eklenirken BİLİNÇLİ
// yapıldı. supabase-kt'nin setAuth() (Realtime JWT'sini periyodik yenileme
// için — bkz. RealtimeConnectionManager) içeren İLK sürümü 3.1.0 (GitHub
// kaynağı okunarak doğrulandı: setAuth Realtime.kt'ye 3.1.0'da eklendi,
// 3.0.x'te yok), ve 3.1.x satırı Kotlin 2.1.10/2.1.20 ile derleniyor (3.0.2
// tam olarak projenin ESKİ 2.0.21'i ile eşleşiyordu ama setAuth'u YOK) — bu
// yüzden projenin kendi Kotlin sürümü, yeni kütüphaneyle uyumlu kalsın diye
// minimum gerekli adımla (2.1.20, tek bir minor sıçrama) yükseltildi.
// plugin.compose SÜRÜMÜ Kotlin ile HER ZAMAN birebir aynı tutulmalı (Compose
// derleyici eklentisi artık Kotlin deposunun bir parçası).
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Faz 5 Dalga 4A: FCM push bildirimi — google-services.json'daki proje
    // bilgilerini (gen-lang-client-07070261-4a9fa) derleme zamanında okuyup
    // BuildConfig/kaynaklara enjekte eden Gradle eklentisi.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
