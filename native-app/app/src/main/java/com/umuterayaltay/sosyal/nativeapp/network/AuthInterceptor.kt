package com.umuterayaltay.sosyal.nativeapp.network

import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Her isteğe `Authorization: Bearer <token>` ekler — token yoksa header eklenmez. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.getToken()
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
