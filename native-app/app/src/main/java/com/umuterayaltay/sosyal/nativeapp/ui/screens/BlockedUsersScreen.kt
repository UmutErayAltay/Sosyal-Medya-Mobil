package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.BlockedUsersEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.BlockedUsersViewModel
import kotlinx.coroutines.delay

/**
 * "Engellenen Kullanıcılar" ekranı — CloseFriendsScreen'in liste görsel
 * dilinin (UserRow + sağda tek bir aksiyon) AYNISI, ama arama kutusu YOK
 * (bu ekrana yeni birini engellemek için gelinmez, sadece VAR OLAN
 * engellemeleri yönetmek için — yeni engelleme ProfileScreen'in üç-nokta
 * menüsünden yapılır). Her satırdaki "Engeli Kaldır" AYNI toggleBlock
 * endpoint'ini kullanır (bkz. BlockedUsersViewModel.unblock()), başarılı
 * olunca satır listeden çıkar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: BlockedUsersViewModel = viewModel(),
) {
    val users by viewModel.users.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    // CloseFriendsScreen.kt'deki AYNI fikir: "Engeli Kaldır" tiklaninca satiri
    // ANINDA fade-out ile gizler.
    var hiddenIds by remember { mutableStateOf(setOf<String>()) }

    // CloseFriendsScreen.kt'deki AYNI kendi-kendini-onaran guvenlik agi:
    // unblock() basarisiz olursa (bkz. BlockedUsersViewModel.unblock() - SADECE
    // Success'te local listeden cikariyor) satir `users` icinde hala vardir -
    // hiddenIds'ten cikarilip TEKRAR gorunur olur.
    LaunchedEffect(users) {
        val stillPresentIds = users.map { it.id }.toSet()
        hiddenIds = hiddenIds.filter { it !in stillPresentIds }.toSet()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BlockedUsersEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engellenen Kullanıcılar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            when {
                loading && users.isEmpty() -> CenteredBox { CircularProgressIndicator() }

                users.isEmpty() -> CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Engellediğin kimse yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(users, key = { _, user -> user.id }) { index, user ->
                        StaggeredBlockedItem(index = index, visible = user.id !in hiddenIds) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserRow(
                                    avatarUrl = user.avatarUrl,
                                    username = user.username,
                                    fullName = user.fullName,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        hiddenIds = hiddenIds + user.id
                                        user.username?.let(viewModel::unblock)
                                    },
                                ) {
                                    Text("Engeli Kaldır", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * CloseFriendsScreen.kt'deki StaggeredCloseFriendItem ile AYNI fikir: satir
 * ilk kez composition'a girdiginde index'e bagli KISA bir gecikmeyle belirir,
 * [visible] false olunca ("Engeli Kaldır" tiklaninca) fade-out ile cikar.
 */
@Composable
private fun StaggeredBlockedItem(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 10) * 30L)
        entered = true
    }
    AnimatedVisibility(
        visible = entered && visible,
        enter = fadeIn(animationSpec = tween(200)) +
            slideInVertically(animationSpec = tween(200)) { it / 8 },
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
    ) {
        content()
    }
}
