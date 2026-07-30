package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.SearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.StartConversationResult
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

sealed class NewMessageEvent {
    data class ConversationReady(val conversationId: String) : NewMessageEvent()
    data object SessionExpired : NewMessageEvent()
}

/**
 * "Yeni Mesaj" ekranı için ViewModel — YENİ bir "kullanıcı ara" endpoint'i
 * İCAT EDİLMEDİ, mevcut DiscoverRepository.search(type="users") reuse edilir
 * (DiscoverViewModel'deki debounce'lı arama deseniyle AYNI, @OptIn(FlowPreview)
 * de aynı gerekçeyle). Kullanıcı seçilince MessagingRepository.startConversation()
 * çağrılır, sonucu (conversationId) bir event ile dışarı (NavHost'a) bildirilir —
 * navigasyon mantığının kendisi burada YOK, sadece "hazır" sinyali var.
 */
@OptIn(FlowPreview::class)
class NewMessageViewModel : ViewModel() {

    private val discoverRepository = ServiceLocator.discoverRepository
    private val messagingRepository = ServiceLocator.messagingRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val results: StateFlow<List<UserSearchDto>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<NewMessageEvent>()
    val events: SharedFlow<NewMessageEvent> = _events

    init {
        _query
            .debounce(400)
            .distinctUntilChanged()
            .onEach { q -> if (q.length >= 2) search(q) else _results.value = emptyList() }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    private fun search(q: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = discoverRepository.search(q, type = "users", dateFrom = null, dateTo = null)) {
                is SearchResult.Success -> _results.value = result.data.users
                is SearchResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(NewMessageEvent.SessionExpired)
                    } else {
                        _error.value = "Arama yapılamadı, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun selectUser(username: String) {
        if (_starting.value) return
        viewModelScope.launch {
            _starting.value = true
            _error.value = null
            when (val result = messagingRepository.startConversation(username)) {
                is StartConversationResult.Success ->
                    _events.emit(NewMessageEvent.ConversationReady(result.conversationId))
                is StartConversationResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(NewMessageEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "not_found" -> "Kullanıcı bulunamadı"
                            "blocked" -> "Bu kullanıcıyla mesajlaşamazsınız"
                            else -> "Konuşma başlatılamadı, lütfen tekrar deneyin"
                        }
                    }
                }
            }
            _starting.value = false
        }
    }
}
