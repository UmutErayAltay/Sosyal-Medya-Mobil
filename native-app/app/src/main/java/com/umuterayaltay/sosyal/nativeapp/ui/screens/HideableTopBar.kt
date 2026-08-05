package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// web'in navbar.js'indeki AYNI iki sabit: touchmove'daki "10px tolerans
// (titremeyi önler)" yorumunun native karşılığı (JITTER_THRESHOLD) ve
// HIDE_THRESHOLD (sayfa en üstteyken gizlenmeyi engelleyen eşik).
private val JITTER_THRESHOLD_DP = 12.dp
private val TOP_SAFE_ZONE_DP = 80.dp
private const val TOP_BAR_ANIM_MS = 250

/**
 * Feed/Discover/Inbox/Profile ekranlarının 3. deneme sonrası KULLANICI RAPORU:
 * Material3'ün `TopAppBarDefaults.enterAlwaysScrollBehavior()` +
 * `Modifier.nestedScroll(...)` deseni bar'ı TAM olarak gizlemiyordu — nested-scroll
 * TAMAMEN TERK EDİLDİ (bkz. bu dosyanın önceki hâli / git geçmişi), [LazyListState]'in
 * scroll POZİSYONUNU izleyen elle bir "yukarı mı aşağı mı" tespiti getirildi.
 *
 * 4. TUR — KULLANICI RAPORU: "mobildeki hali kasıyor gibi hissettiriyor, web
 * sitesindeki geçiş çok yumuşak". Kök neden, web'in `navbar.js`/`style.css`'i ile
 * KARŞILAŞTIRILINCA netleşti — web'in ASLA sahip olmadığımız 2 koruması var:
 *   1. **Titreme toleransı** (`navbar.js` touchmove: "10px tolerans (titremeyi
 *      önler)") — ÖNCEKİ sürüm HER piksel değişiminde yön kararı veriyordu, bu
 *      yüzden parmağın doğal ufak ileri-geri titremesi bile bar'ı sürekli
 *      gizle/göster arasında ZIPLATIYOR, her ziplama YENİ bir animasyon
 *      başlatıp ÖNCEKİsini kesintiye uğratıyordu — "kasma" hissi TAM OLARAK
 *      budur. Çözüm: yön sadece kümülatif fark [JITTER_THRESHOLD_DP]'yi
 *      aşınca güncellenir, aşmayan küçük titremeler YOK SAYILIR.
 *   2. **Sabit süre/easing** (`style.css`: `.navbar { transition: transform
 *      0.25s ease; }`) — ÖNCEKİ sürüm `AnimatedVisibility`'nin VARSAYILAN
 *      (spring tabanlı) animasyon spec'ini kullanıyordu, bu web'in sabit
 *      250ms `ease` geçişinden FARKLI bir "his" veriyordu. [TOP_BAR_ANIM_MS] +
 *      `FastOutSlowInEasing` (CSS `ease`'e en yakın standart Compose eğrisi)
 *      ile SABİTLENDİ.
 */
@Composable
fun rememberTopBarVisibility(listState: LazyListState): State<Boolean> {
    val isVisible = remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val jitterThresholdPx = remember(density) { with(density) { JITTER_THRESHOLD_DP.toPx() } }
    val topSafeZonePx = remember(density) { with(density) { TOP_SAFE_ZONE_DP.toPx() } }

    LaunchedEffect(listState) {
        // confirmedKey: SON karar verilen pozisyon — bir sonraki ölçüm bundan
        // eşik kadar UZAKLAŞMADIKÇA (jitter) hiçbir şey değişmez, TABAN da
        // kaymaz (aksi halde küçük titremeler zamanla birikip yanlışlıkla
        // eşiği aşabilirdi).
        var confirmedKey = 0f
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val currentKey = index * 100_000f + offset
                when {
                    // web'in HIDE_THRESHOLD'ıyla AYNI gerekçe: listenin en
                    // başındayken bar HER ZAMAN görünür kalır.
                    index == 0 && offset < topSafeZonePx -> {
                        isVisible.value = true
                        confirmedKey = currentKey
                    }
                    currentKey - confirmedKey > jitterThresholdPx -> {
                        isVisible.value = false
                        confirmedKey = currentKey
                    }
                    currentKey - confirmedKey < -jitterThresholdPx -> {
                        isVisible.value = true
                        confirmedKey = currentKey
                    }
                    // |fark| <= eşik: web'in "10px tolerans" ile AYNI —
                    // titreme sayılır, YOK SAYILIR (confirmedKey de KAYMAZ).
                }
            }
    }

    return isVisible
}

/**
 * `Scaffold`'un `topBar` slotuna verilecek, görünürlüğü [visible] ile kontrol
 * edilen sarmalayıcı — bar tam gizliyken layout'ta YER KAPLAMASIN diye
 * `AnimatedVisibility` kullanılır (İÇERİĞİN ÜSTÜNE binen bir offset/alpha
 * hilesi DEĞİL, gerçek layout-boyutu sıfıra iner). Süre/easing web'in
 * `transition: transform 0.25s ease`'iyle BİREBİR eşleşsin diye SABİT
 * `tween(TOP_BAR_ANIM_MS, FastOutSlowInEasing)` kullanılır (Compose'un
 * varsayılan spring spec'i FARKLI/daha "zıplayan" bir his veriyordu).
 */
@Composable
fun HideableTopBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animSpec = remember { tween<Float>(durationMillis = TOP_BAR_ANIM_MS, easing = FastOutSlowInEasing) }
    val offsetSpec = remember { tween<androidx.compose.ui.unit.IntOffset>(durationMillis = TOP_BAR_ANIM_MS, easing = FastOutSlowInEasing) }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(animationSpec = offsetSpec, initialOffsetY = { -it }) +
            fadeIn(animationSpec = animSpec),
        exit = slideOutVertically(animationSpec = offsetSpec, targetOffsetY = { -it }) +
            fadeOut(animationSpec = animSpec),
    ) {
        content()
    }
}
