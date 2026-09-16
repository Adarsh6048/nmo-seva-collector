package org.nmo.seva.collector

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.nmo.seva.collector.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val prefs = AppPreferences(this)
        binding.nameInput.setText(prefs.collectorName)
        binding.codeInput.setText(prefs.collectorCode)
        binding.tokenInput.setText(prefs.collectorToken)
        binding.backendInput.setText(prefs.backendUrl)

        binding.saveButton.setOnClickListener {
            val code = binding.codeInput.text.toString().trim().uppercase()
            val token = binding.tokenInput.text.toString().trim()
            val url = binding.backendInput.text.toString().trim()
            if (code.isBlank() || token.isBlank() || !url.startsWith("https://")) {
                Toast.makeText(this, "Enter collector code, token and a valid HTTPS backend URL", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.collectorName = binding.nameInput.text.toString()
            prefs.collectorCode = code
            prefs.collectorToken = token
            prefs.backendUrl = url
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Setup saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
