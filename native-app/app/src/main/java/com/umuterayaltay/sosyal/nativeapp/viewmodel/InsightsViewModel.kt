package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.InsightsResponse
import com.umuterayaltay.sosyal.nativeapp.repository.InsightsResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InsightsEvent {
    data object SessionExpired : InsightsEvent()
}

/** Kendi profil istatistikleri - days (7/14/30) degisince yeniden fetch eder. */
class InsightsViewModel : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _days = MutableStateFlow(14)
    val days: StateFlow<Int> = _days.asStateFlow()

    private val _data = MutableStateFlow<InsightsResponse?>(null)
    val data: StateFlow<InsightsResponse?> = _data.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<InsightsEvent>()
    val events: SharedFlow<InsightsEvent> = _events

    init {
        load()
    }

    fun onDaysChange(newDays: Int) {
        if (newDays == _days.value) return
        _days.value = newDays
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = profileRepository.getInsights(_days.value)) {
                is InsightsResult.Success -> _data.value = result.data
                is InsightsResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(InsightsEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }
}
