package com.fullchee.questradecashoverlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fullchee.questradecashoverlay.onboarding.OnboardingScreen
import com.fullchee.questradecashoverlay.onboarding.SetupChecks
import com.fullchee.questradecashoverlay.onboarding.settingsIntent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val checks = SetupChecks(this)
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
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.app_name))
                        }
                    }
                }
            }
        }
    }
}
