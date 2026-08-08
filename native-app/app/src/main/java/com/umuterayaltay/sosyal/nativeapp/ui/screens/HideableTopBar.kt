package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// web'in navbar.js'indeki AYNI iki sabit: touchmove'daki "10px tolerans
// (titremeyi önler)" yorumunun native karşılığı (JITTER_THRESHOLD) ve
// HIDE_THRESHOLD (sayfa en üstteyken gizlenmeyi engelleyen eşik).
private val JITTER_THRESHOLD_DP = 12.dp
private val TOP_SAFE_ZONE_DP = 80.dp
private const val TOP_BAR_ANIM_MS = 250

// KULLANICI RAPORU (5. tur, ekran görüntüsüyle doğrulandı): "Ana Sayfa" satırı
// ile status bar şeridi arasında koyu/farklı renkte bir çizgi + bar bütün
// olarak gereğinden büyük görünüyor. Kök neden aşağıdaki [OverlayTopBar] ve
// [TopBarSurface] yorumlarında detaylı: bu sabit artık SADECE bir tahmin
// değil, TopBarSurface içindeki gerçek TopAppBar'a `Modifier.height(...)` ile
// DOĞRUDAN uygulanan, gerçekten bağlayıcı bir yükseklik — 64.dp (M3
// varsayılanı) yerine 56.dp (klasik/kompakt app bar yüksekliği, ikon/metin
// boyutlarına dokunmadan rahatça sığıyor) seçildi. private DEĞİL:
// FeedScreen.kt/DiscoverScreen.kt/InboxScreen.kt/ProfileScreen.kt AYNI
// paketten (ui.screens) hem TopAppBar'ın kendi `modifier`ında hem
// contentPadding hesabında bu sabiti reuse eder (import GEREKMEZ).
val TOP_BAR_HEIGHT = 56.dp

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
 * hilesi DEĞİL, gerçek layout-boyutu sıfıra iner).
 *
 * Animasyon/mikro-etkileşim turu: sabit süreli `tween(TOP_BAR_ANIM_MS,
 * FastOutSlowInEasing)` yerine artık spring tabanlı bir spec kullanılıyor -
 * BİLEREK [Spring.DampingRatioNoBouncy] (kritik sönümlü, SIFIR aşım/sekme):
 * bu dosyanın üstündeki [rememberTopBarVisibility] belgesindeki 4. tur
 * KULLANICI RAPORU, Compose'un VARSAYILAN spring spec'inin (dampingRatio
 * ~0.75, gözle görülür sekme) "kasıyor gibi" hissettirdiğini netleştirmişti -
 * o regresyonu GERİ GETİRMEMEK için sekme YAPMAYAN bir spring seçildi, sadece
 * tween'in sabit-süreli doğrusal hissinden daha organik/akışkan bir
 * ivme-yavaşlama eğrisi katıyor.
 */
