package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.CreateGroupResult
import com.umuterayaltay.sosyal.nativeapp.repository.SearchResult
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

sealed class GroupCreateEvent {
    data class GroupReady(val conversationId: String) : GroupCreateEvent()
    data object SessionExpired : GroupCreateEvent()
}

/**
 * "Yeni Grup" ekranı için ViewModel — NewMessageViewModel'in AYNI arama
 * desenini (DiscoverRepository.search(type="users"), debounce 400ms,
 * distinctUntilChanged, @OptIn(FlowPreview)) kullanır ama ÇOKLU-SEÇİM'e
 * genişletir: kullanıcıya tıklamak (NewMessageScreen'in aksine) hemen konuşma
 * BAŞLATMAZ, [selected] listesine ekler/çıkarır. "Oluştur"a basılınca
 * MessagingRepository.createGroup(name, selectedIds) çağrılır.
 */
@OptIn(FlowPreview::class)
class GroupCreateViewModel : ViewModel() {

    private val discoverRepository = ServiceLocator.discoverRepository
    private val messagingRepository = ServiceLocator.messagingRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val results: StateFlow<List<UserSearchDto>> = _results.asStateFlow()

    private val _selected = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val selected: StateFlow<List<UserSearchDto>> = _selected.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<GroupCreateEvent>()
    val events: SharedFlow<GroupCreateEvent> = _events

    init {
        _query
            .debounce(400)
            .distinctUntilChanged()
            .onEach { q -> if (q.length >= 2) search(q) else _results.value = emptyList() }
            .launchIn(viewModelScope)
    }

    fun onNameChange(text: String) {
        _name.value = text
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
                        _events.emit(GroupCreateEvent.SessionExpired)
                    } else {
                        _error.value = "Arama yapılamadı, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    /** Arama sonucundaki bir satıra tıklamak seçili listeye EKLER/ÇIKARIR
     * (NewMessageScreen'in aksine hemen konuşma başlatmaz). */
    fun toggleSelected(user: UserSearchDto) {
        val current = _selected.value
        _selected.value = if (current.any { it.id == user.id }) {
            current.filterNot { it.id == user.id }
        } else {
            current + user
        }
    }

    fun removeSelected(userId: String) {
        _selected.value = _selected.value.filterNot { it.id == userId }
    }

    fun createGroup() {
        if (_creating.value) return
        val groupName = _name.value.trim()
        val memberIds = _selected.value.map { it.id }
        if (groupName.isEmpty() || memberIds.size < 2) return
        viewModelScope.launch {
            _creating.value = true
            _error.value = null
            when (val result = messagingRepository.createGroup(groupName, memberIds)) {
                is CreateGroupResult.Success ->
                    _events.emit(GroupCreateEvent.GroupReady(result.conversationId))
                is CreateGroupResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupCreateEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "missing_name" -> "Grup adı gerekli"
                            "too_few_members" -> "En az 2 kişi seçmelisin"
                            "blocked" -> "Seçtiğin kişilerden biriyle mesajlaşamazsın"
                            "groups_not_available" -> "Grup özelliği şu anda kullanılamıyor"
                            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
                            else -> "Grup oluşturulamadı, lütfen tekrar deneyin"
                        }
                    }
                }
            }
            _creating.value = false
        }
    }
}
