package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.StoryBarItem
import com.umuterayaltay.sosyal.nativeapp.repository.StoryBarResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class StoryBarEvent {
    data object SessionExpired : StoryBarEvent()
}

/**
 * Feed'in üstündeki hikaye çubuğu için AYRI bir ViewModel — FeedViewModel'e
 * KARIŞTIRILMADI (görev tanımı: FeedScreen bunu `viewModel()` ile ayrı
 * instantiate eder). GET /stories/bar başarısız olursa (migration
 * uygulanmamış/geçici hata) çubuk SESSİZCE boş kalır — active_stories_bar()
 * zaten aynı toleransı web'de gösteriyor (bkz. backend docstring'i), tek bir
 * hikaye çubuğu hatası feed'in geri kalanını bir hata state'ine düşürmemeli.
 */
class StoryBarViewModel : ViewModel() {

    private val storiesRepository = ServiceLocator.storiesRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _items = MutableStateFlow<List<StoryBarItem>>(emptyList())
    val items: StateFlow<List<StoryBarItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<StoryBarEvent>()
    val events: SharedFlow<StoryBarEvent> = _events

    init {
        loadBar()
    }

    fun loadBar() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = storiesRepository.getBar()) {
                is StoryBarResult.Success -> _items.value = result.items
                is StoryBarResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(StoryBarEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - bkz. sınıf yorumu.
                }
            }
            _isLoading.value = false
        }
    }
}
