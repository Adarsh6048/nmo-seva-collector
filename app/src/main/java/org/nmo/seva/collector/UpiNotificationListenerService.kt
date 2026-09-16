package org.nmo.seva.collector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.security.MessageDigest

class UpiNotificationListenerService : NotificationListenerService() {

    private val supportedPackages = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM"
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val sourceName = supportedPackages[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        val extras = n.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Some UPI apps (including PhonePe versions) spread one visible message across
        // title/text/subtext/expanded-text fields. Collect the useful visible fields so
        // the parser can reconstruct messages such as "XYZ sent Rs.1 to you".
        val extraParts = buildList<String> {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.let(::add)
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.let(::add)
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map { it.toString() }
                ?.forEach(::add)
            n.tickerText?.toString()?.let(::add)
        }.filter { it.isNotBlank() }.distinct()

        val extraText = extraParts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
        val parsed = NotificationParser.parse(title, text, extraText) ?: return

        val eventId = sha256("${sbn.packageName}|${sbn.key}|${sbn.postTime}|${parsed.direction}|${parsed.amount}")
        val donationStatus = if (parsed.direction == TransactionDirection.INCOMING) {
            DonationStatus.PENDING
        } else {
            DonationStatus.NOT_DONATION
        }

        val db = PaymentDbHelper(applicationContext)
        val inserted = db.insertIfNew(
            Payment(
                eventId = eventId,
                amount = parsed.amount,
                donorName = null,
                senderHint = parsed.senderHint,
                transactionRef = parsed.transactionRef,
                receivedAt = sbn.postTime,
                sourceApp = sourceName,
                direction = parsed.direction,
                donationStatus = donationStatus,
                // New transactions stay local until the collector marks an incoming one as Donation.
                synced = true,
                reconciled = false
            )
        )
        if (!inserted) return

        if (parsed.direction == TransactionDirection.INCOMING) {
            promptForClassification(eventId, parsed.amount, sourceName)
        }
    }

    private fun promptForClassification(eventId: String, amount: Double, source: String) {
        val intent = Intent(this, DonorEntryActivity::class.java).apply {
            putExtra("event_id", eventId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val formatted = if (amount % 1.0 == 0.0) "₹${amount.toLong()}" else "₹%.2f".format(amount)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medical)
            .setContentTitle("$formatted received")
            .setContentText("Tap to mark this as donation or personal payment • $source")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(eventId.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object { const val CHANNEL_ID = "nmo_payment_followup" }
}
