# Architecture and privacy model

## Data flow

```text
Donor
  │ scans collector's personal QR
  ▼
UPI app on collector phone
  │ incoming-payment notification
  ▼
Android NotificationListenerService
  │ package allow-list + payment parser
  ▼
Local SQLite
  ├── immediate donor-name prompt
  └── offline queue
        │ WorkManager when internet is available
        ▼
Google Apps Script endpoint
  │ collector code + random token validation
  ▼
Google Sheet
  └── Admin dashboard
```

## Why the phone app is needed

A normal personal UPI QR does not provide your own server with a payment webhook. The phone already receives the UPI app's incoming-payment notification, so the collector app uses that local signal to create an operational record.

## Privacy boundaries

Android's Notification Access is a powerful permission: at the operating-system level it allows the listener service to receive notifications. This implementation immediately rejects any notification whose package is not in the explicit UPI-app allow-list.

For accepted payment notifications, the server receives only the parsed fields needed for the campaign. Raw notification text is not uploaded.

The app does **not** ask for:

- UPI PIN
- online-banking password
- SMS permission
- contacts
- call logs
- accessibility access

Collectors should knowingly consent before Notification Access is enabled on their phones.

## Reliability model

A payment moves conceptually through two stages:

1. **Auto-detected** — an incoming-payment notification was observed.
2. **Reconciled** — an authorised person checked it against UPI/bank transaction history.

The dashboard intentionally displays these separately.

## Duplicate protection

The Android app generates a SHA-256 event identifier from the source app package, Android notification key, notification post time and amount. The local SQLite `event_id` is a primary key. The backend also upserts by the same event ID.

## Offline behaviour

The payment is written to SQLite before any network request. WorkManager only attempts upload when a network is available. Editing the donor name marks that row unsynced again, so the central Sheet receives the update later.

## Threat-model limitations

This is a lightweight fundraising operations tool, not a banking integration. A malicious collector with control of their phone could potentially manipulate local state or fabricate notifications on a modified device. Final accounting should therefore use bank/UPI reconciliation and normal organisational controls.
