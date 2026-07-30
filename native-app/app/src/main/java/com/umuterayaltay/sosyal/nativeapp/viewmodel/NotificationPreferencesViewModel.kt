package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.NotificationPreferencesDto
import com.umuterayaltay.sosyal.nativeapp.repository.NotificationPreferencesResult
import com.umuterayaltay.sosyal.nativeapp.repository.SimpleActionResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NotificationPreferencesEvent {
    data object Saved : NotificationPreferencesEvent()
    data object SessionExpired : NotificationPreferencesEvent()
}

/**
 * "Bildirim Tercihleri" ekranı için ViewModel — app/api_v1.py
 * api_notification_preferences() TAM seti tek istekte kabul ediyor (per-field
 * DEĞİL), bu yüzden updateField() SADECE yerel state'i değiştirir, save()
 * güncel state'in TAMAMINI tek POST'ta gönderir.
 */
class NotificationPreferencesViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _preferences = MutableStateFlow<NotificationPreferencesDto?>(null)
    val preferences: StateFlow<NotificationPreferencesDto?> = _preferences.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<NotificationPreferencesEvent>()
    val events: SharedFlow<NotificationPreferencesEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = settingsRepository.getNotificationPreferences()) {
                is NotificationPreferencesResult.Success -> _preferences.value = result.preferences
                is NotificationPreferencesResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(NotificationPreferencesEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    /** Sadece YEREL state'i günceller — henüz kaydetmez, kaydetmek için [save]
     * çağrılmalı. [fieldSetter] mevcut değeri alıp güncellenmiş bir kopya döner
     * (ör. `{ it.copy(notifyLike = false) }`). */
    fun updateField(fieldSetter: (NotificationPreferencesDto) -> NotificationPreferencesDto) {
        _preferences.value = _preferences.value?.let(fieldSetter)
    }

    fun save() {
        val current = _preferences.value ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            when (val result = settingsRepository.updateNotificationPreferences(current)) {
                is SimpleActionResult.Success -> _events.emit(NotificationPreferencesEvent.Saved)
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(NotificationPreferencesEvent.SessionExpired)
                    } else {
                        _error.value = "Kaydedilemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _saving.value = false
        }
    }
}
