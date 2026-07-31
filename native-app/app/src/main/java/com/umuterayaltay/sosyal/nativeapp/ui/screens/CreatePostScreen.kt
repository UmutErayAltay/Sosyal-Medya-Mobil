package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CreatePostEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CreatePostViewModel

private data class VisibilityOption(val value: String, val label: String, val icon: ImageVector)

private val VISIBILITY_OPTIONS = listOf(
    VisibilityOption("public", "Herkese Açık", Icons.Filled.Public),
    VisibilityOption("followers", "Takipçiler", Icons.Filled.Group),
    VisibilityOption("close_friends", "Yakın Arkadaşlar", Icons.Filled.Star),
)

/**
 * "Yeni Gönderi" ekranı — app/api_v1.py api_create_post() sözleşmesiyle
 * (bkz. CreatePostViewModel) AYNI BİLİNÇLİ SINIR: metin + TEK opsiyonel görsel
 * (galeriden, Android Photo Picker ile) + görünürlük. Görünürlük seçimi
 * DiscoverScreen'deki SearchTypeFilterRow'un FilterChip satırı DESENİYLE
 * tutarlı. Paylaşım sonrası navigasyon (geri dönme) bu ekranın BİLMEDİĞİ bir
 * şey — [onPostCreated] callback'i çağrılır, NavHost geri navigasyonu yapar
 * (NewMessageScreen'deki onConversationReady deseniyle TUTARLI).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onNavigateBack: () -> Unit,
    onPostCreated: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: CreatePostViewModel = viewModel(),
) {
    val context = LocalContext.current
    val content by viewModel.content.collectAsState()
    val visibility by viewModel.visibility.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreatePostEvent.Success -> onPostCreated()
                is CreatePostEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onImageSelected(uri) }

    val canSubmit = (content.isNotBlank() || selectedImageUri != null) && !submitting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni Gönderi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !submitting) {
                        Icon(Icons.Filled.Close, contentDescription = "Vazgeç")
                    }
                },
                actions = {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = { viewModel.submit(context) }, enabled = canSubmit) {
                            Text("Paylaş")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Kimler görebilir?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VisibilityFilterRow(selected = visibility, onSelect = viewModel::onVisibilityChange)
            }

            OutlinedTextField(
                value = content,
                onValueChange = viewModel::onContentChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ne düşünüyorsun?") },
                minLines = 4,
                enabled = !submitting,
                shape = MaterialTheme.shapes.medium,
            )

            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = { viewModel.onImageSelected(null) },
                        enabled = !submitting,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Görseli kaldır")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !submitting,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Text(text = "Görsel Ekle", modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisibilityFilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(VISIBILITY_OPTIONS, key = { it.value }) { option ->
            val isSelected = selected == option.value
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option.value) },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
