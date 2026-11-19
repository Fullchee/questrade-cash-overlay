app_id := "com.fullchee.questradecashoverlay"
apk := "app/build/outputs/apk/debug/app-debug.apk"
avd := env("AVD", "rex-pixel")
emulator_bin := env("ANDROID_HOME", home_directory() / "Library/Android/sdk") / "emulator/emulator"

# Unit tests, ktlint and Android lint
test:
    ./gradlew test ktlintCheck lint

# Build the debug APK
apk:
    ./gradlew assembleDebug

# Start the emulator if none is running, then install and launch the debug app (AVD=<name> picks another emulator)
emulator: apk
    #!/usr/bin/env bash
    set -euo pipefail
    if ! adb -e get-state >/dev/null 2>&1; then
        echo "Starting emulator {{avd}}"
        nohup "{{emulator_bin}}" -avd "{{avd}}" >/dev/null 2>&1 &
    fi
    adb -e wait-for-device
    until [ "$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
    adb -e install -r "{{apk}}"
    adb -e shell am start -n "{{app_id}}/.MainActivity"

# Build, then install and launch the debug app on the plugged-in phone
phone: apk
    adb -d install -r "{{apk}}"
    adb -d shell am start -n "{{app_id}}/.MainActivity"

# Run the tests before every push
hooks:
    git config core.hooksPath .githooks
