package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.BlockedUserDto
import com.umuterayaltay.sosyal.nativeapp.repository.BlockedUsersResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBlockResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class BlockedUsersEvent {
    data object SessionExpired : BlockedUsersEvent()
}

/**
 * "Engellenen Kullanıcılar" ekranı için ViewModel — CloseFriendsViewModel ile
 * AYNI temel iskelet (load() + tek aksiyon), ama unblock() SONRASI
 * CloseFriendsViewModel.remove()'un aksine TAM listeyi yeniden fetch ETMEZ:
 * backend zaten güncel `blocked` durumunu (false) döner, elimizdeki liste
 * objesinden ilgili satır local olarak filtrelenip çıkarılır — ekstra bir
 * GET /blocked isteğine gerek yok (aynı ProfileViewModel.toggleBlock()'taki
 * "gereksiz reload yapma" kararı).
 */
class BlockedUsersViewModel : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _users = MutableStateFlow<List<BlockedUserDto>>(emptyList())
    val users: StateFlow<List<BlockedUserDto>> = _users.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<BlockedUsersEvent>()
    val events: SharedFlow<BlockedUsersEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = profileRepository.getBlockedUsers()) {
                is BlockedUsersResult.Success -> _users.value = result.users
                is BlockedUsersResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(BlockedUsersEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    /** Aynı POST /block/{username} toggle endpoint'i kullanılır (ayrı bir
     * "unblock" endpoint'i İCAT EDİLMEDİ, backend zaten tek toggle sunuyor —
     * bkz. görev tanımı). Bu ekrandan SADECE engelli kullanıcılar listelendiği
     * için çağrı her zaman "engeli kaldır" anlamına gelir. */
    fun unblock(username: String) {
        viewModelScope.launch {
            when (val result = profileRepository.toggleBlock(username)) {
                is ToggleBlockResult.Success -> {
                    if (!result.blocked) {
                        _users.value = _users.value.filterNot { it.username == username }
                    }
                }
                is ToggleBlockResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(BlockedUsersEvent.SessionExpired)
                    } else {
                        _error.value = "İşlem başarısız, lütfen tekrar deneyin"
                    }
                }
            }
        }
    }
}
