# NMO Seva Ledger

Android collector app + Google Sheets / Apps Script backend for campaign accounting from UPI transaction notifications on approved collector phones.

## What this version does

1. Android's Notification Listener watches supported payment apps on a collector phone after the collector acknowledges the ledger notice and notification-readiness check.
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
10. The Android UI uses an always-dark NMO-branded design with the supplied NMO logo, modern cards, donation/expense color coding and transaction review screens.

The collector is shown a ledger notice during setup. Setup also requires the collector to review notification settings for the payment apps they actually use. Android does not permit one app to silently enable notifications for another app, so NMO Seva Ledger opens the relevant system notification settings and asks the collector to confirm they are enabled.

The app does not request a UPI PIN, bank password, SMS access, contacts permission or accessibility permission. Raw notification text is not uploaded; only parsed transaction fields are sent.

> Important: this app tracks **notifications**, not the bank ledger itself. A transaction can only be detected when a supported payment app posts a notification containing enough transaction information. Final accounting should be reconciled against UPI/bank transaction history.

## Repository layout

- `app/` — Android Studio project (Kotlin, XML layouts, SQLite, WorkManager)
- `backend/apps-script/Code.gs` — Google Sheets endpoint, collector authentication and report data
- `backend/apps-script/Dashboard.html` — admin dashboard with collector report + ledger filters
- `docs/SETUP.md` — setup and upgrade steps
- `docs/ARCHITECTURE.md` — data flow and security model

## Supported payment apps

- Google Pay — `com.google.android.apps.nbu.paisa.user`
- PhonePe — `com.phonepe.app`
- Paytm — `net.one97.paytm`
- BHIM — `in.org.npci.upiapp`
- WhatsApp Pay — `com.whatsapp`
- WhatsApp Business Pay — `com.whatsapp.w4b`

WhatsApp requires stricter parsing because ordinary chat notifications come from the same Android package as WhatsApp Pay. The parser therefore accepts only payment-like WhatsApp notification formats such as `You received ₹500 from Name` or explicit payment-received/sent wording. If a collector's WhatsApp version uses different wording, capture the visible notification text with sensitive details hidden and add a matching rule before relying on it.

The package allow-list lives in `UpiNotificationListenerService.kt`. Notification wording differs between versions, so parser rules should be tested against real notifications used by collectors.

## Android requirements

- Android 8.0+ (`minSdk 26`)
- Android Studio with JDK 17+
- Notification Access enabled by each collector
- Notifications enabled in each payment app the collector uses
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
- source payment app
- reconciled flag
- collector code/name (added server-side)

The raw notification body is **not uploaded**.
