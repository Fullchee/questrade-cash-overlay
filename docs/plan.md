# Implementation plan

Scope: [intent/questrade-cash-overlay.md](intent/questrade-cash-overlay.md). Each step ends in one commit (or small PR) with passing tests.

## Stack

- **Kotlin + Jetpack Compose**, Gradle wrapper, JDK 21 toolchain (the machine default is JDK 25, which the Android Gradle Plugin may reject).
- **minSdk 34** (HyperOS 1.0 on the Poco X5 5G is Android 14), targetSdk latest.
- **Libraries**: OkHttp + kotlinx.serialization for the API, DataStore + Tink (Android Keystore) for tokens, WorkManager for the daily refresh.
- **Tests**: JUnit + MockWebServer for logic and HTTP, Robolectric for the accessibility parser and onboarding checks.

## Steps

| # | Step | Done when |
|---|---|---|
| 0 | **Spike: read Questrade's screen.** Connect the phone with `adb`, open the order screen, run `adb shell uiautomator dump`. Record the package name and the node holding `Cash - <number>`. Do the same with the account picker open. | The account number is readable from the view tree. Fake the number, then save the dump as a test fixture |
| 1 | **Scaffold.** Gradle project, Compose app, lint (ktlint), `just test` and `just install` recipes, pre-push hook running tests | `./gradlew test` passes on an empty test; the APK installs on the emulator |
| 2 | **Account matcher (pure Kotlin).** Parse `Cash - 12345678`-style text into an account number; return none for any other text | Unit tests cover each account type label and non-matches |
| 3 | **Questrade client.** Refresh-token exchange, `GET /v1/accounts`, `GET /v1/accounts/{id}/balances` | MockWebServer tests for success, 401, expired token, and network failure |
| 4 | **Token store.** Encrypted store holding one refresh token per login. Save the new refresh token *before* using the access token: each token works once | Tests: rotation persists; a crash between exchange and save can't lose the token |
| 5 | **Login screen.** Paste a token, name the login, list logins with status (OK / needs reconnecting), remove one. Build the account-number → login index | Robolectric tests on the screen state; manual test with a real token |
| 6 | **Daily refresh.** WorkManager periodic job (24 h, needs network) rotates every login's token. A failure posts a notification | Worker tests with a fake client; a forced run on the emulator rotates the token |
| 7 | **Accessibility service + overlay.** Watch Questrade's package for content changes, find the account number (step 2), draw a `TYPE_ACCESSIBILITY_OVERLAY` with CAD and USD cash. Highlight the order's currency. Cache balances for 30 s. Hide the overlay off the order screen | Robolectric test feeds the step 0 fixture and gets the right account; manual check on the phone |
| 8 | **Onboarding.** One screen per step with a live ✓: restricted settings, accessibility, notifications, battery "No restrictions", Xiaomi Autostart (open `com.miui.securitycenter`; no API reports its state, so the user ticks it) | Each check has a test; a fresh install walks through all five steps on the phone |
| 9 | **Chinese UI.** All strings in `values-zh-rCN`, with Chinese as the default | No hard-coded strings (lint check); screenshots reviewed |
| 10 | **Ship to the phone.** Signed release APK, `adb install`, run onboarding, add each login, buy-screen check for every account | Overlay shows the right cash for every account; refresh still works after 2 days untouched |

## Risks, checked early

- **Step 0 fails** (the account number isn't in the view tree): fall back to the floating bubble listing every account. Decide before step 7.
- **HyperOS stops the service or the worker**: step 10's 2-day check catches it. Fix with Autostart and battery settings, not code.
- **Questrade changes its order screen**: the matcher only needs `<label> - <8 digits>`, and the fixture test documents the expected shape.

## Parallel work

After step 1, steps 2, 3 and 8 share no code and can run in parallel. Steps 4–7 depend on 2 and 3.
