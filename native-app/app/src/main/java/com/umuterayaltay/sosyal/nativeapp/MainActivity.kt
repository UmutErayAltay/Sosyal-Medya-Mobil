package com.umuterayaltay.sosyal.nativeapp

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.umuterayaltay.sosyal.nativeapp.data.ThemeMode
import com.umuterayaltay.sosyal.nativeapp.navigation.AppNavHost
import com.umuterayaltay.sosyal.nativeapp.ui.theme.SosyalNativeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Android 13+/API 33'te bildirim gösterebilmek için runtime izin şart
    // (bkz. service/FcmService.kt showNotification() — izin yoksa notify()
    // SecurityException fırlatır). Reddedilirse akış KESİLMEZ, uygulama
    // normal çalışmaya devam eder — sadece sistem bildirimi gösterilmez.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sonucu görmezden gel — kullanıcı reddederse sessizce devam */ }

    // App Links (2026-08-21) — Compose state, AppNavHost'a geçirilir (bkz.
    // routeForDeepLink()). mutableStateOf (rememberSaveable DEĞİL) BİLEREK:
    // bir config change'de (ör. döndürme) Activity yeniden oluşur, onCreate()
    // AŞAĞIDA intent'ten yeniden türetir — Intent zaten Android tarafından
    // recreation boyunca KORUNUYOR (setIntent ile güncellenen AYNI nesne).
    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkRoute.value = consumeDeepLinkRoute(intent)
        requestNotificationPermissionIfNeeded()
        registerFcmTokenIfLoggedIn()
        // enableEdgeToEdge()'in VARSAYILANI (parametresiz) status/navigation
        // bar'a yarı saydam bir "scrim" çiziyor - dark modda bu scrim neredeyse
        // siyah (~%50 alfa) oluyor ve Compose'un KENDİ NavigationBar/TopAppBar
        // arka plan rengiyle üst üste binince tam da kullanıcının bildirdiği
        // "üst bar çok büyük/koyu" + "alt barın üstünde ince siyah çizgi"
        // görünümünü yaratıyor - iki ayrı bug DEĞİL, TEK kök neden. Şeffaf
        // scrim vererek Compose'un kendi tema renklerinin çizilmesine izin
        // veriliyor.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            // ThemePreferenceStore.themeMode bir StateFlow — kullanıcı Ayarlar'dan
            // tema değiştirdiğinde uygulama yeniden başlatılmadan burada anında
            // yansır (bkz. ThemePreferenceStore.kt / SettingsScreen.kt yorumları).
            val themeMode by ServiceLocator.themePreferenceStore.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SosyalNativeTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val deepLinkRoute by pendingDeepLinkRoute
                    AppNavHost(
                        pendingDeepLinkRoute = deepLinkRoute,
                        onDeepLinkConsumed = { pendingDeepLinkRoute.value = null },
                    )
                }
            }
        }
    }

    /** Uygulama zaten açıkken (sıcak başlangıç) bir link tıklanırsa Android
     * onCreate()'i TEKRAR çağırmaz, bu callback'i çağırır — soğuk başlangıçla
     * (onCreate) AYNI tüketim mantığı burada da uygulanır. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val route = consumeDeepLinkRoute(intent)
        setIntent(intent)
        if (route != null) pendingDeepLinkRoute.value = route
    }

    private fun requestNotificationPermissionIfNeeded() {
        // API 33 altında izin sistemi YOK (no-op) — bildirimler manifest
        // deklarasyonu yeterliyle gösterilebilir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Kullanıcı ÖNCEDEN giriş yapmışsa (soğuk başlangıçta TokenStore'da zaten
     * geçerli bir bearer token varsa) FCM token'ı yine de sunucuya kaydedilmeli
     * — AuthViewModel.login() SADECE yeni bir login akışında tetiklenir, bu
     * cihazda daha önce giriş yapılmış bir oturumu KAPSAMAZ (bkz. görev notu). */
    private fun registerFcmTokenIfLoggedIn() {
        if (!ServiceLocator.authRepository.isLoggedIn()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                ServiceLocator.pushRepository.registerToken(token)
            }
        }
    }

    /** App Links (2026-08-21) — gelen Intent'in Uri'sini (AndroidManifest.xml'deki
     * autoVerify intent-filter'ının EŞLEŞTİRDİĞİ, assetlinks.json ile doğrulanmış
     * https://sosyalmedyadeneme.onrender.com/... linki) AppNavHost route'una
     * çevirir — SADECE üç yol destekleniyor (web'in `/post/<id>`, `/u/<username>`,
     * `/hashtag/<tag>` route'larıyla BİREBİR, native'in KARŞILIĞI olan
     * "postDetail/{id}"/"profile/{username}"/"hashtag/{tag}" route'ları — bkz.
     * AppNavHost.kt). Eşleşmeyen/eksik bir link (ör. tek path segment'i,
     * kapsam dışı bir yol) null döner — uygulama normal soğuk-başlangıç
     * davranışına (ROUTE_MAIN/ROUTE_LOGIN) düşer, kırılma YOK. `intent.data`
     * BİLEREK null'a set edilir — çağıran taraf (onCreate/onNewIntent) AYNI
     * Intent nesnesini bir config change'de (döndürme) TEKRAR görebilir,
     * data temizlenmezse deep link'e HER SEFERİNDE yeniden navigate edilirdi
     * (aynı postu üst üste yığma bug'ı). */
    private fun consumeDeepLinkRoute(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val segments = uri.pathSegments
        val route = when {
            segments.size >= 2 && segments[0] == "post" -> "postDetail/${segments[1]}"
            segments.size >= 2 && segments[0] == "u" -> "profile/${segments[1]}"
            segments.size >= 2 && segments[0] == "hashtag" -> "hashtag/${segments[1]}"
            else -> null
        }
        if (route != null) intent.data = null
        return route
    }
}
