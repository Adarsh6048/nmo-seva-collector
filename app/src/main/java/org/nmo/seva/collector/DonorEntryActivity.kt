package org.nmo.seva.collector

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.nmo.seva.collector.databinding.ActivityDonorEntryBinding
import java.text.DateFormat
import java.util.Date

class DonorEntryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDonorEntryBinding
    private lateinit var db: PaymentDbHelper
    private var eventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDonorEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = PaymentDbHelper(this)
        eventId = intent.getStringExtra("event_id") ?: run { finish(); return }
        val payment = db.getPayment(eventId) ?: run { finish(); return }

        binding.amountText.text = if (payment.amount % 1.0 == 0.0) "₹${payment.amount.toLong()}" else "₹%.2f".format(payment.amount)
        binding.sourceText.text = buildString {
            append(payment.sourceApp)
            payment.senderHint?.takeIf { it.isNotBlank() }?.let { append(" • sender hint: $it") }
            append("\n")
            append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(payment.receivedAt)))
        }
        payment.donorName?.let { binding.donorInput.setText(it) }

        binding.anonymousCheck.setOnCheckedChangeListener { _, checked -> binding.donorInput.isEnabled = !checked }
        binding.saveButton.setOnClickListener {
            val donor = if (binding.anonymousCheck.isChecked) "Anonymous" else binding.donorInput.text.toString().trim()
            if (donor.isBlank()) {
                binding.donorInput.error = "Enter donor name or choose Anonymous"
                return@setOnClickListener
            }
            db.updateDonorName(eventId, donor)
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Donor name saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.laterButton.setOnClickListener { finish() }
    }
}
