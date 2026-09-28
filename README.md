# Sosyal Medya — Native Android

<!-- TODO: ekran görüntüsü eklenecek -->

## Açıklama

Sosyal Medya — Native Android, `sosyal-medya` (Flask + Supabase) backend'ine
bağlanan tam native bir Android istemcisidir. Akış, keşfet, Reels, 24 saatlik
hikâyeler, bireysel ve grup mesajlaşması ile 1:1 (WebRTC) ve grup (LiveKit)
sesli/görüntülü arama gibi bir sosyal medya uygulamasının çekirdek özelliklerini
Kotlin + Jetpack Compose ile sunar. Veri tarafında Retrofit/OkHttp ile REST,
Supabase Realtime ile canlı mesaj teslimi ve arama sinyalleşmesi, Room ile feed
önbelleği kullanılır. Kimlik doğrulama opak Bearer token ve Credential Manager
üzerinden Google Sign-In ile yapılır. Uygulama Play Store dışında GitHub Releases
üzerinden APK olarak dağıtılır ve yama (delta) tabanlı uygulama içi güncellemeyi
destekler.

`native-app/` dizini app'in yaşadığı yer — repo kökü değil.

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
- **Ses çıkışı seçimi:** `com.twilio:audioswitch` (LiveKit'in `AudioSwitchHandler`'ı üzerinden gelen geçişli bağımlılık) — hoparlör/kulaklık/telefon/Bluetooth; paylaşılan UI `ui/components/AudioOutputButton.kt`
- **Push bildirimi:** Firebase Cloud Messaging (`FcmService.kt`)
- **Çökme telemetrisi:** Firebase Crashlytics (aynı Firebase projesi, ek konsol kurulumu gerekmez)
- **`minSdk 26` / `compileSdk 36` / `targetSdk 36`**, Kotlin 2.1.20, AGP 8.9.1, Gradle 8.11.1, Compose BOM 2024.12.01

## Kurulum

### Gereksinimler

- JDK 17
- Android Studio (güncel bir sürüm — Kotlin 2.1.20/AGP 8.9.1 destekli)
- Android SDK (`compileSdk`/`targetSdk` 36, `minSdk` 26 — Android Studio SDK Manager ile kurulur)

`native-app/app/google-services.json` (Firebase Cloud Messaging + Crashlytics için)
repoya zaten dahil — ekstra bir kurulum adımı gerekmez.

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
├── tools/                          # release.ps1 (build->yama üret->cihaz kod yoluyla doğrula->yükle) + PatchVerify.java + README.md
└── app/src/main/java/com/umuterayaltay/sosyal/nativeapp/
    ├── MainActivity.kt             # tek Activity, Compose Navigation + App Links/shortcut/paylaşım intent'i
    ├── SosyalApplication.kt        # Application.onCreate() -> ServiceLocator.init() + Coil ImageLoader (GIF)
    ├── ServiceLocator.kt           # tüm Repository/Api/Manager'ın manuel DI kurulumu
    ├── auth/                       # GoogleSignInHelper — Credential Manager sarmalayıcısı
    ├── data/                       # TokenStore, AppLockPreferenceStore, tema/güncelleme tercihleri
    │   └── local/                  # Room DB (sadece Feed cache)
    ├── network/                    # Retrofit arayüzleri — her özellik alanı için ayrı *Api.kt
    │                                # + RetrofitClient, AuthInterceptor, ortak DTO'lar
    ├── repository/                 # Api'yi sarıp ViewModel'e sealed Result sunan katman
    ├── player/                     # Feed video oynatma havuzu (tek paylaşılan ExoPlayer)
    ├── service/                    # FcmService (push) + ActiveConversationTracker
    ├── webrtc/                     # WebRtcCallManager (1:1 arama medya katmanı)
    ├── update/                     # delta (zstd --patch-from) uygulama içi güncelleme — ApkPatcher/Sha256/UpdateManifest/UpdateStorage/ZstdRefPrefix
    ├── widget/                     # SosyalAppWidgetProvider — ana ekran widget'ı
    ├── navigation/                 # AppNavHost — tüm route tanımları
    ├── viewmodel/                  # her ekran için 1 ViewModel (StateFlow expose eder)
    └── ui/
        ├── theme/                  # Compose ColorScheme (light+dark)
        ├── components/             # ekranlar arası paylaşılan composable'lar (medya seçici, link önizleme kartı, tam ekran görüntü/video, ses çıkışı butonu)
        └── screens/                # her ekran kendi dosyasında
