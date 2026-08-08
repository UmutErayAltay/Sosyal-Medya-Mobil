package com.umuterayaltay.sosyal.nativeapp

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class SosyalApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
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
