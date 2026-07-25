package com.familyhub.display.data.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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
    const val DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"

    val ALL = listOf(
        CalendarScopes.CALENDAR_READONLY,
        DRIVE_READONLY,
    )
}

data class GoogleAccountState(
    val isSignedIn: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
)

/**
 * Thrown when Google requires the user to approve an additional scope
 * (e.g. Drive) that wasn't granted at sign-in. The [consentIntent] must be
 * launched from an Activity to show Google's approval screen.
 */
class GoogleConsentRequiredException(val consentIntent: Intent) :
    Exception("Additional Google permission required")

class GoogleAuthManager(private val context: Context) {
    private val _accountState = MutableStateFlow(readAccountState())
    val accountState: StateFlow<GoogleAccountState> = _accountState.asStateFlow()

    val signInClient: GoogleSignInClient by lazy {
        // On-device Calendar/Photos access relies on the Android OAuth client
        // (package name + SHA-1) registered in Google Cloud, not a web client ID.
        // We only need email + the API scopes here.
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .requestScopes(Scope(GoogleScopes.DRIVE_READONLY))
            .build()
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

        val credential = getCredential() ?: return@withContext null
        try {
            val token = credential.token
            cachedAccessToken = token
            cachedAccessTokenEpochMillis = System.currentTimeMillis()
            token
        } catch (e: UserRecoverableAuthException) {
            // Google needs the user to approve a scope (e.g. Drive) interactively.
            e.intent?.let { throw GoogleConsentRequiredException(it) }
            throw e
        }
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

    fun handleSignInResult(data: Intent?): Result<GoogleAccountState> {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return runCatching {
            task.getResult(ApiException::class.java)
            clearTokenCache()
            readAccountState().also { _accountState.value = it }
        }.onFailure {
            clearTokenCache()
            _accountState.value = readAccountState()
        }
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
