package com.umuterayaltay.sosyal.nativeapp

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            SosyalNativeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
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
}
