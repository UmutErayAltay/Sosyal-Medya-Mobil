package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.MessageSearchResultDto
import com.umuterayaltay.sosyal.nativeapp.repository.MessageSearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class MessageSearchEvent {
    data object SessionExpired : MessageSearchEvent()
}

/**
 * GLOBAL mesaj arama ekranı (Faz 5 Dalga 1B) — GET /messages/search
 * (sohbet-içi aramanın AYNI backend fonksiyonunun konuşma-bağımsız hali,
 * bkz. app/api_v1/messaging.py api_search_all_messages()). NewMessageViewModel
 * ile AYNI debounce'lı arama deseni (@OptIn(FlowPreview) de aynı gerekçeyle).
 * Sohbet-İÇİ arama BURADA DEĞİL — o, ConversationViewModel.searchInConversation()
 * ile aynı ekranda (ConversationScreen'in arama modu) yapılır, ayrı bir
 * ViewModel/ekran gerektirmez.
 */
@OptIn(FlowPreview::class)
class MessageSearchViewModel : ViewModel() {

    private val messagingRepository = ServiceLocator.messagingRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<MessageSearchResultDto>>(emptyList())
    val results: StateFlow<List<MessageSearchResultDto>> = _results.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<MessageSearchEvent>()
    val events: SharedFlow<MessageSearchEvent> = _events

    private var nextOffset: Int? = null

    init {
        _query
            .debounce(400)
            .distinctUntilChanged()
            .onEach { q -> if (q.trim().length >= 2) search(q.trim()) else clearResults() }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    private fun clearResults() {
        _results.value = emptyList()
        _hasNext.value = false
        nextOffset = null
    }

    private fun search(q: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = messagingRepository.searchAllMessages(q, 0)) {
                is MessageSearchResult.Success -> {
                    _results.value = result.results
                    _hasNext.value = result.hasNext
                    nextOffset = result.nextOffset
                }
                is MessageSearchResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    fun loadMore() {
        val offset = nextOffset
        val q = _query.value.trim()
        if (_loadingMore.value || !_hasNext.value || offset == null || q.length < 2) return
        viewModelScope.launch {
            _loadingMore.value = true
            when (val result = messagingRepository.searchAllMessages(q, offset)) {
                is MessageSearchResult.Success -> {
                    _results.value = _results.value + result.results
                    _hasNext.value = result.hasNext
                    nextOffset = result.nextOffset
                }
                is MessageSearchResult.Error -> handleError(result.code, silent = true)
            }
            _loadingMore.value = false
        }
    }

    private suspend fun handleError(code: String?, silent: Boolean = false) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(MessageSearchEvent.SessionExpired)
            return
        }
        if (silent) return
        _error.value = "Arama yapılamadı, lütfen tekrar deneyin"
    }
}
