package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CreateStoryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StoryCreateEvent {
    data class Success(val pollError: Boolean) : StoryCreateEvent()
    data object SessionExpired : StoryCreateEvent()
}

/**
 * "Yeni Hikaye" ekranı için ViewModel — app/api_v1/stories.py api_create_story()
 * sözleşmesiyle AYNI BİLİNÇLİ SINIR (web'in create_story()'siyle BİREBİR):
 * caption + (TEK opsiyonel görsel VEYA TEK opsiyonel video, mutually
 * exclusive — CreatePostViewModel'deki AYNI görsel/video deseni, ama KENDİ
 * kopyası, CreatePostViewModel'e DOKUNULMADI) + opsiyonel anket (0-4 seçenek,
 * ≥2 doluysa aktif) + görünürlük (public/followers/close_friends) +
 * background_color (SADECE medya yoksa anlamlı, backend zaten medya varsa
 * yok sayıyor — burada da medya seçiliyken renk seçici gösterilmemeli, bu
 * ekran tarafında UI kararı).
 */
class StoryCreateViewModel : ViewModel() {

    companion object {
        // storage_helper.py MAX_VIDEO_SIZE ile AYNI limit — CreatePostViewModel
        // ile AYNI ön-kontrol gerekçesi.
        private const val MAX_VIDEO_SIZE_BYTES = 25L * 1024 * 1024

        private val VIDEO_MIME_TO_EXTENSION = mapOf(
            "video/mp4" to ".mp4",
            "video/webm" to ".webm",
            "video/quicktime" to ".mov",
        )
    }

    private val storiesRepository = ServiceLocator.storiesRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    private val _visibility = MutableStateFlow("public")
    val visibility: StateFlow<String> = _visibility.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _backgroundColor = MutableStateFlow<String?>(null)
    val backgroundColor: StateFlow<String?> = _backgroundColor.asStateFlow()

    // Anket seçenekleri — 0-4 arası, backend poll_option_1..4 form alanlarına
    // eşlenir (bkz. StoriesRepository.createStory). Boş satırlar YOK SAYILIR.
    private val _pollOptions = MutableStateFlow<List<String>>(emptyList())
    val pollOptions: StateFlow<List<String>> = _pollOptions.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<StoryCreateEvent>()
    val events: SharedFlow<StoryCreateEvent> = _events

    fun onCaptionChange(text: String) {
        _caption.value = text
    }

    fun onVisibilityChange(value: String) {
        _visibility.value = value
    }

    fun onBackgroundColorChange(hex: String?) {
        _backgroundColor.value = hex
    }

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
        if (uri != null) _selectedVideoUri.value = null
    }

    fun onVideoSelected(uri: Uri?) {
        _selectedVideoUri.value = uri
        if (uri != null) _selectedImageUri.value = null
    }

    fun addPollOption() {
        if (_pollOptions.value.size >= 4) return
        _pollOptions.value = _pollOptions.value + ""
    }

    fun removePollOption(index: Int) {
        _pollOptions.value = _pollOptions.value.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
    }

    fun onPollOptionChange(index: Int, text: String) {
        _pollOptions.value = _pollOptions.value.toMutableList().apply {
            if (index in indices) this[index] = text
        }
    }

    /** [context] SADECE seçilen görsel/video Uri'sinin byte'larını/mime tipini
     * okumak için gerekiyor (ContentResolver) — CreatePostViewModel.submit()
     * ile AYNI gerekçe, ViewModel Context'i SAKLAMAZ. */
    fun submit(context: Context) {
        if (_submitting.value) return
        val text = _caption.value.trim()
        val imageUri = _selectedImageUri.value
        val videoUri = _selectedVideoUri.value
        val filledOptions = _pollOptions.value.map { it.trim() }.filter { it.isNotEmpty() }
        val hasPoll = filledOptions.size >= 2

        if (text.isEmpty() && imageUri == null && videoUri == null && !hasPoll) {
            _error.value = "Bir şeyler yaz, bir görsel/video seç veya anket ekle"
            return
        }

        viewModelScope.launch {
            _submitting.value = true
            _error.value = null

            var imageBytes: ByteArray? = null
            var imageMimeType: String? = null
            if (imageUri != null) {
                try {
                    withContext(Dispatchers.IO) {
                        imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    }
                    imageMimeType = context.contentResolver.getType(imageUri)
                } catch (e: Exception) {
                    _error.value = "Görsel okunamadı, lütfen tekrar deneyin"
                    _submitting.value = false
                    return@launch
                }
            }

            var videoBytes: ByteArray? = null
            var videoMimeType: String? = null
            var videoFileName: String? = null
            if (videoUri != null) {
                videoMimeType = context.contentResolver.getType(videoUri)
                val extension = VIDEO_MIME_TO_EXTENSION[videoMimeType]
                if (extension == null) {
                    _error.value = "Desteklenmeyen video formatı (mp4/webm/mov kullanın)"
                    _submitting.value = false
                    return@launch
                }

                try {
                    val size = context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { it.length }
                    if (size != null && size > MAX_VIDEO_SIZE_BYTES) {
                        _error.value = "Video 25MB'tan büyük olamaz"
                        _submitting.value = false
                        return@launch
                    }
                } catch (e: Exception) {
                    // Boyut önceden okunamazsa yine de gönder — backend zaten
                    // MAX_VIDEO_SIZE kontrolünü yapıyor (fail-open).
                }

                try {
                    withContext(Dispatchers.IO) {
                        videoBytes = context.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    _error.value = "Video okunamadı, lütfen tekrar deneyin"
                    _submitting.value = false
                    return@launch
                }
                videoFileName = "upload$extension"
            }

            when (
                val result = storiesRepository.createStory(
                    caption = text,
                    imageBytes = imageBytes,
                    imageMimeType = imageMimeType,
                    imageFileName = if (imageBytes != null) "upload.jpg" else null,
                    videoBytes = videoBytes,
                    videoMimeType = videoMimeType,
                    videoFileName = videoFileName,
                    visibility = _visibility.value,
                    // Renk sadece medya YOKSA anlamlı — backend zaten medya
                    // varsa yok sayıyor (bkz. app/api_v1/stories.py), burada
                    // ekstra bir istemci-taraf kontrolüne gerek yok.
                    backgroundColor = if (imageUri == null && videoUri == null) _backgroundColor.value else null,
                    captionPositionX = null,
                    captionPositionY = null,
                    pollOptions = filledOptions,
                    pollPositionX = null,
                    pollPositionY = null,
                    pollScale = null,
                )
            ) {
                is CreateStoryResult.Success -> _events.emit(StoryCreateEvent.Success(result.pollError))
                is CreateStoryResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(StoryCreateEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }

            _submitting.value = false
        }
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "empty_story" -> "Bir şeyler yaz, bir görsel/video seç veya anket ekle"
        "upload_failed" -> "Yükleme başarısız, lütfen tekrar deneyin"
        "stories_not_available" -> "Hikaye özelliği şu an kullanılamıyor"
        else -> "Hikaye paylaşılamadı, lütfen tekrar deneyin"
    }
}
