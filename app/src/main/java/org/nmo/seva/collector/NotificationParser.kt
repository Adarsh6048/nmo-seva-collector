package org.nmo.seva.collector

import java.util.Locale

data class ParsedTransaction(
    val amount: Double,
    val direction: TransactionDirection,
    val senderHint: String? = null,
    val transactionRef: String? = null
)

object NotificationParser {
    private val incomingSignals = listOf(
        "received", "credited", "sent you", "you received", "payment received",
        "money received", "has paid you", "paid you", "refund received"
    )
    private val outgoingSignals = listOf(
        "you paid", "you sent", "debited", "paid to", "sent to",
        "payment successful to", "payment successful", "payment completed"
    )
    private val ignoredSignals = listOf(
        "scratch card", "reward unlocked", "offer unlocked", "payment failed",
        "transaction failed", "payment declined", "transaction declined"
    )

    private val amountRegex = Regex(
        "(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    // PhonePe commonly shows incoming notifications like: "Deep sent Rs 10".
    // The title may be prepended when Android notification fields are combined.
    private val personSentAmountRegex = Regex(
        "(?:^|\\|)\\s*([^|•,\\n]+?)\\s+sent\\s+(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val refRegexes = listOf(
        Regex("(?:upi\\s*(?:ref(?:erence)?|txn|transaction)\\s*(?:no\\.?|id)?|utr)\\s*[:#-]?\\s*([A-Za-z0-9-]{6,32})", RegexOption.IGNORE_CASE),
        Regex("\\b([0-9]{12})\\b")
    )

    private val incomingPartyPatterns = listOf(
        Regex("received\\s+(?:₹|rs\\.?|inr)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s+from\\s+([^•|,\\n]+)", RegexOption.IGNORE_CASE),
        Regex("([^•|,\\n]+?)\\s+sent\\s+you\\s+(?:₹|rs\\.?|inr)", RegexOption.IGNORE_CASE),
        Regex("(?:from|by)\\s+([^•|,\\n]+)", RegexOption.IGNORE_CASE)
    )

    private val outgoingPartyPatterns = listOf(
        Regex("you\\s+(?:paid|sent)\\s+(?:₹|rs\\.?|inr)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s+(?:to\\s+)?([^•|,\\n]+)", RegexOption.IGNORE_CASE),
        Regex("(?:paid|sent)\\s+to\\s+([^•|,\\n]+)", RegexOption.IGNORE_CASE),
        Regex("to\\s+([^•|,\\n]+)", RegexOption.IGNORE_CASE)
    )

    fun parse(title: String?, text: String?, bigText: String? = null): ParsedTransaction? {
        val combined = listOfNotNull(title, text, bigText)
            .joinToString(" | ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (combined.isBlank()) return null

        val lower = combined.lowercase(Locale.ENGLISH)
        if (ignoredSignals.any { lower.contains(it) }) return null

        val personSentMatch = personSentAmountRegex.find(combined)
        val personSentName = personSentMatch?.groupValues?.getOrNull(1)?.trim()
        val personSentIncoming = personSentMatch != null && !personSentName.equals("you", ignoreCase = true)

        val hasOutgoing = outgoingSignals.any { lower.contains(it) } || personSentName.equals("you", ignoreCase = true)
        val hasIncoming = incomingSignals.any { lower.contains(it) } || personSentIncoming

        val direction = when {
            hasOutgoing -> TransactionDirection.OUTGOING
            hasIncoming -> TransactionDirection.INCOMING
            else -> return null
        }

        val amount = if (personSentMatch != null) {
            personSentMatch.groupValues.getOrNull(2)?.replace(",", "")?.toDoubleOrNull()
        } else {
            amountRegex.find(combined)?.groupValues?.getOrNull(1)
                ?.replace(",", "")
                ?.toDoubleOrNull()
        } ?: return null

        if (amount <= 0.0 || amount > 1_000_000.0) return null

        val ref = refRegexes.asSequence()
            .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1) }
            .firstOrNull()

        val partyHint = when {
            personSentIncoming -> personSentName?.take(80)
            direction == TransactionDirection.INCOMING -> incomingPartyPatterns.asSequence()
                .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1)?.trim() }
                .map { it.take(80) }
                .firstOrNull()
            else -> outgoingPartyPatterns.asSequence()
                .mapNotNull { it.find(combined)?.groupValues?.getOrNull(1)?.trim() }
                .map { it.take(80) }
                .firstOrNull()
        }

        return ParsedTransaction(
            amount = amount,
            direction = direction,
            senderHint = partyHint,
            transactionRef = ref
        )
    }
}