@Composable
fun HideableTopBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    val offsetSpec = remember {
        spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
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

/**
 * Madde 6 (top bar mimarisi rewrite) — [HideableTopBar]'ın (yukarıda, hâlâ
 * var - AnimatedVisibility Scaffold topBar slotu için kullanılabilir kalsın
 * diye SİLİNMEDİ) YERİNE Feed/Discover/Inbox/Profile'da kullanılan overlay
 * deseni: bar'ı `Scaffold`'un `topBar` slotundan tamamen ÇIKARIP bir
 * `Box(Modifier.align(TopCenter))` katmanına taşımak, bar'ın görünürlük
 * değişiminin İÇERİĞİN layout'unu/content-padding'ini HİÇ ETKİLEMEMESİNİ
 * sağlar — web'in `position: fixed` + SADECE `transform: translateY()`
 * navbar'ının (`app/static/css/style.css`) birebir native karşılığı.
 *
 * BİLEREK fadeIn/fadeOut YOK (HideableTopBar'ın AKSİNE) - web'in navbar'ı da
 * fade YAPMIYOR, SADECE transform. Bar'ın kendisi `AnimatedVisibility` ile
 * DEĞİL sabit `offset()` ile kaydırılır - bar tam gizliyken bile layout'ta
 * YER KAPLAMAYA DEVAM EDER (bu YAN ETKİ DEĞİL, TASARIM: çağıran taraf zaten
 * içerik LazyColumn'una `contentPadding = PaddingValues(top = TOP_BAR_HEIGHT
 * + ...)` ile SABİT bir üst boşluk ayırıyor, bar'ın kendi layout alanı bu
 * boşluğun tam altında/üstünde çakışmaz).
 */
@Composable
fun OverlayTopBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // KULLANICI RAPORU (5. tur) KÖK NEDEN: [MainScaffold]'un `Scaffold`'u bu
    // ekranlar için `topBar` slotu KULLANMIYOR (bar buradaki [OverlayTopBar]
    // ile Box overlay olarak veriliyor, bkz. yukarıdaki mimari notu) — bu
    // yüzden Scaffold, hiçbir şeyin status bar inset'ini "tükettiğini"
    // BİLMİYOR ve kendi `contentWindowInsets` varsayılanı (`safeDrawing`,
    // status bar dahil) gereği `content` slotuna GERÇEK status bar
    // yüksekliği kadar TEPEDEN padding uyguluyor (MainScaffold.kt: `Box(
    // Modifier.padding(padding))`). Yani bu composable'ın "local y=0"ı zaten
    // GERÇEK ekranın status-bar-sonrası noktası - status barın KENDİSİ
    // DEĞİL. `content` (TopBarSurface + TopAppBar) İSE kendi
    // `windowInsetsPadding`'iyle status bar için AYRICA yer ayırıyordu - iki
    // kat status bar boşluğu üst üste biniyordu: biri Scaffold'un padding'i
    // (gerçek status bar rengi/arka planı sızıyor - kullanıcının gördüğü
    // "koyu çizgi" TAM OLARAK bu), biri de TopAppBar'ın kendi iç boşluğu (bar
    // gereğinden büyük görünüyordu).
    //
    // Fix: bu Box'ı [statusBarHeight] kadar YUKARI (negatif offset) taşı - bu
    // sadece Scaffold'un ZATEN uyguladığı padding'i telafi eder (gerçek
    // ekranın en tepesine geri döner), Box'ın kendisi hiçbir ata tarafından
    // KIRPILMIYOR (Modifier.padding/Scaffold clip UYGULAMAZ) yani negatif
    // offset güvenle status bar bölgesine "geri render" edilebilir. Bundan
    // sonra [TopBarSurface] KENDİ status bar boşluğunu TEK SEFER, doğru
    // yerde (gerçek status bar bölgesinde, kendi arka plan rengiyle boyanmış
    // olarak) açar - artık çift sayım YOK, sızan farklı renk YOK.
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    // KULLANICI RAPORU (6. tur): "navbar tam yukarı gidip kaybolmuyor,
    // bildirimler kısmının arkasında gözüküyor" — kök neden, bu Box'ın
    // TOPLAM yüksekliğinin (statusBarHeight'lık Spacer + TOP_BAR_HEIGHT'lık
    // gerçek bar, bkz. TopBarSurface) gizliyken sadece TOP_BAR_HEIGHT kadar
    // yukarı kaydırılmasıydı — statusBarHeight'lık Spacer'ın kendisi hâlâ
    // ekranın gerçek tepesinde kalıyordu, bu da GERÇEK bar'ın (Spacer'ın
    // altındaki içerik) statusBarHeight kadarlık alt kesitinin status
    // bar/bildirim simgeleri bölgesine sıkışmış kalmasına yol açıyordu.
    // Gizliyken statusBarHeight İKİ KEZ düşülmeli: biri (aşağıdaki genel
    // `- statusBarHeight`) görünürken uygulanan telafiyi karşılamak için,
    // biri de bu Box'ın kendi statusBarHeight'lık Spacer'ını TAMAMEN
    // ekranın dışına çıkarmak için.
    //
    // Animasyon/mikro-etkileşim turu: sabit-süreli `tween` yerine spring
    // tabanlı bir spec - AMA BİLEREK [Spring.DampingRatioNoBouncy] (kritik
    // sönümlü, sıfır aşım/sekme). rememberTopBarVisibility'nin belgesindeki
    // 4. tur KULLANICI RAPORU tam olarak Compose'un VARSAYILAN (sekmeli/daha
    // "zıplayan") spring spec'ini reddetmişti - o regresyonu GERİ
    // GETİRMEMEK için burada de aynı no-bounce spring kullanılıyor, sadece
    // tween'in doğrusal hissinden biraz daha akışkan bir ivme-yavaşlama
    // eğrisi katıyor.
    val offsetY by animateDpAsState(
        targetValue = (if (visible) 0.dp else -TOP_BAR_HEIGHT - statusBarHeight) - statusBarHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "topBarOffsetY",
    )
    Box(modifier = modifier.offset(y = offsetY)) {
        content()
    }
}

