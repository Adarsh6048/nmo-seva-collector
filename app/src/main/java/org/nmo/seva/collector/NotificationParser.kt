package org.nmo.seva.collector

import java.util.Locale

data class ParsedPayment(
    val amount: Double,
    val senderHint: String? = null,
    val transactionRef: String? = null
)

object NotificationParser {
    private val incomingSignals = listOf(
        "received", "credited", "sent you", "you received", "payment received",
        "money received", "has paid you", "paid you"
    )
    private val outgoingSignals = listOf(
        "you paid", "you sent", "debited", "paid to", "sent to", "payment successful to"
    )
    private val excludedSignals = listOf(
        "cashback", "cash back", "reward", "scratch card", "refund", "reversal"
    )

    private val amountRegex = Regex(
        "(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val refRegexes = listOf(
        Regex("(?:upi\\s*(?:ref(?:erence)?|txn|transaction)\\s*(?:no\\.?|id)?|utr)\\s*[:#-]?\\s*([A-Za-z0-9-]{6,32})", RegexOption.IGNORE_CASE),
        Regex("\\b([0-9]{12})\\b")
    )
    private val senderPatterns = listOf(
        Regex("received\\s+(?:₹|rs\\.?|inr)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s+from\\s+([^•|,\\n]+)", RegexOption.IGNORE_CASE),
        Regex("([^•|,\\n]+?)\\s+sent\\s+you\\s+(?:₹|rs\\.?|inr)", RegexOption.IGNORE_CASE),
        Regex("(?:from|by)\\s+([^•|,\\n]+)", RegexOption.IGNORE_CASE)
    )

    fun parse(title: String?, text: String?, bigText: String? = null): ParsedPayment? {
        val combined = listOfNotNull(title, text, bigText)
            .joinToString(" | ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (combined.isBlank()) return null

        val lower = combined.lowercase(Locale.ENGLISH)
        val hasIncoming = incomingSignals.any { lower.contains(it) }
        val hasOutgoing = outgoingSignals.any { lower.contains(it) }
        val isExcluded = excludedSignals.any { lower.contains(it) }
        if (!hasIncoming || hasOutgoing || isExcluded) return null

        val amount = amountRegex.find(combined)?.groupValues?.getOrNull(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null
        if (amount <= 0.0 || amount > 1_000_000.0) return null

        val ref = refRegexes.asSequence()
            .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1) }
            .firstOrNull()

        val sender = senderPatterns.asSequence()
            .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1)?.trim() }
            .map { it.take(80) }
            .firstOrNull()

        return ParsedPayment(amount = amount, senderHint = sender, transactionRef = ref)
    }
}
