package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.ConversationSummaryDto
import com.umuterayaltay.sosyal.nativeapp.repository.ConversationsResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InboxEvent {
    data object SessionExpired : InboxEvent()
}

/**
 * Gelen kutusu (inbox) ekranı için ViewModel — FollowListViewModel ile AYNI
 * basit desen (parametresiz, tek liste + loading/error).
 */
class InboxViewModel : ViewModel() {

    private val messagingRepository = ServiceLocator.messagingRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _conversations = MutableStateFlow<List<ConversationSummaryDto>>(emptyList())
    val conversations: StateFlow<List<ConversationSummaryDto>> = _conversations.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<InboxEvent>()
    val events: SharedFlow<InboxEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = messagingRepository.getConversations()) {
                is ConversationsResult.Success -> _conversations.value = result.conversations
                is ConversationsResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(InboxEvent.SessionExpired)
                    } else {
                        _error.value = "Mesajlar yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }
}
