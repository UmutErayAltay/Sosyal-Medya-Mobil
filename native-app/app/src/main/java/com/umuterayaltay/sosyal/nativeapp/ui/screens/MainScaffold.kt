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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

private enum class MainTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Feed("Ana Sayfa", Icons.Filled.Home),
    Discover("Keşfet", Icons.Filled.Explore),
    Reels("Reels", Icons.Filled.SmartDisplay),
    Messages("Mesajlar", Icons.Filled.Chat),
    Profile("Profil", Icons.Filled.Person),
}

/**
 * Alt navigasyon barlı ana ekran — "Ana Sayfa" (Feed), "Keşfet", "Reels" (Faz 5,
 * dikey video akışı) ve "Profil" (Faz 4, native Android profil ekrani) gerçekten
 * çalışıyor, kalan 1 sekme ("Mesajlar") dürüstçe "Yakında" placeholder'ı
 * gösteriyor (bkz. PlaceholderScreen — sahte/yarım bir uygulama izlenimi
 * verilmesin diye).
 *
 * navController AppNavHost'tan geliyor - Profil/Kesfet sekmelerindeki
 * kullanici satirlarindan "profile/{username}", stats satirindan
 * "followers/{username}"/"following/{username}", TopAppBar aksiyonlarindan
 * "insights"/"followRequests" route'larina PUSH yapmak icin (bottom bar'in
 * bu push'larda gizlenmesi standart/beklenen davranis).
 */
@Composable
fun MainScaffold(navController: NavHostController, onSessionExpired: () -> Unit) {
    var selectedTab by remember { mutableStateOf(MainTab.Feed) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                MainTab.Feed -> FeedScreen(onSessionExpired = onSessionExpired)
                MainTab.Discover -> DiscoverScreen(
                    onSessionExpired = onSessionExpired,
                    onUserClick = { username -> navController.navigate("profile/$username") },
                )
                MainTab.Reels -> ReelsScreen(onSessionExpired = onSessionExpired)
                MainTab.Messages -> PlaceholderScreen("Mesajlar")
                MainTab.Profile -> ProfileScreen(
                    username = null,
                    onNavigateToProfile = { username -> navController.navigate("profile/$username") },
                    onNavigateToFollowers = { username -> navController.navigate("followers/$username") },
                    onNavigateToFollowing = { username -> navController.navigate("following/$username") },
                    onNavigateToInsights = { navController.navigate("insights") },
                    onNavigateToFollowRequests = { navController.navigate("followRequests") },
                    onSessionExpired = onSessionExpired,
                    // onNavigateBack YOK: bu, alt navigasyondaki KOK "Profil" sekmesi
                    // - geri tusu YOK (push edilmis bir route degil).
                )
            }
        }
    }
}
