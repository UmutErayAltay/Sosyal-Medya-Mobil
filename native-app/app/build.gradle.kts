plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    // Faz 5 Dalga 4A: FCM push bildirimi — apply false YOK burada (kök
    // build.gradle.kts'teki tersine), asıl uygulama BU modülde gerçekleşir.
    id("com.google.gms.google-services")
}

android {
    // namespace: Kotlin/Java kaynak paketi (BuildConfig/R sınıfları buraya üretilir).
    // "native" segmenti Java'da ayrılmış bir anahtar kelime olduğu için
    // applicationId'de KASITLI kullanılsa da namespace'te KULLANILAMAZ — aksi
    // halde üretilen BuildConfig.java "package ...sosyal.native;" satırıyla
    // derleme hatası verir. Bu yüzden namespace ayrı tutuldu (bkz. rapor).
    namespace = "com.umuterayaltay.sosyal.nativeapp"
    compileSdk = 36

    defaultConfig {
        // KASITLI: TWA'nın applicationId'i (com.umuterayaltay.sosyal) ile
        // ÇAKIŞMASIN diye farklı — aynı cihazda yan yana kurulabilsinler.
        applicationId = "com.umuterayaltay.sosyal.native"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Delta güncelleme planı Aşama 1a: gerçek telefonların hiçbiri
        // x86/x86_64 kullanmıyor ama debug APK'nın ~%52'si (54 MB) bu iki
        // ABI'nin native kütüphaneleri (WebRTC/LiveKit) — Aşama 0'da ölçüldü.
        // `splits` DEĞİL (çıktı dosya adını app-arm64-v8a-debug.apk yapıp
        // release.ps1'in elle akışını bozardı) — bayrak YOKKEN (emülatör/
        // geliştirme) 4 ABI'nin hepsi üretilmeye devam eder, SADECE
        // `-PreleaseAbi=arm64-v8a` ile çağrılınca (release.ps1) tek ABI'ye
        // düşer. 122 MB -> ~58 MB (yayın APK'sı), dex'in DEFLATE'li kalmasına
        // rağmen (dex-STORED'a GEREK YOK, bkz. Aşama 0 ölçümü).
        (project.findProperty("releaseAbi") as String?)?.let { abi ->
            ndk { abiFilters += abi }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug loglama (HttpLoggingInterceptor BODY seviyesi) sadece
            // debug build'de aktif — bkz. network/RetrofitClient.kt (BuildConfig.DEBUG kontrolü).
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose / UI
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Görsel yükleme (post kartlarındaki opsiyonel görsel için)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // 2026-08-08 (kullanıcı raporu: "gifler hareket etmiyor") — coil-compose
    // TEK BAŞINA GIF'leri animasyonsuz (SADECE ilk kare) çözer, animasyon için
    // ayrı bir decoder (ImageDecoderDecoder/GifDecoder) GEREKİYOR — bkz.
    // SosyalApplication.kt ImageLoaderFactory.
    implementation("io.coil-kt:coil-gif:2.7.0")

    // CameraX — hikaye oluşturma ekranında Instagram tarzı canlı kamera
    // önizlemesi + tek dokunuşla foto / basılı-tutmayla video çekimi (bkz.
    // StoryCreateScreen.kt). 1.4.1 seçildi: minSdk 26/compileSdk 36 ile uyumlu
    // güncel stabil sürüm, video kaydı için gereken Recorder/VideoCapture API'si
    // bu sürüm hattında stabil (camera-video 1.4.x). Hepsi AYNI sürüm numarası
    // ile alınıyor (CameraX'in kendi gerekliliği — modüller birbirine sıkı bağımlı).
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")

    // Ağ katmanı — Retrofit2 + OkHttp + Gson (kotlinx.serialization DEĞİL, spesifikasyon gereği)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Token saklama — EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google ile giriş — Android Credential Manager ("Sign in with Google" native
    // akışı, tarayıcı/WebView YOK). GoogleIdTokenCredential googleid kütüphanesinden
    // gelir, credentials-play-services-auth Credential Manager'ın Play Services
    // implementasyonunu sağlar (bkz. GoogleSignInHelper.kt).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Yerel cache — Room. 2.6.1 -> 2.8.4: Kotlin 2.1.20'ye geçince (bkz. kök
    // build.gradle.kts yorumu) kaptDebugKotlin şu hatayla PATLADI: "Provided
    // Metadata instance has version 2.1.0, while maximum supported version is
    // 2.0.0" — Room 2.6.1'in kapt annotation processor'ı içine gömülü
    // (jarjarred) kotlinx-metadata-jvm okuyucusu Kotlin 2.1.x'in ürettiği
    // @Metadata formatını TANIMIYORDU (supabase-kt'den TAMAMEN bağımsız bir
    // sorun — kendi PostEntity.kt'mizin metadata'sını okurken patlıyordu).
    // 2.8.4 (bu satırın en güncel stabil sürümü, Google Maven metadata'sından
    // doğrulandı) güncel kotlinx-metadata-jvm ile geliyor, gerçek build ile
    // doğrulandı.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    // Video oynatma (Reels) — ExoPlayer + PlayerView, Compose BOM 2024.12.01 ile
    // uyumlu stabil sürüm (1.5.1). VerticalPager için ek bağımlılık GEREKMEZ,
    // androidx.compose.foundation.pager zaten Compose foundation'ın parçası.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Gerçek Supabase Realtime (Faz 4 sonu — mesajlaşmada polling fallback'li
    // canlı mesaj teslimi, bkz. RealtimeConnectionManager). Bu proje İLK KEZ
    // Supabase'e DOĞRUDAN bağlanıyor (o ana kadar her şey Flask /api/v1 REST
    // katmanı üzerinden gidiyordu) — sadece realtime-kt modülü kullanılıyor,
    // auth-kt/postgrest-kt YOK (JWT'yi kendi Flask backend'imizin
    // /realtime-token uç noktasından alıyoruz, Supabase Auth'a native'den HİÇ
    // login olunmuyor). 3.1.4 seçildi: setAuth() (periyodik JWT yenileme) İLK
    // kez 3.1.0'da eklendi (GitHub kaynağı okunarak doğrulandı — 3.0.x'te
    // yok), 3.1.4 o satırın son yaması. Ktor engine olarak OkHttp seçildi
    // (projede zaten OkHttp var, resmi Troubleshooting dokümanı Android'de
    // WebSocket için OkHttp/CIO'yu öneriyor — ktor-client-android eski engine
    // WebSocket'i DESTEKLEMİYOR). kotlinx.serialization eklentisi BİLİNÇLİ
    // olarak eklenmedi: postgres_changes payload'ı (JsonObject) Gson'a
    // toString() ile aktarılıp MessageDto'ya çevriliyor (bkz.
    // RealtimeConnectionManager) — projenin "Retrofit+Gson, kotlinx.serialization
    // DEĞİL" kararıyla (bkz. ServiceLocator.kt yorumu) tutarlı kalınsın diye.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")

    // Faz 5 Dalga 4A: FCM push bildirimi — google-services.json ZATEN yerinde
    // (paket com.umuterayaltay.sosyal.native, proje gen-lang-client-07070261-4a9fa).
    // BOM ile firebase-messaging-ktx'in sürümü otomatik hizalanır (ayrı sürüm
    // numarası YAZILMAZ — BOM'un garantisi bu).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Grup sesli/görüntülü arama (native görev — LiveKit) — backend zaten hazır
    // (app/api_v1/messaging.py api_call_token(), commit 5a03122): POST
    // messages/conversations/{id}/call-token bir LiveKit JWT + url + room adı
    // döner (bkz. MessagingApi.getCallToken()). Coordinate'ler context7 MCP ile
    // (/livekit/client-sdk-android, /livekit/components-android) DOĞRULANDI —
    // tahmin edilmedi. Core SDK "io.livekit:livekit-android" (Room.connect(),
    // LocalParticipant.setCameraEnabled/setMicrophoneEnabled) + Compose
    // yardımcıları "io.livekit:livekit-android-compose-components"
    // (rememberParticipants/rememberParticipantTrackReferences/VideoTrackView —
    // bkz. CallScreen.kt). CameraX uzantısı (pinch-to-zoom/torch) ve
    // track-processors (virtual background) BİLİNÇLİ olarak eklenmedi — bu
    // turun kapsamı SADECE temel grup görüşmesi (aç/kapa mikrofon-kamera +
    // video grid), gereksiz bağımlılık şişmesin diye. JitPack maven deposu
    // resmi kurulum talimatına göre settings.gradle.kts'e ZORUNLU olarak
    // eklendi (io.livekit paketi bu depodan çözülüyor) — CLAUDE.md'nin
    // "kararlılık > özellik hızı" ilkesi gereği bu adım atlanmadı, aksi halde
    // gerçek `gradlew assembleDebug` bağımlılık çözümlemesinde BAŞARISIZ olurdu.
    implementation("io.livekit:livekit-android:2.27.0")
    implementation("io.livekit:livekit-android-compose-components:2.4.0")

    // 1:1 sesli/görüntülü arama (native görev — WebRTC + Supabase Realtime
    // broadcast) — TAMAMEN AYRI bir sistem LiveKit'ten (grup araması yukarıda,
    // dokunulmadı): web tarafı (app/static/js/call.js) saf WebRTC + Supabase
    // Realtime broadcast sinyalleşmesi kullanıyor, backend'de bu iş için hiç
    // endpoint YOK. Coordinate'ler context7 MCP ile (/getstream/webrtc-android)
    // DOĞRULANDI: "io.getstream:stream-webrtc-android" (org.webrtc.* — standart
    // Google WebRTC Java API'sinin derlenmiş hâli, PeerConnectionFactory/
    // Camera2Capturer/SurfaceViewRenderer/EglBase) + AYRI bir "-compose" modülü
    // (VideoRenderer composable — videoTrack/eglBaseContext/rendererEvents
    // alır, LiveKit'in VideoTrackView'ine denk). KTX modülü (coroutine
    // sarmalayıcıları) BİLİNÇLİ olarak eklenmedi — SdpObserver/PeerConnection.
    // Observer callback'leri projenin zaten kullandığı AuthRepository.
    // currentFcmToken() deseniyle (suspendCancellableCoroutine) elle sarmalandı,
    // gereksiz bağımlılık şişmesin diye.
    implementation("io.getstream:stream-webrtc-android:1.3.9")
    implementation("io.getstream:stream-webrtc-android-compose:1.3.9")

    // Link önizleme kartında YouTube gömülü video oynatma (native görev) —
    // ÖNCE çıplak WebView + loadDataWithBaseURL ile denendi (kullanıcının
    // web tarafında ÇALIŞAN "sayfaya gömülü iframe" yaklaşımının native
    // karşılığı olarak) ama gerçek cihazda YİNE siyah ekran raporlandı —
    // web araştırması (Compose+SurfaceView offscreen-compositing teorisi)
    // yeterli KANIT değildi, kod incelemesiyle bazı varsayımları çürütüldü
    // (PostVideoPlayer'ın AYNI clip-ata deseniyle ExoPlayer sorunsuz
    // çalışıyor). Cihaz erişimi olmadan çıplak WebView'i daha fazla debug
    // etmek yerine, YouTube'un resmi IFrame Player API'sini DOĞRU origin/
    // postMessage yönetimiyle uygulayan, binlerce üretim uygulamasında
    // kullanılan bu kütüphaneye geçildi (Maven Central, JitPack GEREKMEZ —
    // LiveKit'in aksine). Kullanım deseni PostVideoPlayer'daki AndroidView+
    // remember+DisposableEffect(release) ile AYNI, sadece PlayerView yerine
    // YouTubePlayerView.
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // Delta (ikili yama) uygulama içi güncelleme — release.ps1 gerçek
    // `zstd.exe` CLI'siyle `--patch-from` (ZSTD_CCtx_refPrefix) yaması
    // üretir, cihaz da AYNI native fonksiyonu (ZSTD_DCtx_refPrefix)
    // çağırmalı. Bu Java API'sinde YOK — zstd-jni'nin ZstdInputStream.
    // setDict()/ZstdCompressCtx.loadDict() metotları BAŞKA bir native
    // fonksiyona (ZSTD_DCtx_loadDictionary, "sözlük" modu) denk geliyor ve
    // gerçek APK'larda ~700× daha kötü yama boyutu veriyor (ölçüldü, bkz.
    // update/ZstdRefPrefix.kt KDoc'u ve .context/active_context.md). `@aar`
    // ZORUNLU — aynı GAV altında Android .aar'ının yanında 6,4 MB'lık
    // masaüstü .jar'ı da var, `@aar` olmadan Gradle onu çeker.
    // 1.5.7-13 DEĞİL: o sürüm minCompileSdk=37 istiyor (proje compileSdk 36'da,
    // AGP 8.9.1'in desteklediği tavan zaten 36) — derleme AAR metadata
    // kontrolünde patlıyordu. 1.5.7-6 bu kısıtı taşımıyor VE arm64-v8a .so'sunda
    // ihtiyaç duyulan tüm ZSTD_*refPrefix* fonksiyonları aynı şekilde dışa açık
    // (ayrıca doğrulandı, pyelftools ile).
    implementation("com.github.luben:zstd-jni:1.5.7-6@aar")
    // JNA — zstd-jni'nin APK'ya zaten gömdüğü .so'nun içindeki, Java
    // sarmalayıcının AÇMADIĞI ZSTD_DCtx_refPrefix'e doğrudan erişmek için
    // (o .so'nun bu sembolü dışa açtığı `pyelftools` ile ELF .dynsym
    // üzerinden doğrulandı — ek bir native/NDK derlemesi GEREKMİYOR).
    implementation("net.java.dev.jna:jna:5.15.0@aar")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
