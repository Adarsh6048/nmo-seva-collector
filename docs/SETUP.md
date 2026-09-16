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

The local database upgrades automatically and adds transaction direction, donation classification and private upload-state tracking.

## E. New transaction workflow

### Incoming

1. Transaction is saved locally as `INCOMING` + `PENDING`.
2. Collector receives a prompt to review it.
3. Collector chooses:
   - **Mark as donation** → donor name / Anonymous required
   - **Not a donation**
   - **Decide later**
4. If marked **Donation**, it is queued for campaign sync.
5. If marked **Not a donation** or left pending, it stays only on that collector's phone.

### Outgoing

1. Transaction is saved locally as `OUTGOING`.
2. It is automatically `NOT_DONATION`.
3. It remains visible in the collector's local transaction ledger.
4. It is not uploaded to the campaign backend and never increases fundraising totals.

### Privacy rule

The central Google Sheet is for the campaign, not for a collector's private banking history. The app therefore uploads only transactions the collector explicitly identifies as donations. If a synced donation is later reclassified, only the correction is sent so the campaign total can be fixed.

## F. Test before fundraising

Use small real transactions such as ₹1 or ₹10.

1. Receive money into the collector's UPI account.
2. Confirm the app shows the incoming transaction and asks whether it is a donation.
3. Mark one test as **Donation** and another as **Not a donation**.
4. Send a small outgoing payment and confirm it appears under recent transactions on the phone.
5. Confirm the outgoing and personal incoming payment do **not** appear in the central Google Sheet.
6. Confirm only the test marked **Donation** appears on the Apps Script dashboard and contributes to fundraising progress.
7. Check the `Payments` sheet for `Direction` and `Donation Status`.

Notification wording differs by UPI app/version. If a real transaction is missed, capture only the notification wording needed for parser debugging and hide sensitive bank details.

## G. Reconciliation

Notification detection is not final bank verification.

At the end of a shift/day:

1. Collector opens their UPI/bank history.
2. Compare campaign donations with amount, time and available reference number in the `Payments` sheet.
3. Set `Reconciled` to `TRUE` for confirmed donation rows.
4. The dashboard separately displays reconciled donation value.

## H. Campaign settings

In the `Settings` sheet:

- `TARGET` → `400000`
- `CAMPAIGN` → campaign display name

The dashboard reads these values automatically.
