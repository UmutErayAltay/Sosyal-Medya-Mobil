package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.GroupMemberDto
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.AddGroupMembersResult
import com.umuterayaltay.sosyal.nativeapp.repository.ConversationDetailResult
import com.umuterayaltay.sosyal.nativeapp.repository.GroupMembersResult
import com.umuterayaltay.sosyal.nativeapp.repository.LeaveGroupResult
import com.umuterayaltay.sosyal.nativeapp.repository.RemoveGroupMemberResult
import com.umuterayaltay.sosyal.nativeapp.repository.RenameGroupResult
import com.umuterayaltay.sosyal.nativeapp.repository.SearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleAdminResult
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

sealed class GroupManageEvent {
    data object LeftGroup : GroupManageEvent()
    data object SessionExpired : GroupManageEvent()
}

/**
 * "Grubu Yönet" ekranı için ViewModel — ConversationViewModel'deki gibi
 * constructor parametreli (conversationId), bu yüzden [GroupManageViewModelFactory]
 * gerekir.
 *
 * Grup adı GroupMembersResponse'ta YOK (sadece üye listesi döner) — bu yüzden
 * initial adı MessagingRepository.getConversationDetail(id, page=1) ile
 * ConversationInfoDto'dan çekiyoruz (ConversationScreen zaten AYNI çağrıyı
 * kendi ekranında yapıyor ama iki ekran arasında state PAYLAŞILMIYOR - bkz.
 * spesifikasyon: onManageGroupClick parametresiz, bu yüzden isim NAV ARGÜMANI
 * olarak da taşınmıyor). Sayfa 1 mesajları burada RENDER edilmez, sadece
 * conversation.name için kullanılır.
 *
 * Kendi admin durumum: üye listesinde AuthRepository.getCurrentUser() ile
 * çözülen kendi id'me denk gelen satırdan okunur (backend request.api_user["id"]
 * ile eşleşen satır - ayrı bir "am I admin" endpoint'i İCAT EDİLMEDİ).
 *
 * add-members alt-akışı GroupCreateViewModel'in AYNI arama+çoklu-seçim
 * desenini (debounce 400ms) tekrarlar - iki ayrı ekranın ViewModel'i olduğu
 * için KÜÇÜK bir kod tekrarı var, bilinçli (ekranlar arası state PAYLAŞMAK
 * için ekstra bir singleton/shared ViewModel İCAT ETMEK bu ölçekte gereksiz
 * karmaşıklık olurdu).
 */
@OptIn(FlowPreview::class)
class GroupManageViewModel(private val conversationId: String) : ViewModel() {

    private val messagingRepository = ServiceLocator.messagingRepository
    private val discoverRepository = ServiceLocator.discoverRepository
    private val authRepository = ServiceLocator.authRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    private val _members = MutableStateFlow<List<GroupMemberDto>>(emptyList())
    val members: StateFlow<List<GroupMemberDto>> = _members.asStateFlow()

    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _isSelfAdmin = MutableStateFlow(false)
    val isSelfAdmin: StateFlow<Boolean> = _isSelfAdmin.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _renaming = MutableStateFlow(false)
    val renaming: StateFlow<Boolean> = _renaming.asStateFlow()

    private val _leaving = MutableStateFlow(false)
    val leaving: StateFlow<Boolean> = _leaving.asStateFlow()

    private val _memberActionInProgress = MutableStateFlow<String?>(null)
    val memberActionInProgress: StateFlow<String?> = _memberActionInProgress.asStateFlow()

    // ---- Üye ekleme alt-akışı (GroupCreateViewModel ile AYNI desen) ----

    private val _showAddMembers = MutableStateFlow(false)
    val showAddMembers: StateFlow<Boolean> = _showAddMembers.asStateFlow()

    private val _addQuery = MutableStateFlow("")
    val addQuery: StateFlow<String> = _addQuery.asStateFlow()

    private val _addResults = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val addResults: StateFlow<List<UserSearchDto>> = _addResults.asStateFlow()

