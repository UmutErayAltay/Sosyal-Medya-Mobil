package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.BuildConfig
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.PrepareResult
import com.umuterayaltay.sosyal.nativeapp.repository.UpdateCheckResult
import com.umuterayaltay.sosyal.nativeapp.repository.UpdateInfo
import com.umuterayaltay.sosyal.nativeapp.repository.UpdatePhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Ayarlar > Uygulama Güncellemeleri diyalogunun state makinesi (2026-08-09,
 * 2026-08-13 delta fazlarıyla genişletildi). Diğer ekranların
 * CallUiState/ConversationEvent gibi sealed durum desenleriyle AYNI
 * yaklaşım. Her indirme/uygulama fazı AYRI bir state — dialog gerçek bir
 * yüzde + byte sayacı gösterebilsin diye (önceden SADECE belirsiz spinner
 * vardı). */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class DownloadingPatch(val info: UpdateInfo, val done: Long, val total: Long) : UpdateUiState()
    data class DownloadingFull(val info: UpdateInfo, val done: Long, val total: Long, val fellBackReason: String?) : UpdateUiState()
    data class ApplyingPatch(val info: UpdateInfo, val done: Long, val total: Long) : UpdateUiState()
    data object Verifying : UpdateUiState()
    data class ReadyToInstall(val file: File, val viaDelta: Boolean, val savedBytes: Long) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class UpdateViewModel : ViewModel() {

    private val updateRepository = ServiceLocator.updateRepository

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME

    /** Aktif indirme/kontrol Job'u — önceden hiç TUTULMUYORDU, dialog
     * kapansa bile indirme arka planda sessizce sürüp `ReadyToInstall`'a
     * geçiyordu. [resetToIdle] artık bunu da iptal ediyor. */
    private var job: Job? = null

    fun checkForUpdate(context: Context) {
        if (_uiState.value is UpdateUiState.Checking || isBusy()) return
        _uiState.value = UpdateUiState.Checking
        job = viewModelScope.launch {
            when (val result = updateRepository.checkForUpdate(context.applicationContext)) {
                is UpdateCheckResult.UpToDate -> _uiState.value = UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> _uiState.value = UpdateUiState.Available(result.info)
                is UpdateCheckResult.Error -> _uiState.value = UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadUpdate(context: Context) {
        val info = (_uiState.value as? UpdateUiState.Available)?.info ?: return
        job = viewModelScope.launch {
            val result = updateRepository.prepareUpdate(context.applicationContext, info) { phase ->
                _uiState.value = when (phase) {
                    is UpdatePhase.DownloadingPatch -> UpdateUiState.DownloadingPatch(info, phase.done, phase.total)
                    is UpdatePhase.DownloadingFull -> UpdateUiState.DownloadingFull(info, phase.done, phase.total, phase.fellBackReason)
                    is UpdatePhase.ApplyingPatch -> UpdateUiState.ApplyingPatch(info, phase.done, phase.total)
                    is UpdatePhase.Verifying -> UpdateUiState.Verifying
                }
            }
            _uiState.value = when (result) {
                is PrepareResult.Ready -> UpdateUiState.ReadyToInstall(result.file, result.viaDelta, result.savedBytes)
                is PrepareResult.Error -> UpdateUiState.Error(result.message)
            }
        }
    }

    private fun isBusy(): Boolean = when (_uiState.value) {
        is UpdateUiState.DownloadingPatch, is UpdateUiState.DownloadingFull,
        is UpdateUiState.ApplyingPatch, UpdateUiState.Verifying -> true
        else -> false
    }

    fun hasInstallPermission(context: Context): Boolean = updateRepository.hasInstallPermission(context)

    fun buildInstallPermissionSettingsIntent(context: Context) =
        updateRepository.buildInstallPermissionSettingsIntent(context)

    fun buildInstallIntent(context: Context, file: File) = updateRepository.buildInstallIntent(context, file)

    /** Diyalog kapatılıp tekrar açılınca (veya izin ekranından dönünce)
     * baştan başlasın diye — AKTİF Job'u da iptal eder (bkz. [job] KDoc'u),
     * böylece dialog kapanınca indirme GERÇEKTEN durur. */
    fun resetToIdle() {
        job?.cancel()
        job = null
        _uiState.value = UpdateUiState.Idle
    }
}
