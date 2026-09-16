# Setup guide

## A. Create or update the central Google Sheet

1. Open the Google Sheet used by the campaign.
2. Open **Extensions → Apps Script**.
3. Replace `Code.gs` with the latest `backend/apps-script/Code.gs` from this repository.
4. Replace the HTML file named exactly `Dashboard` with the latest `backend/apps-script/Dashboard.html`.
5. Save the Apps Script project.
6. Run:

```javascript
setupSheets()
```

`setupSheets()` is safe to run again after upgrading. It keeps existing rows and upgrades the `Payments` sheet to include:

- Direction
- Donation Status

Existing old payment rows are migrated as incoming transactions; rows that already have a donor name are treated as donations, while unnamed old rows remain pending review.

The workbook contains:

- `Payments`
- `Collectors`
- `Settings`
- `Expenses`

The default campaign target is **₹4,00,000**.

## B. Admin PIN and collectors

If not already configured, set an admin PIN using a helper function or your existing setup method.

Collector credentials remain unchanged after this upgrade. You do **not** need to create a new collector token merely because the app was updated.

For a new collector:

```javascript
addCollector("NMO02", "Collector Name")
```

Keep the returned token private.

## C. Redeploy Apps Script after code changes

After replacing `Code.gs` and `Dashboard.html`:

1. Click **Deploy → Manage deployments**.
2. Edit the current Web app deployment.
3. Choose **New version**.
4. Deploy.
5. Keep using the `/exec` URL from the active deployment.

If you instead create a completely new deployment, update the backend URL inside every collector app.

## D. Update the Android app

1. In Android Studio choose **Git → Pull**.
2. Wait for Gradle sync.
3. Run the app on the phone again to install the latest build over the existing app.
4. Existing collector settings and the local SQLite database remain on the phone when installing an update normally.
5. Confirm **Notification access** is still enabled.

The local database automatically upgrades from version 1 to version 2 and adds:

- `direction`
- `donation_status`

## E. New transaction workflow

For supported UPI notifications:

### Incoming

1. Transaction is saved as `INCOMING` + `PENDING`.
2. Collector receives a prompt to review it.
3. Collector chooses:
   - **Mark as donation** → donor name / Anonymous required
   - **Not a donation**
   - **Decide later**
4. Only **Mark as donation** contributes to the campaign fundraising total.

### Outgoing

1. Transaction is saved as `OUTGOING`.
2. It is automatically `NOT_DONATION`.
3. It remains visible in the transaction ledger for accountability.
4. It never increases the fundraising total.

## F. Test before fundraising

Use small real transactions such as ₹1 or ₹10.

Test both directions if possible:

1. Receive money into the collector's UPI account.
2. Confirm the app shows the incoming transaction and asks whether it is a donation.
3. Mark one test as **Donation** and another as **Not a donation**.
4. Send a small outgoing payment and confirm it appears under recent transactions as outgoing.
5. Open the Apps Script dashboard and verify only the transaction marked **Donation** is added to fundraising progress.
6. Check the `Payments` sheet for `Direction` and `Donation Status`.

Notification wording differs by UPI app/version. If a real transaction is missed, capture only the notification wording needed for parser debugging and hide sensitive bank details.

## G. Reconciliation

Notification detection is not final bank verification.

At the end of a shift/day:

1. Collector opens their UPI/bank history.
2. Compare amount, time and available reference number with the `Payments` sheet.
3. Set `Reconciled` to `TRUE` for confirmed rows.
4. The dashboard separately displays reconciled donation value.

## H. Campaign settings

In the `Settings` sheet:

- `TARGET` → `400000`
- `CAMPAIGN` → campaign display name

The dashboard reads these values automatically.
