plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.fullchee.questradecashoverlay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fullchee.questradecashoverlay"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // The keystore and passwords live in ~/.gradle/gradle.properties, outside the repo.
            storeFile = providers.gradleProperty("QCO_KEYSTORE_PATH").orNull?.let(::file)
            storePassword = providers.gradleProperty("QCO_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.gradleProperty("QCO_KEY_ALIAS").orNull
            keyPassword = providers.gradleProperty("QCO_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Every English string needs its Simplified Chinese entry, the language the app runs in.
        error += "MissingTranslation"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's SDK 36 runtime reads file descriptors through this JDK-internal package.
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore)
    implementation(libs.tink.android)
    implementation(libs.work.runtime)

    lintChecks(project(":lint-checks"))

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.work.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
