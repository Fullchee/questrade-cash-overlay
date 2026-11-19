package com.fullchee.questradecashoverlay.refresh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fullchee.questradecashoverlay.MainActivity
import com.fullchee.questradecashoverlay.R
import com.fullchee.questradecashoverlay.logins.Login
import com.fullchee.questradecashoverlay.logins.LoginStore
import com.fullchee.questradecashoverlay.logins.loginStore
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import com.fullchee.questradecashoverlay.questrade.Session
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

internal const val TOKEN_REFRESH_WORK = "token_refresh"
private const val LOGIN_ALERTS_CHANNEL = "login_alerts"

/** Safe to call on every app start: an already scheduled job keeps its timing. */
fun scheduleTokenRefresh(context: Context) {
    val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(24, TimeUnit.HOURS)
        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(TOKEN_REFRESH_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
}

/** Rotates every login's refresh token, so a login nobody uses still holds a live one. */
class TokenRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val store: LoginStore,
    private val refresh: suspend (refreshToken: String) -> Session
) : CoroutineWorker(context, params) {
    // WorkManager's default factory builds workers through this constructor.
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        loginStore(context),
        QuestradeClient(OkHttpClient())::refresh
    )

    override suspend fun doWork(): Result {
        var offline = false
        for (login in store.logins.first()) {
            try {
                store.rotate(login.id, refresh)
            } catch (e: QuestradeException.NetworkFailure) {
                offline = true
            } catch (e: QuestradeException) {
                notifyNeedsReconnecting(login)
            }
        }
        // WorkManager's backoff reruns every login; rotating a fresh token again is harmless.
        return if (offline) Result.retry() else Result.success()
    }

    private fun notifyNeedsReconnecting(login: Login) {
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                LOGIN_ALERTS_CHANNEL,
                applicationContext.getString(R.string.login_alerts_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(applicationContext, LOGIN_ALERTS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(
                applicationContext.getString(R.string.login_needs_reconnecting_title, login.name)
            )
            .setContentText(applicationContext.getString(R.string.login_needs_reconnecting_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        // One notification per login: a second failure of the same login replaces its first.
        notifications.notify(login.id.hashCode(), notification)
    }
}
