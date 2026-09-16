# Setup guide

## A. Update the central Google Sheet backend

1. Open the campaign Google Sheet.
2. Open **Extensions → Apps Script**.
3. Replace `Code.gs` with the latest `backend/apps-script/Code.gs` from this repository.
4. Replace the HTML file named exactly `Dashboard` with the latest `backend/apps-script/Dashboard.html`.
5. Save the Apps Script project.
6. Run:

```javascript
setupSheets()
```

`setupSheets()` is safe to run again. It creates a new `Ledger` sheet and migrates existing rows from the old `Payments` sheet without deleting that old sheet.

The central workbook then contains:

- `Ledger` — incoming + outgoing transaction ledger
- `Payments` — legacy sheet retained for safety if it already exists
- `Collectors`
- `Settings`
- `Expenses`

The default campaign target is **₹4,00,000**.

## B. Collector credentials

Existing collector code/token pairs remain valid. You do not need to create new tokens after this upgrade.

For a new collector:

```javascript
addCollector("NMO02", "Collector Name")
```

Keep the returned token private.

## C. Redeploy Apps Script

After replacing `Code.gs` and `Dashboard.html`:

1. **Deploy → Manage deployments**.
2. Edit the active Web app deployment.
3. Choose **New version**.
4. Deploy.
5. Keep using the same `/exec` URL.

## D. Update the Android app

1. Android Studio → **Git → Pull**.
2. Wait for Gradle sync.
3. Run the app on the collector phone again.
4. The local SQLite database upgrades automatically.
5. Open **Collector settings** once after this upgrade.
6. Read and accept the transaction-ledger notice, then save setup.
7. Confirm **Notification access** remains enabled.

The collector is informed that parsed transaction metadata from supported UPI notifications is synced to the central campaign ledger. Raw notification text, UPI PINs, bank passwords, SMS and contacts are not collected.

## E. Transaction workflow

### Incoming transaction

1. Detected as `INCOMING` and immediately queued to the central ledger.
2. Donation status begins as `PENDING`.
3. Collector can choose:
   - **Mark as donation** → donor name or Anonymous required
   - **Personal / not a donation**
   - **Decide later**
4. Any later classification change is synced to the same ledger row.

### Outgoing transaction

1. Detected as `OUTGOING` and immediately queued to the central ledger.
2. Expense status begins as `PENDING`.
3. Collector can choose:
   - **Mark as campaign expense** → short expense comment required
   - **Personal / not campaign expense**
   - **Decide later**
4. Campaign expenses appear separately in the admin report and reduce the displayed net campaign balance.

## F. Admin dashboard

The dashboard shows:

- donation total and donation count
- campaign expense total and expense count
- net campaign balance = donations − campaign expenses
- all detected incoming and outgoing values
- pending donation and expense reviews
- collector-wise transaction, donation and expense report
- searchable/filterable ledger by collector, direction and classification

## G. Test before fundraising

Use small real transactions such as ₹1 or ₹10.

1. Receive a PhonePe/UPI payment and confirm it appears as incoming.
2. Mark it as Donation and enter a donor name.
3. Receive another incoming payment and mark it Personal.
4. Make a small outgoing payment.
5. Mark one outgoing test as Campaign expense and enter a comment such as `test printing`.
6. Mark another outgoing test as Personal.
7. Confirm all four transaction records appear in the central `Ledger` sheet.
8. Confirm only Donation entries increase fundraising progress.
9. Confirm only Campaign expense entries increase campaign expenses.
10. Open the dashboard and verify the collector-wise report and ledger filters.

Notification wording differs by UPI app/version. If a real transaction is missed, capture only the visible notification wording needed for parser debugging and hide sensitive bank details.

## H. Reconciliation

Notification detection is not final bank verification. At the end of a shift/day, compare campaign ledger entries with UPI/bank history and set `Reconciled` to `TRUE` for confirmed records where appropriate.

## I. Campaign settings

In the `Settings` sheet:

- `TARGET` → `400000`
- `CAMPAIGN` → campaign display name
