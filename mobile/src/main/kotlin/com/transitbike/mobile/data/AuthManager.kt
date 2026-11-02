package com.transitbike.mobile.data

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class AuthState(
    val user: FirebaseUser? = null,
    val isAdmin: Boolean = false,
    val idToken: String? = null,
    val appCheckToken: String? = null
)

class AuthManager {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    suspend fun refreshAuthTokens(): AuthState {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            val empty = AuthState()
            _authState.value = empty
            return empty
        }

        return try {
            val tokenResult = currentUser.getIdToken(false).await()
            val idToken = tokenResult.token
            val isAdmin = tokenResult.claims["admin"] == true

            // Fetch live Play Integrity App Check token, with graceful dev fallback
            val appCheckToken = try {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
            } catch (_: Exception) {
                "mock_play_integrity_token_for_dev"
            }

            val updated = AuthState(
                user = currentUser,
                isAdmin = isAdmin,
                idToken = idToken,
                appCheckToken = appCheckToken
            )
            _authState.value = updated
            updated
        } catch (e: Exception) {
            _authState.value
        }
    }

    fun isUserAdmin(): Boolean = _authState.value.isAdmin
}
