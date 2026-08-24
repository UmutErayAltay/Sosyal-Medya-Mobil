package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CreateStoryResult
import com.umuterayaltay.sosyal.nativeapp.repository.StoryOverlayElement
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
 * Canvas'a eklenmiş TEK bir öğe — kararlı `id` (yerel, backend'e hiç
 * gönderilmez) Compose'un liste render'ında key() için var, aksi halde bir
 * eleman kaldırılınca kalanların pozisyon/ölçek state'i BİRBİRİNE KAYABİLİRDİ
 * (bkz. StoryCreateScreen.kt'deki key() kullanımı).
 *
 * 2026-08-11 (kullanıcı isteği: "@bahsetme ve #hashtag sticker'ı") —
 * repository.StoryOverlayElement (backend'e GİDEN/backend'den GELEN Double
 * tabanlı domain) ile AYNI 3 alt tip, ama UI katmanı Float kullandığı için
 * (DraggableStoryElement zaten Float konuşuyor) AYRI bir hafif "düzenleme
 * state'i" — submit()'te StoryOverlayElement'e çevrilir.
 */
sealed class StoryOverlayElementState {
    abstract val id: String
    abstract val positionX: Float
    abstract val positionY: Float
    abstract val scale: Float

    data class Image(
        override val id: String,
        val url: String,
        override val positionX: Float = 0.5f,
        override val positionY: Float = 0.5f,
        override val scale: Float = 1f,
    ) : StoryOverlayElementState()

    data class Mention(
        override val id: String,
        val username: String,
        override val positionX: Float = 0.5f,
        override val positionY: Float = 0.5f,
        override val scale: Float = 1f,
    ) : StoryOverlayElementState()

    data class Hashtag(
        override val id: String,
        val tag: String,
        override val positionX: Float = 0.5f,
        override val positionY: Float = 0.5f,
        override val scale: Float = 1f,
    ) : StoryOverlayElementState()
}

/** Sürükleme/pinch sonucu KENDİ alt tipini KORUYARAK yeni konum/ölçekle
 * `copy()` etmek için — `sealed class`'ın `abstract val`'ları ayrı ayrı
 * `copy()` EDİLEMEZ, her alt tip kendi `copy()`'sini çağırmalı. */
private fun StoryOverlayElementState.withPosition(x: Float, y: Float): StoryOverlayElementState = when (this) {
    is StoryOverlayElementState.Image -> copy(positionX = x, positionY = y)
    is StoryOverlayElementState.Mention -> copy(positionX = x, positionY = y)
    is StoryOverlayElementState.Hashtag -> copy(positionX = x, positionY = y)
}

private fun StoryOverlayElementState.withScale(scale: Float): StoryOverlayElementState = when (this) {
    is StoryOverlayElementState.Image -> copy(scale = scale)
    is StoryOverlayElementState.Mention -> copy(scale = scale)
    is StoryOverlayElementState.Hashtag -> copy(scale = scale)
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

        // Backend api_create_story()'nin AYNI sınırı — fazlası sessizce
        // kırpılır, burada da ÖNCEDEN engellenir (bkz. onOverlayImageSelected).
        private const val MAX_OVERLAY_ELEMENTS = 3
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

    // 2026-08-09 (kullanıcı isteği: "instagram'a benzer hikaye editörü,
    // yazıyı/anketi çekerek istediğimiz yere koyabilelim") — backend
    // api_create_story() BUNU ZATEN kabul ediyordu (web'in stories.js'inde
    // sürükle-bırak editörü ÇOKTAN vardı, bkz. .context/active_context.md),
    // SADECE native tarafında hiç UI/state YOKTU — submit() bu alanları
    // HER ZAMAN null gönderiyordu (backend'in kendi varsayılanlarına
    // düşülüyordu: caption 0.5/0.75, poll 0.5/0.75/1.0). Varsayılanlar
    // backend'deki AYNI değerler.
    private val _captionPositionX = MutableStateFlow(0.5f)
    val captionPositionX: StateFlow<Float> = _captionPositionX.asStateFlow()

    private val _captionPositionY = MutableStateFlow(0.75f)
    val captionPositionY: StateFlow<Float> = _captionPositionY.asStateFlow()

    // 2026-08-11 (kullanıcı isteği: "metin stili/rengi seçenekleri") — null =
    // klasik (düz beyaz yazı), CAPTION_STYLES listesindeki DİĞER değerler
    // arasında döngüsel geçiş (bkz. onCaptionStyleCycle).
    private val _captionStyle = MutableStateFlow<String?>(null)
    val captionStyle: StateFlow<String?> = _captionStyle.asStateFlow()

    // 2026-08-22 (kullanıcı isteği: "yazıyı yazarken alt kısımda 9 farklı
    // renk çıkabilir, yazının kendi rengi seçilebilsin") — captionStyle'dan
    // (pill_light/pill_dark, arka plan hapının rengi) TAMAMEN BAĞIMSIZ bir
    // kavram: yazının KENDİ rengi. null = varsayılan (beyaz). Backend
    // background_color ile AYNI serbest hex regex'i kabul ediyor (bkz.
    // STORY_TEXT_COLOR_SWATCHES, sabit 9 renk sadece native UI'da).
    private val _captionColor = MutableStateFlow<String?>(null)
    val captionColor: StateFlow<String?> = _captionColor.asStateFlow()

    private val _pollPositionX = MutableStateFlow(0.5f)
    val pollPositionX: StateFlow<Float> = _pollPositionX.asStateFlow()

    private val _pollPositionY = MutableStateFlow(0.75f)
    val pollPositionY: StateFlow<Float> = _pollPositionY.asStateFlow()

    private val _pollScale = MutableStateFlow(1f)
    val pollScale: StateFlow<Float> = _pollScale.asStateFlow()

    // 2026-08-10 (kullanıcı raporu: "metin/gif ekle... 2.ye tıklayınca
    // öncekini siliyor ve yeni ekliyor") — İLK sürüm (2026-08-09) TEKİL bir
    // overlayImageUrl/Position/Scale tutuyordu, ikinci bir GIF/sticker
    // eklemek İLKİNİ SİLİYORDU. Artık bir LİSTE (en fazla MAX_OVERLAY_ELEMENTS,
    // backend api_create_story()'nin AYNI sınırı) — her eleman kendi
    // pozisyon/ölçeğini BAĞIMSIZ taşıyan, kararlı bir `id`'yle (Compose
    // canvas'ta key() için) tutuluyor.
    private val _overlayElements = MutableStateFlow<List<StoryOverlayElementState>>(emptyList())
    val overlayElements: StateFlow<List<StoryOverlayElementState>> = _overlayElements.asStateFlow()

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

    /** Canvas'taki caption önizlemesi sürüklenince (0..1 aralığına kırpılmış) çağrılır. */
    fun onCaptionPositionChange(x: Float, y: Float) {
        _captionPositionX.value = x.coerceIn(0f, 1f)
        _captionPositionY.value = y.coerceIn(0f, 1f)
    }

    /** Metin düzenleyicideki stil butonuna her dokunuşta döngüsel geçiş —
     * null (klasik) -> pill_light -> pill_dark -> null. Backend'in kabul
     * ettiği TAM OLARAK bu iki değer (bkz. StoriesApi.createStory yorumu). */
    fun onCaptionStyleCycle() {
        _captionStyle.value = when (_captionStyle.value) {
            null -> "pill_light"
            "pill_light" -> "pill_dark"
            else -> null
        }
    }

    /** Metin rengi paletindeki bir renge dokununca — tekrar aynı renge
     * dokununca (toggle) null'a (varsayılan beyaz) döner. */
    fun onCaptionColorChange(hex: String) {
        _captionColor.value = if (_captionColor.value == hex) null else hex
    }

    /** Canvas'taki anket widget'ı sürüklenince/pinch ile ölçeklenince çağrılır. */
    fun onPollPositionChange(x: Float, y: Float) {
        _pollPositionX.value = x.coerceIn(0f, 1f)
        _pollPositionY.value = y.coerceIn(0f, 1f)
    }

    fun onPollScaleChange(scale: Float) {
        // Backend poll_scale sınırıyla AYNI (0.3..3) — bkz. app/api_v1/stories.py.
        _pollScale.value = scale.coerceIn(0.3f, 3f)
    }

    /** MediaPickerSheet'ten GIF (URL) veya sticker (StickerDto.imageUrl) seçilince
     * çağrılır — ikisi de sadece bir görsel URL'i, canvas'ta AYNI şekilde davranır.
     * 2026-08-10 (kullanıcı raporu: "2.ye tıklayınca öncekini siliyor") — artık
     * var olanı DEĞİŞTİRMEK yerine listeye EKLİYOR (backend'in AYNI
     * MAX_OVERLAY_ELEMENTS sınırı — üstündeyse sessizce yok sayılır, kullanıcı
     * zaten "kaldır" ile yer açabilir). */
    fun onOverlayImageSelected(url: String) {
        addOverlayElement(StoryOverlayElementState.Image(id = java.util.UUID.randomUUID().toString(), url = url))
    }

    /** Mention arama sheet'inden bir kullanıcı seçilince çağrılır — 2026-08-11
     * (kullanıcı isteği: "@bahsetme ve #hashtag sticker'ı"). */
    fun onMentionSelected(username: String) {
        addOverlayElement(StoryOverlayElementState.Mention(id = java.util.UUID.randomUUID().toString(), username = username))
    }

    /** Hashtag ekleme dialog'unda "Ekle"ye basılınca çağrılır — mention'ın
     * AKSİNE önceden var olması GEREKMEZ (backend serbest metin kabul eder). */
    fun onHashtagAdded(tag: String) {
        val normalized = tag.trim().removePrefix("#").lowercase()
        if (normalized.isBlank()) return
        addOverlayElement(StoryOverlayElementState.Hashtag(id = java.util.UUID.randomUUID().toString(), tag = normalized))
    }

    private fun addOverlayElement(element: StoryOverlayElementState) {
        if (_overlayElements.value.size >= MAX_OVERLAY_ELEMENTS) return
        _overlayElements.value = _overlayElements.value + element
    }

    fun onOverlayImageRemoved(id: String) {
        _overlayElements.value = _overlayElements.value.filterNot { it.id == id }
    }

    fun onOverlayImagePositionChange(id: String, x: Float, y: Float) {
        _overlayElements.value = _overlayElements.value.map {
            if (it.id == id) it.withPosition(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)) else it
        }
    }

    fun onOverlayImageScaleChange(id: String, scale: Float) {
        _overlayElements.value = _overlayElements.value.map {
            if (it.id == id) it.withScale(scale.coerceIn(0.3f, 3f)) else it
        }
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
                    // SADECE caption doluysa göndermenin bir anlamı var —
                    // boş caption'da backend zaten metni render etmiyor,
                    // ama pozisyonu yine de göndermek zararsız (backend
                    // caption boşsa hiç kullanmıyor).
                    captionPositionX = if (text.isNotEmpty()) _captionPositionX.value else null,
                    captionPositionY = if (text.isNotEmpty()) _captionPositionY.value else null,
                    captionStyle = if (text.isNotEmpty()) _captionStyle.value else null,
                    captionColor = if (text.isNotEmpty()) _captionColor.value else null,
                    pollOptions = filledOptions,
                    pollPositionX = if (hasPoll) _pollPositionX.value else null,
                    pollPositionY = if (hasPoll) _pollPositionY.value else null,
                    pollScale = if (hasPoll) _pollScale.value else null,
                    overlayElements = _overlayElements.value.map { state ->
                        when (state) {
                            is StoryOverlayElementState.Image -> StoryOverlayElement.Image(
                                url = state.url,
                                positionX = state.positionX.toDouble(),
                                positionY = state.positionY.toDouble(),
                                scale = state.scale.toDouble(),
                            )
                            is StoryOverlayElementState.Mention -> StoryOverlayElement.Mention(
                                username = state.username,
                                positionX = state.positionX.toDouble(),
                                positionY = state.positionY.toDouble(),
                                scale = state.scale.toDouble(),
                            )
                            is StoryOverlayElementState.Hashtag -> StoryOverlayElement.Hashtag(
                                tag = state.tag,
                                positionX = state.positionX.toDouble(),
                                positionY = state.positionY.toDouble(),
                                scale = state.scale.toDouble(),
                            )
                        }
                    },
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
