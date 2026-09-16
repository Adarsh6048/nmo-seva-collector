const SHEETS = {
  PAYMENTS: 'Payments',
  COLLECTORS: 'Collectors',
  SETTINGS: 'Settings',
  EXPENSES: 'Expenses'
};

const PAYMENT_HEADERS = [
  'Event ID','Transaction At','Server Updated At','Collector Code','Collector Name',
  'Amount','Donor Name','Counterparty Hint','UPI Ref','Source App','Reconciled',
  'Direction','Donation Status'
];

function setupSheets() {
  const ss = SpreadsheetApp.getActive();
  const payments = ensureSheet_(ss, SHEETS.PAYMENTS, PAYMENT_HEADERS);
  ensurePaymentSchema_(payments);
  ensureSheet_(ss, SHEETS.COLLECTORS, ['Collector Code','Collector Name','Token','Active']);
  ensureSheet_(ss, SHEETS.SETTINGS, ['Key','Value']);
  ensureSheet_(ss, SHEETS.EXPENSES, ['Expense ID','Date','Category','Vendor','Amount','Remarks','Added By']);

  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  const data = settings.getDataRange().getValues();
  if (!data.some(r => r[0] === 'TARGET')) settings.appendRow(['TARGET', 400000]);
  if (!data.some(r => r[0] === 'CAMPAIGN')) settings.appendRow(['CAMPAIGN', 'Dr. B. R. Ambedkar Swasthya Sewa Yatra — 100 Health Camps, Kanpur']);
  SpreadsheetApp.flush();
  return 'Sheets ready. Existing payment rows were migrated to the transaction + donation schema.';
}

function setAdminPin(pin) {
  if (!pin || String(pin).length < 4) throw new Error('Use an admin PIN of at least 4 characters.');
  PropertiesService.getScriptProperties().setProperty('ADMIN_PIN', String(pin));
  return 'Admin PIN saved.';
}

function addCollector(code, name) {
  code = String(code || '').trim().toUpperCase();
  name = String(name || '').trim();
  if (!code || !name) throw new Error('Collector code and name are required.');
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEETS.COLLECTORS);
  if (!sh) throw new Error('Run setupSheets() first.');
  const values = sh.getDataRange().getValues();
  if (values.slice(1).some(r => String(r[0]).toUpperCase() === code)) throw new Error('Collector code already exists.');
  const token = Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, '').slice(0, 16);
  sh.appendRow([code, name, token, true]);
  return {code, name, token};
}

function setCollectorActive(code, active) {
  code = String(code || '').trim().toUpperCase();
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEETS.COLLECTORS);
  const values = sh.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    if (String(values[i][0]).toUpperCase() === code) {
      sh.getRange(i + 1, 4).setValue(Boolean(active));
      return `Collector ${code} active=${Boolean(active)}`;
    }
  }
  throw new Error('Collector not found.');
}

function doPost(e) {
  try {
    const body = JSON.parse(e.postData && e.postData.contents ? e.postData.contents : '{}');
    const collector = validateCollector_(body.collectorCode, body.token);
    const events = Array.isArray(body.events) ? body.events : [];
    if (!events.length) return json_({ok: true, accepted: 0});
    if (events.length > 100) throw new Error('Maximum 100 events per request.');

    const sh = SpreadsheetApp.getActive().getSheetByName(SHEETS.PAYMENTS);
    ensurePaymentSchema_(sh);
    const existing = sh.getLastRow() > 1
      ? sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues().flat().map(String)
      : [];
    const rowById = new Map(existing.map((id, idx) => [id, idx + 2]));
    const now = new Date();
    let accepted = 0;

    events.forEach(ev => {
      const eventId = clean_(ev.eventId, 100);
      const amount = Number(ev.amount);
      const transactionAt = Number(ev.receivedAt);
      if (!eventId || !Number.isFinite(amount) || amount <= 0 || amount > 1000000 || !Number.isFinite(transactionAt)) return;

      let direction = String(ev.direction || 'INCOMING').toUpperCase();
      if (!['INCOMING','OUTGOING'].includes(direction)) direction = 'INCOMING';

      let donationStatus = String(ev.donationStatus || 'PENDING').toUpperCase();
      if (!['PENDING','DONATION','NOT_DONATION'].includes(donationStatus)) donationStatus = 'PENDING';
      if (direction === 'OUTGOING') donationStatus = 'NOT_DONATION';

      const row = [
        eventId,
        new Date(transactionAt),
        now,
        collector.code,
        collector.name,
        amount,
        clean_(ev.donorName, 120),
        clean_(ev.senderHint, 120),
        clean_(ev.transactionRef, 80),
        clean_(ev.sourceApp, 80),
        Boolean(ev.reconciled),
        direction,
        donationStatus
      ];
      const existingRow = rowById.get(eventId);
      if (existingRow) {
        sh.getRange(existingRow, 1, 1, row.length).setValues([row]);
      } else {
        sh.appendRow(row);
        rowById.set(eventId, sh.getLastRow());
      }
      accepted++;
    });
    return json_({ok: true, accepted});
  } catch (err) {
    return json_({ok: false, error: String(err && err.message ? err.message : err)});
  }
}

