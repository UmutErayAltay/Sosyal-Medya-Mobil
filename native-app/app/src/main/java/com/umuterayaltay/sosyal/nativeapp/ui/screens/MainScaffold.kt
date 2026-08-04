package com.umuterayaltay.sosyal.nativeapp.ui.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var selectedTab by remember { mutableStateOf(MainTab.Feed) }

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
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
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
            when (selectedTab) {
                MainTab.Feed -> FeedScreen(
                    onSessionExpired = onSessionExpired,
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                    onNewPostClick = { navController.navigate("createPost") },
                    onNotificationsClick = { navController.navigate("notifications") },
                    onTrendingClick = { navController.navigate("trending") },
                    onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
                    // Faz 5 Dalga 2C: hikaye çubuğu — "+"tan oluşturma ekranına,
                    // bir halkaya tıklanınca o kullanıcının viewer'ına gider.
                    onNavigateToStoryViewer = { userId -> navController.navigate("storyViewer/$userId") },
                    onNavigateToStoryCreate = { navController.navigate("storyCreate") },
                    storyCreated = storyCreated,
                    onStoryBarRefreshHandled = {
                        // Bayrağı false'a geri döndür - tekrar tetiklenmesin (aksi
                        // halde her tab değişiminde/recomposition'da yeniden
                        // loadBar() çağrılırdı).
                        currentBackStackEntry?.savedStateHandle?.set("story_created", false)
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
                    onSessionExpired = onSessionExpired,
                    // onNavigateBack YOK: bu, alt navigasyondaki KOK "Profil" sekmesi
                    // - geri tusu YOK (push edilmis bir route degil).
                )
            }
        }
    }
}
