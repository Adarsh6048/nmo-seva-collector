# NMO Seva Collector

Android collector app + Google Sheets / Apps Script backend for campaign accounting from UPI transaction notifications on approved collector phones.

## What this version does

1. Android's Notification Listener watches supported UPI apps on a collector phone after the collector acknowledges the ledger notice.
2. Incoming and outgoing transaction notifications are detected and stored in the local SQLite ledger.
3. All detected transaction metadata is queued to the central `Ledger` sheet so the admin has a collector-wise accounting record.
4. Incoming transactions start as `PENDING`; the collector chooses:
   - **Donation** → donor name or Anonymous
   - **Personal / not a donation**
   - **Decide later**
5. Outgoing transactions start with expense review pending; the collector chooses:
   - **Campaign expense** → a short expense comment is required
   - **Personal / not campaign expense**
   - **Decide later**
6. Only incoming transactions classified as `DONATION` count toward fundraising progress.
7. Only outgoing transactions classified as `CAMPAIGN_EXPENSE` count toward campaign expenses.
8. The admin dashboard shows donation total, campaign expenses, net campaign balance, pending reviews, collector-wise reports and a searchable transaction ledger.
9. WorkManager syncs queued ledger changes when internet is available.

The collector is shown a ledger notice during setup. The app does not request a UPI PIN, bank password, SMS access, contacts permission or accessibility permission. Raw notification text is not uploaded; only parsed transaction fields are sent.

> Important: this app tracks **notifications**, not the bank ledger itself. A transaction can only be detected when a supported UPI app posts a notification containing enough transaction information. Final accounting should be reconciled against UPI/bank transaction history.

## Repository layout

- `app/` — Android Studio project (Kotlin, XML layouts, SQLite, WorkManager)
- `backend/apps-script/Code.gs` — Google Sheets endpoint, collector authentication and report data
- `backend/apps-script/Dashboard.html` — admin dashboard with collector report + ledger filters
- `docs/SETUP.md` — setup and upgrade steps
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
- Collector acknowledgement of the central ledger notice
- On Android 13+, normal notification permission so transaction-review prompts can be shown

## Fields synced to the central ledger

- generated event fingerprint
- transaction amount
- incoming/outgoing direction
- donation classification
- expense classification
- donor name when classified as donation
- expense comment when classified as campaign expense
- optional counterparty hint parsed from the notification
- optional UPI/reference ID when present
- transaction time
- source UPI app
- reconciled flag
- collector code/name (added server-side)

The raw notification body is **not uploaded**.
