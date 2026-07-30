package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.BlockedUsersEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.BlockedUsersViewModel

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
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            when {
                loading && users.isEmpty() -> CenteredBox { CircularProgressIndicator() }

                users.isEmpty() -> CenteredBox { Text("Engellediğin kimse yok.") }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(users, key = { it.id }) { user ->
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
                            TextButton(onClick = { user.username?.let(viewModel::unblock) }) {
                                Text("Engeli Kaldır")
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
