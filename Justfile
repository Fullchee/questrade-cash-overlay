app_id := "com.fullchee.questradecashoverlay"
apk := "app/build/outputs/apk/debug/app-debug.apk"

# Unit tests and ktlint
test:
    ./gradlew test ktlintCheck

# Build the debug APK, install it on the running emulator and launch it
install:
    ./gradlew assembleDebug
    adb -e install -r {{apk}}
    adb -e shell am start -n {{app_id}}/.MainActivity

# Run the tests before every push
hooks:
    git config core.hooksPath .githooks
