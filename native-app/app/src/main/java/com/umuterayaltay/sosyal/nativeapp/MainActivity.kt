package com.umuterayaltay.sosyal.nativeapp

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.umuterayaltay.sosyal.nativeapp.data.ThemeMode
import com.umuterayaltay.sosyal.nativeapp.navigation.AppNavHost
import com.umuterayaltay.sosyal.nativeapp.ui.screens.AppLockScreen
import com.umuterayaltay.sosyal.nativeapp.ui.theme.SosyalNativeTheme
import kotlinx.coroutines.launch

/** Share-target (2026-08-21) — Android paylaş menüsünden gelen içerik.
 * `text` (EXTRA_TEXT) ve `imageUri` (EXTRA_STREAM, image mimeType'ı) AYRI
 * intent-filter'lardan gelir ama ikisi de AYNI şekilde CreatePostScreen'i
 * önceden-doldurulmuş açar (bkz. MainActivity.consumeShareIntent()). */
data class PendingShareContent(val text: String?, val imageUri: Uri?)

// Uygulama kilidi (2026-08-21) — BiometricPrompt androidx.fragment.app.FragmentActivity
// GEREKTİRİYOR (internal olarak headless bir Fragment kullanıyor), sade
// ComponentActivity YETMİYOR. FragmentActivity zaten ComponentActivity'nin
// alt sınıfı — setContent()/enableEdgeToEdge() gibi mevcut TÜM Compose
// entegrasyonu DOKUNULMADAN çalışmaya devam ediyor.
class MainActivity : FragmentActivity() {

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

    // Share-target (2026-08-21) — pendingDeepLinkRoute ile AYNI desen/gerekçe
    // (bkz. yukarıdaki yorum) — AYRI tutuldu, ikisi AYNI anda gelemez (biri
    // ACTION_VIEW biri ACTION_SEND).
    private val pendingShareContent = mutableStateOf<PendingShareContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkRoute.value = consumeDeepLinkRoute(intent)
        pendingShareContent.value = consumeShareIntent(intent)
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
            // Uygulama kilidi (2026-08-21) — [isUnlocked]'ın `remember` bloğu
            // SADECE composition İLK kurulduğunda çalışır: soğuk başlangıçta
            // (Activity'nin KENDİSİ yeniden oluştuğunda) tazelenir, arka
            // plandan öne dönüşte (composition CANLI kaldığı sürece)
            // TEKRAR tetiklenmez — kullanıcının "her soğuk başlangıçta,
            // arka plandan dönüşte DEĞİL" kararı BÖYLE uygulanıyor.
            val appLockEnabled by ServiceLocator.appLockPreferenceStore.enabled.collectAsState()
            var isUnlocked by remember { mutableStateOf(!appLockEnabled) }
            SosyalNativeTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isUnlocked) {
                        val deepLinkRoute by pendingDeepLinkRoute
                        val shareContent by pendingShareContent
                        AppNavHost(
                            pendingDeepLinkRoute = deepLinkRoute,
                            onDeepLinkConsumed = { pendingDeepLinkRoute.value = null },
                            pendingShareContent = shareContent,
                            onShareConsumed = { pendingShareContent.value = null },
                        )
                    } else {
                        AppLockScreen(
                            onUnlockClick = {
                                showBiometricPrompt(onSuccess = { isUnlocked = true })
                            },
                        )
                    }
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
        val share = consumeShareIntent(intent)
        setIntent(intent)
        if (route != null) pendingDeepLinkRoute.value = route
        if (share != null) pendingShareContent.value = share
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

    /** Share-target (2026-08-21) — AndroidManifest.xml'deki iki SEND
     * intent-filter'ının (text/plain, image mime tipi) karşılığı. `EXTRA_STREAM`
     * (`@Suppress` GEREKMEZ — `getParcelableExtra` API 33+'ta deprecated
     * ama minSdk'de hâlâ ana yol, TIRAMISU dalı ayrıca ele alınıyor).
     * ACTION_SEND DIŞINDAKİ (ör. normal VIEW/MAIN) bir intent'te null döner
     * — consumeDeepLinkRoute()'la AYNI "eşleşmezse sessizce yok say" ilkesi. */
    private fun consumeShareIntent(intent: Intent?): PendingShareContent? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val imageUri = if (intent.type?.startsWith("image/") == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else {
            null
        }
        if (text.isNullOrBlank() && imageUri == null) return null
        intent.action = null
        return PendingShareContent(text = text?.takeIf { it.isNotBlank() }, imageUri = imageUri)
    }

    /** Uygulama kilidi (2026-08-21) — BIOMETRIC_STRONG'un yanında
     * DEVICE_CREDENTIAL da (PIN/desen/şifre) kabul edilir — parmak izi
     * kayıtlı olmayan/kapatan bir kullanıcı da cihazının KENDİ ekran kilidiyle
     * açabilsin diye (Ayarlar > "Uygulama Kilidi" toggle'ı için ayrı bir PIN
     * EKRANI İCAT EDİLMEDİ). `setNegativeButtonText` BİLEREK çağrılmıyor —
     * DEVICE_CREDENTIAL dahilken bunu çağırmak BiometricPrompt'ta exception
     * fırlatır (sistem kendi "İptal"ini zaten gösteriyor). Cihazda hiç
     * biyometri/PIN kurulu DEĞİLSE (ör. kullanıcı SONRADAN kaldırmışsa —
     * Ayarlar'daki aç/kapa anındaki kontrol BUNU önler ama sonradan
     * değişebilir) kullanıcıyı SONSUZA DEK KİLİTLİ BIRAKMAK yerine kilit
     * ayarı OTOMATİK kapatılıp içeri alınır (fail-open, projenin başka
     * yerlerindeki AYNI ilke — bkz. touch_session()/api_login_required
     * yorumları). */
    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Uygulama kilidi")
            .setSubtitle("Devam etmek için kimliğini doğrula")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                    ) {
                        ServiceLocator.appLockPreferenceStore.setEnabled(false)
                        onSuccess()
                    }
                    // Diğer hatalar (kullanıcı iptal etti, çok fazla deneme vb.)
                    // — kilitli ekranda kalınır, "Kilidi Aç" butonu tekrar dener.
                }

                override fun onAuthenticationFailed() {
                    // Yanlış parmak izi/yüz — sistem kendi tekrar deneme UI'ını
                    // gösteriyor, burada ek bir şey YAPILMASI GEREKMİYOR.
                }
            },
        )
        prompt.authenticate(promptInfo)
    }
}
