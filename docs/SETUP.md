# Setup guide

## A. Create the central Google Sheet

1. Create a new Google Sheet, for example **NMO Seva Payments**.
2. Open **Extensions → Apps Script**.
3. Replace the default code with `backend/apps-script/Code.gs`.
4. Add a new HTML file named exactly `Dashboard` and paste `backend/apps-script/Dashboard.html`.
5. Save the project.

### Initialize the database

Run once from Apps Script:

```javascript
setupSheets()
```

It creates:

- `Payments`
- `Collectors`
- `Settings`
- `Expenses`

The default campaign target is **₹4,00,000**.

### Set an admin dashboard PIN

Run once, replacing the example:

```javascript
setAdminPin("choose-a-strong-pin")
```

### Add collectors

For each collector, run:

```javascript
addCollector("NMO01", "Collector Name")
```

The function returns a long random `token`. Copy the collector **code + token** somewhere secure and give only that pair to the relevant collector.

Repeat, for example:

```javascript
addCollector("NMO02", "Collector Two")
addCollector("NMO03", "Collector Three")
```

You can deactivate a collector later:

```javascript
setCollectorActive("NMO03", false)
```

## B. Deploy the Apps Script backend

1. In Apps Script choose **Deploy → New deployment**.
2. Select **Web app**.
3. Execute as: **Me**.
4. Access: choose the setting that allows the collector apps to reach the endpoint. For a personal Google account this is typically **Anyone**.
5. Deploy and copy the URL ending in `/exec`.

The endpoint does not expose the Sheet directly. Collector uploads are accepted only when `collectorCode + token` match an active row in the `Collectors` sheet.

The same `/exec` URL opens the admin dashboard in a browser. It asks for the PIN set with `setAdminPin()`.

## C. Install/configure each Android collector app

1. Build/install the APK on the collector's Android phone.
2. On first launch enter:
   - Collector name
   - Collector code, e.g. `NMO01`
   - Collector token returned by Apps Script
   - `/exec` backend URL
3. Tap **Enable notification access** and enable **NMO Seva Collector**.
4. Allow normal app notifications when Android asks. This permission is needed for the **“add donor name”** prompt.
5. Keep Google Pay / PhonePe / Paytm / BHIM notifications enabled.

## D. Test before fundraising

Do a small real transfer between team members, e.g. ₹1 or ₹10.

Expected sequence:

1. UPI app posts an incoming-payment notification.
2. NMO Seva Collector posts its own high-priority notification.
3. Tap it and enter the donor name.
4. Open the app: the payment should be visible.
5. Open the Apps Script `/exec` dashboard: the payment should appear after sync.
6. Check the Google Sheet `Payments` tab.

Test each UPI app your collectors actually use. Notification text differs by app/version; if one format is not detected, capture the exact notification wording **without sharing sensitive bank details** and add a parser rule.

## E. Reconciliation

Notification detection should be considered **Auto-detected**, not final bank verification.

At the end of a shift/day:

1. Collector opens their UPI/bank history.
2. Compare amounts/times/reference numbers with the Sheet.
3. In the `Payments` sheet set the `Reconciled` column to `TRUE` for confirmed entries.
4. The dashboard will show both **Detected total** and **Reconciled total**.

## F. Changing the campaign target/name

In the `Settings` sheet:

- `TARGET` → `400000`
- `CAMPAIGN` → your preferred campaign name

The dashboard reads these values automatically.
