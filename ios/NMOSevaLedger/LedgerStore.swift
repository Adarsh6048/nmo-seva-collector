import Foundation
import Combine

@MainActor
final class LedgerStore: ObservableObject {
    @Published private(set) var events: [LedgerEvent] = []
    private let fileURL: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("nmo-ios-ledger.json")
        load()
    }

    var donationTotal: Double {
        events.filter { $0.direction == .INCOMING && $0.donationStatus == .DONATION }.reduce(0) { $0 + $1.amount }
    }

    var expenseTotal: Double {
        events.filter { $0.direction == .OUTGOING && $0.expenseStatus == .CAMPAIGN_EXPENSE }.reduce(0) { $0 + $1.amount }
    }

    var pendingCount: Int {
        events.filter {
            ($0.direction == .INCOMING && $0.donationStatus == .PENDING) ||
            ($0.direction == .OUTGOING && $0.expenseStatus == .PENDING)
        }.count
    }

    func add(_ event: LedgerEvent) {
        guard !events.contains(where: { $0.eventId == event.eventId }) else { return }
        events.insert(event, at: 0)
        persist()
    }

    func update(_ event: LedgerEvent) {
        guard let index = events.firstIndex(where: { $0.eventId == event.eventId }) else { return }
        events[index] = event
        persist()
    }

    func sync(settings: SettingsStore) async throws {
        let pending = events.filter { !$0.synced }
        try await APIClient.upload(events: pending, settings: settings)
        let ids = Set(pending.map(\.eventId))
        events = events.map { event in
            var copy = event
            if ids.contains(copy.eventId) { copy.synced = true }
            return copy
        }
        persist()
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([LedgerEvent].self, from: data) else { return }
        events = decoded.sorted { $0.receivedAt > $1.receivedAt }
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(events) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}
