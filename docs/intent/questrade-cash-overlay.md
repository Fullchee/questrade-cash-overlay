# Intent: Questrade cash overlay

Confirmed 2026-11-17.

| | |
|---|---|
| **Outcome** | An accessibility overlay on Questrade's order screen shows the selected account's CAD and USD cash |
| **User** | A family member who reads Chinese, on one phone with several Questrade logins. The owner does the setup |
| **Device** | One Poco X5 5G on HyperOS 1.0.25, sideloaded |
| **Why** | Stop surprise currency conversions when buying with cash the account doesn't hold in that currency |
| **How it matches** | Read `Cash - <account number>` from the order screen; look the number up across every saved login |
| **Setup** | Guided onboarding: allow restricted settings, accessibility, notifications, battery "No restrictions", Xiaomi Autostart. Each step shows a ✓ when done |
| **Success** | They choose an account and see its exact CAD and USD cash, with no app switching or token handling |
| **Upkeep** | Read-only Questrade API. A daily WorkManager job rotates each login's refresh token on the phone. When one breaks, the overlay says so and the owner pastes a new token |
| **Out of scope** | Placing trades, comparing the order total to the cash, other phones, Play Store release, iOS, any server or GitHub Actions |

## Facts this rests on

- `GET /v1/accounts/{id}/balances` returns `perCurrencyBalances` with `cash` per currency (verified 2026-11-17).
- The order screen shows the full account number, e.g. `Cash - 12345678`, and the order currency.
- A third-party overlay stays visible on Questrade's order screen (tested with a floating clock app).
- Refresh tokens are single-use, so only one place may refresh them: the phone.
