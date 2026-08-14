package com.umuterayaltay.sosyal.nativeapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Projede İLK instrumented (cihaz/emülatör gerektiren) test dosyası
 * (2026-08-14, yayın öncesi denetim: "androidTest tamamen boş").
 *
 * ⚠️ BU DOSYA BU OTURUMDA ÇALIŞTIRILAMADI — bağlı cihaz/emülatör yoktu,
 * sadece derleme kontrolü (`compileDebugAndroidTestKotlin`) yapıldı.
 * `app/src/test`'teki JVM unit testlerinin (MessageTimeFormatTest,
 * UpdateManifestTest, Sha256Test — hepsi gerçekten ÇALIŞTIRILIP geçti)
 * AKSİNE, bu dosyanın gerçekten yeşil geçtiği CİHAZDA doğrulanmalı:
 *
 *     cd native-app
 *     .\gradlew.bat connectedDebugAndroidTest
 *
 * Bilinçli olarak MİNİMAL tutuldu: ServiceLocator.init() gerçek ağ/DB
 * çağrıları yapan repository'ler kuruyor (bkz. ServiceLocator.kt init()),
 * bu yüzden "giriş ekranında X yazısı görünüyor" gibi içerik-spesifik bir
 * assertion YAZILMADI (gerçek kurulu durumu bilmeden — token var mı, hangi
 * ekrana yönleniyor — yanlış varsayım riski yüksek). Tek iddia: Activity
 * çöküp crash-loop'a girmeden RESUMED durumuna ulaşıyor.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun uygulamaCokmedenAciliyor() {
        // createAndroidComposeRule Activity'yi zaten başlatıp RESUMED'a
        // getiriyor — buraya ulaşabilmek TEK BAŞINA anlamlı bir smoke test
        // (ServiceLocator.init() + ilk composition + navigasyon kararı
        // hiçbiri exception fırlatmadı demek).
        composeRule.waitForIdle()
    }
}
