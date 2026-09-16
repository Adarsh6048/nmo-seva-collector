package org.nmo.seva.collector

enum class TransactionDirection { INCOMING, OUTGOING }
enum class DonationStatus { PENDING, DONATION, NOT_DONATION }

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
    val synced: Boolean,
    val reconciled: Boolean
)
