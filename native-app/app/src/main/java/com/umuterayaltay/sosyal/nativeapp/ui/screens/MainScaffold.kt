package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.MutableStateFlow

private enum class MainTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Feed("Ana Sayfa", Icons.Filled.Home),
    Discover("Keşfet", Icons.Filled.Explore),
    Reels("Reels", Icons.Filled.SmartDisplay),
    Messages("Mesajlar", Icons.Filled.Chat),
    Profile("Profil", Icons.Filled.Person),
}

/**
 * Alt navigasyon barlı ana ekran — Faz 3'ün 5 sekmesi de artık gerçekten
 * çalışıyor: "Ana Sayfa" (Feed), "Keşfet", "Reels" (dikey video akışı),
 * "Mesajlar" (gelen kutusu + konuşma + yeni mesaj — bu fazın SON parçasıydı)
 * ve "Profil". Hiç placeholder KALMADI (bkz. PlaceholderScreen — artık başka
 * hiçbir sekmede kullanılmıyor).
 *
 * navController AppNavHost'tan geliyor - Profil/Kesfet sekmelerindeki
 * kullanici satirlarindan "profile/{username}", stats satirindan
 * "followers/{username}"/"following/{username}", TopAppBar aksiyonlarindan
 * "insights"/"followRequests", Mesajlar sekmesinden "conversation/{id}"/
 * "newMessage" route'larina PUSH yapmak icin (bottom bar'in bu push'larda
 * gizlenmesi standart/beklenen davranis).
 */
