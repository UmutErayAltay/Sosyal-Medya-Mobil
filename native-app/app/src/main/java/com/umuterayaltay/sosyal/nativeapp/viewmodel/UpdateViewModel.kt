package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.BuildConfig
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.DownloadResult
import com.umuterayaltay.sosyal.nativeapp.repository.UpdateCheckResult
import com.umuterayaltay.sosyal.nativeapp.repository.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Ayarlar > Uygulama Güncellemeleri diyalogunun state makinesi (2026-08-09).
 * Diğer ekranların CallUiState/ConversationEvent gibi sealed durum
 * desenleriyle AYNI yaklaşım — burada da tek bir StateFlow<UpdateUiState>
 * yeterli, ayrı bir "loading" bool'una GEREK yok (Checking zaten bunu
 * kapsıyor). */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val info: UpdateInfo) : UpdateUiState()
    data class ReadyToInstall(val file: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class UpdateViewModel : ViewModel() {

    private val updateRepository = ServiceLocator.updateRepository

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME

    fun checkForUpdate() {
        if (_uiState.value is UpdateUiState.Checking || _uiState.value is UpdateUiState.Downloading) return
        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            when (val result = updateRepository.checkForUpdate()) {
                is UpdateCheckResult.UpToDate -> _uiState.value = UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> _uiState.value = UpdateUiState.Available(result.info)
                is UpdateCheckResult.Error -> _uiState.value = UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadUpdate(context: Context) {
        val current = _uiState.value as? UpdateUiState.Available ?: return
        _uiState.value = UpdateUiState.Downloading(current.info)
        viewModelScope.launch {
            when (val result = updateRepository.downloadApk(context, current.info)) {
                is DownloadResult.Success -> _uiState.value = UpdateUiState.ReadyToInstall(result.file)
                is DownloadResult.Error -> _uiState.value = UpdateUiState.Error(result.message)
            }
        }
    }

    fun hasInstallPermission(context: Context): Boolean = updateRepository.hasInstallPermission(context)

    fun buildInstallPermissionSettingsIntent(context: Context) =
        updateRepository.buildInstallPermissionSettingsIntent(context)

    fun buildInstallIntent(context: Context, file: File) = updateRepository.buildInstallIntent(context, file)

    /** Diyalog kapatılıp tekrar açılınca (veya izin ekranından dönünce)
     * baştan başlasın diye — özellikle Error/UpToDate durumunda kullanıcı
     * tekrar "Kontrol Et"e basmak istediğinde eski mesaj YAPIŞIK kalmasın. */
    fun resetToIdle() {
        _uiState.value = UpdateUiState.Idle
    }
}
