plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
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

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
