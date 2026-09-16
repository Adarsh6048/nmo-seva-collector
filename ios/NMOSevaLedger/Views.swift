import SwiftUI

private let nmoGreen = Color(red: 0.09, green: 0.72, blue: 0.47)
private let card = Color(red: 0.08, green: 0.10, blue: 0.12)

struct SetupView: View {
    @EnvironmentObject private var settings: SettingsStore
    @State private var showPrivacy = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 14) {
                        NMOLogoView(size: 72)
                        VStack(alignment: .leading) {
                            Text("NMO Seva Ledger").font(.title2.bold())
                            Text("iOS collector setup").foregroundStyle(.secondary)
                        }
                    }
                }

                Section("Collector identity") {
                    TextField("Collector name", text: $settings.collectorName)
                    TextField("Collector code, e.g. NMO02", text: $settings.collectorCode)
                        .textInputAutocapitalization(.characters)
                    SecureField("Collector token", text: $settings.collectorToken)
                    TextField("Apps Script /exec URL", text: $settings.backendURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                Section("iOS transaction capture") {
                    Text("iOS does not allow this app to read notifications from PhonePe, Google Pay, Paytm, BHIM or WhatsApp. Use Add transaction, or share supported payment confirmation text into NMO Seva Ledger when available.")
                        .foregroundStyle(.secondary)
                    Toggle("I understand the iOS capture limitation", isOn: $settings.ledgerConsent)
                }

                Section {
                    Button("Privacy details") { showPrivacy = true }
                    if settings.isConfigured {
                        Label("Setup complete", systemImage: "checkmark.seal.fill").foregroundStyle(nmoGreen)
                    }
                }
            }
            .navigationTitle("Collector setup")
            .preferredColorScheme(.dark)
            .sheet(isPresented: $showPrivacy) {
                NavigationStack {
                    ScrollView {
                        Text("NMO Seva Ledger stores transaction records you enter or explicitly share to it. It sends parsed ledger fields to the campaign backend using your collector code and token. It does not receive another app's notifications, UPI PIN, bank password, SMS or contacts.")
                            .padding()
                    }
                    .navigationTitle("Privacy")
                    .toolbar { Button("Done") { showPrivacy = false } }
                }
            }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var ledger: LedgerStore
    @State private var showingAdd = false
    @State private var showingSetup = false
    @State private var selected: LedgerEvent?
    @State private var syncMessage = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    HStack(spacing: 12) {
                        NMOLogoView(size: 64)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("NMO Seva Ledger").font(.title2.bold())
                            Text("\(settings.collectorName.isEmpty ? "Collector" : settings.collectorName) • \(settings.collectorCode)")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer()
                    }

                    HStack(spacing: 10) {
                        MetricCard(title: "Donations", value: money(ledger.donationTotal), accent: nmoGreen)
                        MetricCard(title: "Expenses", value: money(ledger.expenseTotal), accent: .orange)
                    }
                    HStack(spacing: 10) {
                        MetricCard(title: "Net", value: money(ledger.donationTotal - ledger.expenseTotal), accent: .cyan)
                        MetricCard(title: "Pending", value: "\(ledger.pendingCount)", accent: .yellow)
                    }

                    Button {
                        showingAdd = true
                    } label: {
                        Label("Add transaction", systemImage: "plus.circle.fill")
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                    }
                    .buttonStyle(.borderedProminent).tint(nmoGreen)

                    HStack {
                        Button("Sync ledger") {
                            Task {
                                do { try await ledger.sync(settings: settings); syncMessage = "Synced" }
                                catch { syncMessage = "Sync failed" }
                            }
                        }
                        .buttonStyle(.bordered)
                        Button("Collector settings") { showingSetup = true }.buttonStyle(.bordered)
                        Spacer()
                        Text(syncMessage).font(.caption).foregroundStyle(.secondary)
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Recent transactions").font(.headline)
                        if ledger.events.isEmpty {
                            Text("No transactions yet").foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            ForEach(ledger.events) { event in
                                TransactionRow(event: event)
                                    .onTapGesture { selected = event }
                            }
                        }
                    }
                    .padding().background(card, in: RoundedRectangle(cornerRadius: 20))
                }
                .padding()
            }
            .background(Color.black)
            .sheet(isPresented: $showingAdd) { AddTransactionView() }
            .sheet(isPresented: $showingSetup) { SetupView() }
            .sheet(item: $selected) { event in ReviewTransactionView(event: event) }
        }
    }

    private func money(_ value: Double) -> String {
        value.formatted(.currency(code: "INR").precision(.fractionLength(0...2)))
    }
}

struct MetricCard: View {
    let title: String
    let value: String
    let accent: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.title3.bold()).foregroundStyle(accent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding().background(card, in: RoundedRectangle(cornerRadius: 18))
    }
}

