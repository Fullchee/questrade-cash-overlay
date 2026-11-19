package com.fullchee.questradecashoverlay.logins

import androidx.datastore.core.DataStore
import com.fullchee.questradecashoverlay.questrade.Session
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Holds one Questrade refresh token per login. Tokens never leave the store except through [rotate]. */
class LoginStore internal constructor(private val dataStore: DataStore<StoredLogins>) {
    // Each refresh token works once: two rotations of one login would spend the same token twice.
    private val rotation = Mutex()

    val logins: Flow<List<Login>> = dataStore.data.map { stored ->
        stored.logins.map { Login(it.id, it.name, it.status) }
    }

    /** Returns the new login's ID. */
    suspend fun add(name: String, refreshToken: String): String {
        val id = UUID.randomUUID().toString()
        dataStore.updateData {
            it.copy(logins = it.logins + StoredLogin(id, name, refreshToken, LoginStatus.Ok))
        }
        return id
    }

    suspend fun rename(id: String, name: String) = update(id) { it.copy(name = name) }

    suspend fun remove(id: String) {
        dataStore.updateData { stored ->
            stored.copy(logins = stored.logins.filter { it.id != id })
        }
    }

    /**
     * Exchanges the login's refresh token with [refresh] and stores the new one before returning the session.
     * The login reads as [LoginStatus.NeedsReconnecting] from before the exchange until the new token is saved,
     * so a failure or crash at any point leaves it marked broken rather than holding a spent token that looks valid.
     */
    suspend fun rotate(id: String, refresh: suspend (refreshToken: String) -> Session): Session =
        rotation.withLock {
            val spending = dataStore.data.first().logins.single { it.id == id }
            update(id) { it.copy(status = LoginStatus.NeedsReconnecting) }
            val session = refresh(spending.refreshToken)
            // The old token is spent now, so a caller cancelling must not drop its only replacement.
            withContext(NonCancellable) {
                update(id) { it.copy(refreshToken = session.refreshToken, status = LoginStatus.Ok) }
            }
            session
        }

    private suspend fun update(id: String, change: (StoredLogin) -> StoredLogin) {
        dataStore.updateData { stored ->
            stored.copy(logins = stored.logins.map { if (it.id == id) change(it) else it })
        }
    }
}

data class Login(val id: String, val name: String, val status: LoginStatus)

enum class LoginStatus { Ok, NeedsReconnecting }

@Serializable
internal data class StoredLogins(val logins: List<StoredLogin> = emptyList())

/** Not printed anywhere: its generated toString includes the token. */
@Serializable
internal data class StoredLogin(
    val id: String,
    val name: String,
    val refreshToken: String,
    val status: LoginStatus
)
