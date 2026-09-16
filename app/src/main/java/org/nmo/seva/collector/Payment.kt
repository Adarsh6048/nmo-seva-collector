package org.nmo.seva.collector

enum class TransactionDirection { INCOMING, OUTGOING }
enum class DonationStatus { PENDING, DONATION, NOT_DONATION }
enum class ExpenseStatus { NOT_APPLICABLE, PENDING, CAMPAIGN_EXPENSE, PERSONAL }

data class Payment(
    val eventId: String,
    val amount: Double,
    val donorName: String?,
    val senderHint: String?,
    val transactionRef: String?,
    val receivedAt: Long,
    val sourceApp: String,
    val direction: TransactionDirection,
    val donationStatus: DonationStatus,
    val expenseStatus: ExpenseStatus,
    val expenseNote: String?,
    val synced: Boolean,
    val reconciled: Boolean
)
