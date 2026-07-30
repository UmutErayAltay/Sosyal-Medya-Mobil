package com.umuterayaltay.sosyal.nativeapp.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.umuterayaltay.sosyal.nativeapp.R

/**
 * Android Credential Manager ("Sign in with Google" native akışı — tarayıcı/
 * WebView YOK) ile bir Google ID Token (OIDC JWT) alır. Bu token
 * AuthRepository.loginWithGoogle() ile POST /api/v1/auth/google'a gönderilir,
 * backend sign_in_with_id_token ile Supabase'e değiştirir.
 *
 * webClientId res/values/strings.xml'deki google_web_client_id — Supabase
 * Google provider'ıyla AYNI Client ID (native için ayrı bir Google Cloud client
 * YOK, bkz. görev spesifikasyonu).
 *
 * Nonce BİLİNÇLİ olarak kullanılmıyor bu iterasyonda (Supabase nonce'suz da
 * doğrulamayı kabul ediyor — replay-koruması kapsam dışı, ayrı bir iyileştirme).
 *
 * [context] bir Activity context OLMALI (Credential Manager hesap seçme
 * UI'ı için) — Compose içinde LocalContext.current bunu sağlar.
 *
 * Fırlatabileceği [GetCredentialException] (ve alt sınıfı
 * [androidx.credentials.exceptions.NoCredentialException] — kullanıcı hesap
 * seçmeden iptal ederse) çağıran taraf (LoginScreen) yakalamalı: NoCredentialException
 * sessizce ele alınmalı (hata SPAM'lemesin), diğerleri kullanıcıya "Google ile
 * giriş başarısız" olarak gösterilmeli.
 */
object GoogleSignInHelper {

    suspend fun requestIdToken(context: Context): String {
        val credentialManager = CredentialManager.create(context)
        val webClientId = context.getString(R.string.google_web_client_id)
        val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return credential.idToken
    }
}
