package com.fullchee.questradecashoverlay.logins

import com.fullchee.questradecashoverlay.AccountNumber

typealias LoginId = String

/** Finds which login can read an account, so the overlay knows whose token fetches its cash. */
class AccountIndex(logins: List<Login>) {
    private val loginByAccount: Map<AccountNumber, LoginId> =
        logins.flatMap { login -> login.accounts.map { it to login.id } }.toMap()

    fun loginFor(account: AccountNumber): LoginId? = loginByAccount[account]
}
