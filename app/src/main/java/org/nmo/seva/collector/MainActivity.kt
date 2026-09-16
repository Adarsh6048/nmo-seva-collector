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
        val (total, count) = db.totals()
        val pending = db.getPendingNames(100).size
        binding.totalText.text = rupees(total)
        binding.statsText.text = "$count payments • $pending donor names pending"

        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.accessWarning.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.accessWarning.text = "Notification access is OFF. Incoming UPI notifications cannot be detected until you enable it."
        binding.notificationAccessButton.text = if (enabled) "Notification access enabled" else "Enable notification access"

        renderPayments(binding.pendingContainer, db.getPendingNames(10), clickable = true)
        renderPayments(binding.recentContainer, db.getRecent(15), clickable = true)
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
            row.findViewById<TextView>(R.id.rowAmount).text = rupees(p.amount)
            row.findViewById<TextView>(R.id.rowName).text = p.donorName?.takeIf { it.isNotBlank() } ?: "Tap to add donor name"
            row.findViewById<TextView>(R.id.rowMeta).text = "${p.sourceApp} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.receivedAt))}${if (p.synced) " • synced" else " • sync pending"}"
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
