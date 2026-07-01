package com.example.offlineupi

import android.content.*
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var currentVpa: String = ""
    private var currentAmount: String = ""
    private lateinit var ussdManager: USSDManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ussdManager = USSDManager.getInstance(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        getSharedPreferences("OfflineUPIPrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("dark_mode", enabled).apply()
    }

    fun isDarkMode(): Boolean {
        val prefs = getSharedPreferences("OfflineUPIPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_mode", false)
    }

    // ─── QR Scan → Merchant Payment ────────────────────────────────────

    fun handleQrResult(scannedData: String) {
        currentVpa = if (scannedData.contains("pa=")) {
            scannedData.substringAfter("pa=").substringBefore("&")
        } else {
            scannedData
        }

        // Auto-copy VPA to clipboard as a convenience
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("UPI_ID", currentVpa)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "UPI ID Copied Automatically", Toast.LENGTH_SHORT).show()

        showAmountDialog()
    }

    private fun showAmountDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val txtTitle = TextView(this).apply {
            text = "Confirm Payment Detail"
            textSize = 20f
            setPadding(0, 0, 0, 20)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val vpaDisplay = TextView(this).apply {
            text = currentVpa
            textSize = 18f
            setTextColor(android.graphics.Color.BLUE)
            setPadding(0, 10, 0, 10)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val btnCopy = Button(this).apply {
            text = "RE-COPY UPI ID"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UPI_ID", currentVpa)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "UPI ID Copied Again!", Toast.LENGTH_SHORT).show()
            }
        }

        val amountInput = EditText(this).apply {
            hint = "Enter Amount"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(txtTitle)
        layout.addView(vpaDisplay)
        layout.addView(btnCopy)
        layout.addView(amountInput)

        AlertDialog.Builder(this)
            .setTitle("Merchant Payment")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Start Payment") { _, _ ->
                currentAmount = amountInput.text.toString()
                initiateMerchantPayment()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Multi-step USSD payment to VPA via *99*1*3#.
     * Flow: Dial → Bank asks for VPA → Send VPA → Bank asks for amount → Send amount → PIN → Done
     */
    private fun initiateMerchantPayment() {
        val vpa = currentVpa
        val amount = currentAmount

        // Step tracking for multi-step USSD
        var step = 0

        ussdManager.callUSSD("*99*1*3#", object : USSDManager.USSDCallback {
            override fun onUssdResponse(message: String, reply: ((String) -> Unit)?) {
                when (step) {
                    0 -> {
                        // Bank asking for VPA — send it
                        step = 1
                        reply?.invoke(vpa)
                    }
                    1 -> {
                        // Bank asking for amount — send it
                        step = 2
                        reply?.invoke(amount)
                    }
                    2 -> {
                        // Bank asking for PIN — show PIN dialog
                        step = 3
                        reply?.let { showPinDialog(it) }
                    }
                    else -> {
                        // Any further responses — ignore
                    }
                }
            }

            override fun onUssdComplete(finalMessage: String) {
                showTransactionResult(finalMessage)
            }

            override fun onError(error: String) {
                Toast.makeText(this@MainActivity, "Payment Error: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    // ─── UPI PIN Entry ──────────────────────────────────────────────────

    private fun showPinDialog(sendPin: (String) -> Unit) {
        runOnUiThread {
            val pinInput = EditText(this).apply {
                hint = "Enter UPI PIN"
                inputType = InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("UPI PIN Required")
                .setMessage("Enter your UPI PIN to authorize ₹$currentAmount payment to $currentVpa")
                .setView(pinInput)
                .setCancelable(false)
                .setPositiveButton("Submit") { _, _ ->
                    val pin = pinInput.text.toString().trim()
                    if (pin.length >= 4) {
                        sendPin(pin)
                    } else {
                        Toast.makeText(this@MainActivity, "Enter a valid PIN", Toast.LENGTH_SHORT).show()
                        showPinDialog(sendPin)
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this@MainActivity, "Transaction cancelled", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    // ─── Transaction Result ─────────────────────────────────────────────

    private fun showTransactionResult(finalMessage: String) {
        runOnUiThread {
            val isSuccess = !finalMessage.contains("fail", true) &&
                !finalMessage.contains("error", true) &&
                !finalMessage.contains("sorry", true) &&
                !finalMessage.contains("declined", true) &&
                finalMessage.isNotBlank()

            AlertDialog.Builder(this)
                .setTitle(if (isSuccess) "Payment Successful ✓" else "Payment Failed ✗")
                .setMessage(finalMessage)
                .setPositiveButton("OK") { _, _ ->
                    if (isSuccess) {
                        saveTransactionToHistory()
                        currentVpa = ""
                        currentAmount = ""
                    }
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Payment")
            .setMessage("Did the bank confirm the transaction for ₹$currentAmount?")
            .setCancelable(false)
            .setPositiveButton("Yes, Save to History") { _, _ ->
                saveTransactionToHistory()
                currentVpa = ""
                currentAmount = ""
            }
            .setNegativeButton("No / Failed") { _, _ ->
                currentVpa = ""
                currentAmount = ""
            }
            .show()
    }

    fun setPendingTransaction(vpa: String, amount: String) {
        this.currentVpa = vpa
        this.currentAmount = amount
    }

    private fun saveTransactionToHistory() {
        val timeStamp = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val txId = "TXN" + System.currentTimeMillis().toString().takeLast(6)

        val prefs = getSharedPreferences("OfflineUPIPrefs", Context.MODE_PRIVATE)
        val existingHistory = prefs.getString("history", "") ?: ""
        val newEntry = "$timeStamp|$currentVpa|$currentAmount|$txId"

        prefs.edit().putString("history", "$newEntry;$existingHistory").apply()
        Toast.makeText(this, "Receipt Saved in History", Toast.LENGTH_SHORT).show()
    }
}