    private val _addSelected = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val addSelected: StateFlow<List<UserSearchDto>> = _addSelected.asStateFlow()

    private val _addSearching = MutableStateFlow(false)
    val addSearching: StateFlow<Boolean> = _addSearching.asStateFlow()

    private val _addingMembers = MutableStateFlow(false)
    val addingMembers: StateFlow<Boolean> = _addingMembers.asStateFlow()

    private val _events = MutableSharedFlow<GroupManageEvent>()
    val events: SharedFlow<GroupManageEvent> = _events

    init {
        load()
        _addQuery
            .debounce(400)
            .distinctUntilChanged()
            .onEach { q -> if (q.length >= 2) searchToAdd(q) else _addResults.value = emptyList() }
            .launchIn(viewModelScope)
    }

    /** İLK yükleme — grup adı (conversation detail) + üye listesi + kendi kullanıcı
     * id'im, hepsi birlikte. Üye mutasyonlarından (ekle/çıkar/admin-toggle) SONRA
     * bunun yerine [reloadMembers] kullanılır — grup adı değişmediği için sayfa 1
     * mesajlarını tekrar tekrar çekmeye gerek yok. */
    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            // Grup adı için conversation detail (sayfa 1 mesajları KULLANILMAZ,
            // sadece conversation.name okunur - bkz. sınıf docstring'i).
            when (val detail = messagingRepository.getConversationDetail(conversationId, 1)) {
                is ConversationDetailResult.Success -> _groupName.value = detail.conversation?.name.orEmpty()
                is ConversationDetailResult.Error -> Unit // isim olmadan da devam edilebilir
            }

