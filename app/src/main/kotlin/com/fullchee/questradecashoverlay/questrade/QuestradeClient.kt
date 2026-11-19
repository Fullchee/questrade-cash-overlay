package com.fullchee.questradecashoverlay.questrade

import com.fullchee.questradecashoverlay.AccountNumber
import java.io.IOException
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Read-only Questrade API client. Each refresh token works once, so callers must keep [Session.refreshToken]. */
class QuestradeClient(
    private val http: OkHttpClient,
    private val loginServer: HttpUrl = "https://login.questrade.com/".toHttpUrl()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refresh(refreshToken: String): Session {
        val url = loginServer.newBuilder()
            .addPathSegments("oauth2/token")
            .addQueryParameter("grant_type", "refresh_token")
            .addQueryParameter("refresh_token", refreshToken)
            .build()
        val body = json.decodeFromString<TokenResponse>(get(Request.Builder().url(url).build()))
        return Session(body.accessToken, body.apiServer.toHttpUrl(), body.refreshToken)
    }

    suspend fun accounts(session: Session): List<Account> {
        val body = json.decodeFromString<AccountsResponse>(get(session, "v1/accounts"))
        return body.accounts.map { Account(AccountNumber(it.number), it.type) }
    }

    suspend fun cashBalances(session: Session, account: AccountNumber): Map<Currency, BigDecimal> {
        val body = json.decodeFromString<BalancesResponse>(
            get(session, "v1/accounts/${account.digits}/balances")
        )
        return body.perCurrencyBalances.associate { Currency.getInstance(it.currency) to it.cash }
    }

    private suspend fun get(session: Session, path: String): String {
        val request = Request.Builder()
            .url(session.apiServer.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer ${session.accessToken}")
            .build()
        return get(request)
    }

    private suspend fun get(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body.string()
                    response.code in INVALID_TOKEN_CODES -> throw QuestradeException.InvalidToken()
                    else -> throw QuestradeException.HttpError(response.code)
                }
            }
        } catch (e: IOException) {
            throw QuestradeException.NetworkFailure(e)
        }
    }

    @Serializable
    private class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("api_server") val apiServer: String,
        @SerialName("refresh_token") val refreshToken: String
    )

    @Serializable
    private class AccountsResponse(val accounts: List<AccountJson>)

    @Serializable
    private class AccountJson(val number: String, val type: String)

    @Serializable
    private class BalancesResponse(val perCurrencyBalances: List<CurrencyBalance>)

    @Serializable
    private class CurrencyBalance(
        val currency: String,
        @Serializable(with = ExactDecimal::class) val cash: BigDecimal
    )

    /** Reads the JSON number's text so money never passes through a Double. */
    private object ExactDecimal : KSerializer<BigDecimal> {
        override val descriptor = PrimitiveSerialDescriptor("ExactDecimal", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): BigDecimal =
            BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)

        override fun serialize(encoder: Encoder, value: BigDecimal) =
            throw UnsupportedOperationException()
    }

    private companion object {
        // The login server answers a used or unknown refresh token with 400; the API answers an expired access token with 401.
        val INVALID_TOKEN_CODES = setOf(400, 401)
    }
}

/** Not a data class: its generated toString would print the tokens. */
class Session(val accessToken: String, val apiServer: HttpUrl, val refreshToken: String)

data class Account(val number: AccountNumber, val type: String)

/** Messages never include a token. */
sealed class QuestradeException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class InvalidToken : QuestradeException("Questrade rejected the token")

    class HttpError(val code: Int) : QuestradeException("Questrade returned HTTP $code")

    class NetworkFailure(cause: IOException) :
        QuestradeException("Could not reach Questrade", cause)
}
