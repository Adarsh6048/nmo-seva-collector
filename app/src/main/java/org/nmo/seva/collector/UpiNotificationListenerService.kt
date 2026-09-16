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
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val parsed = NotificationParser.parse(title, text, bigText) ?: return

        val eventId = sha256("${sbn.packageName}|${sbn.key}|${sbn.postTime}|${parsed.amount}")
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
                synced = false,
                reconciled = false
            )
        )
        if (!inserted) return

        SyncScheduler.enqueue(applicationContext)
        promptForDonorName(eventId, parsed.amount, sourceName)
    }

    private fun promptForDonorName(eventId: String, amount: Double, source: String) {
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
            .setContentText("Tap to add the donor name • $source")
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
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object { const val CHANNEL_ID = "nmo_payment_followup" }
}
