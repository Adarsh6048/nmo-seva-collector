package org.nmo.seva.collector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import org.nmo.seva.collector.databinding.ActivityMainBinding
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var db: PaymentDbHelper
    private lateinit var prefs: AppPreferences

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = PaymentDbHelper(this)
        prefs = AppPreferences(this)

        if (!prefs.isConfigured()) startActivity(Intent(this, SetupActivity::class.java))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.syncButton.setOnClickListener { SyncScheduler.enqueue(this) }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        binding.collectorText.text = if (prefs.collectorCode.isBlank()) "Not configured" else "${prefs.collectorName.ifBlank { "Collector" }} • ${prefs.collectorCode}"
        val donationTotal = db.donationTotal()
        val count = db.transactionCount()
        val incoming = db.incomingCount()
        val pending = db.getPendingClassification(100).size
        binding.totalText.text = rupees(donationTotal)
        binding.statsText.text = "$count transactions tracked • $incoming incoming • $pending awaiting donation review"

        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.accessWarning.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.accessWarning.text = "Notification access is OFF. UPI transaction notifications cannot be detected until you enable it."
        binding.notificationAccessButton.text = if (enabled) "Notification access enabled" else "Enable notification access"

        renderPayments(binding.pendingContainer, db.getPendingClassification(10), clickable = true)
        renderPayments(binding.recentContainer, db.getRecent(20), clickable = true)
    }

    private fun renderPayments(container: android.widget.LinearLayout, items: List<Payment>, clickable: Boolean) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nothing here"
                setTextColor(getColor(R.color.text_secondary))
                setPadding(8, 16, 8, 16)
            }
            container.addView(empty)
            return
        }
        items.forEach { p ->
            val row = LayoutInflater.from(this).inflate(R.layout.payment_row, container, false)
            row.findViewById<TextView>(R.id.rowAmount).text = if (p.direction == TransactionDirection.INCOMING) "+${rupees(p.amount)}" else "−${rupees(p.amount)}"
            row.findViewById<TextView>(R.id.rowName).text = when (p.donationStatus) {
                DonationStatus.DONATION -> p.donorName?.takeIf { it.isNotBlank() } ?: "Donation"
                DonationStatus.NOT_DONATION -> if (p.direction == TransactionDirection.OUTGOING) "Outgoing transaction" else "Not a donation"
                DonationStatus.PENDING -> "Tap to classify donation"
            }
            val directionLabel = if (p.direction == TransactionDirection.INCOMING) "incoming" else "outgoing"
            val donationLabel = when (p.donationStatus) {
                DonationStatus.DONATION -> "donation"
                DonationStatus.NOT_DONATION -> "not donation"
                DonationStatus.PENDING -> "review pending"
            }
            val storageLabel = if (p.donationStatus == DonationStatus.DONATION) {
                if (p.synced) "campaign synced" else "campaign sync pending"
            } else {
                "local only"
            }
            row.findViewById<TextView>(R.id.rowMeta).text = "$directionLabel • $donationLabel • ${p.sourceApp} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.receivedAt))} • $storageLabel"
            if (clickable) row.setOnClickListener {
                startActivity(Intent(this, DonorEntryActivity::class.java).putExtra("event_id", p.eventId))
            }
            container.addView(row)
        }
    }

    private fun rupees(value: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        return format.format(value)
    }
}
