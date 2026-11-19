package com.fullchee.questradecashoverlay.logins

import androidx.datastore.core.DataStore
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.questrade.QuestradeException
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
        stored.logins.map { login ->
            Login(login.id, login.name, login.status, login.accounts.map(::AccountNumber))
        }
    }

    val accountIndex: Flow<AccountIndex> = logins.map(::AccountIndex)

    /** Returns the new login's ID. */
    suspend fun add(name: String, refreshToken: String): String {
        val id = UUID.randomUUID().toString()
        dataStore.updateData {
            it.copy(logins = it.logins + StoredLogin(id, name, refreshToken, LoginStatus.Ok))
        }
        return id
    }

    suspend fun rename(id: String, name: String) = update(id) { it.copy(name = name) }

    /** Stores a freshly pasted token. The status stays as it is until a [rotate] proves the token works. */
    suspend fun reconnect(id: String, refreshToken: String) = rotation.withLock {
        // Under the lock: a rotation in flight would otherwise overwrite the pasted token with its own.
        update(id) { it.copy(refreshToken = refreshToken) }
    }

    suspend fun setAccounts(id: String, accounts: List<AccountNumber>) =
        update(id) { login -> login.copy(accounts = accounts.map { it.digits }) }

    suspend fun remove(id: String) {
        dataStore.updateData { stored ->
            stored.copy(logins = stored.logins.filter { it.id != id })
        }
    }

    /**
     * Exchanges the login's refresh token with [refresh] and stores the new one before returning the session.
     * The login reads as [LoginStatus.NeedsReconnecting] from before the exchange until the new token is saved,
     * so a failure or crash at any point leaves it marked broken rather than holding a spent token that looks valid.
     * A [QuestradeException.isTemporary] failure is the exception: it restores the login's earlier status for a retry.
     */
    suspend fun rotate(id: String, refresh: suspend (refreshToken: String) -> Session): Session =
        rotation.withLock {
            val spending = dataStore.data.first().logins.single { it.id == id }
            update(id) { it.copy(status = LoginStatus.NeedsReconnecting) }
            val session = try {
                refresh(spending.refreshToken)
            } catch (e: QuestradeException) {
                // Whether the server spent the token is unknown; the caller's retry finds out and marks it then.
                if (e.isTemporary) update(id) { it.copy(status = spending.status) }
                throw e
            }
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

data class Login(
    val id: String,
    val name: String,
    val status: LoginStatus,
    val accounts: List<AccountNumber> = emptyList()
)

enum class LoginStatus { Ok, NeedsReconnecting }

@Serializable
internal data class StoredLogins(val logins: List<StoredLogin> = emptyList())

/** Not printed anywhere: its generated toString includes the token. */
@Serializable
internal data class StoredLogin(
    val id: String,
    val name: String,
    val refreshToken: String,
    val status: LoginStatus,
    // Logins saved before accounts were stored have none until their next check.
    val accounts: List<String> = emptyList()
)
