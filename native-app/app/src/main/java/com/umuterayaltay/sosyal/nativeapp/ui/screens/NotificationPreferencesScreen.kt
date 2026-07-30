package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.umuterayaltay.sosyal.nativeapp.network.NotificationPreferencesDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.NotificationPreferencesEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.NotificationPreferencesViewModel

/** Tek bir bildirim türü satırı — label/description app/notifications.py
 * NOTIFICATION_TYPES sabitindeki GERÇEK Türkçe metinlerle BİREBİR aynı (o
 * dosya okunarak doğrulandı), sıra da AYNI. [getter]/[setter] NotificationPreferencesDto
 * üzerindeki ilgili alanı okur/kopyalar. */
private data class PreferenceRow(
    val label: String,
    val description: String,
    val getter: (NotificationPreferencesDto) -> Boolean,
    val setter: (NotificationPreferencesDto, Boolean) -> NotificationPreferencesDto,
)

private val PREFERENCE_ROWS = listOf(
    PreferenceRow(
        "Beğeniler", "Gönderini biri beğendiğinde",
        { it.notifyLike }, { p, v -> p.copy(notifyLike = v) },
    ),
    PreferenceRow(
        "Yorumlar", "Gönderine biri yorum yaptığında",
        { it.notifyComment }, { p, v -> p.copy(notifyComment = v) },
    ),
    PreferenceRow(
        "Yanıtlar", "Yorumuna biri yanıt verdiğinde",
        { it.notifyReply }, { p, v -> p.copy(notifyReply = v) },
    ),
    PreferenceRow(
        "Yorum beğenileri", "Yorumunu biri beğendiğinde",
        { it.notifyCommentLike }, { p, v -> p.copy(notifyCommentLike = v) },
    ),
    PreferenceRow(
        "Yorum tepkileri", "Yorumuna biri emoji tepkisi verdiğinde",
        { it.notifyCommentReaction }, { p, v -> p.copy(notifyCommentReaction = v) },
    ),
    PreferenceRow(
        "Takipçiler", "Biri seni takip etmeye başladığında",
        { it.notifyFollow }, { p, v -> p.copy(notifyFollow = v) },
    ),
    PreferenceRow(
        "Takip İstekleri", "Gizli profiline biri takip isteği gönderdiğinde",
        { it.notifyFollowRequest }, { p, v -> p.copy(notifyFollowRequest = v) },
    ),
    PreferenceRow(
        "İstek Kabülleri", "Takip isteğin kabul edildiğinde",
        { it.notifyFollowAccept }, { p, v -> p.copy(notifyFollowAccept = v) },
    ),
    PreferenceRow(
        "Mesajlar", "Sana mesaj geldiğinde",
        { it.notifyMessage }, { p, v -> p.copy(notifyMessage = v) },
    ),
    PreferenceRow(
        "Etiketlenmeler", "Bir gönderide etiketlendiğinde",
        { it.notifyMention }, { p, v -> p.copy(notifyMention = v) },
    ),
    PreferenceRow(
        "Takip edilen etiketler", "Takip ettiğin bir etikette yeni paylaşım olduğunda",
        { it.notifyHashtagPost }, { p, v -> p.copy(notifyHashtagPost = v) },
    ),
    PreferenceRow(
        "Hikaye tepkileri", "Hikayene biri emoji tepkisi verdiğinde",
        { it.notifyStoryReaction }, { p, v -> p.copy(notifyStoryReaction = v) },
    ),
    PreferenceRow(
        "Yeniden paylaşımlar", "Gönderini biri yeniden paylaştığında",
        { it.notifyRepost }, { p, v -> p.copy(notifyRepost = v) },
    ),
)

/**
 * "Bildirim Tercihleri" ekranı — 13 satır (Switch + tür adı + açıklama).
 * Backend per-field DEĞİL TAM seti tek istekte kabul ediyor (bkz.
 * NotificationPreferencesViewModel) — bu yüzden her satırdaki Switch SADECE
 * yerel state'i değiştirir, TopAppBar'daki "Kaydet" tıklanınca güncel setin
 * TAMAMI tek POST'ta gönderilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreferencesScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: NotificationPreferencesViewModel = viewModel(),
) {
    val preferences by viewModel.preferences.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NotificationPreferencesEvent.Saved -> onNavigateBack()
                is NotificationPreferencesEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bildirim Tercihleri") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = viewModel::save, enabled = preferences != null) {
                            Text("Kaydet")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && preferences == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null && preferences == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    TextButton(onClick = viewModel::load) { Text("Tekrar dene") }
                }
            }

            preferences != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(PREFERENCE_ROWS, key = { it.label }) { row ->
                    PreferenceSwitchRow(
                        row = row,
                        checked = row.getter(preferences!!),
                        enabled = !saving,
                        onCheckedChange = { newValue ->
                            viewModel.updateField { row.setter(it, newValue) }
                        },
                    )
                    HorizontalDivider()
                }
                if (error != null) {
                    item {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    row: PreferenceRow,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = row.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
