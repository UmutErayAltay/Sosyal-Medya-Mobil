package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CreatePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class CreatePostEvent {
    // 2026-08-21 (taslak): [savedAsDraft] ekranın "Paylaşıldı" mı "Taslak
    // kaydedildi" mi göstereceğini ayırt etmesi için.
    data class Success(val savedAsDraft: Boolean) : CreatePostEvent()
    data object SessionExpired : CreatePostEvent()
}

/**
 * "Yeni Gönderi" ekranı için ViewModel — app/api_v1.py api_create_post()
 * sözleşmesiyle (bkz. InteractionsRepository.createPost) AYNI BİLİNÇLİ SINIR:
 * metin + EN FAZLA 4 opsiyonel görsel (2026-08-09'dan beri çoklu, bkz. MAX_IMAGES)
 * VEYA TEK opsiyonel video/reel + görünürlük (public/followers/close_friends).
 * Diğer ViewModel'lerdeki AYNI 401 deseni
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
        // web'in upload_images(..., max_count=4) ile AYNI sınır (2026-08-09,
        // kullanıcı isteği: "1'den fazla görsel ekleme olsun").
        private const val MAX_IMAGES = 4

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

    // 2026-08-08 (kullanıcı kararı: "sitedeki bütün postları ve hesapları
    // gizli ve takipçiye özel yap") — yeni post oluşturmanın varsayılan
    // görünürlüğü "public"ten "followers"a çevrildi (backend'deki AYNI karar,
    // bkz. app/routes/posts.py). Kullanıcı hâlâ elle "Herkese açık" seçebilir,
    // sadece varsayılan değişti.
    private val _visibility = MutableStateFlow("followers")
    val visibility: StateFlow<String> = _visibility.asStateFlow()

    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris.asStateFlow()

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

    // Anket editörü (Faz 5 Dalga 4C) — GIF'in showMediaPicker deseniyle BENZER:
    // kullanıcı bilerek "Anket Ekle" butonuna basmadıkça alanlar görünmez.
    // Görsel/video/GIF ile mutually-exclusive DEĞİL (backend api_create_post()
    // ile AYNI kural) — bu yüzden diğer seçimler burada TEMİZLENMİYOR.
    private val _showPollEditor = MutableStateFlow(false)
    val showPollEditor: StateFlow<Boolean> = _showPollEditor.asStateFlow()

    // Başlangıçta 2 boş seçenek — "en az 2 seçenek" kuralı UI'da baştan görünür.
    private val _pollOptions = MutableStateFlow(listOf("", ""))
    val pollOptions: StateFlow<List<String>> = _pollOptions.asStateFlow()

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

    /** PickMultipleVisualMedia sonucu — MAX_IMAGES'ı aşan seçim SESSİZCE
     * kırpılır (Android Photo Picker'ın kendisi maxItems ile ZATEN sınırlıyor,
     * bu sadece bir güvenlik ağı). Boş liste geçmek "seçimi temizle" anlamına
     * gelir (tek görselin AKSİNE `null` yerine `emptyList()` kullanılıyor). */
    fun onImagesSelected(uris: List<Uri>) {
        _selectedImageUris.value = uris.take(MAX_IMAGES)
        // Görsel VE video mutually-exclusive (UI'da) — biri seçilince öteki
        // temizlenir, backend ikisini de kabul etse de karışık bir post İCAT
        // EDİLMEDİ (bkz. görev tanımı).
        if (uris.isNotEmpty()) {
            _selectedVideoUri.value = null
            _isReel.value = false
            _selectedGifUrl.value = null
        }
    }

    /** Önizleme şeridindeki X butonu — tek bir görseli listeden çıkarır. */
    fun onImageRemovedAt(index: Int) {
        _selectedImageUris.value = _selectedImageUris.value.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
    }

    fun onVideoSelected(uri: Uri?) {
        _selectedVideoUri.value = uri
        if (uri != null) {
            _selectedImageUris.value = emptyList()
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
        _selectedImageUris.value = emptyList()
        _selectedVideoUri.value = null
        _isReel.value = false
    }

    fun onGifCleared() {
        _selectedGifUrl.value = null
    }

    /** "Anket Ekle" butonuna basınca editörü aç/kapat — kapatınca girilen
     * seçenekler DE sıfırlanır (yeniden açılınca temiz 2 boş alanla başlasın,
     * yarım kalmış bir anket sessizce gönderilmesin). */
    fun togglePollEditor() {
        val next = !_showPollEditor.value
        _showPollEditor.value = next
        if (!next) {
            _pollOptions.value = listOf("", "")
        }
    }

    fun onPollOptionChange(index: Int, value: String) {
        _pollOptions.value = _pollOptions.value.toMutableList().also {
            if (index in it.indices) it[index] = value
        }
    }

    /** Maksimum 4 seçenek — backend poll_option_1..4 ile AYNI sınır. */
    fun addPollOption() {
        if (_pollOptions.value.size < 4) {
            _pollOptions.value = _pollOptions.value + ""
        }
    }

    /** Minimum 2 seçeneğin altına inmez — "en az 2 dolu seçenek anket sayılır"
     * kuralının UI'da baştan garanti edilmesi için. */
    fun removePollOption(index: Int) {
        if (_pollOptions.value.size > 2 && index in _pollOptions.value.indices) {
            _pollOptions.value = _pollOptions.value.toMutableList().also { it.removeAt(index) }
        }
    }

    /** [context] SADECE seçilen görsel/video Uri'sinin byte'larını/mime tipini
     * okumak için gerekiyor (ContentResolver) — ViewModel Context'i SAKLAMAZ,
     * sadece bu tek çağrı sırasında kullanır. */
    fun submit(context: Context, saveAsDraft: Boolean = false) {
        if (_submitting.value) return
        val text = _content.value.trim()
        val imageUris = _selectedImageUris.value
        val videoUri = _selectedVideoUri.value
        val gifUrl = _selectedGifUrl.value
        // En az 2 dolu seçenek varsa "anket var" sayılır — backend
        // api_create_post() ile AYNI eşik (bkz. InteractionsRepository).
        val filledPollOptions = if (_showPollEditor.value) {
            _pollOptions.value.map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        val hasPoll = filledPollOptions.size >= 2
        if (text.isEmpty() && imageUris.isEmpty() && videoUri == null && gifUrl.isNullOrBlank() && !hasPoll) {
            _error.value = "Bir şeyler yaz, bir görsel veya video seç"
            return
        }
        // Backend'in "anket varsa content zorunlu" kuralının UI'daki hızlı
        // geri bildirimi — tek doğruluk kaynağı hâlâ backend, bu sadece
        // isteği hiç GÖNDERMEDEN önce kullanıcıyı uyarır.
        if (hasPoll && text.isEmpty()) {
            _error.value = "Anket için bir soru yaz"
            return
        }

        viewModelScope.launch {
            _submitting.value = true
            _error.value = null

            val images = mutableListOf<ImageUpload>()
            for (uri in imageUris) {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        images += ImageUpload(bytes, context.contentResolver.getType(uri))
                    }
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
                    images = images,
                    videoBytes = videoBytes,
                    videoMimeType = videoMimeType,
                    // Video için AKSİNE gerçek uzantı ÖNEMLİ (yukarıdaki
                    // eşleme+docstring'e bkz.) — sabit isim burada YETERSİZ.
                    videoFileName = videoFileName,
                    isReel = _isReel.value,
                    gifUrl = gifUrl,
                    pollOptions = filledPollOptions,
                    saveAsDraft = saveAsDraft,
                )
            ) {
                is CreatePostResult.Success -> _events.emit(CreatePostEvent.Success(saveAsDraft))
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
