package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.EditProfileResult
import com.umuterayaltay.sosyal.nativeapp.repository.ProfileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class EditProfileEvent {
    data object Success : EditProfileEvent()
    data object SessionExpired : EditProfileEvent()
}

/**
 * "Profili Düzenle" ekranı için ViewModel. YENİ bir "mevcut ayarları getir"
 * endpoint'i İCAT EDİLMEDİ — backend sadece POST /profile/edit sunuyor, GET
 * tarafı için VAR OLAN ProfileRepository.getProfile(username) reuse edilir
 * (ProfileViewModel'deki AYNI iki-adımlı çözümleme: önce
 * AuthRepository.getCurrentUser() ile kendi username'imiz, SONRA
 * getProfile(username) ile tam profil).
 *
 * submit() CreatePostViewModel.submit()'teki AYNI ContentResolver byte-okuma
 * deseni ve AYNI 401 tepkisi (token temizle + SessionExpired event) ile çalışır.
 */
class EditProfileViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository
    private val profileRepository = ServiceLocator.profileRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _fullName = MutableStateFlow("")
    val fullName: StateFlow<String> = _fullName.asStateFlow()

    private val _bio = MutableStateFlow("")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isPrivate = MutableStateFlow(false)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _hideLastSeen = MutableStateFlow(false)
    val hideLastSeen: StateFlow<Boolean> = _hideLastSeen.asStateFlow()

    /** Sunucudaki MEVCUT avatar — kullanıcı yeni bir görsel seçmediği sürece
     * önizleme bu URL'den gösterilir. */
    private val _currentAvatarUrl = MutableStateFlow<String?>(null)
    val currentAvatarUrl: StateFlow<String?> = _currentAvatarUrl.asStateFlow()

    /** Kullanıcının galeriden YENİ seçtiği görsel — dolu olduğunda önizleme ve
     * submit() bunu kullanır, currentAvatarUrl'i geçici olarak gölgeler. */
    private val _selectedAvatarUri = MutableStateFlow<Uri?>(null)
    val selectedAvatarUri: StateFlow<Uri?> = _selectedAvatarUri.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<EditProfileEvent>()
    val events: SharedFlow<EditProfileEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val me = authRepository.getCurrentUser()
            val meUsername = me?.username
            if (meUsername.isNullOrBlank()) {
                tokenStore.clearToken()
                _events.emit(EditProfileEvent.SessionExpired)
                _loading.value = false
                return@launch
            }

            when (val result = profileRepository.getProfile(meUsername)) {
                is ProfileResult.Success -> {
                    val profile = result.data.profile
                    _fullName.value = profile?.fullName ?: ""
                    _bio.value = profile?.bio ?: ""
                    _username.value = profile?.username ?: meUsername
                    _isPrivate.value = profile?.isPrivate ?: false
                    _hideLastSeen.value = profile?.hideLastSeen ?: false
                    _currentAvatarUrl.value = profile?.avatarUrl
                }
                is ProfileResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(EditProfileEvent.SessionExpired)
                    } else {
                        _error.value = "Profil yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun onFullNameChange(value: String) {
        _fullName.value = value
    }

    fun onBioChange(value: String) {
        _bio.value = value
    }

    fun onUsernameChange(value: String) {
        _username.value = value
    }

    fun onIsPrivateChange(value: Boolean) {
        _isPrivate.value = value
    }

    fun onHideLastSeenChange(value: Boolean) {
        _hideLastSeen.value = value
    }

    fun onAvatarSelected(uri: Uri?) {
        _selectedAvatarUri.value = uri
    }

    /** [context] SADECE seçilen görselin byte'larını/mime tipini okumak için
     * gerekiyor (ContentResolver) — ViewModel Context'i SAKLAMAZ. */
    fun submit(context: Context) {
        if (_submitting.value) return
        val trimmedUsername = _username.value.trim()
        if (trimmedUsername.length < 3) {
            _error.value = "Kullanıcı adı en az 3 karakter olmalı"
            return
        }

        viewModelScope.launch {
            _submitting.value = true
            _error.value = null

            var avatarBytes: ByteArray? = null
            var avatarMimeType: String? = null
            val avatarUri = _selectedAvatarUri.value
            if (avatarUri != null) {
                try {
                    withContext(Dispatchers.IO) {
                        avatarBytes = context.contentResolver.openInputStream(avatarUri)?.use { it.readBytes() }
                    }
                    avatarMimeType = context.contentResolver.getType(avatarUri)
                } catch (e: Exception) {
                    _error.value = "Görsel okunamadı, lütfen tekrar deneyin"
                    _submitting.value = false
                    return@launch
                }
            }

            when (
                val result = settingsRepository.editProfile(
                    fullName = _fullName.value.trim(),
                    bio = _bio.value.trim(),
                    username = trimmedUsername,
                    isPrivate = _isPrivate.value,
                    hideLastSeen = _hideLastSeen.value,
                    avatarBytes = avatarBytes,
                    avatarMimeType = avatarMimeType,
                )
            ) {
                is EditProfileResult.Success -> _events.emit(EditProfileEvent.Success)
                is EditProfileResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(EditProfileEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }

            _submitting.value = false
        }
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "short_username" -> "Kullanıcı adı en az 3 karakter olmalı"
        "username_taken" -> "Bu kullanıcı adı zaten kullanılıyor"
        "upload_failed" -> "Görsel yüklenemedi, lütfen tekrar deneyin"
        else -> "Kaydedilemedi, lütfen tekrar deneyin"
    }
}
