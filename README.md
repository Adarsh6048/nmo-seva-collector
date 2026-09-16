# NMO Seva Collector — MVP

Android collector app + Google Sheets / Apps Script backend for tracking UPI transaction notifications from **personal QR / UPI accounts** and letting the collector decide which incoming payments are donations.

## What this version does

1. Android's Notification Listener watches supported UPI apps on the collector phone.
2. Incoming and outgoing transaction notifications are detected and stored locally.
3. Every transaction is tagged as `INCOMING` or `OUTGOING`.
4. Outgoing transactions are kept in the ledger but automatically classified as `NOT_DONATION`.
5. Incoming transactions start as `PENDING` and the collector gets a prompt such as **“₹500 received — tap to mark as donation or personal payment.”**
6. The collector can choose:
   - **Mark as donation** → enter donor name or choose Anonymous
   - **Not a donation** → personal/non-campaign payment
   - **Decide later**
7. Only transactions classified as `DONATION` count toward the ₹4 lakh fundraising total.
8. WorkManager syncs records to Google Sheets when internet is available; offline transactions remain queued locally.
9. The central dashboard shows donation totals separately from the complete incoming/outgoing transaction ledger.

No UPI PIN, bank password, SMS access, contacts permission or accessibility permission is requested.

> Important: this app tracks **notifications**, not the bank ledger itself. A transaction can only be detected when a supported UPI app posts a notification that contains enough transaction information. Final accounting should be reconciled with the UPI/bank transaction history.

## Repository layout

- `app/` — Android Studio project (Kotlin, XML layouts, SQLite, WorkManager)
- `backend/apps-script/Code.gs` — Google Sheets endpoint + collector authentication + dashboard data
- `backend/apps-script/Dashboard.html` — admin dashboard UI
- `docs/SETUP.md` — exact setup / update steps
- `docs/ARCHITECTURE.md` — data flow and security model

## Supported UPI apps

- Google Pay — `com.google.android.apps.nbu.paisa.user`
- PhonePe — `com.phonepe.app`
- Paytm — `net.one97.paytm`
- BHIM — `in.org.npci.upiapp`

The package allow-list lives in `UpiNotificationListenerService.kt`. Notification wording differs between versions, so parser rules should be tested against real notifications used by collectors.

## Android requirements

- Android 8.0+ (`minSdk 26`)
- Android Studio with JDK 17+
- Notification Access enabled by each collector
- On Android 13+, normal notification permission so the donation-review prompt can be shown

## Transaction fields synced

- event fingerprint
- amount
- direction: `INCOMING` / `OUTGOING`
- donation status: `PENDING` / `DONATION` / `NOT_DONATION`
- donor name when marked as donation
- optional counterparty hint parsed from the notification
- optional UPI/reference ID when present
- transaction time
- source UPI app
- reconciled flag
- collector code/name (added server-side)

The raw notification body is **not uploaded**.
