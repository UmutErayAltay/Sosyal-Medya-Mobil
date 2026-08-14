package com.umuterayaltay.sosyal.nativeapp

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.umuterayaltay.sosyal.nativeapp.update.UpdateStorage

class SosyalApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // 2026-08-14 (yayın öncesi denetim: "sıfır çökme telemetrisi — bir
        // kullanıcının telefonunda bir şey patlarsa ASLA öğrenilemez") —
        // Crashlytics kendi uncaught-exception handler'ını BURADA (Google
        // Services init aşamasında) otomatik kuruyor, elle bir
        // Thread.setDefaultUncaughtExceptionHandler EKLENMEMELİ (ikisi
        // çakışıp Crashlytics'in çökmeyi yakalamasını engelleyebilir).
        // setCrashlyticsCollectionEnabled(true) burada AÇIKÇA çağrılıyor —
        // varsayılan zaten `true` (debug build'ler dahil, Firebase resmi
        // dokümantasyonuyla doğrulandı), ama bu proje ŞU AN SADECE debug
        // build gönderiyor (sideload, GitHub Releases) — yani "varsayılanı
        // kapatmayı unutma" riski yok, tam tersine niyeti (debug'da da
        // KASITLI açık) satırda görünür kılıyor.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        ServiceLocator.init(this)
        // Delta güncelleme planı: bir önceki kurulumdan/yarım kalan bir
        // indirmeden kalan APK/yama artıklarını temizle — hedef henüz
        // bilinmediği için (checkForUpdate henüz çağrılmadı) HEPSİ silinir.
        UpdateStorage.cleanStale(this)
        // 2026-08-14 (kullanıcı raporu: "ilk açılışta akış kaydırırken
        // takılıyor, sonra düzeliyor") — ExoPlayer havuzunu burada, ana
        // thread'de ama UYGULAMA SOĞUK BAŞLARKEN inşa et (bkz.
        // FeedVideoPlayerPool.warmUp() yorumu) — Feed ekranı ilk kez
        // kaydırılıp ilk video aktif olduğunda artık build() maliyeti
        // ZATEN bitmiş oluyor.
        ServiceLocator.feedVideoPlayerPool.warmUp()
    }

    // 2026-08-08 (kullanıcı raporu: "gifler hareket etmiyor") — Coil'in
    // varsayılan ImageLoader'ı GIF'leri SADECE ilk kare olarak (statik bitmap)
    // çözer, animasyon için TEK BAŞINA yeterli değil. Uygulama Application'ı
    // ImageLoaderFactory implement edince Coil bunu TÜM AsyncImage
    // çağrılarında (app genelinde, tek tek belirtmeye gerek kalmadan)
    // otomatik kullanır. API 28+ platform decoder'ı (ImageDecoderDecoder),
    // minSdk 26-27 için coil-gif'in GifDecoder fallback'i.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
