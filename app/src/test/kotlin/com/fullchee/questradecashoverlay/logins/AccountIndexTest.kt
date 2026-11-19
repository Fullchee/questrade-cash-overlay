package com.fullchee.questradecashoverlay.logins

import com.fullchee.questradecashoverlay.AccountNumber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountIndexTest {
    @Test
    fun `accounts across two logins resolve to the login that holds them`() = runTest {
        val store = LoginStore(memoryDataStore())
        val mom = store.add("Mom", "refresh-mom")
        val dad = store.add("Dad", "refresh-dad")
        store.setAccounts(mom, listOf(AccountNumber("11111111"), AccountNumber("22222222")))
        store.setAccounts(dad, listOf(AccountNumber("33333333")))

        val index = store.accountIndex.first()

        assertEquals(mom, index.loginFor(AccountNumber("11111111")))
        assertEquals(mom, index.loginFor(AccountNumber("22222222")))
        assertEquals(dad, index.loginFor(AccountNumber("33333333")))
    }

    @Test
    fun `an unknown account number has no login`() = runTest {
        val store = LoginStore(memoryDataStore())
        val mom = store.add("Mom", "refresh-mom")
        store.setAccounts(mom, listOf(AccountNumber("11111111")))

        assertNull(store.accountIndex.first().loginFor(AccountNumber("99999999")))
    }

    @Test
    fun `the index is rebuilt when a login's accounts load again`() = runTest {
        val store = LoginStore(memoryDataStore())
        val mom = store.add("Mom", "refresh-mom")
        store.setAccounts(mom, listOf(AccountNumber("11111111")))

        store.setAccounts(mom, listOf(AccountNumber("22222222")))

        val index = store.accountIndex.first()
        assertNull(index.loginFor(AccountNumber("11111111")))
        assertEquals(mom, index.loginFor(AccountNumber("22222222")))
    }

    @Test
    fun `a removed login's accounts leave the index`() = runTest {
        val store = LoginStore(memoryDataStore())
        val mom = store.add("Mom", "refresh-mom")
        store.setAccounts(mom, listOf(AccountNumber("11111111")))

        store.remove(mom)

        assertNull(store.accountIndex.first().loginFor(AccountNumber("11111111")))
    }
}
