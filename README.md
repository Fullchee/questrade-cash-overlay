# Questrade cash overlay

Android app that shows how much cash the selected Questrade account holds in the order's currency, on the order screen, so buys don't trigger a surprise currency conversion.

Scope and decisions: [docs/intent/questrade-cash-overlay.md](docs/intent/questrade-cash-overlay.md).

## Develop

1. Install `just` and the Android SDK. Gradle downloads JDK 21 itself.
2. Run `just hooks` once, so every push runs the tests.
3. Run `just test` for unit tests, ktlint and Android lint (which fails on hard-coded UI text and missing Chinese strings).
4. Start an emulator, then run `just install` to install and launch the debug app.
