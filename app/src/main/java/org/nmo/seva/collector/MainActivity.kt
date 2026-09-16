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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    private val supportedPaymentApps = linkedMapOf(
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.whatsapp" to "WhatsApp Pay",
        "com.whatsapp.w4b" to "WhatsApp Business Pay"
    )

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = PaymentDbHelper(this)
        prefs = AppPreferences(this)
        LogoAsset.applyTo(binding.logoImage)

        if (!prefs.isConfigured()) startActivity(Intent(this, SetupActivity::class.java))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.paymentNotificationsButton.setOnClickListener { showPaymentAppNotificationSettings() }
        binding.syncButton.setOnClickListener {
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Ledger sync queued", Toast.LENGTH_SHORT).show()
        }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        binding.collectorText.text = if (prefs.collectorCode.isBlank()) {
            "Collector not configured"
        } else {
            "${prefs.collectorName.ifBlank { "Collector" }} • ${prefs.collectorCode}"
        }

        val donationTotal = db.donationTotal()
        val expenseTotal = db.campaignExpenseTotal()
        val net = donationTotal - expenseTotal
        val count = db.transactionCount()
        val pending = db.getPendingReview(100).size

        binding.totalText.text = rupees(donationTotal)
        binding.expenseTotalText.text = rupees(expenseTotal)
        binding.netText.text = rupees(net)
        binding.statsText.text = "$count transactions tracked • $pending awaiting review"

        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.accessWarning.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.accessWarning.text = "Transaction detection is OFF. Enable Notification Access, then verify notifications are enabled for the payment apps you use."
        binding.notificationAccessButton.text = if (enabled) "Transaction detection enabled" else "Enable transaction detection"

        renderPayments(binding.pendingContainer, db.getPendingReview(12), clickable = true)
        renderPayments(binding.recentContainer, db.getRecent(25), clickable = true)
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
            val amountView = row.findViewById<TextView>(R.id.rowAmount)
            amountView.text = if (p.direction == TransactionDirection.INCOMING) "+${rupees(p.amount)}" else "−${rupees(p.amount)}"
            amountView.setTextColor(getColor(if (p.direction == TransactionDirection.INCOMING) R.color.success else R.color.expense))

            row.findViewById<TextView>(R.id.rowName).text = if (p.direction == TransactionDirection.INCOMING) {
                when (p.donationStatus) {
                    DonationStatus.DONATION -> p.donorName?.takeIf { it.isNotBlank() } ?: "Donation"
                    DonationStatus.NOT_DONATION -> "Personal incoming payment"
                    DonationStatus.PENDING -> "Review: donation or personal?"
                }
            } else {
                when (p.expenseStatus) {
                    ExpenseStatus.CAMPAIGN_EXPENSE -> p.expenseNote?.takeIf { it.isNotBlank() } ?: "Campaign expense"
                    ExpenseStatus.PERSONAL -> "Personal outgoing payment"
                    ExpenseStatus.PENDING -> "Review: campaign expense or personal?"
                    ExpenseStatus.NOT_APPLICABLE -> "Outgoing transaction"
                }
            }

            val directionLabel = if (p.direction == TransactionDirection.INCOMING) "Incoming" else "Outgoing"
            val classification = if (p.direction == TransactionDirection.INCOMING) {
                when (p.donationStatus) {
                    DonationStatus.DONATION -> "Donation"
                    DonationStatus.NOT_DONATION -> "Personal"
                    DonationStatus.PENDING -> "Review pending"
                }
            } else {
                when (p.expenseStatus) {
                    ExpenseStatus.CAMPAIGN_EXPENSE -> "Campaign expense"
                    ExpenseStatus.PERSONAL -> "Personal"
                    ExpenseStatus.PENDING -> "Expense review pending"
                    ExpenseStatus.NOT_APPLICABLE -> "Outgoing"
                }
            }
            val syncLabel = if (p.synced) "Synced" else "Sync pending"
            row.findViewById<TextView>(R.id.rowMeta).text = "$directionLabel • $classification • ${p.sourceApp}\n${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.receivedAt))} • $syncLabel"

            if (clickable) row.setOnClickListener {
                startActivity(Intent(this, DonorEntryActivity::class.java).putExtra("event_id", p.eventId))
            }
            container.addView(row)
        }
    }

    private fun showPaymentAppNotificationSettings() {
        val installed = supportedPaymentApps.filterKeys { isPackageInstalled(it) }
        if (installed.isEmpty()) {
            Toast.makeText(this, "No supported payment apps were found on this phone", Toast.LENGTH_LONG).show()
            return
        }

        val labels = installed.values.toTypedArray()
        val packages = installed.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Check payment-app notifications")
            .setMessage("Android does not let NMO Seva Ledger switch another app's notifications on automatically. Open each payment app you use and make sure notifications are allowed.")
            .setItems(labels) { _, which -> openAppNotificationSettings(packages[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openAppNotificationSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun rupees(value: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        return format.format(value)
    }
}
