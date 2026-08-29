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
 * tabanlı domain) ile AYNI alt tipler, ama UI katmanı Float kullandığı için
 * (DraggableStoryElement zaten Float konuşuyor) AYRI bir hafif "düzenleme
 * state'i" — submit()'te StoryOverlayElement'e çevrilir.
 *
 * Kullanıcı raporu ("storye metin eklediğimizde sadece 1 tane ekliyor, tekrar
 * tıklayınca öncekini düzenliyor") burada çözülüyor: eskiden TEK bir tekil
 * `caption`/`captionStyle`/`captionColor`/`captionPosition*` state seti vardı
 * (bu sınıfın DIŞINDA, ViewModel'de ayrı alanlar) — artık metin de GIF/
 * sticker/mention/hashtag gibi bu listenin bir `Text` elemanı, yani ÇOKLU ve
 * her biri BAĞIMSIZ. `rotation` (derece, iki-parmak pinch+rotate jesti) TÜM
 * alt tiplere eklendi.
 */
sealed class StoryOverlayElementState {
    abstract val id: String
    abstract val positionX: Float
    abstract val positionY: Float
    abstract val scale: Float
    abstract val rotation: Float

    data class Text(
        override val id: String,
        val text: String = "",
        // null = klasik (düz beyaz yazı), "pill_light"/"pill_dark" = renkli
        // pilli arka plan — backend'in kabul ettiği TAM OLARAK bu iki değer.
        val style: String? = null,
        // style'dan BAĞIMSIZ, background_color ile AYNI serbest hex; null =
        // varsayılan (beyaz).
        val color: String? = null,
        override val positionX: Float = 0.5f,
        // Metin katmanları varsayılan olarak alt-orta bölgede başlar (web'in
        // eski tekil caption'ıyla AYNI 0.75 — backend'in caption_position_y
        // varsayılanı, overlay'in genel 0.5'i DEĞİL).
        override val positionY: Float = 0.75f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
    ) : StoryOverlayElementState()

    data class Image(
        override val id: String,
        val url: String,
        override val positionX: Float = 0.5f,
        override val positionY: Float = 0.5f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
    ) : StoryOverlayElementState()

    data class Mention(
        override val id: String,
        val username: String,
        override val positionX: Float = 0.5f,
        override val positionY: Float = 0.5f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
    ) : StoryOverlayElementState()

    data class Hashtag(
        override val id: String,
        val tag: String,
        override val positionX: Float = 0.5f,
        override val positionY: Float = 0.5f,
        override val scale: Float = 1f,
        override val rotation: Float = 0f,
    ) : StoryOverlayElementState()
}

/** Sürükleme/pinch sonucu KENDİ alt tipini KORUYARAK yeni konum/ölçek/açıyla
 * `copy()` etmek için — `sealed class`'ın `abstract val`'ları ayrı ayrı
 * `copy()` EDİLEMEZ, her alt tip kendi `copy()`'sini çağırmalı. */
private fun StoryOverlayElementState.withPosition(x: Float, y: Float): StoryOverlayElementState = when (this) {
    is StoryOverlayElementState.Text -> copy(positionX = x, positionY = y)
    is StoryOverlayElementState.Image -> copy(positionX = x, positionY = y)
    is StoryOverlayElementState.Mention -> copy(positionX = x, positionY = y)
    is StoryOverlayElementState.Hashtag -> copy(positionX = x, positionY = y)
}

private fun StoryOverlayElementState.withScale(scale: Float): StoryOverlayElementState = when (this) {
    is StoryOverlayElementState.Text -> copy(scale = scale)
    is StoryOverlayElementState.Image -> copy(scale = scale)
    is StoryOverlayElementState.Mention -> copy(scale = scale)
    is StoryOverlayElementState.Hashtag -> copy(scale = scale)
}

private fun StoryOverlayElementState.withRotation(rotation: Float): StoryOverlayElementState = when (this) {
    is StoryOverlayElementState.Text -> copy(rotation = rotation)
    is StoryOverlayElementState.Image -> copy(rotation = rotation)
    is StoryOverlayElementState.Mention -> copy(rotation = rotation)
    is StoryOverlayElementState.Hashtag -> copy(rotation = rotation)
}

/**
 * "Yeni Hikaye" ekranı için ViewModel — app/stories.py::parse_overlay_elements()
 * sözleşmesiyle AYNI BİLİNÇLİ SINIR: (TEK opsiyonel görsel VEYA TEK opsiyonel
 * video, mutually exclusive — CreatePostViewModel'deki AYNI görsel/video
 * deseni, ama KENDİ kopyası, CreatePostViewModel'e DOKUNULMADI) + opsiyonel
 * anket (0-4 seçenek, ≥2 doluysa aktif) + görünürlük (public/followers/
 * close_friends) + background_color (SADECE medya yoksa anlamlı, backend
 * zaten medya varsa yok sayıyor) + `overlayElements` listesi (metin dahil,
 * en fazla MAX_OVERLAY_ELEMENTS).
 *
 * `caption` alanı artık AYRI bir state DEĞİL — backend'e her zaman boş
 * gönderilir, backend `overlay_elements` içindeki "text" katmanlarından
 * türetir (bkz. app/stories.py::create_story() "türetilmiş caption" bloğu).
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

        // Backend app/stories.py::MAX_OVERLAY_ELEMENTS ile AYNI sınır —
        // fazlası sessizce kırpılır, burada da ÖNCEDEN engellenir (bkz.
        // addOverlayElement). Çoklu metin katmanı özelliğiyle 3'ten 10'a
        // çıkarıldı (metin de artık bu listenin bir elemanı).
        private const val MAX_OVERLAY_ELEMENTS = 10
    }

    private val storiesRepository = ServiceLocator.storiesRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _visibility = MutableStateFlow("public")
    val visibility: StateFlow<String> = _visibility.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _backgroundColor = MutableStateFlow<String?>(null)
    val backgroundColor: StateFlow<String?> = _backgroundColor.asStateFlow()

    private val _pollPositionX = MutableStateFlow(0.5f)
    val pollPositionX: StateFlow<Float> = _pollPositionX.asStateFlow()

    private val _pollPositionY = MutableStateFlow(0.75f)
    val pollPositionY: StateFlow<Float> = _pollPositionY.asStateFlow()

    private val _pollScale = MutableStateFlow(1f)
    val pollScale: StateFlow<Float> = _pollScale.asStateFlow()

    // Anketin iki-parmak döndürme açısı — overlay elemanlarıyla AYNI jest.
    private val _pollRotation = MutableStateFlow(0f)
    val pollRotation: StateFlow<Float> = _pollRotation.asStateFlow()

    // 2026-08-10 (kullanıcı raporu: "metin/gif ekle... 2.ye tıklayınca
    // öncekini siliyor ve yeni ekliyor") — İLK sürüm (2026-08-09) TEKİL bir
    // overlayImageUrl/Position/Scale tutuyordu, ikinci bir GIF/sticker
    // eklemek İLKİNİ SİLİYORDU. Artık bir LİSTE (en fazla MAX_OVERLAY_ELEMENTS)
    // — her eleman kendi pozisyon/ölçek/açısını BAĞIMSIZ taşıyan, kararlı bir
    // `id`'yle (Compose canvas'ta key() için) tutuluyor. Metin de (2. kullanıcı
    // raporu: "sadece 1 tane ekliyor") artık BU listenin bir `Text` elemanı.
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

    fun onVisibilityChange(value: String) {
        _visibility.value = value
    }

    fun onBackgroundColorChange(hex: String?) {
        _backgroundColor.value = hex
    }

    /** Canvas'taki anket widget'ı sürüklenince/pinch ile ölçeklenince/döndürülünce çağrılır. */
    fun onPollPositionChange(x: Float, y: Float) {
        _pollPositionX.value = x.coerceIn(0f, 1f)
        _pollPositionY.value = y.coerceIn(0f, 1f)
    }

    fun onPollScaleChange(scale: Float) {
        // Backend poll_scale sınırıyla AYNI (0.3..3) — bkz. app/stories.py.
        _pollScale.value = scale.coerceIn(0.3f, 3f)
    }

    fun onPollRotationChange(rotation: Float) {
        _pollRotation.value = rotation
    }

    /** "Aa" araç butonuna dokununca çağrılır — HER dokunuş YENİ, boş bir
     * text katmanı ekler (eskiden burada tekil caption state'i AÇILIYORDU,
     * ikinci dokunuş İLKİNİ düzenlemeye başlıyordu — bildirilen bug tam
     * olarak buydu). Dönen id, açılacak [StoryTextEditorOverlay]'i bu YENİ
     * katmana bağlamak için ekrana taşınır. Limit doluysa (araç çubuğundaki
     * `canAddMoreOverlay` zaten butonu devre dışı bırakır) null döner. */
    fun onTextElementAdded(): String? {
        if (_overlayElements.value.size >= MAX_OVERLAY_ELEMENTS) return null
        val id = java.util.UUID.randomUUID().toString()
        _overlayElements.value = _overlayElements.value + StoryOverlayElementState.Text(id = id)
        return id
    }

    /** Metin düzenleyicide "Tamam"a basılınca çağrılır — SADECE o katmanın
     * metnini günceller, diğer katmanlara DOKUNMAZ (eskiki tekil caption'ın
     * AKSİNE). Boş metin burada REDDEDİLMEZ — ekran tarafı boşsa katmanı
     * `onOverlayImageRemoved` ile ayrıca siler (bkz. StoryCreateScreen.kt). */
    fun onTextContentChange(id: String, text: String) {
        _overlayElements.value = _overlayElements.value.map {
            if (it.id == id && it is StoryOverlayElementState.Text) it.copy(text = text) else it
        }
    }

    /** Metin düzenleyicideki stil butonuna her dokunuşta döngüsel geçiş —
     * null (klasik) -> pill_light -> pill_dark -> null, SADECE düzenlenen
     * katman için (eskiden TEK global caption'a uygulanıyordu). */
    fun onTextStyleCycle(id: String) {
        _overlayElements.value = _overlayElements.value.map {
            if (it.id == id && it is StoryOverlayElementState.Text) {
                it.copy(style = when (it.style) {
                    null -> "pill_light"
                    "pill_light" -> "pill_dark"
                    else -> null
                })
            } else it
        }
    }

    /** Metin rengi paletindeki bir renge dokununca — tekrar aynı renge
     * dokununca (toggle) null'a (varsayılan beyaz) döner, SADECE düzenlenen
     * katman için. */
    fun onTextColorChange(id: String, hex: String) {
        _overlayElements.value = _overlayElements.value.map {
            if (it.id == id && it is StoryOverlayElementState.Text) {
                it.copy(color = if (it.color == hex) null else hex)
            } else it
        }
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

    /** İSİM tarihsel — sadece görsel/GIF DEĞİL, mention/hashtag/text dahil
     * HERHANGİ bir overlay elemanını id'sine göre kaldırır (bkz. bu
     * fonksiyonun HER ZAMAN genel amaçlı olduğu StoryCreateScreen.kt'deki
     * "Kaldır" butonu — tüm eleman tiplerinde AYNI isimle çağrılıyor). */
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

    /** İki-parmak pinch+rotate jestinin döndürme bileşeni — pozisyon/ölçekle
     * AYNI id-bazlı güncelleme deseni. Derece cinsinden, clamp YOK (360'a göre
     * normalize backend'de yapılır, bkz. app/stories.py::parse_overlay_elements). */
    fun onOverlayRotationChange(id: String, rotation: Float) {
        _overlayElements.value = _overlayElements.value.map {
            if (it.id == id) it.withRotation(rotation) else it
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
        val imageUri = _selectedImageUri.value
        val videoUri = _selectedVideoUri.value
        val filledOptions = _pollOptions.value.map { it.trim() }.filter { it.isNotEmpty() }
        val hasPoll = filledOptions.size >= 2
        val overlayElements = _overlayElements.value

        if (overlayElements.isEmpty() && imageUri == null && videoUri == null && !hasPoll) {
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
                    // Boş gönderilir — backend overlay_elements içindeki
                    // "text" katmanlarından türetir (bkz. app/stories.py::
                    // create_story() "türetilmiş caption" bloğu). Eski tekil
                    // caption/captionPosition/captionStyle/captionColor
                    // parametreleri de bu yüzden ARTIK HEP null gönderiliyor.
                    caption = "",
                    imageBytes = imageBytes,
                    imageMimeType = imageMimeType,
                    imageFileName = if (imageBytes != null) "upload.jpg" else null,
                    videoBytes = videoBytes,
                    videoMimeType = videoMimeType,
                    videoFileName = videoFileName,
                    visibility = _visibility.value,
                    // Renk sadece medya YOKSA anlamlı — backend zaten medya
                    // varsa yok sayıyor (bkz. app/stories.py), burada ekstra
                    // bir istemci-taraf kontrolüne gerek yok.
                    backgroundColor = if (imageUri == null && videoUri == null) _backgroundColor.value else null,
                    captionPositionX = null,
                    captionPositionY = null,
                    captionStyle = null,
                    captionColor = null,
                    pollOptions = filledOptions,
                    pollPositionX = if (hasPoll) _pollPositionX.value else null,
                    pollPositionY = if (hasPoll) _pollPositionY.value else null,
                    pollScale = if (hasPoll) _pollScale.value else null,
                    pollRotation = if (hasPoll) _pollRotation.value else null,
                    overlayElements = overlayElements.mapNotNull { state ->
                        when (state) {
                            is StoryOverlayElementState.Text -> {
                                // Boş metinli bir katman (kullanıcı "Aa"ya
                                // basıp hiçbir şey yazmadan kapattıysa)
                                // gönderilmez — ekran tarafı zaten bunu
                                // canlıyken silmeye çalışır, bu SON bir
                                // güvenlik ağı.
                                val trimmed = state.text.trim()
                                if (trimmed.isEmpty()) null else StoryOverlayElement.Text(
                                    text = trimmed, style = state.style, color = state.color,
                                    positionX = state.positionX.toDouble(),
                                    positionY = state.positionY.toDouble(),
                                    scale = state.scale.toDouble(),
                                    rotation = state.rotation.toDouble(),
                                )
                            }
                            is StoryOverlayElementState.Image -> StoryOverlayElement.Image(
                                url = state.url,
                                positionX = state.positionX.toDouble(),
                                positionY = state.positionY.toDouble(),
                                scale = state.scale.toDouble(),
                                rotation = state.rotation.toDouble(),
                            )
                            is StoryOverlayElementState.Mention -> StoryOverlayElement.Mention(
                                username = state.username,
                                positionX = state.positionX.toDouble(),
                                positionY = state.positionY.toDouble(),
                                scale = state.scale.toDouble(),
                                rotation = state.rotation.toDouble(),
                            )
                            is StoryOverlayElementState.Hashtag -> StoryOverlayElement.Hashtag(
                                tag = state.tag,
                                positionX = state.positionX.toDouble(),
                                positionY = state.positionY.toDouble(),
                                scale = state.scale.toDouble(),
                                rotation = state.rotation.toDouble(),
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
