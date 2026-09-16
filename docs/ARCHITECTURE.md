# Architecture and privacy model

## Data flow

```text
Collector acknowledges ledger notice
  │
  ▼
Supported UPI app notification
  │ incoming or outgoing transaction
  ▼
Android NotificationListenerService
  │ package allow-list + transaction parser
  ▼
Local SQLite ledger
  ├── incoming review: Donation / Personal / Later
  ├── outgoing review: Campaign expense / Personal / Later
  └── offline sync queue
        │ WorkManager when internet is available
        ▼
Google Apps Script endpoint
  │ collector code + random token validation
  ▼
Google Sheet: Ledger
  ├── collector-wise report
  └── admin dashboard / searchable ledger
```

## Why the phone app is needed

A normal personal UPI QR does not provide this campaign backend with a bank/payment webhook. The app therefore uses transaction notifications already posted on the approved collector phone as an operational accounting signal.

## Ledger model

Every detected supported transaction is stored with:

- incoming/outgoing direction
- amount and transaction time
- source UPI app
- available counterparty/reference fields
- incoming donation classification
- outgoing expense classification
- donor name for donations
- expense comment for campaign expenses
- collector code/name
- reconciliation flag

Incoming transactions begin with donation review pending. Outgoing transactions begin with expense review pending. Classification changes update the same ledger row by event ID.

## Privacy boundaries and consent

Android Notification Access is a powerful permission. The collector setup screen explicitly states that parsed metadata for detected supported UPI transactions is synced to the campaign ledger, and setup requires acknowledgement before collection/sync is enabled.

The listener immediately rejects notifications whose package is not in the explicit supported UPI-app allow-list. Raw notification text is not uploaded.

The app does **not** ask for:

- UPI PIN
- online-banking password
- SMS permission
- contacts
- call logs
- accessibility access

This design must not be deployed as hidden monitoring. Collectors should understand the ledger behaviour before Notification Access is enabled.

## Campaign accounting

- `DONATION` incoming transactions contribute to fundraising total.
- `CAMPAIGN_EXPENSE` outgoing transactions contribute to campaign expenses.
- Net campaign balance = donations − campaign expenses.
- Personal and pending transactions remain visible in the admin ledger but do not affect donation or expense totals.

## Reliability model

Notification detection is not bank-grade settlement confirmation. Campaign records should be reconciled against UPI/bank transaction history. The dashboard keeps reconciliation separate from notification detection/classification.

## Duplicate protection

The Android app generates a SHA-256 event identifier from the source app package, Android notification key, notification post time, direction and amount. Local SQLite uses `event_id` as a primary key, and the backend upserts by the same event ID.

## Offline behaviour

Transactions are written to SQLite before network upload. WorkManager retries queued records when connectivity is available. Any later donation/expense classification change marks the row unsynced so the central ledger receives the update.

## Threat-model limitations

This is a lightweight campaign operations tool, not a banking integration. Notification wording can change, some UPI apps may suppress notifications, and a user with control of a modified device could manipulate local state. Final campaign accounting should therefore use bank/UPI reconciliation and normal organisational controls.
