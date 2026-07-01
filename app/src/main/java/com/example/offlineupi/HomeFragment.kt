package com.example.offlineupi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.zxing.integration.android.IntentIntegrator

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var ussdManager: USSDManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ussdManager = USSDManager.getInstance(requireContext())

        // Balance card — tap to check balance
        view.findViewById<MaterialCardView>(R.id.cardBalance).setOnClickListener {
            checkBalance()
        }

        // 1. QR Scanner
        view.findViewById<MaterialCardView>(R.id.btnScanQR).setOnClickListener {
            startQrScanner()
        }

        // 2. Send to Mobile Number
        view.findViewById<MaterialCardView>(R.id.btnSendMobile).setOnClickListener {
            showMobileInputDialog()
        }

        // 3. Send to Bank (IFSC)
        view.findViewById<MaterialCardView>(R.id.btnSendBank).setOnClickListener {
            dialSimpleUssd("*99*1*5#")
        }

        // 4. Check Balance (quick action grid)
        view.findViewById<MaterialCardView>(R.id.cardCheckBalance).setOnClickListener {
            dialSimpleUssd("*99*3#")
        }

        // 5. Mini Statement
        view.findViewById<MaterialCardView>(R.id.cardMiniStatement).setOnClickListener {
            dialSimpleUssd("*99*6*1#")
        }

        // Load recent transactions
        loadRecentTransactions()
    }

    // ─── Balance Check ──────────────────────────────────────────────────

    private fun checkBalance() {
        val view = view ?: return
        if (!checkUssdPermissions()) return

        val balanceAmount = view.findViewById<TextView>(R.id.balanceAmount)
        val balanceLabel = view.findViewById<TextView>(R.id.balanceLabel)
        balanceAmount.text = "——"
        balanceLabel.text = "Checking balance…"

        ussdManager.callUSSD("*99*3#", object : USSDManager.USSDCallback {
            override fun onUssdResponse(message: String, reply: ((String) -> Unit)?) {
                // Some banks send intermediary messages — ignore, wait for final
            }

            override fun onUssdComplete(finalMessage: String) {
                activity?.runOnUiThread {
                    val cleaned = finalMessage
                        .replace("null", "")
                        .replace("  ", " ")
                        .trim()
                    if (cleaned.contains("fail", true) || cleaned.contains("error", true) || cleaned.isBlank()) {
                        balanceAmount.text = "—"
                        balanceLabel.text = "Tap to check balance"
                    } else {
                        // Extract the balance amount from response if possible
                        val amtMatch = Regex("(?:Rs|₹|INR)\\s*([\\d,]+(?:\\.[\\d]{2})?)").find(cleaned)
                        if (amtMatch != null) {
                            balanceAmount.text = "₹${amtMatch.groupValues[1]}"
                            balanceLabel.text = "Available Balance"
                        } else {
                            balanceAmount.text = "✓"
                            balanceLabel.text = cleaned.take(80)
                        }
                    }
                }
            }

            override fun onError(error: String) {
                activity?.runOnUiThread {
                    balanceAmount.text = "—"
                    balanceLabel.text = "Tap to retry"
                    Toast.makeText(requireContext(), "Balance check failed", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // ─── Recent Transactions ────────────────────────────────────────────

    private fun loadRecentTransactions() {
        val view = view ?: return
        val prefs = requireActivity().getSharedPreferences("OfflineUPIPrefs", Context.MODE_PRIVATE)
        val rawData = prefs.getString("history", "") ?: ""
        val entries = rawData.split(";").filter { it.isNotEmpty() }

        val emptyView = view.findViewById<TextView>(R.id.emptyRecent)
        val container = view.findViewById<LinearLayout>(R.id.recentContainer)

        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        // Show last 5 entries
        val recent = entries.takeLast(5)
        container.removeAllViews()

        for (entry in recent.reversed()) {
            val parts = entry.split("|")
            if (parts.size < 4) continue

            val date = parts[0]
            val vpa = parts[1]
            val amount = parts[2]
            val txId = parts[3]

            val item = layoutInflater.inflate(R.layout.item_transaction, container, false)
            item.findViewById<TextView>(R.id.txTitle).text = "Sent to $vpa"
            item.findViewById<TextView>(R.id.txSubtitle).text = "$date • $txId"
            item.findViewById<TextView>(R.id.txAmount).text = "₹$amount"
            container.addView(item)
        }
    }

    // ─── Send to Mobile ─────────────────────────────────────────────────

    private fun showMobileInputDialog() {
        val safeContext = activity ?: return

        val layout = LinearLayout(safeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 50, 60, 50)
        }

        val phoneInput = EditText(safeContext).apply {
            hint = "10-Digit Mobile Number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val amountInput = EditText(safeContext).apply {
            hint = "Enter Amount"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(phoneInput)
        layout.addView(amountInput)

        AlertDialog.Builder(safeContext)
            .setTitle("Direct Pay to Mobile")
            .setView(layout)
            .setCancelable(true)
            .setPositiveButton("Pay Now") { _, _ ->
                val phone = phoneInput.text.toString().trim()
                val amount = amountInput.text.toString().trim()
                if (phone.length == 10 && amount.isNotEmpty()) {
                    (activity as? MainActivity)?.setPendingTransaction(phone, amount)
                    initiateMobilePayment(phone, amount)
                } else {
                    Toast.makeText(safeContext, "Enter valid 10-digit number and amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initiateMobilePayment(phone: String, amount: String) {
        if (!checkUssdPermissions()) return

        // This USSD code includes all params (option + sub-option + phone + amount + confirm)
        // Bank will then ask for UPI PIN
        val ussdCode = "*99*1*1*$phone*$amount*1#"

        ussdManager.callUSSD(ussdCode, object : USSDManager.USSDCallback {
            override fun onUssdResponse(message: String, reply: ((String) -> Unit)?) {
                // If bank asks for PIN, show the PIN dialog
                if (message.contains("pin", true) || message.contains("mpin", true) ||
                    message.contains("enter", true)
                ) {
                    reply?.let { showPinDialog(it) }
                }
            }

            override fun onUssdComplete(finalMessage: String) {
                showTransactionResult(finalMessage)
            }

            override fun onError(error: String) {
                Toast.makeText(requireContext(), "USSD Error: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    // ─── Simple one-shot USSD (balance, statement, etc.) ────────────────

    private fun dialSimpleUssd(code: String) {
        if (!checkUssdPermissions()) return

        ussdManager.callUSSD(code, object : USSDManager.USSDCallback {
            override fun onUssdResponse(message: String, reply: ((String) -> Unit)?) {
                activity?.runOnUiThread {
                    AlertDialog.Builder(requireActivity())
                        .setTitle("USSD Response")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            override fun onUssdComplete(finalMessage: String) {
                activity?.runOnUiThread {
                    AlertDialog.Builder(requireActivity())
                        .setTitle("Result")
                        .setMessage(finalMessage)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            override fun onError(error: String) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "USSD Error: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    // ─── UPI PIN Entry ──────────────────────────────────────────────────

    private fun showPinDialog(sendPin: (String) -> Unit) {
        activity?.runOnUiThread {
            val pinInput = EditText(requireActivity()).apply {
                hint = "Enter UPI PIN"
                inputType = InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(requireActivity())
                .setTitle("UPI PIN Required")
                .setMessage("Enter your UPI PIN to authorize this transaction")
                .setView(pinInput)
                .setCancelable(false)
                .setPositiveButton("Submit") { _, _ ->
                    val pin = pinInput.text.toString().trim()
                    if (pin.length >= 4) {
                        sendPin(pin)
                    } else {
                        Toast.makeText(requireContext(), "Enter a valid PIN", Toast.LENGTH_SHORT).show()
                        showPinDialog(sendPin)
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(requireContext(), "Transaction cancelled", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    // ─── Result display ─────────────────────────────────────────────────

    private fun showTransactionResult(finalMessage: String) {
        activity?.runOnUiThread {
            val isSuccess = !finalMessage.contains("fail", true) &&
                !finalMessage.contains("error", true) &&
                !finalMessage.contains("sorry", true) &&
                !finalMessage.contains("declined", true) &&
                finalMessage.isNotBlank()

            AlertDialog.Builder(requireActivity())
                .setTitle(if (isSuccess) "Transaction Successful ✓" else "Transaction Failed ✗")
                .setMessage(finalMessage)
                .setPositiveButton("OK") { _, _ ->
                    if (isSuccess) {
                        (activity as? MainActivity)?.showCompletionDialog()
                    }
                }
                .show()
        }
    }

    // ─── Permissions ────────────────────────────────────────────────────

    private fun checkUssdPermissions(): Boolean {
        val act = activity ?: return false

        val allGranted = ContextCompat.checkSelfPermission(act, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(act, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if (!allGranted) {
            requestPermissions(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE),
                101
            )
            return false
        }

        // Overlay permission needed to hide system USSD dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(act)) {
            ussdManager.requireOverlayPermission(act)
            Toast.makeText(act, "Please allow overlay permission for seamless USSD", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    // ─── QR Scanner ─────────────────────────────────────────────────────

    private fun startQrScanner() {
        IntentIntegrator.forSupportFragment(this)
            .setPrompt("Scan UPI QR")
            .setOrientationLocked(true)
            .initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result?.contents != null) {
            (activity as? MainActivity)?.handleQrResult(result.contents)
        }
    }
}
