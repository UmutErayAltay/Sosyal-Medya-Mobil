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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryCreateEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryCreateViewModel

private data class StoryVisibilityOption(val value: String, val label: String, val icon: ImageVector)

private val STORY_VISIBILITY_OPTIONS = listOf(
    StoryVisibilityOption("public", "Herkese Açık", Icons.Filled.Public),
    StoryVisibilityOption("followers", "Takipçiler", Icons.Filled.Group),
    StoryVisibilityOption("close_friends", "Yakın Arkadaşlar", Icons.Filled.Star),
)

/**
 * "Yeni Hikaye" ekranı — app/api_v1/stories.py api_create_story() sözleşmesiyle
 * AYNI BİLİNÇLİ SINIR (bkz. StoryCreateViewModel): caption + (TEK opsiyonel
 * görsel VEYA TEK opsiyonel video, mutually exclusive) + opsiyonel anket (0-4
 * seçenek) + görünürlük. CreatePostScreen'deki VisibilityFilterRow (private,
 * dosyaya özgü) ile GÖRSEL DİL tutarlı ama AYRI bir composable — CreatePostScreen'e
 * DOKUNULMADI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCreateScreen(
    onNavigateBack: () -> Unit,
    onStoryCreated: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: StoryCreateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val caption by viewModel.caption.collectAsState()
    val visibility by viewModel.visibility.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()
    val pollOptions by viewModel.pollOptions.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoryCreateEvent.Success -> onStoryCreated()
                is StoryCreateEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onImageSelected(uri) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onVideoSelected(uri) }

    val filledPollOptionCount = pollOptions.count { it.isNotBlank() }
    val canSubmit = (caption.isNotBlank() || selectedImageUri != null || selectedVideoUri != null ||
        filledPollOptionCount >= 2) && !submitting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni Hikaye") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Kimler görebilir?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StoryVisibilityFilterRow(selected = visibility, onSelect = viewModel::onVisibilityChange)
            }

            OutlinedTextField(
                value = caption,
                onValueChange = viewModel::onCaptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Bir şeyler yaz...") },
                minLines = 2,
                enabled = !submitting,
                shape = MaterialTheme.shapes.medium,
            )

            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
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
            } else if (selectedVideoUri != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null)
                    Text(
                        text = "Video seçildi",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f),
                    )
                    IconButton(onClick = { viewModel.onVideoSelected(null) }, enabled = !submitting) {
                        Icon(Icons.Filled.Close, contentDescription = "Videoyu kaldır")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !submitting,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Text(text = "Görsel", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                        enabled = !submitting,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                        Text(text = "Video", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Anket seçenekleri (0-4) — SADECE medya yokken zorunlu DEĞİL,
            // backend medya+anket birlikte olabiliyor (bkz. create_story()).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Anket (opsiyonel)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pollOptions.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { viewModel.onPollOptionChange(index, it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Seçenek ${index + 1}") },
                            singleLine = true,
                            enabled = !submitting,
                        )
                        IconButton(onClick = { viewModel.removePollOption(index) }, enabled = !submitting) {
                            Icon(Icons.Filled.Close, contentDescription = "Seçeneği kaldır")
                        }
                    }
                }
                if (pollOptions.size < 4) {
                    TextButton(onClick = { viewModel.addPollOption() }, enabled = !submitting) {
                        Text("+ Seçenek Ekle")
                    }
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
private fun StoryVisibilityFilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(STORY_VISIBILITY_OPTIONS, key = { it.value }) { option ->
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
