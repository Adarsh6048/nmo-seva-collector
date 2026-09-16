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

    // PhonePe may render one visual sentence using multiple Android notification fields,
    // for example title="XYZ" and text="sent Rs.1 to you".
    private val personSentAmountRegex = Regex(
        "^\\s*([^|•,\\n]+?)\\s+sent\\s+(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:\\s+to\\s+you\\b)?",
        RegexOption.IGNORE_CASE
    )

    // WhatsApp shares the same package for chats and payments, so parsing must be stricter
    // to avoid treating ordinary chat messages that mention money as transactions.
    private val whatsappIncomingRegexes = listOf(
        Regex("\\byou\\s+received\\s+(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+from\\s+([^|•,\\n]+)", RegexOption.IGNORE_CASE),
        Regex("\\bpayment\\s+(?:of\\s+)?(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+(?:has\\s+been\\s+)?received(?:\\s+from\\s+([^|•,\\n]+))?", RegexOption.IGNORE_CASE),
        Regex("\\b(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+(?:has\\s+been\\s+)?received\\b", RegexOption.IGNORE_CASE)
    )
    private val whatsappOutgoingRegexes = listOf(
        Regex("\\byou\\s+(?:sent|paid)\\s+(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+(?:to\\s+)?([^|•,\\n]+)", RegexOption.IGNORE_CASE),
        Regex("\\bpayment\\s+(?:of\\s+)?(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+(?:was\\s+)?(?:sent|paid)(?:\\s+to\\s+([^|•,\\n]+))?", RegexOption.IGNORE_CASE)
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

    fun parse(
        title: String?,
        text: String?,
        bigText: String? = null,
        sourcePackage: String? = null
    ): ParsedTransaction? {
        val parts = listOfNotNull(title, text, bigText)
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (parts.isEmpty()) return null

        val candidates = buildList {
            addAll(parts)
            if (parts.size >= 2) {
                for (i in 0 until parts.lastIndex) add("${parts[i]} ${parts[i + 1]}")
            }
            add(parts.joinToString(" "))
        }.map { normalize(it) }.distinct()

        val combined = parts.joinToString(" | ")
        val lower = combined.lowercase(Locale.ENGLISH)
        if (ignoredSignals.any { lower.contains(it) }) return null

        if (sourcePackage == "com.whatsapp" || sourcePackage == "com.whatsapp.w4b") {
            return parseWhatsApp(candidates)
        }

        val personSentMatch = candidates.asSequence()
            .mapNotNull { candidate -> personSentAmountRegex.find(candidate) }
            .firstOrNull()
        val personSentName = personSentMatch?.groupValues?.getOrNull(1)?.trim()
        val personSentIncoming = personSentMatch != null &&
            !personSentName.equals("you", ignoreCase = true)

        val candidateLower = candidates.map { it.lowercase(Locale.ENGLISH) }
        val hasOutgoing = candidateLower.any { candidate -> outgoingSignals.any { candidate.contains(it) } } ||
            personSentName.equals("you", ignoreCase = true)
        val hasIncoming = candidateLower.any { candidate -> incomingSignals.any { candidate.contains(it) } } ||
            personSentIncoming

        val direction = when {
            hasOutgoing -> TransactionDirection.OUTGOING
            hasIncoming -> TransactionDirection.INCOMING
            else -> return null
        }

        val amount = if (personSentMatch != null) {
            personSentMatch.groupValues.getOrNull(2)?.replace(",", "")?.toDoubleOrNull()
        } else {
            candidates.asSequence()
                .mapNotNull { amountRegex.find(it)?.groupValues?.getOrNull(1) }
                .mapNotNull { it.replace(",", "").toDoubleOrNull() }
                .firstOrNull()
        } ?: return null

        if (amount <= 0.0 || amount > 1_000_000.0) return null

        val ref = refRegexes.asSequence()
            .flatMap { regex -> candidates.asSequence().mapNotNull { regex.find(it)?.groupValues?.getOrNull(1) } }
            .firstOrNull()

        val partyHint = when {
            personSentIncoming -> personSentName?.take(80)
            direction == TransactionDirection.INCOMING -> incomingPartyPatterns.asSequence()
                .flatMap { regex -> candidates.asSequence().mapNotNull { regex.find(it)?.groupValues?.getOrNull(1)?.trim() } }
                .map { it.take(80) }
                .firstOrNull()
            else -> outgoingPartyPatterns.asSequence()
                .flatMap { regex -> candidates.asSequence().mapNotNull { regex.find(it)?.groupValues?.getOrNull(1)?.trim() } }
                .map { it.take(80) }
                .firstOrNull()
        }

        return ParsedTransaction(amount, direction, partyHint, ref)
    }

    private fun parseWhatsApp(candidates: List<String>): ParsedTransaction? {
        val incoming = whatsappIncomingRegexes.asSequence()
            .flatMap { regex -> candidates.asSequence().mapNotNull { c -> regex.find(c) } }
            .firstOrNull()
        if (incoming != null) {
            val amount = incoming.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
            if (amount <= 0.0 || amount > 1_000_000.0) return null
            val party = incoming.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }?.take(80)
            val ref = findRef(candidates)
            return ParsedTransaction(amount, TransactionDirection.INCOMING, party, ref)
        }

        val outgoing = whatsappOutgoingRegexes.asSequence()
            .flatMap { regex -> candidates.asSequence().mapNotNull { c -> regex.find(c) } }
            .firstOrNull()
        if (outgoing != null) {
            val amount = outgoing.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
            if (amount <= 0.0 || amount > 1_000_000.0) return null
            val party = outgoing.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }?.take(80)
            val ref = findRef(candidates)
            return ParsedTransaction(amount, TransactionDirection.OUTGOING, party, ref)
        }

        return null
    }

    private fun findRef(candidates: List<String>): String? = refRegexes.asSequence()
        .flatMap { regex -> candidates.asSequence().mapNotNull { regex.find(it)?.groupValues?.getOrNull(1) } }
        .firstOrNull()

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
}
