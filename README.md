# NMO Seva Collector — MVP

Android collector app + Google Sheets / Apps Script backend for tracking UPI transaction notifications from **personal QR / UPI accounts** and letting the collector decide which incoming payments are donations.

## What this version does

1. Android's Notification Listener watches supported UPI apps on the collector phone.
2. Incoming and outgoing transaction notifications are detected and stored in the phone's local SQLite ledger.
3. Every transaction is tagged as `INCOMING` or `OUTGOING`.
4. Outgoing transactions stay local and are automatically classified as `NOT_DONATION`.
5. Incoming transactions start as `PENDING` and the collector gets a prompt such as **“₹500 received — tap to mark as donation or personal payment.”**
6. The collector can choose:
   - **Mark as donation** → enter donor name or choose Anonymous
   - **Not a donation** → personal/non-campaign payment
   - **Decide later**
7. Only transactions classified as `DONATION` count toward the ₹4 lakh fundraising total.
8. Personal, pending and outgoing transactions remain on the collector's phone and are **not uploaded to the central Google Sheet**.
9. Donations sync through WorkManager when internet is available. If an already-uploaded donation is later reclassified as not a donation, that change is synced so the central total is corrected.
10. The central dashboard contains campaign donation records, not the collector's full personal transaction history.

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

## Donation fields synced to the campaign backend

- event fingerprint
- amount
- direction
- donation status
- donor name when marked as donation
- optional counterparty hint parsed from the notification
- optional UPI/reference ID when present
- transaction time
- source UPI app
- reconciled flag
- collector code/name (added server-side)

The raw notification body is **not uploaded**.
