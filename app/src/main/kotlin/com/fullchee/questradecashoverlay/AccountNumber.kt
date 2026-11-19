package com.fullchee.questradecashoverlay

@JvmInline
value class AccountNumber(val digits: String)

// Questrade account numbers are 8 digits; the type label is open-ended (Cash, TFSA, Spousal RRSP, ...).
private val accountLabel = Regex("""(?:[A-Za-z]+ )*[A-Za-z]+ - (\d{8})""")

fun parseAccountNumber(text: String): AccountNumber? = accountLabel.matchEntire(text.trim())?.let {
    AccountNumber(it.groupValues[1])
}
