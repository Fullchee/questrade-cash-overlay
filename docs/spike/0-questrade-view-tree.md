# Spike 0: Questrade's view tree

Result: **the account number is readable.** Step 7 can use the accessibility service; the floating-bubble fallback is not needed.

Dumped on the Poco X5 5G with `adb shell uiautomator dump`, 2026-11-18. Account numbers and dollar amounts in the fixtures are fake.

## Package

`com.questrade.questmobile` (React Native: every node's `resource-id` is a `testID`, mirrored in `content-desc`).

## Order screen

Fixture: [`app/src/test/resources/questrade/order-screen.xml`](../../app/src/test/resources/questrade/order-screen.xml)

| resource-id | text |
|---|---|
| `oe-account-selector-selected-account-name` | `TFSA - 22222222` |
| `oe-account-selector-cash-label` | `Buying power (Combined in CAD)` |
| `oe-account-selector-cash-value` | `$1,234.56` |

The order's currency sits in the `Total amount, $, CAD` node's `content-desc`.

## Account picker open

Fixture: [`app/src/test/resources/questrade/account-picker.xml`](../../app/src/test/resources/questrade/account-picker.xml)

One card per account, `N` from 0:

| resource-id | text |
|---|---|
| `oe-account-selector-account-card-N-selected` / `-unselected` | (container) |
| `oe-account-selector-account-card-N-acoount-name` (sic) | `Cash - 11111111 ` |
| `oe-account-selector-account-card-N-cash-value` | `$1,234.56` |

## Consequences for later steps

- **Step 2 matcher**: the label is `<account type> - <8 digits>`, not only `Cash - …`. Seen types: `Cash`, `TFSA`, `RRSP`. Picker labels end with a trailing space, so trim.
- **Step 7**: match on `oe-account-selector-selected-account-name` first; fall back to scanning all text with the step 2 matcher. When the picker is open, several accounts are on screen: read the card ending in `-selected`, or hide the overlay.
- Questrade only shows cash combined in CAD, which is why the overlay still needs the API for separate CAD and USD.
