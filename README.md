# Sosyal Medya — Native Android

`sosyal-medya` (Flask + Supabase) backend'ine bağlanan, Kotlin + Jetpack
Compose ile yazılmış tam native Android istemcisi. Backend'in versiyonlu
`/api/v1/` REST katmanını kullanır; backend ayrı bir kardeş repodur
(`sosyal-medya`), kendi README'sinde detaylandırılmıştır.

> Gerçek uygulama kodu **`native-app/`** alt dizininde yaşar (kendi
> `gradlew`/`.gradle` köküyle bağımsız bir Gradle projesi) — repo kökü değil.

## Teknoloji Yığını

- **Dil / UI:** Kotlin, Jetpack Compose (Material3), MVVM (ViewModel + StateFlow)
- **DI:** Manuel (`ServiceLocator.kt` tek noktadan tüm Repository/Api/Manager'ı kurar — Hilt/Dagger yok, MVP fazında bilinçli sadelik kararı)
- **Ağ:** Retrofit2 + Gson + OkHttp (kotlinx.serialization bilinçli olarak eklenmedi)
- **Kimlik doğrulama:** Backend'in `/api/v1/*` uçlarına opak Bearer token (`api_tokens` tablosu, `EncryptedSharedPreferences` ile saklanır); ayrıca Credential Manager ile Google Sign-In
- **Yerel önbellek:** Room (sadece Feed cache — `data/local/`)
- **Gerçek zamanlı:** Supabase Realtime (`realtime-kt`, BOM 3.1.4) — mesajlaşmada `postgres_changes` dinleme (başarısız olursa sessizce polling'e düşer), 1:1 arama sinyalleşmesinde backend'in ürettiği HMAC tabanlı public broadcast kanalları
- **Görsel/GIF yükleme:** Coil (`coil-compose` + `coil-gif`, animasyonlu GIF için ayrı decoder gerekir)
- **Kamera:** CameraX (hikaye oluşturma — canlı önizleme, foto/basılı-tutmayla video)
- **Video oynatma:** Media3/ExoPlayer (Reels, feed içi video — tek paylaşılan player havuzu)
- **1:1 sesli/görüntülü arama:** `stream-webrtc-android` (Google WebRTC'nin Android bindings'i) + Supabase Realtime broadcast sinyalleşmesi
- **Grup sesli/görüntülü arama:** LiveKit (`livekit-android` + `livekit-android-compose-components`, JitPack üzerinden) — 1:1 aramadan tamamen ayrı bir sistem
- **YouTube gömülü oynatma:** `android-youtube-player` (link önizleme kartında, resmi IFrame Player API sarmalayıcısı)
- **Push bildirimi:** Firebase Cloud Messaging (`FcmService.kt`)
- **`minSdk 26` / `compileSdk 36` / `targetSdk 36`**, Kotlin 2.1.20, AGP 8.9.1, Compose BOM 2024.12.01

## Kurulum

### Gereksinimler

- JDK 17
- Android Studio (güncel bir sürüm — Kotlin 2.1.20/AGP 8.9.1 destekli)
- Android SDK (`compileSdk`/`targetSdk` 36, `minSdk` 26 — Android Studio SDK Manager ile kurulur)

`native-app/app/google-services.json` (Firebase Cloud Messaging için) repoya
zaten dahil — ekstra bir kurulum adımı gerekmez.

### Adımlar

1. Android Studio'da **`native-app/`** dizinini proje kökü olarak açın (repo kökünü değil).
2. Gradle sync'i bekleyin — bağımlılıklar `google()`, `mavenCentral()` ve (LiveKit için) JitPack'ten otomatik çözülür.
3. Çalıştırın (Android Studio'dan bir emülatör/cihaza) veya komut satırından debug APK üretin:

```bash
cd native-app
./gradlew assembleDebug
```

APK çıktısı `native-app/app/build/outputs/apk/debug/` altında oluşur.

Backend adresi `network/RetrofitClient.kt` içinde sabit tanımlıdır
(`https://sosyalmedyadeneme.onrender.com/api/v1/`) — farklı bir backend'e
bağlanmak için bu dosya düzenlenir.

## Proje Yapısı

```
native-app/
├── app/build.gradle.kts            # applicationId=com.umuterayaltay.sosyal.native
└── app/src/main/java/com/umuterayaltay/sosyal/nativeapp/
    ├── MainActivity.kt             # tek Activity, Compose Navigation ile tüm ekranlar
    ├── SosyalApplication.kt        # Application.onCreate() -> ServiceLocator.init()
    ├── ServiceLocator.kt           # tüm Repository/Api/Manager'ın manuel DI kurulumu
    ├── auth/                       # Google Sign-In (Credential Manager sarmalayıcısı)
    ├── data/                       # TokenStore, tema/güncelleme tercihleri
    │   └── local/                  # Room DB (sadece Feed cache)
    ├── network/                    # Retrofit arayüzleri — her özellik alanı için ayrı *Api.kt
    │                                # + RetrofitClient, AuthInterceptor, ortak DTO'lar
    ├── repository/                 # Api'yi sarıp ViewModel'e sealed Result sunan katman
    ├── player/                     # Feed video oynatma havuzu (tek paylaşılan ExoPlayer)
    ├── service/                    # FcmService — push token yenileme + bildirim gösterme
    ├── webrtc/                     # WebRtcCallManager (1:1 arama medya katmanı)
    ├── navigation/                 # AppNavHost — tüm route tanımları
    ├── viewmodel/                  # her ekran için 1 ViewModel (StateFlow expose eder)
    └── ui/
        ├── theme/                  # Compose ColorScheme (light+dark)
        ├── components/             # ekranlar arası paylaşılan composable'lar
        └── screens/                # her ekran kendi dosyasında
```

## Özellikler

- Akış (feed), post oluşturma (foto/video, 25 MB'a kadar), post detay, keşfet, hashtag ve trend sayfaları
- Beğeni, yorum, repost, paylaşım, kaydetme (bookmark), emoji tepkileri, mention
- Profil, profil düzenleme, takip/takipçi listeleri, takip istekleri, yakın arkadaş listesi, engellenen kullanıcılar, içgörüler (insights)
- Reels — dikey kaydırmalı video akışı
- 24 saatlik hikayeler: kamera ile oluşturma (foto/basılı-tutmayla video), görüntüleme, öne çıkanlar (highlights)
- Mesajlaşma: bireysel + grup sohbet, grup oluşturma/yönetme, mesaj iletme, mesaj arama, çıkartma/GIF gönderimi
- Sesli/görüntülü arama: 1:1 (WebRTC) ve grup (LiveKit), ses çıkışı cihazı seçimi
- Bildirimler + bildirim tercihleri + push (FCM)
- Anketler
- Link önizleme kartları (tweet-stili kart dahil), YouTube linkleri için tıkla-oynat gömülü video
- Google ile giriş, iki faktörlü doğrulama (2FA), aktif oturumlar ekranı, şifremi unuttum akışı
- Aydınlık/koyu/sistem teması
- Uygulama içi güncelleme kontrolü (GitHub Releases API üzerinden)

## Sürümler / Releases

Play Store dışında dağıtılıyor — debug APK'lar bu reponun GitHub Releases
sayfasına yükleniyor (örn. `native-v0.1.0`). Uygulama içi güncelleme kontrolü
de aynı Releases API'sini kullanıp yeni bir sürüm bulunca APK'yı indirip
kurdurabiliyor (`REQUEST_INSTALL_PACKAGES` izni bunun için gerekli).