struct TransactionRow: View {
    let event: LedgerEvent
    var body: some View {
        HStack {
            Circle().fill(event.direction == .INCOMING ? nmoGreen : Color.orange).frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 3) {
                Text(event.direction == .INCOMING ? incomingLabel : outgoingLabel).font(.subheadline.bold())
                Text("\(event.sourceApp) • \(Date(timeIntervalSince1970: Double(event.receivedAt)/1000).formatted(date: .abbreviated, time: .shortened))")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text((event.direction == .INCOMING ? "+" : "−") + event.amount.formatted(.currency(code: "INR").precision(.fractionLength(0...2))))
                .bold().foregroundStyle(event.direction == .INCOMING ? nmoGreen : .orange)
        }
        .padding(.vertical, 5)
    }
    private var incomingLabel: String {
        switch event.donationStatus {
        case .DONATION: return event.donorName?.isEmpty == false ? event.donorName! : "Donation"
        case .NOT_DONATION: return "Personal incoming"
        case .PENDING: return "Review donation"
        }
    }
    private var outgoingLabel: String {
        switch event.expenseStatus {
        case .CAMPAIGN_EXPENSE: return event.expenseNote?.isEmpty == false ? event.expenseNote! : "Campaign expense"
        case .PERSONAL: return "Personal outgoing"
        case .PENDING: return "Review expense"
        case .NOT_APPLICABLE: return "Outgoing"
        }
    }
}

struct AddTransactionView: View {
    @EnvironmentObject private var ledger: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""
    @State private var direction: TransactionDirection = .INCOMING
    @State private var party = ""
    @State private var reference = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Transaction") {
                    Picker("Direction", selection: $direction) {
                        Text("Incoming").tag(TransactionDirection.INCOMING)
                        Text("Outgoing").tag(TransactionDirection.OUTGOING)
                    }.pickerStyle(.segmented)
                    TextField("Amount", text: $amount).keyboardType(.decimalPad)
                    TextField("Counterparty / sender", text: $party)
                    TextField("Reference / UTR (optional)", text: $reference)
                }
                Section {
                    Button("Save and review") {
                        guard let value = Double(amount.replacingOccurrences(of: ",", with: "")), value > 0 else { return }
                        ledger.add(.manual(amount: value, direction: direction, party: party.nilIfBlank, reference: reference.nilIfBlank))
                        dismiss()
                    }
                    .disabled(Double(amount.replacingOccurrences(of: ",", with: "")) == nil)
                }
            }
            .navigationTitle("Add transaction")
            .toolbar { Button("Cancel") { dismiss() } }
        }
    }
}

struct ReviewTransactionView: View {
    @EnvironmentObject private var ledger: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @State private var draft: LedgerEvent
    @State private var donor = ""
    @State private var expense = ""

    init(event: LedgerEvent) {
        _draft = State(initialValue: event)
        _donor = State(initialValue: event.donorName ?? "")
        _expense = State(initialValue: event.expenseNote ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(draft.amount.formatted(.currency(code: "INR"))).font(.largeTitle.bold())
                    Text(draft.direction == .INCOMING ? "Incoming transaction" : "Outgoing transaction").foregroundStyle(.secondary)
                }
                if draft.direction == .INCOMING {
                    Section("Classify incoming") {
                        TextField("Donor name or Anonymous", text: $donor)
                        Button("Mark as donation") {
                            draft.donationStatus = .DONATION
                            draft.donorName = donor.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Anonymous" : donor.trimmingCharacters(in: .whitespacesAndNewlines)
                            draft.synced = false; ledger.update(draft); dismiss()
                        }
                        Button("Personal / not donation", role: .destructive) {
                            draft.donationStatus = .NOT_DONATION; draft.donorName = nil; draft.synced = false; ledger.update(draft); dismiss()
                        }
                    }
                } else {
                    Section("Classify outgoing") {
                        TextField("Expense comment", text: $expense)
                        Button("Mark as campaign expense") {
                            let note = expense.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !note.isEmpty else { return }
                            draft.expenseStatus = .CAMPAIGN_EXPENSE; draft.expenseNote = note; draft.synced = false; ledger.update(draft); dismiss()
                        }
                        Button("Personal / not campaign expense", role: .destructive) {
                            draft.expenseStatus = .PERSONAL; draft.expenseNote = nil; draft.synced = false; ledger.update(draft); dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Review transaction")
            .toolbar { Button("Later") { dismiss() } }
        }
    }
}

struct NMOLogoView: View {
    let size: CGFloat
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.22).fill(Color.white)
            VStack(spacing: -2) {
                Text("NMO").font(.system(size: size * 0.27, weight: .black, design: .rounded)).foregroundStyle(.red)
                Image(systemName: "cross.case.fill").font(.system(size: size * 0.28)).foregroundStyle(nmoGreen)
            }
        }
        .frame(width: size, height: size)
        .overlay(RoundedRectangle(cornerRadius: size * 0.22).stroke(Color.white.opacity(0.18)))
    }
}

private extension String {
    var nilIfBlank: String? {
        let v = trimmingCharacters(in: .whitespacesAndNewlines)
        return v.isEmpty ? nil : v
    }
}
