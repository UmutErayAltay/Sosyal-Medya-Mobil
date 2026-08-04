package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CreatePostResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class CreatePostEvent {
    data object Success : CreatePostEvent()
    data object SessionExpired : CreatePostEvent()
}

/**
 * "Yeni Gönderi" ekranı için ViewModel — app/api_v1.py api_create_post()
 * sözleşmesiyle (bkz. InteractionsRepository.createPost) AYNI BİLİNÇLİ SINIR:
 * metin + TEK opsiyonel görsel VEYA TEK opsiyonel video/reel + görünürlük
 * (public/followers/close_friends). Diğer ViewModel'lerdeki AYNI 401 deseni
 * (PostDetailViewModel/NewMessageViewModel): proaktif doğrulama yok, ilk
 * "unauthorized" gelince token temizlenip SessionExpired event'i yayınlanır.
 *
 * Paylaşım BAŞARILI olunca sadece bir Success event'i yayınlanır — Feed'in
 * ANINDA yeni postu göstermesi bu turun kapsamı DIŞI (kullanıcı var olan
 * pull-to-refresh ile görebilir), karmaşık bir ekranlar-arası state-geçişi
 * BİLİNÇLİ olarak İCAT EDİLMEDİ.
 */
class CreatePostViewModel : ViewModel() {

    companion object {
        // Backend storage_helper.py MAX_VIDEO_SIZE ile AYNI limit — istemci
        // tarafında ön-kontrol yaparak 25MB'ı aşan bir video hiç gönderilmez
        // (yükleme bandwidth'i + backend'in reddetme gecikmesi boşa gitmesin).
        private const val MAX_VIDEO_SIZE_BYTES = 25L * 1024 * 1024

        // storage_helper.py ALLOWED_VIDEO_MIMES/ALLOWED_VIDEO_EXTENSIONS ile
        // AYNI eşleme — backend HEM uzantı HEM sniff edilmiş MIME'ı doğruluyor,
        // bu yüzden dosya adının gerçek içerik tipiyle eşleşmesi ZORUNLU
        // (görsel deseninin AKSİNE burada sabit "upload.mp4" YETERSİZ).
        private val VIDEO_MIME_TO_EXTENSION = mapOf(
            "video/mp4" to ".mp4",
            "video/webm" to ".webm",
            "video/quicktime" to ".mov",
        )
    }

    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _visibility = MutableStateFlow("public")
    val visibility: StateFlow<String> = _visibility.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _isReel = MutableStateFlow(false)
    val isReel: StateFlow<Boolean> = _isReel.asStateFlow()

    // GIF seçimi (Faz 5 Dalga 3B, MediaPickerSheet'ten) — backend api_create_post()
    // ile AYNI mutually-exclusive kural: gifUrl SADECE görsel/video YOKSA
    // kullanılır. onGifSelected görsel/video'yu TEMİZLER, onImageSelected/
    // onVideoSelected de (aşağıda) seçilirse gifUrl'i TEMİZLER — hangi taraftan
    // girilirse girilsin en fazla biri dolu kalır.
    private val _selectedGifUrl = MutableStateFlow<String?>(null)
    val selectedGifUrl: StateFlow<String?> = _selectedGifUrl.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<CreatePostEvent>()
    val events: SharedFlow<CreatePostEvent> = _events

    fun onContentChange(text: String) {
        _content.value = text
    }

    fun onVisibilityChange(value: String) {
        _visibility.value = value
    }

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
        // Görsel VE video mutually-exclusive (UI'da) — biri seçilince öteki
        // temizlenir, backend ikisini de kabul etse de karışık bir post İCAT
        // EDİLMEDİ (bkz. görev tanımı).
        if (uri != null) {
            _selectedVideoUri.value = null
            _isReel.value = false
            _selectedGifUrl.value = null
        }
    }

    fun onVideoSelected(uri: Uri?) {
        _selectedVideoUri.value = uri
        if (uri != null) {
            _selectedImageUri.value = null
            _selectedGifUrl.value = null
        } else {
            _isReel.value = false
        }
    }

    fun onReelToggle(value: Boolean) {
        _isReel.value = value
    }

    /** GIF seçilince görsel/video temizlenir — backend'in mutually-exclusive
     * kuralıyla (bkz. InteractionsRepository.createPost yorumu) AYNI. */
    fun onGifSelected(url: String) {
        _selectedGifUrl.value = url
        _selectedImageUri.value = null
        _selectedVideoUri.value = null
        _isReel.value = false
    }

    fun onGifCleared() {
        _selectedGifUrl.value = null
    }

    /** [context] SADECE seçilen görsel/video Uri'sinin byte'larını/mime tipini
     * okumak için gerekiyor (ContentResolver) — ViewModel Context'i SAKLAMAZ,
     * sadece bu tek çağrı sırasında kullanır. */
    fun submit(context: Context) {
        if (_submitting.value) return
        val text = _content.value.trim()
        val imageUri = _selectedImageUri.value
        val videoUri = _selectedVideoUri.value
        val gifUrl = _selectedGifUrl.value
        if (text.isEmpty() && imageUri == null && videoUri == null && gifUrl.isNullOrBlank()) {
            _error.value = "Bir şeyler yaz, bir görsel veya video seç"
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
                    // Bilinmeyen/desteklenmeyen video tipi — backend zaten
                    // uzantı+MIME'ı doğrulayıp reddedecek, isteği hiç
                    // GÖNDERMEDEN önce burada durdurmak bant genişliği/
                    // kullanıcı bekleme süresi israfını önler.
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
                    // Boyut önceden okunamazsa isteği yine de gönder — backend
                    // MAX_VIDEO_SIZE kontrolünü zaten yapıyor (fail-open, ön-
                    // kontrol sadece bir optimizasyon, tek doğruluk kaynağı
                    // backend).
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
                val result = interactionsRepository.createPost(
                    content = text,
                    visibility = _visibility.value,
                    imageBytes = imageBytes,
                    imageMimeType = imageMimeType,
                    // Gerçek uzantı önemli değil — backend içeriği magic-byte ile
                    // doğruluyor (bkz. storage_helper.py deseni), sabit isim yeterli.
                    imageFileName = if (imageBytes != null) "upload.jpg" else null,
                    videoBytes = videoBytes,
                    videoMimeType = videoMimeType,
                    // Video için AKSİNE gerçek uzantı ÖNEMLİ (yukarıdaki
                    // eşleme+docstring'e bkz.) — sabit isim burada YETERSİZ.
                    videoFileName = videoFileName,
                    isReel = _isReel.value,
                    gifUrl = gifUrl,
                )
            ) {
                is CreatePostResult.Success -> _events.emit(CreatePostEvent.Success)
                is CreatePostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(CreatePostEvent.SessionExpired)
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
        "empty" -> "Bir şeyler yaz, bir görsel veya video seç"
        "upload_failed" -> "Görsel yüklenemedi, lütfen tekrar deneyin"
        "video_upload_failed" -> "Video yüklenemedi, lütfen tekrar deneyin"
        "reel_requires_video" -> "Reel için video gerekli"
        else -> "Paylaşılamadı, lütfen tekrar deneyin"
    }
}