            reloadMembers()
            _loading.value = false
        }
    }

    /** Üye listesini (ve kendi admin durumumu) yeniden çeker — grup adına
     * DOKUNMAZ, bu yüzden ekle/çıkar/admin-toggle sonrası [load] yerine bu
     * kullanılır (gereksiz conversation-detail çağrısından kaçınmak için). */
    private suspend fun reloadMembers() {
        val me = authRepository.getCurrentUser()
        _myUserId.value = me?.id
        when (val result = messagingRepository.getGroupMembers(conversationId)) {
            is GroupMembersResult.Success -> {
                _members.value = result.members
                _isSelfAdmin.value = result.members.any { it.id == me?.id && it.isAdmin }
            }
            is GroupMembersResult.Error -> {
                if (result.code == "unauthorized") {
                    tokenStore.clearToken()
                    _events.emit(GroupManageEvent.SessionExpired)
                } else {
                    _error.value = "Üye listesi yüklenemedi, lütfen tekrar deneyin"
                }
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun renameGroup(newName: String) {
        val trimmed = newName.trim()
        if (_renaming.value || trimmed.isEmpty() || trimmed == _groupName.value) return
        viewModelScope.launch {
            _renaming.value = true
            _actionError.value = null
            when (val result = messagingRepository.renameGroup(conversationId, trimmed)) {
                is RenameGroupResult.Success -> _groupName.value = result.name
                is RenameGroupResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupManageEvent.SessionExpired)
                    } else {
                        _actionError.value = when (result.code) {
                            "missing_name" -> "Grup adı gerekli"
                            "not_admin" -> "Sadece adminler grubun adını değiştirebilir"
                            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
                            else -> "Ad değiştirilemedi, lütfen tekrar deneyin"
                        }
                    }
                }
            }
            _renaming.value = false
        }
    }

    fun toggleAdmin(userId: String) {
        if (_memberActionInProgress.value != null) return
        viewModelScope.launch {
            _memberActionInProgress.value = userId
            _actionError.value = null
            when (val result = messagingRepository.toggleGroupAdmin(conversationId, userId)) {
                is ToggleAdminResult.Success -> reloadMembers()
                is ToggleAdminResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupManageEvent.SessionExpired)
                    } else {
                        _actionError.value = mapMemberActionError(result.code)
                    }
                }
            }
            _memberActionInProgress.value = null
        }
    }

    fun removeMember(userId: String) {
        if (_memberActionInProgress.value != null) return
        viewModelScope.launch {
            _memberActionInProgress.value = userId
            _actionError.value = null
            when (val result = messagingRepository.removeGroupMember(conversationId, userId)) {
                is RemoveGroupMemberResult.Success -> reloadMembers()
                is RemoveGroupMemberResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupManageEvent.SessionExpired)
                    } else {
                        _actionError.value = mapMemberActionError(result.code)
                    }
                }
            }
            _memberActionInProgress.value = null
        }
    }

    private fun mapMemberActionError(code: String?): String = when (code) {
        "not_admin" -> "Sadece adminler bu işlemi yapabilir"
        "cannot_remove_self" -> "Kendini çıkaramazsın, bunun yerine gruptan ayrıl"
        "not_a_member" -> "Bu kullanıcı artık grupta değil"
        "last_admin" -> "Tek admin kendini düşüremez"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "İşlem başarısız oldu, lütfen tekrar deneyin"
    }

    // ---- Üye ekleme alt-akışı ----

    fun openAddMembers() {
        _showAddMembers.value = true
    }

    fun closeAddMembers() {
        _showAddMembers.value = false
        _addQuery.value = ""
        _addResults.value = emptyList()
        _addSelected.value = emptyList()
    }

    fun onAddQueryChange(text: String) {
        _addQuery.value = text
    }

    private fun searchToAdd(q: String) {
        viewModelScope.launch {
            _addSearching.value = true
            when (val result = discoverRepository.search(q, type = "users", dateFrom = null, dateTo = null)) {
                is SearchResult.Success -> {
                    val existingIds = _members.value.mapTo(HashSet()) { it.id }
                    _addResults.value = result.data.users.filter { it.id !in existingIds }
                }
                is SearchResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupManageEvent.SessionExpired)
                    }
                }
            }
            _addSearching.value = false
        }
    }

    fun toggleAddSelected(user: UserSearchDto) {
        val current = _addSelected.value
        _addSelected.value = if (current.any { it.id == user.id }) {
            current.filterNot { it.id == user.id }
        } else {
            current + user
        }
    }

    fun confirmAddMembers() {
        if (_addingMembers.value) return
        val userIds = _addSelected.value.map { it.id }
        if (userIds.isEmpty()) return
        viewModelScope.launch {
            _addingMembers.value = true
            _actionError.value = null
            when (val result = messagingRepository.addGroupMembers(conversationId, userIds)) {
                is AddGroupMembersResult.Success -> {
                    closeAddMembers()
                    reloadMembers()
                }
                is AddGroupMembersResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupManageEvent.SessionExpired)
                    } else {
                        _actionError.value = when (result.code) {
                            "missing_user_ids" -> "En az bir kullanıcı seçmelisin"
                            "blocked" -> "Seçtiğin kişilerden biriyle mesajlaşamazsın"
                            "not_admin" -> "Sadece adminler üye ekleyebilir"
                            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
                            else -> "Üye eklenemedi, lütfen tekrar deneyin"
                        }
                    }
                }
            }
            _addingMembers.value = false
        }
    }

    // ---- Gruptan ayrılma ----

    fun leaveGroup() {
        if (_leaving.value) return
        viewModelScope.launch {
            _leaving.value = true
            _actionError.value = null
            when (val result = messagingRepository.leaveGroup(conversationId)) {
                is LeaveGroupResult.Success -> _events.emit(GroupManageEvent.LeftGroup)
                is LeaveGroupResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(GroupManageEvent.SessionExpired)
                    } else {
                        _actionError.value = "Gruptan ayrılamadın, lütfen tekrar deneyin"
                    }
                }
            }
            _leaving.value = false
        }
    }
}

/** GroupManageViewModel constructor'ı conversationId parametresi aldığı için
 * ConversationViewModelFactory ile AYNI desen. */
class GroupManageViewModelFactory(private val conversationId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GroupManageViewModel(conversationId) as T
    }
}
