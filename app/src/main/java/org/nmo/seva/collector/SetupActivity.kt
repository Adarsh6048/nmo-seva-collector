package org.nmo.seva.collector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.nmo.seva.collector.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupBinding

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
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = AppPreferences(this)
        LogoAsset.applyTo(binding.logoImage)
        binding.nameInput.setText(prefs.collectorName)
        binding.codeInput.setText(prefs.collectorCode)
        binding.tokenInput.setText(prefs.collectorToken)
        binding.backendInput.setText(prefs.backendUrl)
        binding.ledgerConsentCheck.isChecked = prefs.ledgerConsent

        binding.notificationAccessButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.paymentNotificationsButton.setOnClickListener { showPaymentAppNotificationSettings() }

        binding.saveButton.setOnClickListener {
            val code = binding.codeInput.text.toString().trim().uppercase()
            val token = binding.tokenInput.text.toString().trim()
            val url = binding.backendInput.text.toString().trim()

            if (code.isBlank() || token.isBlank() || !url.startsWith("https://")) {
                Toast.makeText(this, "Enter collector code, token and a valid HTTPS backend URL", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!binding.ledgerConsentCheck.isChecked) {
                Toast.makeText(this, "Please acknowledge the ledger notice before enabling collection", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.collectorName = binding.nameInput.text.toString()
            prefs.collectorCode = code
            prefs.collectorToken = token
            prefs.backendUrl = url
            prefs.ledgerConsent = true
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Collector setup saved", Toast.LENGTH_SHORT).show()
            finish()
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
            .setTitle("Enable payment notifications")
            .setMessage("Choose every payment app you use and make sure its notifications are allowed. Android requires you to enable another app's notifications from system settings.")
            .setItems(labels) { _, which ->
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packages[which])
                })
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
