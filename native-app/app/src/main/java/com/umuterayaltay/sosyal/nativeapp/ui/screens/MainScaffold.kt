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
                MainTab.Feed -> FeedScreen(
                    onSessionExpired = onSessionExpired,
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                    onNewPostClick = { navController.navigate("createPost") },
                )
                MainTab.Discover -> DiscoverScreen(
                    onSessionExpired = onSessionExpired,
                    onUserClick = { username -> navController.navigate("profile/$username") },
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                )
                MainTab.Reels -> ReelsScreen(
                    onSessionExpired = onSessionExpired,
                    onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                )
                MainTab.Messages -> InboxScreen(
                    onConversationClick = { conversationId -> navController.navigate("conversation/$conversationId") },
                    onNewMessageClick = { navController.navigate("newMessage") },
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
                    onSessionExpired = onSessionExpired,
                    // onNavigateBack YOK: bu, alt navigasyondaki KOK "Profil" sekmesi
                    // - geri tusu YOK (push edilmis bir route degil).
                )
            }
        }
    }
}