@Composable
fun MainScaffold(navController: NavHostController, onSessionExpired: () -> Unit) {
    // Kullanıcı raporu: "her geri geldiğimde ana sayfaya atıyor" (ör. Mesajlar
    // sekmesindeyken bir konuşmaya girip navigateUp() ile dönünce Mesajlar
    // yerine Ana Sayfa'ya düşüyordu). Kök neden: Navigation-Compose farklı bir
    // route'a geçilince (ör. "conversation/{id}") BU composable'ın kompozisyonu
    // TAMAMEN DISPOSE edilir — sade `remember` bu dispose/recompose döngüsünü
    // ATLATAMAZ, sadece `rememberSaveable` (SavedStateHandle/Bundle üzerinden
    // kalıcı) hayatta kalır. MainTab bir enum olduğu için (JVM'de otomatik
    // Serializable) ekstra bir Saver YAZILMASI GEREKMEDİ.
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Feed) }

    // Kullanıcı raporu: hikaye paylaşıldıktan sonra feed'deki hikaye çubuğu
    // yenilenmiyordu. MainScaffold "main" route'unun composable'ı olduğu için
    // currentBackStackEntryAsState()'in döndürdüğü entry BURADAYKEN "main"in
    // KENDİ entry'sidir - "storyCreate" ekranı navigateUp() ile buraya
    // dönerken previousBackStackEntry (=bu entry) üzerine bıraktığı
    // "story_created" bayrağı burada okunur (bkz. AppNavHost.kt onStoryCreated).
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val storyCreated by remember(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.getStateFlow("story_created", false)
            ?: MutableStateFlow(false)
    }.collectAsState()

    // 2026-08-09 (kullanıcı isteği: "post paylaşınca sayfayı yenilememe
    // gerek kalmadan ana sayfada da görmek istiyorum") — storyCreated ile
    // BİREBİR AYNI desen (bkz. AppNavHost.kt "createPost" route'undaki
    // onPostCreated yorumu).
    val postCreated by remember(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.getStateFlow("post_created", false)
            ?: MutableStateFlow(false)
    }.collectAsState()

    // 2026-08-09 (kullanıcı raporu: "öne çıkarılanlara ekleyince uygulamayı
    // aç kapa yapmak zorunda kalıyorum") — storyCreated/postCreated ile AYNI
    // desen. storyViewer HER ZAMAN Feed'in hikaye çubuğundan (yani bu "main"
    // route'undan) açıldığı için previousBackStackEntry HER ZAMAN buradaki
    // currentBackStackEntry'dir (bkz. AppNavHost.kt storyViewer composable'ı).
    val highlightsChanged by remember(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.getStateFlow("highlight_changed", false)
            ?: MutableStateFlow(false)
    }.collectAsState()

    Scaffold(
        bottomBar = {
            // Görsel cila: seçili sekmenin ikon/etiket rengi + arka plan
            // göstergesi marka renklerinden (colorScheme.primary/secondaryContainer)
            // besleniyor — sekme değiştirme MANTIĞI (selectedTab state'i,
            // onClick) DEĞİŞMEDİ, sadece renk/vurgu token'ları eklendi.
            // tonalElevation = 0.dp (BİLİNÇLİ): Compose'un elevation gölgesi
            // barın ÜSTÜNDE ince, keskin bir siyah çizgi gibi görünüyordu
            // (kullanıcı raporu, gerçek cihaz) — düz containerColor yeterli.
            //
            // Kullanıcı raporu (gerçek cihaz ekran görüntüsü, 2. tur): tonalElevation=0
            // TEK BAŞINA yetmiyordu — barın HEMEN ÜSTÜNDE ince, açık renkli bir yatay
            // çizgi hâlâ görünüyordu. Kök neden: containerColor = colorScheme.surface
            // idi, ama bu Scaffold'un content Box'ı (aşağıdaki `Box(Modifier.padding(padding))`)
            // KENDİ containerColor'ını HİÇ ayarlamıyor, yani varsayılan
            // colorScheme.background kullanıyor — Theme.kt'de surface/background
            // BİLEREK farklı tonlar (kartların "yükseltilmiş" görünmesi için, ör.
            // dark: surface=Charcoal850 background=Charcoal950). Bu iki farklı ton
            // TAM OLARAK bu NavigationBar'ın üst kenarında karşılaşıyor -> gözle
            // görülür bir renk dikişi/çizgi. containerColor'ı da background'a
            // sabitleyince (aşağıdaki içerik Scaffold'larıyla AYNI ton) dikiş kayboluyor.
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                MainTab.entries.forEach { tab ->
                    // Animasyon/mikro-etkileşim turu: seçili sekmenin ikonu hafif
                    // büyüyüp geri döner (spring tabanlı "bounce" geri bildirimi) -
                    // sekme değiştirme MANTIĞI (selectedTab state'i, onClick) BURADA
                    // DA değişmedi, sadece ikon ölçeği selectedTab'a bağlı animate
                    // ediliyor. Medium bouncy + orta stiffness: hissedilir ama
                    // Instagram/Twitter'ın "abartısız" hissinin ötesine geçmez.
                    val iconScale by animateFloatAsState(
                        targetValue = if (selectedTab == tab) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        label = "tabIconScale",
                    )
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.scale(iconScale),
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Animasyon/mikro-etkileşim turu: sekmeler arası içerik geçişi artık
            // ani kesme yerine yumuşak bir Crossfade ile yapılıyor. `when` bloğu
            // ÖNCEDEN de selectedTab'a göre TEK bir dalı composable ediyordu (yani
            // diğer sekmelerin state'i zaten tab değişince tamamen dispose
            // oluyordu) - Crossfade bu davranışı DEĞİŞTİRMEZ, sadece eski/yeni
            // dal arasındaki geçişi animasyonlu hale getirir.
            Crossfade(targetState = selectedTab, label = "mainTabCrossfade") { tab ->
            when (tab) {
                MainTab.Feed -> FeedScreen(
                    onSessionExpired = onSessionExpired,
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                    onNewPostClick = { navController.navigate("createPost") },
                    onNotificationsClick = { navController.navigate("notifications") },
                    onTrendingClick = { navController.navigate("trending") },
                    onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
                    onNavigateToProfile = { username -> navController.navigate("profile/$username") },
                    // Faz 5 Dalga 2C: hikaye çubuğu — "+"tan oluşturma ekranına,
                    // bir halkaya tıklanınca o kullanıcının viewer'ına gider.
                    // 2026-08-10 (kullanıcı isteği: "birinin storyleri bitince
                    // sıradakine geçsin") — sıralı userId listesi virgülle
                    // birleştirilip "queue" query param'ı olarak, tıklanan
                    // kullanıcının o listedeki index'i "start" olarak taşınır
                    // (UUID'ler virgül İÇERMEZ, ek bir encode/escape GEREKMEZ).
                    onNavigateToStoryViewer = { userId, allUserIds ->
                        val startIndex = allUserIds.indexOf(userId).coerceAtLeast(0)
                        val queue = allUserIds.joinToString(",")
                        navController.navigate("storyViewer/$userId?queue=$queue&start=$startIndex")
                    },
                    onNavigateToStoryCreate = { navController.navigate("storyCreate") },
                    storyCreated = storyCreated,
                    onStoryBarRefreshHandled = {
                        // Bayrağı false'a geri döndür - tekrar tetiklenmesin (aksi
                        // halde her tab değişiminde/recomposition'da yeniden
                        // loadBar() çağrılırdı).
                        currentBackStackEntry?.savedStateHandle?.set("story_created", false)
                    },
                    postCreated = postCreated,
                    onPostCreatedRefreshHandled = {
                        currentBackStackEntry?.savedStateHandle?.set("post_created", false)
                    },
                )
                MainTab.Discover -> DiscoverScreen(
                    onSessionExpired = onSessionExpired,
                    onUserClick = { username -> navController.navigate("profile/$username") },
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                    onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
                )
                MainTab.Reels -> ReelsScreen(
                    onSessionExpired = onSessionExpired,
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                )
                MainTab.Messages -> InboxScreen(
                    onConversationClick = { conversationId -> navController.navigate("conversation/$conversationId") },
                    onNewMessageClick = { navController.navigate("newMessage") },
                    onNewGroupClick = { navController.navigate("groupCreate") },
                    onSessionExpired = onSessionExpired,
                )
                MainTab.Profile -> ProfileScreen(
                    username = null,
                    onNavigateToProfile = { username -> navController.navigate("profile/$username") },
                    onNavigateToFollowers = { username -> navController.navigate("followers/$username") },
                    onNavigateToFollowing = { username -> navController.navigate("following/$username") },
                    onNavigateToInsights = { navController.navigate("insights") },
                    onNavigateToFollowRequests = { navController.navigate("followRequests") },
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
                    // Kullanıcı raporu: SADECE bu kök "Profil" sekmesinden highlight'lara
                    // tıklanmıyordu - AppNavHost.kt'nin AYRI "profile/{username}" push
                    // route'unda bu parametre zaten doğru bağlıydı, burada UNUTULMUŞTU
                    // (ProfileScreen'in varsayılan {} no-op'una düşüyordu).
                    onNavigateToHighlights = { userId -> navController.navigate("highlights/$userId") },
                    highlightsChanged = highlightsChanged,
                    onHighlightsRefreshHandled = {
                        currentBackStackEntry?.savedStateHandle?.set("highlight_changed", false)
                    },
                    onSessionExpired = onSessionExpired,
                    // onNavigateBack YOK: bu, alt navigasyondaki KOK "Profil" sekmesi
                    // - geri tusu YOK (push edilmis bir route degil).
                )
            }
            }
        }
    }
}
