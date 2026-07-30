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
 * metin + TEK opsiyonel görsel + görünürlük (public/followers/close_friends).
 * Diğer ViewModel'lerdeki AYNI 401 deseni (PostDetailViewModel/NewMessageViewModel):
 * proaktif doğrulama yok, ilk "unauthorized" gelince token temizlenip
 * SessionExpired event'i yayınlanır.
 *
 * Paylaşım BAŞARILI olunca sadece bir Success event'i yayınlanır — Feed'in
 * ANINDA yeni postu göstermesi bu turun kapsamı DIŞI (kullanıcı var olan
 * pull-to-refresh ile görebilir), karmaşık bir ekranlar-arası state-geçişi
 * BİLİNÇLİ olarak İCAT EDİLMEDİ.
 */
class CreatePostViewModel : ViewModel() {

    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _visibility = MutableStateFlow("public")
    val visibility: StateFlow<String> = _visibility.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

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
    }

    /** [context] SADECE seçilen görsel Uri'sinin byte'larını/mime tipini okumak
     * için gerekiyor (ContentResolver) — ViewModel Context'i SAKLAMAZ, sadece
     * bu tek çağrı sırasında kullanır. */
    fun submit(context: Context) {
        if (_submitting.value) return
        val text = _content.value.trim()
        val imageUri = _selectedImageUri.value
        if (text.isEmpty() && imageUri == null) {
            _error.value = "Bir şeyler yaz veya bir görsel seç"
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

            when (
                val result = interactionsRepository.createPost(
                    content = text,
                    visibility = _visibility.value,
                    imageBytes = imageBytes,
                    imageMimeType = imageMimeType,
                    // Gerçek uzantı önemli değil — backend içeriği magic-byte ile
                    // doğruluyor (bkz. storage_helper.py deseni), sabit isim yeterli.
                    imageFileName = if (imageBytes != null) "upload.jpg" else null,
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
        "empty" -> "Bir şeyler yaz veya bir görsel seç"
        "upload_failed" -> "Görsel yüklenemedi, lütfen tekrar deneyin"
        else -> "Paylaşılamadı, lütfen tekrar deneyin"
    }
}
