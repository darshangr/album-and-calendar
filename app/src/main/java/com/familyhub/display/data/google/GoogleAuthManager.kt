package com.familyhub.display.data.google

import android.content.Context
import android.content.Intent
import com.familyhub.display.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object GoogleScopes {
    const val PHOTOS_READONLY = "https://www.googleapis.com/auth/photoslibrary.readonly"

    val ALL = listOf(
        CalendarScopes.CALENDAR_READONLY,
        PHOTOS_READONLY,
    )
}

data class GoogleAccountState(
    val isSignedIn: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
)

class GoogleAuthManager(private val context: Context) {
    private val _accountState = MutableStateFlow(readAccountState())
    val accountState: StateFlow<GoogleAccountState> = _accountState.asStateFlow()

    val signInClient: GoogleSignInClient by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .requestScopes(Scope(GoogleScopes.PHOTOS_READONLY))

        if (BuildConfig.GOOGLE_WEB_CLIENT_ID != "REPLACE_WITH_WEB_CLIENT_ID") {
            builder.requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }

        val options = builder.build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedAccessTokenEpochMillis: Long = 0L

    fun getCachedAccessToken(): String? {
        val age = System.currentTimeMillis() - cachedAccessTokenEpochMillis
        return if (cachedAccessToken != null && age < TOKEN_CACHE_MS) cachedAccessToken else null
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val cached = getCachedAccessToken()
        if (cached != null) return@withContext cached

        val token = runCatching { getCredential()?.token }.getOrNull()
        cachedAccessToken = token
        cachedAccessTokenEpochMillis = System.currentTimeMillis()
        token
    }

    private fun clearTokenCache() {
        cachedAccessToken = null
        cachedAccessTokenEpochMillis = 0L
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            signInClient.signOut()
        }
        clearTokenCache()
        _accountState.value = GoogleAccountState()
    }

    fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        runCatching { task.getResult(com.google.android.gms.common.api.ApiException::class.java) }
        clearTokenCache()
        _accountState.value = readAccountState()
    }

    companion object {
        private const val TOKEN_CACHE_MS = 50 * 60 * 1000L
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun refreshAccountState() {
        _accountState.value = readAccountState()
    }

    suspend fun getCredential(): GoogleAccountCredential? = withContext(Dispatchers.IO) {
        val account = getSignedInAccount()?.account ?: return@withContext null
        GoogleAccountCredential.usingOAuth2(context, GoogleScopes.ALL).apply {
            selectedAccount = account
            backOff = ExponentialBackOff()
        }
    }

    private fun readAccountState(): GoogleAccountState {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null) {
            GoogleAccountState(
                isSignedIn = true,
                email = account.email,
                displayName = account.displayName,
            )
        } else {
            GoogleAccountState()
        }
    }
}
