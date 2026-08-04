package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/**
 * Feed/Discover/Inbox/Profile ekranlarının 3. deneme sonrası KULLANICI RAPORU:
 * Material3'ün `TopAppBarDefaults.enterAlwaysScrollBehavior()` +
 * `Modifier.nestedScroll(...)` deseni (renk uyuşmazlığı düzeltilmiş, nestedScroll
 * LazyColumn'a taşınmış hâliyle bile) bar'ı TAM olarak gizlemiyordu — üstte
 * "kararmış" bir şerit kalıyordu (bkz. FeedScreen.kt'nin eski scrollBehavior
 * yorumu: PullToRefreshBox'ın KENDİ nestedScroll'uyla etkileşimi heightOffset'in
 * tam heightOffsetLimit'e ulaşmasını EKSİLTEBİLİYORDU). Bu, nested-scroll tabanlı
 * mekanizmanın diğer bileşenlerle (StoryBar, PullToRefreshBox, arama modu) ata
 * bağlantı sırası gibi kırılgan detaylara bağımlı, GÜVENİLMEZ bir yaklaşım
 * olduğunun 2. kanıtıydı.
 *
 * KARAR: nested-scroll TAMAMEN TERK EDİLDİ. Bunun yerine [LazyListState]'in
 * scroll POZİSYONUNU (delta paylaşımı değil) izleyen, çok daha basit ve
 * öngörülebilir bir "yukarı mı aşağı mı kaydırılıyor" mantığı: her scroll
 * pozisyonu `index * 100_000 + offset` ile TEK bir monotonik karşılaştırma
 * anahtarına indirgenir (index değişince offset sıfırlanır — SADECE offset
 * delta'sına bakmak bu noktada yanlış yön sinyali verirdi, index+offset'i
 * birleştiren bir anahtar bu sorunu ortadan kaldırır). Anahtar önceki anahtardan
 * küçük/eşitse YUKARI kaydırılıyor demektir → bar görünür; büyükse AŞAĞI
 * kaydırılıyor demektir → bar gizlenir. Liste en üstteyken (index == 0) bar HER
 * ZAMAN görünür tutulur.
 */
@Composable
fun rememberTopBarVisibility(listState: LazyListState): State<Boolean> {
    val isVisible = remember { mutableStateOf(true) }
    var previousKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val currentKey = index * 100_000 + offset
                isVisible.value = index == 0 || currentKey <= previousKey
                previousKey = currentKey
            }
    }

    return isVisible
}

/**
 * `Scaffold`'un `topBar` slotuna verilecek, görünürlüğü [visible] ile kontrol
 * edilen sarmalayıcı — bar tam gizliyken layout'ta YER KAPLAMASIN diye
 * `AnimatedVisibility` kullanılır (İÇERİĞİN ÜSTÜNE binen bir offset/alpha
 * hilesi DEĞİL, gerçek layout-boyutu sıfıra iner). Kayarak gelip gitme +
 * fade — web'in navbar.js'indeki yumuşak geçişle AYNI his.
 */
@Composable
fun HideableTopBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        content()
    }
}
