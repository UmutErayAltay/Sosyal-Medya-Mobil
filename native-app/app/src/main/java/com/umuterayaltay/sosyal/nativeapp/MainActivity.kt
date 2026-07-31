package com.umuterayaltay.sosyal.nativeapp

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.umuterayaltay.sosyal.nativeapp.navigation.AppNavHost
import com.umuterayaltay.sosyal.nativeapp.ui.theme.SosyalNativeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