/**
 * [OverlayTopBar]'ın YUKARIDAKİ telafi offset'iyle BİRLİKTE çalışır: bu
 * composable artık gerçek status bar bölgesinin TEPESİNDEN başladığı için,
 * status bar boşluğunu TEK SEFER burada, EXPLICIT bir renkle (arka planla
 * AYNI ton - `colorScheme.surface`, aşağıdaki TopAppBar'ın `containerColor`ı
 * ile BİREBİR eşleşecek) açan bir `Spacer` + asıl TopAppBar'dan oluşan bir
 * `Column`. TopAppBar'ı çağıran taraf (Feed/Discover/Inbox/Profile) KENDİ
 * `windowInsets`'ini `WindowInsets(0,0,0,0)`'a, `colors`'ını bu composable
 * ile AYNI `colorScheme.surface`'e ve `modifier`'ını `Modifier.height(
 * TOP_BAR_HEIGHT)`'a SABİTLEMELİDİR - aksi halde TopAppBar KENDİ status bar
 * boşluğunu TEKRAR açar (çift sayım tekrar geri döner) veya farklı bir
 * renkle boyanıp yeni bir dikiş yaratır.
 */
@Composable
fun TopBarSurface(content: @Composable () -> Unit) {
    Column {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.surface),
        )
        content()
    }
}

/**
 * KULLANICI RAPORU (madde 1, ESKİ tur — navbar hikayelerin/keşfetin/aramanın
 * ÜSTÜNE biniyor) için önceki sürümde bu değer `TOP_BAR_HEIGHT + status bar
 * yüksekliği` idi. 5. tur (yukarıdaki [OverlayTopBar]/[TopBarSurface]
 * yorumlarında detaylı kök neden analizi) bunu DEĞİŞTİRDİ: artık
 * [OverlayTopBar] kendi `-statusBarHeight` telafi offset'iyle gerçek status
 * bar bölgesinin TEPESİNDEN başlıyor ve [TopBarSurface] o boşluğu KENDİ
 * içinde (bir `Spacer` ile) açıyor — yani status bar yüksekliği artık
 * [FeedScreen]/[DiscoverScreen]/[InboxScreen]/[ProfileScreen]'in LOCAL
 * koordinat uzayında (bu ekranların kendi `Box`ı zaten [MainScaffold]'un
 * `Scaffold`'u tarafından status bar kadar aşağı itilmiş durumda, bkz.
 * MainScaffold.kt) ZATEN "harcanmış" oluyor — bu yardımcının onu BİR DAHA
 * eklemesi ÇİFT SAYIM olurdu (content, bar'ın GERÇEKTE kapladığından FAZLA
 * boşluk bırakırdı). Bu yüzden artık SADECE `TOP_BAR_HEIGHT` (56dp) — bar'ın
 * LOCAL koordinat uzayındaki GERÇEK yüksekliği — döndürülüyor.
 */
@Composable
fun rememberTopBarContentPadding(): Dp = TOP_BAR_HEIGHT
