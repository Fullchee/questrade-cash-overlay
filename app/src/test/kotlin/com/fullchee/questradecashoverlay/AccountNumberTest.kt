package com.fullchee.questradecashoverlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountNumberTest {
    @Test
    fun cashLabelReturnsAccountNumber() {
        assertEquals(AccountNumber("12345678"), parseAccountNumber("Cash - 12345678"))
    }

    @Test
    fun unrelatedLabelReturnsNull() {
        assertNull(parseAccountNumber("Buying power (Combined in CAD)"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        // The account picker's card text ends in a space.
        assertEquals(AccountNumber("11111111"), parseAccountNumber(" Cash - 11111111 "))
    }

    @Test
    fun multiWordLabelReturnsAccountNumber() {
        assertEquals(AccountNumber("33333333"), parseAccountNumber("Spousal RRSP - 33333333"))
    }

    @Test
    fun eachAccountTypeLabelReturnsAccountNumber() {
        listOf("Cash", "Margin", "TFSA", "RRSP", "FHSA", "RESP", "LIRA", "RRIF").forEach { label ->
            assertEquals(label, AccountNumber("22222222"), parseAccountNumber("$label - 22222222"))
        }
    }

    @Test
    fun labelWithoutNumberReturnsNull() {
        assertNull(parseAccountNumber("Cash - "))
        assertNull(parseAccountNumber("Cash"))
    }

    @Test
    fun numberWithLettersReturnsNull() {
        assertNull(parseAccountNumber("Cash - 1234A678"))
    }

    @Test
    fun numberOfWrongLengthReturnsNull() {
        assertNull(parseAccountNumber("Cash - 1234567"))
        assertNull(parseAccountNumber("Cash - 123456789"))
    }

    @Test
    fun numberWithoutLabelReturnsNull() {
        assertNull(parseAccountNumber(" - 12345678"))
    }
}
