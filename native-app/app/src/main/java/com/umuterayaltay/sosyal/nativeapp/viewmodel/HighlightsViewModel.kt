package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.DeleteHighlightResult
import com.umuterayaltay.sosyal.nativeapp.repository.Highlight
import com.umuterayaltay.sosyal.nativeapp.repository.HighlightItem
import com.umuterayaltay.sosyal.nativeapp.repository.HighlightViewResult
import com.umuterayaltay.sosyal.nativeapp.repository.HighlightsResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HighlightsEvent {
    data object SessionExpired : HighlightsEvent()
    data object HighlightDeleted : HighlightsEvent()
}

/**
 * Öne çıkanlar (highlights) — bağımsız ekran/VM (görev kapsamı: ProfileScreen'e
 * BU DALGADA bağlanmayacak, Dalga 4'e ertelendi — bkz. görev tanımı "Kapsam
 * NOTU"). Backend app/api_v1/stories.py'nin highlight uçlarının (get/view/
 * delete/save) tüketicisi. `save-highlight` bu VM'de YOK bilerek —
 * StoryViewerViewModel.saveToHighlight() zaten story-viewer akışının bir
 * parçası (bir hikayeden highlight'a kaydetme), burası SADECE var olan
 * highlight'ları listeleme/görüntüleme/silme.
 */
class HighlightsViewModel(private val userId: String) : ViewModel() {

    private val storiesRepository = ServiceLocator.storiesRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private val _selectedTitle = MutableStateFlow("")
    val selectedTitle: StateFlow<String> = _selectedTitle.asStateFlow()

    private val _selectedIsMine = MutableStateFlow(false)
    val selectedIsMine: StateFlow<Boolean> = _selectedIsMine.asStateFlow()

    private val _selectedItems = MutableStateFlow<List<HighlightItem>>(emptyList())
    val selectedItems: StateFlow<List<HighlightItem>> = _selectedItems.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<HighlightsEvent>()
    val events: SharedFlow<HighlightsEvent> = _events

    init {
        loadHighlights()
    }

    fun loadHighlights() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = storiesRepository.getHighlights(userId)) {
                is HighlightsResult.Success -> _highlights.value = result.highlights
                is HighlightsResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    fun viewHighlight(highlightId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = storiesRepository.viewHighlight(highlightId)) {
                is HighlightViewResult.Success -> {
                    _selectedTitle.value = result.data.title
                    _selectedIsMine.value = result.data.isMine
                    _selectedItems.value = result.data.items
                }
                is HighlightViewResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    fun deleteHighlight(highlightId: String) {
        viewModelScope.launch {
            when (val result = storiesRepository.deleteHighlight(highlightId)) {
                is DeleteHighlightResult.Success -> {
                    _highlights.value = _highlights.value.filter { it.id != highlightId }
                    _events.emit(HighlightsEvent.HighlightDeleted)
                }
                is DeleteHighlightResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    private suspend fun handleError(code: String?, silent: Boolean = false) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(HighlightsEvent.SessionExpired)
            return
        }
        if (silent) return
        _error.value = when (code) {
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> "Öne çıkanlar yüklenemedi"
        }
    }
}

/** HighlightsViewModel constructor'ı userId parametresi aldığı için
 * Compose'un varsayılan factory'si yetmez — HashtagViewModelFactory ile
 * AYNI desen. */
class HighlightsViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HighlightsViewModel(userId) as T
    }
}
