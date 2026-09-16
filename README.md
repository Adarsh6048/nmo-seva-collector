# NMO Seva Collector — MVP

Android collector app + Google Sheets / Apps Script backend for tracking incoming payments made to **personal UPI QR codes**.

## What this version does

1. A donor pays a collector's normal personal UPI QR in Google Pay, PhonePe, Paytm or BHIM.
2. Android's Notification Listener receives the UPI app notification.
3. The app only inspects notifications from the supported UPI package allow-list.
4. A conservative parser looks for an **incoming-payment phrase + amount** and rejects obvious outgoing payments, cashback, rewards, refunds and reversals.
5. The payment is saved immediately in the phone's local SQLite database.
6. A high-priority app notification appears: **“₹500 received — tap to add donor name.”**
7. The collector enters the donor name or selects **Anonymous**.
8. WorkManager syncs the transaction to a Google Sheet when internet is available. If the phone is offline, it remains queued locally.
9. An Apps Script dashboard combines totals from every collector phone.

No UPI PIN, bank password, SMS access, contacts permission or accessibility permission is requested.

> Important: notification detection is not bank-grade settlement confirmation. Treat records as **Auto-detected** until reconciled against the UPI/bank transaction history. Notification wording can also change after a UPI app update.

## Repository layout

- `app/` — Android Studio project (Kotlin, XML layouts, SQLite, WorkManager)
- `backend/apps-script/Code.gs` — Google Sheets API endpoint + collector authentication + dashboard data
- `backend/apps-script/Dashboard.html` — admin dashboard UI
- `docs/SETUP.md` — exact setup steps
- `docs/ARCHITECTURE.md` — data flow and security model

## Supported UPI apps in the first parser

- Google Pay — `com.google.android.apps.nbu.paisa.user`
- PhonePe — `com.phonepe.app`
- Paytm — `net.one97.paytm`
- BHIM — `in.org.npci.upiapp`

The package allow-list lives in `UpiNotificationListenerService.kt` and is easy to extend after testing actual notifications from other bank/UPI apps.

## Android requirements

- Android 8.0+ (`minSdk 26`)
- Android Studio with JDK 17+
- Notification Access enabled by each collector
- On Android 13+, normal notification permission so the donor-name prompt can be shown

## Build

Open this folder in Android Studio, allow Gradle sync, then choose **Build > Build APK(s)**. The project is configured with `compileSdk 35`.

## Data fields synced

- generated event ID / fingerprint
- amount
- donor name
- optional sender hint parsed from the notification
- optional UPI/reference ID if present in the notification
- receive time
- source UPI app
- reconciled flag
- collector code/name (added server-side)

The raw notification body is **not uploaded**.
