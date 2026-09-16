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

        if (payment.direction == TransactionDirection.INCOMING) {
            setupIncoming(payment)
        } else {
            setupOutgoing(payment)
        }

        binding.anonymousCheck.setOnCheckedChangeListener { _, checked ->
            binding.donorInput.isEnabled = !checked
        }

        binding.saveButton.setOnClickListener {
            if (payment.direction == TransactionDirection.INCOMING) {
                val donor = if (binding.anonymousCheck.isChecked) "Anonymous" else binding.donorInput.text.toString().trim()
                if (donor.isBlank()) {
                    binding.donorInput.error = "Enter donor name or choose Anonymous"
                    return@setOnClickListener
                }
                db.updateClassification(eventId, DonationStatus.DONATION, donor)
                SyncScheduler.enqueue(this)
                Toast.makeText(this, "Marked as donation", Toast.LENGTH_SHORT).show()
            } else {
                val note = binding.expenseInput.text.toString().trim()
                if (note.isBlank()) {
                    binding.expenseInput.error = "Add a short expense comment"
                    return@setOnClickListener
                }
                db.updateExpenseClassification(eventId, ExpenseStatus.CAMPAIGN_EXPENSE, note)
                SyncScheduler.enqueue(this)
                Toast.makeText(this, "Marked as campaign expense", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        binding.notDonationButton.setOnClickListener {
            if (payment.direction == TransactionDirection.INCOMING) {
                db.updateClassification(eventId, DonationStatus.NOT_DONATION)
                Toast.makeText(this, "Marked as personal / not a donation", Toast.LENGTH_SHORT).show()
            } else {
                db.updateExpenseClassification(eventId, ExpenseStatus.PERSONAL)
                Toast.makeText(this, "Marked as personal / not campaign expense", Toast.LENGTH_SHORT).show()
            }
            SyncScheduler.enqueue(this)
            finish()
        }

        binding.laterButton.setOnClickListener { finish() }
    }

    private fun setupIncoming(payment: Payment) {
        binding.expenseInput.visibility = View.GONE
        binding.donorInput.visibility = View.VISIBLE
        binding.anonymousCheck.visibility = View.VISIBLE
        payment.donorName?.let { binding.donorInput.setText(it) }
        binding.anonymousCheck.isChecked = payment.donorName == "Anonymous"
        binding.saveButton.text = "Mark as donation"
        binding.notDonationButton.text = "Personal / not a donation"
        binding.questionText.text = when (payment.donationStatus) {
            DonationStatus.DONATION -> "This is marked as a donation. You can update the donor name."
            DonationStatus.NOT_DONATION -> "This is currently marked as a personal incoming payment."
            DonationStatus.PENDING -> "Is this incoming payment a donation?"
        }
    }

    private fun setupOutgoing(payment: Payment) {
        binding.donorInput.visibility = View.GONE
        binding.anonymousCheck.visibility = View.GONE
        binding.expenseInput.visibility = View.VISIBLE
        payment.expenseNote?.let { binding.expenseInput.setText(it) }
        binding.saveButton.text = "Mark as campaign expense"
        binding.notDonationButton.text = "Personal / not campaign expense"
        binding.questionText.text = when (payment.expenseStatus) {
            ExpenseStatus.CAMPAIGN_EXPENSE -> "This outgoing transaction is marked as a campaign expense. Update the comment if needed."
            ExpenseStatus.PERSONAL -> "This outgoing transaction is currently marked as personal."
            ExpenseStatus.PENDING -> "Was this outgoing payment a campaign expense?"
            ExpenseStatus.NOT_APPLICABLE -> "Review this outgoing transaction."
        }
    }
}