function doGet() {
  return HtmlService.createHtmlOutputFromFile('Dashboard')
    .setTitle('NMO Seva Dashboard')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function getDashboard(pin) {
  const expected = PropertiesService.getScriptProperties().getProperty('ADMIN_PIN');
  if (!expected || String(pin) !== expected) throw new Error('Invalid admin PIN.');

  const ss = SpreadsheetApp.getActive();
  const sh = ss.getSheetByName(SHEETS.PAYMENTS);
  ensurePaymentSchema_(sh);
  const payments = sh.getDataRange().getValues();
  const target = Number(getSetting_('TARGET') || 400000);
  const campaign = String(getSetting_('CAMPAIGN') || 'Dr. B. R. Ambedkar Swasthya Sewa Yatra — 100 Health Camps, Kanpur');

  let donationTotal = 0;
  let reconciledDonations = 0;
  let donationCount = 0;
  let reclassifiedCount = 0;
  const byCollector = {};
  const recent = [];

  for (let i = 1; i < payments.length; i++) {
    const r = payments[i];
    const amount = Number(r[5]) || 0;
    const direction = String(r[11] || 'INCOMING').toUpperCase();
    const donationStatus = String(r[12] || 'PENDING').toUpperCase();
    const isDonation = direction === 'INCOMING' && donationStatus === 'DONATION';

    if (isDonation) {
      donationTotal += amount;
      donationCount++;
      if (r[10] === true) reconciledDonations += amount;
    } else {
      reclassifiedCount++;
    }

    const code = String(r[3] || 'UNKNOWN');
    const name = String(r[4] || code);
    if (!byCollector[code]) byCollector[code] = {code, name, donationAmount: 0, donationCount: 0, campaignRecordCount: 0};
    byCollector[code].campaignRecordCount++;
    if (isDonation) {
      byCollector[code].donationAmount += amount;
      byCollector[code].donationCount++;
    }

    recent.push({
      transactionAt: r[1] instanceof Date ? r[1].toISOString() : String(r[1]),
      collector: code,
      amount,
      donor: String(r[6] || ''),
      party: String(r[7] || ''),
      source: String(r[9] || ''),
      reconciled: r[10] === true,
      direction,
      donationStatus
    });
  }

  recent.sort((a,b) => new Date(b.transactionAt) - new Date(a.transactionAt));
  return {
    campaign,
    target,
    donationTotal,
    reconciledDonations,
    donationCount,
    reclassifiedCount,
    campaignRecordCount: Math.max(0, payments.length - 1),
    remaining: Math.max(0, target - donationTotal),
    byCollector: Object.values(byCollector).sort((a,b) => b.donationAmount - a.donationAmount),
    recent: recent.slice(0, 60)
  };
}

function getSetting_(key) {
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEETS.SETTINGS);
  const values = sh.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) if (String(values[i][0]) === key) return values[i][1];
  return null;
}

function validateCollector_(code, token) {
  code = String(code || '').trim().toUpperCase();
  token = String(token || '').trim();
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEETS.COLLECTORS);
  const values = sh.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    const rowCode = String(values[i][0] || '').trim().toUpperCase();
    if (rowCode === code && String(values[i][2] || '') === token && values[i][3] !== false) {
      return {code: rowCode, name: String(values[i][1] || rowCode)};
    }
  }
  throw new Error('Collector credentials rejected.');
}

function ensurePaymentSchema_(sh) {
  if (!sh) throw new Error('Payments sheet missing. Run setupSheets().');
  if (sh.getMaxColumns() < PAYMENT_HEADERS.length) {
    sh.insertColumnsAfter(sh.getMaxColumns(), PAYMENT_HEADERS.length - sh.getMaxColumns());
  }
  sh.getRange(1, 1, 1, PAYMENT_HEADERS.length).setValues([PAYMENT_HEADERS]);
  sh.getRange(1, 1, 1, PAYMENT_HEADERS.length).setFontWeight('bold');
  sh.setFrozenRows(1);

  const lastRow = sh.getLastRow();
  if (lastRow > 1) {
    const rows = sh.getRange(2, 1, lastRow - 1, PAYMENT_HEADERS.length).getValues();
    let changed = false;
    rows.forEach(r => {
      if (!String(r[11] || '').trim()) { r[11] = 'INCOMING'; changed = true; }
      if (!String(r[12] || '').trim()) {
        r[12] = String(r[6] || '').trim() ? 'DONATION' : 'PENDING';
        changed = true;
      }
    });
    if (changed) sh.getRange(2, 1, rows.length, PAYMENT_HEADERS.length).setValues(rows);
  }
}

function ensureSheet_(ss, name, headers) {
  let sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  if (sh.getMaxColumns() < headers.length) sh.insertColumnsAfter(sh.getMaxColumns(), headers.length - sh.getMaxColumns());
  if (sh.getLastRow() === 0) {
    sh.getRange(1, 1, 1, headers.length).setValues([headers]);
    sh.getRange(1, 1, 1, headers.length).setFontWeight('bold');
    sh.setFrozenRows(1);
  }
  return sh;
}

function clean_(value, max) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[\r\n\t]+/g, ' ').trim().slice(0, max);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
