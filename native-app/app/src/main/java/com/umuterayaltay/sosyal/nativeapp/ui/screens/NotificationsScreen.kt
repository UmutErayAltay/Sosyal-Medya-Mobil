package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.NotificationDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.NotificationsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.NotificationsViewModel

/**
 * Bildirimler ekranı — InboxScreen'in LazyColumn + loading/error/empty
 * (CenteredMessage) deseniyle GÖRSEL olarak tutarlı. Sayfalama sonsuz kaydırma
 * DEĞİL, ConversationViewModel.loadOlder() gibi basit bir "Daha fazla yükle"
 * butonu (görev tanımı: sonsuz kaydırma şart değil).
 *
 * Backend sözleşmesi: app/api_v1.py api_list_notifications() — GET
 * /api/v1/notifications?page=N (bkz. ApiModels.kt NotificationDto yorumu).
 *
 * Navigasyon 5 ayrı callback'e bölündü — bu ekran navController'ı BİLMEZ,
 * kararı [resolveNotificationTarget] verir, gerçek navigate() çağrısı
 * AppNavHost.kt'de yapılır (ConversationScreen/ProfileScreen ile AYNI desen).
 * hashtag_post (Faz 4 sonrası eksik giderme SONUNCUSU: hashtag sayfası eklendi)
 * artık [onNavigateToHashtag] ile "hashtag/{tag}" route'una gider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToConversation: (String) -> Unit,
    onNavigateToFollowRequests: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: NotificationsViewModel = viewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NotificationsEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bildirimler") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && notifications.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && notifications.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            notifications.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Henüz bildirimin yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(notifications, key = { index, _ -> index }) { _, notification ->
                    val target = resolveNotificationTarget(notification)
                    NotificationRow(
                        notification = notification,
                        onClick = target?.let {
                            {
                                when (it) {
                                    is NotificationTarget.Post -> onNavigateToPost(it.postId)
                                    is NotificationTarget.Profile -> onNavigateToProfile(it.username)
                                    is NotificationTarget.Conversation -> onNavigateToConversation(it.conversationId)
                                    is NotificationTarget.Hashtag -> onNavigateToHashtag(it.tag)
                                    NotificationTarget.FollowRequests -> onNavigateToFollowRequests()
                                }
                            }
                        },
                    )
                }
                if (hasNext) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                OutlinedButton(onClick = { viewModel.loadMore() }) {
                                    Text("Daha fazla yükle")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bir bildirim satırına tıklanınca gidilecek yer — görev tanımındaki "Native
 * navigasyon karşılıkları" tablosunun BİREBİR Kotlin karşılığı. Tip bazlı
 * kontrol EDİLİR (sadece alan doluluğuna bakılmaz): `hashtag_post` backend'de
 * hem post_id HEM hashtag doldurur, ama etikete gitmek (postun kendisine değil)
 * kullanıcının "bu etiketi takip ediyorum" bağlamına daha uygun — bu yüzden
 * post_id kontrolünden ÖNCE type kontrolü yapılır. */
private sealed class NotificationTarget {
    data class Post(val postId: String) : NotificationTarget()
    data class Profile(val username: String) : NotificationTarget()
    data class Conversation(val conversationId: String) : NotificationTarget()
    data class Hashtag(val tag: String) : NotificationTarget()
    data object FollowRequests : NotificationTarget()
}

private fun resolveNotificationTarget(notification: NotificationDto): NotificationTarget? = when {
    notification.type == "follow_request" -> NotificationTarget.FollowRequests
    notification.type == "hashtag_post" -> notification.hashtag?.takeIf { it.isNotBlank() }?.let { NotificationTarget.Hashtag(it) }
    !notification.username.isNullOrBlank() -> NotificationTarget.Profile(notification.username)
    !notification.postId.isNullOrBlank() -> NotificationTarget.Post(notification.postId)
    !notification.conversationId.isNullOrBlank() -> NotificationTarget.Conversation(notification.conversationId)
    else -> null
}

/** InboxScreen.kt'deki (file-private) CenteredMessage ile AYNI görsel — Kotlin
 * top-level `private` dosya sınırlarını aştığı için burada AYRI bir kopya
 * tutuluyor (FollowRequestsScreen.kt'nin Box'ı doğrudan inline etmesiyle AYNI
 * gerekçe). */
@Composable
private fun CenteredMessage(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun NotificationRow(notification: NotificationDto, onClick: (() -> Unit)?) {
    // Okunmamış bildirimler kalın metin + sağdaki nokta rozetiyle ZATEN ayrışıyordu
    // (davranış aynı) — buraya EK olarak hafif bir zemin tonu eklendi, tarama
    // hızında "hangileri yeni" sorusunu tek bakışta netleştirmek için.
    val background = if (!notification.isRead) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    } else {
        Color.Transparent
    }
    val baseModifier = Modifier
        .fillMaxWidth()
        .background(background)
    val rowModifier = if (onClick != null) {
        baseModifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    } else {
        baseModifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (!notification.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = notification.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = listOfNotNull(notification.actorSummary, notification.text)
                    .joinToString(" ")
                    .ifBlank { "Yeni bildirim" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = formatClockTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!notification.isRead) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
