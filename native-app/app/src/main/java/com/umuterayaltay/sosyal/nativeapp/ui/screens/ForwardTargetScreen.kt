package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.network.ForwardTargetDto

/**
 * "İlet" aksiyonu seçilince açılan hedef-konuşma seçici — GET
 * /messages/forward-targets'ın (bkz. ConversationViewModel.loadForwardTargets())
 * döndürdüğü TÜM konuşmalar listelenir, birine dokununca [onTargetSelected]
 * çağrılır (asıl POST /messages/{id}/forward isteğini çağıran taraf —
 * ConversationScreen — yapar). Ayrı bir nav route İCAT EDİLMEDİ — bottom
 * sheet, mesaj bağlamını (hangi mesajın iletileceği) navigation argümanlarına
 * taşımak zorunda kalmadan aynı ekranda tutuyor (spesifikasyondaki "veya
 * bottom sheet" seçeneği).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardTargetScreen(
    targets: List<ForwardTargetDto>,
    loading: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onTargetSelected: (ForwardTargetDto) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "Şuna ilet:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            targets.isEmpty() -> Text(
                text = "Henüz bir konuşman yok",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            else -> LazyColumn(modifier = Modifier.height(320.dp)) {
                items(targets, key = { it.id }) { target ->
                    Text(
                        text = target.name ?: "Bilinmeyen",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTargetSelected(target) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
