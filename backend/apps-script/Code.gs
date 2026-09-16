const SHEETS = {
  LEDGER: 'Ledger',
  PAYMENTS_LEGACY: 'Payments',
  COLLECTORS: 'Collectors',
  SETTINGS: 'Settings',
  EXPENSES: 'Expenses'
};

const LEDGER_HEADERS = [
  'Event ID','Transaction At','Server Updated At','Collector Code','Collector Name',
  'Amount','Direction','Donation Status','Expense Status','Donor Name','Expense Comment',
  'Counterparty Hint','UPI Ref','Source App','Reconciled'
];

function setupSheets() {
  const ss = SpreadsheetApp.getActive();
  const ledger = ensureSheet_(ss, SHEETS.LEDGER, LEDGER_HEADERS);
  ensureLedgerSchema_(ledger);
  ensureSheet_(ss, SHEETS.COLLECTORS, ['Collector Code','Collector Name','Token','Active']);
  ensureSheet_(ss, SHEETS.SETTINGS, ['Key','Value']);
  ensureSheet_(ss, SHEETS.EXPENSES, ['Expense ID','Date','Category','Vendor','Amount','Remarks','Added By']);
  migrateLegacyPayments_(ss, ledger);

  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  const data = settings.getDataRange().getValues();
  if (!data.some(r => r[0] === 'TARGET')) settings.appendRow(['TARGET', 400000]);
  if (!data.some(r => r[0] === 'CAMPAIGN')) settings.appendRow(['CAMPAIGN', 'Dr. B. R. Ambedkar Swasthya Sewa Yatra — 100 Health Camps, Kanpur']);
  SpreadsheetApp.flush();
  return 'Ledger ready. Existing campaign payment rows were migrated without deleting the old Payments sheet.';
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

    const sh = SpreadsheetApp.getActive().getSheetByName(SHEETS.LEDGER);
    ensureLedgerSchema_(sh);
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

      let expenseStatus = String(ev.expenseStatus || (direction === 'OUTGOING' ? 'PENDING' : 'NOT_APPLICABLE')).toUpperCase();
      if (!['NOT_APPLICABLE','PENDING','CAMPAIGN_EXPENSE','PERSONAL'].includes(expenseStatus)) {
        expenseStatus = direction === 'OUTGOING' ? 'PENDING' : 'NOT_APPLICABLE';
      }
      if (direction === 'INCOMING') expenseStatus = 'NOT_APPLICABLE';

      const row = [
        eventId,
        new Date(transactionAt),
        now,
        collector.code,
        collector.name,
        amount,
        direction,
        donationStatus,
        expenseStatus,
        clean_(ev.donorName, 120),
        clean_(ev.expenseNote, 240),
        clean_(ev.senderHint, 120),
        clean_(ev.transactionRef, 80),
        clean_(ev.sourceApp, 80),
        Boolean(ev.reconciled)
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
  const sh = ss.getSheetByName(SHEETS.LEDGER);
  ensureLedgerSchema_(sh);
  const rows = sh.getDataRange().getValues();
  const target = Number(getSetting_('TARGET') || 400000);
  const campaign = String(getSetting_('CAMPAIGN') || 'Dr. B. R. Ambedkar Swasthya Sewa Yatra — 100 Health Camps, Kanpur');

  let donationTotal = 0;
  let donationCount = 0;
  let reconciledDonations = 0;
  let campaignExpenseTotal = 0;
  let campaignExpenseCount = 0;
  let incomingTotal = 0;
  let outgoingTotal = 0;
  let pendingIncoming = 0;
  let pendingExpenses = 0;
  const byCollector = {};
  const ledger = [];

  for (let i = 1; i < rows.length; i++) {
    const r = rows[i];
    const amount = Number(r[5]) || 0;
    const direction = String(r[6] || 'INCOMING').toUpperCase();
    const donationStatus = String(r[7] || 'PENDING').toUpperCase();
    const expenseStatus = String(r[8] || (direction === 'OUTGOING' ? 'PENDING' : 'NOT_APPLICABLE')).toUpperCase();
    const code = String(r[3] || 'UNKNOWN');
    const name = String(r[4] || code);

    if (!byCollector[code]) {
      byCollector[code] = {
        code, name, transactionCount: 0,
        incomingAmount: 0, outgoingAmount: 0,
        donationAmount: 0, donationCount: 0,
        expenseAmount: 0, expenseCount: 0,
        pendingIncoming: 0, pendingExpenses: 0
      };
    }
    const c = byCollector[code];
    c.transactionCount++;

    if (direction === 'INCOMING') {
      incomingTotal += amount;
      c.incomingAmount += amount;
      if (donationStatus === 'DONATION') {
        donationTotal += amount;
        donationCount++;
        c.donationAmount += amount;
        c.donationCount++;
        if (r[14] === true) reconciledDonations += amount;
      } else if (donationStatus === 'PENDING') {
        pendingIncoming++;
        c.pendingIncoming++;
      }
    } else {
      outgoingTotal += amount;
      c.outgoingAmount += amount;
      if (expenseStatus === 'CAMPAIGN_EXPENSE') {
        campaignExpenseTotal += amount;
        campaignExpenseCount++;
        c.expenseAmount += amount;
        c.expenseCount++;
      } else if (expenseStatus === 'PENDING') {
        pendingExpenses++;
        c.pendingExpenses++;
      }
    }

    ledger.push({
      eventId: String(r[0] || ''),
      transactionAt: r[1] instanceof Date ? r[1].toISOString() : String(r[1]),
      collectorCode: code,
      collectorName: name,
      amount,
      direction,
      donationStatus,
      expenseStatus,
      donor: String(r[9] || ''),
      expenseNote: String(r[10] || ''),
      party: String(r[11] || ''),
      ref: String(r[12] || ''),
      source: String(r[13] || ''),
      reconciled: r[14] === true
    });
  }

  ledger.sort((a,b) => new Date(b.transactionAt) - new Date(a.transactionAt));
  const collectorReport = Object.values(byCollector).map(c => ({
    ...c,
    netCampaign: c.donationAmount - c.expenseAmount
  })).sort((a,b) => b.donationAmount - a.donationAmount);

  return {
    campaign,
    target,
    donationTotal,
    donationCount,
    reconciledDonations,
    campaignExpenseTotal,
    campaignExpenseCount,
    netCampaign: donationTotal - campaignExpenseTotal,
    incomingTotal,
    outgoingTotal,
    pendingIncoming,
    pendingExpenses,
    ledgerCount: Math.max(0, rows.length - 1),
    remaining: Math.max(0, target - donationTotal),
    byCollector: collectorReport,
    ledger: ledger.slice(0, 1000)
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

function ensureLedgerSchema_(sh) {
  if (!sh) throw new Error('Ledger sheet missing. Run setupSheets().');
  if (sh.getMaxColumns() < LEDGER_HEADERS.length) {
    sh.insertColumnsAfter(sh.getMaxColumns(), LEDGER_HEADERS.length - sh.getMaxColumns());
  }
  sh.getRange(1, 1, 1, LEDGER_HEADERS.length).setValues([LEDGER_HEADERS]);
  sh.getRange(1, 1, 1, LEDGER_HEADERS.length).setFontWeight('bold');
  sh.setFrozenRows(1);
}

function migrateLegacyPayments_(ss, ledger) {
  const old = ss.getSheetByName(SHEETS.PAYMENTS_LEGACY);
  if (!old || old.getLastRow() <= 1) return;

  const existingIds = ledger.getLastRow() > 1
    ? new Set(ledger.getRange(2, 1, ledger.getLastRow() - 1, 1).getValues().flat().map(String))
    : new Set();
  const oldRows = old.getDataRange().getValues();
  const migrated = [];

  for (let i = 1; i < oldRows.length; i++) {
    const r = oldRows[i];
    const eventId = String(r[0] || '').trim();
    if (!eventId || existingIds.has(eventId)) continue;
    const direction = String(r[11] || 'INCOMING').toUpperCase();
    const donationStatus = String(r[12] || (String(r[6] || '').trim() ? 'DONATION' : 'PENDING')).toUpperCase();
    migrated.push([
      eventId,
      r[1] || new Date(),
      r[2] || new Date(),
      r[3] || '',
      r[4] || '',
      Number(r[5]) || 0,
      direction === 'OUTGOING' ? 'OUTGOING' : 'INCOMING',
      direction === 'OUTGOING' ? 'NOT_DONATION' : donationStatus,
      direction === 'OUTGOING' ? 'PENDING' : 'NOT_APPLICABLE',
      r[6] || '',
      '',
      r[7] || '',
      r[8] || '',
      r[9] || '',
      r[10] === true
    ]);
    existingIds.add(eventId);
  }

  if (migrated.length) {
    ledger.getRange(ledger.getLastRow() + 1, 1, migrated.length, LEDGER_HEADERS.length).setValues(migrated);
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
