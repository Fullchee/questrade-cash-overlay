# Questrade cash overlay

Android app that shows how much cash the selected Questrade account holds in the order's currency, on the order screen, so buys don't trigger a surprise currency conversion.

Scope and decisions: [docs/intent/questrade-cash-overlay.md](docs/intent/questrade-cash-overlay.md).

## Develop

1. Install the Android SDK, then run `mise install` for `just` and `betterleaks`. Gradle downloads JDK 21 itself.
2. Run `just hooks` once, so every push runs the tests and the secret scan.
3. Run `just test` for unit tests, ktlint and Android lint (which fails on hard-coded UI text and missing Chinese strings).
4. Run `just emulator` to start the emulator if needed, then install and launch the debug app. `just apk` only builds the APK.
5. Run `just phone` to build, install and launch the debug app on the plugged-in phone (USB debugging on).

## Release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `just release` to build the signed APK and publish it as a GitHub release tagged `v<versionName>`.

Signing reads `QCO_*` properties from `~/.gradle/gradle.properties`. The keystore is `~/.android/questrade-cash-overlay-release.jks`. Losing it means users must uninstall before they can update.
