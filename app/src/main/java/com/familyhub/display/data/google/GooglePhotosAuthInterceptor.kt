package com.familyhub.display.data.google

import okhttp3.Interceptor
import okhttp3.Response

class GooglePhotosAuthInterceptor(
    private val authManager: GoogleAuthManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        if (host.contains("googleusercontent.com") || host.contains("googleapis.com")) {
            val token = authManager.getCachedAccessToken()
            if (!token.isNullOrBlank()) {
                val authed = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                return chain.proceed(authed)
            }
        }

        return chain.proceed(request)
    }
}
