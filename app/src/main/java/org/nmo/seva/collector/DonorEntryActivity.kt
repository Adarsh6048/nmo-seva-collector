package org.nmo.seva.collector

import android.os.Bundle
import android.view.View
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
        binding.directionText.text = if (payment.direction == TransactionDirection.INCOMING) "Incoming transaction" else "Outgoing transaction"
        binding.sourceText.text = buildString {
            append(payment.sourceApp)
            payment.senderHint?.takeIf { it.isNotBlank() }?.let { append(" • party: $it") }
            payment.transactionRef?.takeIf { it.isNotBlank() }?.let { append(" • ref: $it") }
            append("\n")
            append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(payment.receivedAt)))
        }
        payment.donorName?.let { binding.donorInput.setText(it) }
        binding.anonymousCheck.isChecked = payment.donorName == "Anonymous"

        if (payment.direction == TransactionDirection.OUTGOING) {
            binding.questionText.text = "Outgoing transactions are tracked for the ledger and are not counted as donations."
            binding.donorInput.visibility = View.GONE
            binding.anonymousCheck.visibility = View.GONE
            binding.saveButton.visibility = View.GONE
            binding.notDonationButton.text = "Confirm as non-donation"
            binding.laterButton.visibility = View.GONE
        } else {
            binding.questionText.text = when (payment.donationStatus) {
                DonationStatus.DONATION -> "This transaction is marked as a donation. You can update the donor name below."
                DonationStatus.NOT_DONATION -> "This transaction is currently marked as not a donation."
                DonationStatus.PENDING -> "Is this incoming payment a donation?"
            }
        }

        binding.anonymousCheck.setOnCheckedChangeListener { _, checked -> binding.donorInput.isEnabled = !checked }

        binding.saveButton.setOnClickListener {
            val donor = if (binding.anonymousCheck.isChecked) "Anonymous" else binding.donorInput.text.toString().trim()
            if (donor.isBlank()) {
                binding.donorInput.error = "Enter donor name or choose Anonymous"
                return@setOnClickListener
            }
            db.updateClassification(eventId, DonationStatus.DONATION, donor)
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Marked as donation", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.notDonationButton.setOnClickListener {
            db.updateClassification(eventId, DonationStatus.NOT_DONATION)
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Marked as not a donation", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.laterButton.setOnClickListener { finish() }
    }
}
