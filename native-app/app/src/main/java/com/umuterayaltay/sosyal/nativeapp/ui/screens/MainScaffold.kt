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

private enum class MainTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Feed("Ana Sayfa", Icons.Filled.Home),
    Discover("Keşfet", Icons.Filled.Explore),
    Reels("Reels", Icons.Filled.SmartDisplay),
    Messages("Mesajlar", Icons.Filled.Chat),
    Profile("Profil", Icons.Filled.Person),
}

/**
 * Alt navigasyon barlı ana ekran — 5 sekmeden sadece "Ana Sayfa" (Feed)
 * gerçekten çalışıyor, diğer 4'ü dürüstçe "Yakında" placeholder'ı gösteriyor
 * (bkz. PlaceholderScreen — sahte/yarım bir uygulama izlenimi verilmesin diye).
 */
@Composable
fun MainScaffold(onSessionExpired: () -> Unit) {
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
                MainTab.Discover -> PlaceholderScreen("Keşfet")
                MainTab.Reels -> PlaceholderScreen("Reels")
                MainTab.Messages -> PlaceholderScreen("Mesajlar")
                MainTab.Profile -> PlaceholderScreen("Profil")
            }
        }
    }
}
