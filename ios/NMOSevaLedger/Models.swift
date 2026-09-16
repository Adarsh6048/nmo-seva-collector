import Foundation

enum TransactionDirection: String, Codable, CaseIterable { case INCOMING, OUTGOING }
enum DonationStatus: String, Codable { case PENDING, DONATION, NOT_DONATION }
enum ExpenseStatus: String, Codable { case NOT_APPLICABLE, PENDING, CAMPAIGN_EXPENSE, PERSONAL }

struct LedgerEvent: Identifiable, Codable, Equatable {
    var id: String { eventId }
    let eventId: String
    var amount: Double
    var donorName: String?
    var expenseNote: String?
    var senderHint: String?
    var transactionRef: String?
    var receivedAt: Int64
    var sourceApp: String
    var direction: TransactionDirection
    var donationStatus: DonationStatus
    var expenseStatus: ExpenseStatus
    var reconciled: Bool
    var synced: Bool
}

extension LedgerEvent {
    static func manual(amount: Double, direction: TransactionDirection, party: String?, reference: String?) -> LedgerEvent {
        LedgerEvent(
            eventId: UUID().uuidString,
            amount: amount,
            donorName: nil,
            expenseNote: nil,
            senderHint: party,
            transactionRef: reference,
            receivedAt: Int64(Date().timeIntervalSince1970 * 1000),
            sourceApp: "iOS Manual",
            direction: direction,
            donationStatus: direction == .INCOMING ? .PENDING : .NOT_DONATION,
            expenseStatus: direction == .OUTGOING ? .PENDING : .NOT_APPLICABLE,
            reconciled: false,
            synced: false
        )
    }
}
