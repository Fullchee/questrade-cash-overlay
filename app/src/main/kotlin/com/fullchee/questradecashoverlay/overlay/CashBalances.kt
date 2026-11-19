package com.fullchee.questradecashoverlay.overlay

import android.os.SystemClock
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.logins.LoginId
import com.fullchee.questradecashoverlay.logins.LoginStore
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import com.fullchee.questradecashoverlay.questrade.Session
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.flow.first

private const val CACHE_MILLIS = 30_000L

/** Each account's cash per currency, fetched at most every 30 s. Not thread-safe: the overlay calls it from one coroutine. */
class CashBalances(
    private val store: LoginStore,
    private val client: QuestradeClient,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private class Fetched(val cash: Map<Currency, BigDecimal>, val atMillis: Long)

    private val fetched = mutableMapOf<AccountNumber, Fetched>()

    // Access tokens outlive many fetches; rotating the refresh token on every fetch would spend it needlessly.
    private val sessions = mutableMapOf<LoginId, Session>()

    /** Returns null when no login can read [account]. */
    suspend fun of(account: AccountNumber): Map<Currency, BigDecimal>? {
        val recent = fetched[account]
        if (recent != null && nowMillis() - recent.atMillis < CACHE_MILLIS) return recent.cash
        val login = store.accountIndex.first().loginFor(account) ?: return null
        val session = sessions[login]
        val cash = if (session == null) {
            fetchWithNewSession(login, account)
        } else {
            try {
                client.cashBalances(session, account)
            } catch (e: QuestradeException.InvalidToken) {
                // The access token expired; only a fresh session's rejection means the login is broken.
                fetchWithNewSession(login, account)
            }
        }
        fetched[account] = Fetched(cash, nowMillis())
        return cash
    }

    private suspend fun fetchWithNewSession(
        login: LoginId,
        account: AccountNumber
    ): Map<Currency, BigDecimal> {
        val session = store.rotate(login, client::refresh)
        sessions[login] = session
        return client.cashBalances(session, account)
    }
}
