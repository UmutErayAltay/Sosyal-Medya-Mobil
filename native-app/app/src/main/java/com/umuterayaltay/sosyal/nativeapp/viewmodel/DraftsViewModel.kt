package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.DeletePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.DraftsResult
import com.umuterayaltay.sosyal.nativeapp.repository.EditPostResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.PublishDraftResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DraftsEvent {
    data object SessionExpired : DraftsEvent()
    data class ShowToast(val message: String) : DraftsEvent()
}

/**
 * "Taslaklarım" ekranı — app/routes/posts.py drafts_list()/publish_draft()'ün
 * JSON mirror'ı (api_v1/interactions.py api_list_drafts()/api_publish_draft()).
 * Düzenleme/silme MEVCUT editPost()/deletePost() endpoint'lerini reuse eder —
 * ikisi de SADECE user_id sahiplik kontrolü yapıyor, is_draft filtresi hiç
 * YOK (bkz. api_v1/posts.py), bu yüzden ayrı bir "taslak düzenle/sil"
 * endpoint'i İCAT EDİLMEDİ.
 */
class DraftsViewModel : ViewModel() {

    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _drafts = MutableStateFlow<List<Post>>(emptyList())
    val drafts: StateFlow<List<Post>> = _drafts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Yayınla/Sil sırasında SADECE o satırın butonlarını devre dışı bırakmak
    // için — tek bir ekran-geneli "submitting" bayrağı yerine hangi taslağın
    // işlendiğini tutar.
    private val _processingIds = MutableStateFlow<Set<String>>(emptySet())
    val processingIds: StateFlow<Set<String>> = _processingIds.asStateFlow()

    private val _events = MutableSharedFlow<DraftsEvent>()
    val events: SharedFlow<DraftsEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = interactionsRepository.getDrafts()) {
                is DraftsResult.Success -> _drafts.value = result.drafts
                is DraftsResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DraftsEvent.SessionExpired)
                    } else {
                        _error.value = "Taslaklar yüklenemedi"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun publish(postId: String) {
        if (postId in _processingIds.value) return
        viewModelScope.launch {
            _processingIds.value = _processingIds.value + postId
            when (val result = interactionsRepository.publishDraft(postId)) {
                is PublishDraftResult.Success -> {
                    _drafts.value = _drafts.value.filterNot { it.id == postId }
                    _events.emit(DraftsEvent.ShowToast("Yayınlandı"))
                }
                is PublishDraftResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DraftsEvent.SessionExpired)
                    } else {
                        _events.emit(DraftsEvent.ShowToast("Yayınlanamadı, tekrar dene"))
                    }
                }
            }
            _processingIds.value = _processingIds.value - postId
        }
    }

    fun editContent(postId: String, newContent: String) {
        if (postId in _processingIds.value) return
        viewModelScope.launch {
            _processingIds.value = _processingIds.value + postId
            when (val result = interactionsRepository.editPost(postId, newContent)) {
                is EditPostResult.Success -> {
                    _drafts.value = _drafts.value.map {
                        if (it.id == postId) it.copy(content = result.content) else it
                    }
                }
                is EditPostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DraftsEvent.SessionExpired)
                    } else {
                        _events.emit(DraftsEvent.ShowToast("Düzenlenemedi, tekrar dene"))
                    }
                }
            }
            _processingIds.value = _processingIds.value - postId
        }
    }

    fun delete(postId: String) {
        if (postId in _processingIds.value) return
        viewModelScope.launch {
            _processingIds.value = _processingIds.value + postId
            when (val result = interactionsRepository.deletePost(postId)) {
                is DeletePostResult.Success -> _drafts.value = _drafts.value.filterNot { it.id == postId }
                is DeletePostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DraftsEvent.SessionExpired)
                    } else {
                        _events.emit(DraftsEvent.ShowToast("Silinemedi, tekrar dene"))
                    }
                }
            }
            _processingIds.value = _processingIds.value - postId
        }
    }
}
