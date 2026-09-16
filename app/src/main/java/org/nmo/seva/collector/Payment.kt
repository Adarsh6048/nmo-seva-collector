package org.nmo.seva.collector

data class Payment(
    val eventId: String,
    val amount: Double,
    val donorName: String?,
    val senderHint: String?,
    val transactionRef: String?,
    val receivedAt: Long,
    val sourceApp: String,
    val synced: Boolean,
    val reconciled: Boolean
)
