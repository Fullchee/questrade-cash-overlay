package com.fullchee.questradecashoverlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fullchee.questradecashoverlay.logins.LoginsScreen
import com.fullchee.questradecashoverlay.logins.loginStore
import com.fullchee.questradecashoverlay.onboarding.OnboardingScreen
import com.fullchee.questradecashoverlay.onboarding.SetupChecks
import com.fullchee.questradecashoverlay.onboarding.settingsIntent
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val checks = SetupChecks(this)
        val questrade = QuestradeClient(OkHttpClient())
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var onboarding by remember { mutableStateOf(!checks.isOnboardingFinished()) }
                    if (onboarding) {
                        OnboardingScreen(
                            checks = checks,
                            openSettings = { step -> startActivity(settingsIntent(step, this)) },
                            onFinished = {
                                checks.markOnboardingFinished()
                                onboarding = false
                            }
                        )
                    } else {
                        LoginsScreen(loginStore(this), questrade, openSetup = { onboarding = true })
                    }
                }
            }
        }
    }
}