```

## Özellikler

- Akış (feed), post oluşturma (en fazla 4 görsel veya 25 MB'a kadar video), post detay, keşfet, hashtag (takip edilebilir) ve trend sayfaları
- Beğeni, yorum, repost, paylaşım, kaydetme — kullanıcı tanımlı kaydetme koleksiyonları, emoji tepkileri, mention, gönderi bildirme
- Kendi postunu düzenleme / silme / sabitleme / arşivleme
- Profil (Gönderiler / Medya / Beğenilenler / Kaydedilenler / Çıkartmalarım / Arşiv sekmeleri), profil düzenleme, takip/takipçi listeleri, takip istekleri, yakın arkadaş listesi, engelleme, susturma, içgörüler (insights)
- Reels — dikey kaydırmalı video akışı
- 24 saatlik hikâyeler: kamera ile oluşturma (foto/basılı-tutmayla video), tam ekran canvas editörü (çoklu metin katmanı, sürükle/zoom/döndür, 6 arka plan + 9 yazı rengi, GIF/çıkartma/anket ekleme), görüntüleme, tepki/yanıt, öne çıkanlar (highlights) ve 24 saat sonra silinmeyen **hikâye arşivi**
- Mesajlaşma: bireysel + grup sohbet, grup oluşturma/yönetme (yeniden adlandırma, üye listesi), mesaj iletme, mesaj arama, çıkartma/GIF gönderimi, konuşma arama/kapatma
- Sesli/görüntülü arama: 1:1 (WebRTC) ve grup (LiveKit), ses çıkışı cihazı seçimi
- Bildirimler + bildirim tercihleri + push (FCM)
- Anketler (post ve hikâye üzerinde)
- Link önizleme kartları (tweet-stili kart dahil), YouTube linkleri için tıkla-oynat gömülü video
- Taslaklar: yazılmamış gönderileri sakla, sonra yayınla
- Çıkartma galerisi: kendi çıkartmanı oluştur/kaydet, profil sekmesinden yönet
- Google ile giriş, iki faktörlü doğrulama (2FA — TOTP ile kayıt/doğrulama/kapatma), aktif oturumlar ekranı, şifremi unuttum akışı, hesabı deaktive et / yeniden aktifleştir
- Aydınlık/koyu/sistem teması
- Biyometrik/PIN **uygulama kilidi** (Ayarlar'dan açılır, soğuk başlangıçta ister)
- Ana ekran **widget'ı** (tek işlevi: "Yeni Gönderi" kısayolu) ve launcher **App Shortcuts** (uzun bas → Yeni Gönderi / Bildirimler)
- **App Links** (`https://sosyalmedyadeneme.onrender.com` altındaki paylaşım linkleri uygulamayı doğrudan açar) ve paylaşım hedefi olarak kabul edilme
- Uygulama içi güncelleme kontrolü (GitHub Releases API üzerinden)

## Sürümler / Releases

Play Store dışında dağıtılıyor — APK'lar bu reponun GitHub Releases sayfasına
yükleniyor (örn. `native-v0.1.0`). Yayın APK'sı kalıcı olarak arm64-v8a'ya
daraltılmıştır (122→58 MB, `-PreleaseAbi` bayrağıyla kapılı; dev/emülatör
build'i etkilenmez).

Uygulama içi güncelleme kontrolü aynı Releases API'sini kullanır, ama tam
APK yerine önce **delta (ikili) yama** dener: zstd `--patch-from` ile mevcut
sürümle yeni sürüm arasındaki fark indirilir (gerçek ölçümde tek satırlık
bir değişiklik için ~300 KB). Cihaz tarafı, zstd-jni'nin APK'ya gömdüğü
`.so`'nun `ZSTD_DCtx_refPrefix` fonksiyonuna JNA ile doğrudan bağlanır
(NDK/C derlemesi gerekmez). İndirilen dosya SHA-256 ile doğrulanır, gerçek
ilerleme göstergesi + iptal butonu vardır, ve delta uygulanamazsa görünür
şekilde tam APK indirmeye düşülür. `REQUEST_INSTALL_PACKAGES` izni kuruluma
gerekli. `native-app/tools/release.ps1` tüm yayın akışını (eski APK'yı
yakala → build → yama üret → cihaz kod yoluyla doğrula → yükle) tek komuda
toplar.
