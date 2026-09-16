import Foundation

@MainActor
enum SharedImportService {
    private static let groupId = "group.org.nmo.seva.ledger"

    static func consume(into ledger: LedgerStore) {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId),
              let files = try? FileManager.default.contentsOfDirectory(at: container, includingPropertiesForKeys: nil) else { return }

        for file in files where file.lastPathComponent.hasPrefix("pending-import-") && file.pathExtension == "json" {
            defer { try? FileManager.default.removeItem(at: file) }
            guard let data = try? Data(contentsOf: file),
                  let raw = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
                  let eventId = raw["eventId"] as? String,
                  let amountNumber = raw["amount"] as? NSNumber,
                  let timeNumber = raw["receivedAt"] as? NSNumber,
                  let source = raw["sourceApp"] as? String,
                  let directionRaw = raw["direction"] as? String,
                  let direction = TransactionDirection(rawValue: directionRaw),
                  let donationRaw = raw["donationStatus"] as? String,
                  let donationStatus = DonationStatus(rawValue: donationRaw),
                  let expenseRaw = raw["expenseStatus"] as? String,
                  let expenseStatus = ExpenseStatus(rawValue: expenseRaw) else { continue }

            ledger.add(LedgerEvent(
                eventId: eventId,
                amount: amountNumber.doubleValue,
                donorName: nil,
                expenseNote: nil,
                senderHint: nil,
                transactionRef: nil,
                receivedAt: timeNumber.int64Value,
                sourceApp: source,
                direction: direction,
                donationStatus: donationStatus,
                expenseStatus: expenseStatus,
                reconciled: false,
                synced: false
            ))
        }
    }
}
