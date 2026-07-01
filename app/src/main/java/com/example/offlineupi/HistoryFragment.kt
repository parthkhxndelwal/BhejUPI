package com.example.offlineupi

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HistoryFragment : Fragment(R.layout.fragment_history) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listView = view.findViewById<ListView>(R.id.historyListView)
        val emptyState = view.findViewById<View>(R.id.emptyState)

        val prefs = requireActivity().getSharedPreferences("OfflineUPIPrefs", Context.MODE_PRIVATE)
        val rawData = prefs.getString("history", "") ?: ""
        val entries = rawData.split(";").filter { it.isNotEmpty() }.toMutableList()

        // Toggle visibility
        if (entries.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        val adapter = object : ArrayAdapter<String>(requireContext(),
            android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val cardView = layoutInflater.inflate(R.layout.item_transaction, parent, false)
                val data = entries[position].split("|")
                val vpa = data.getOrElse(1) { "Unknown" }
                val amount = data.getOrElse(2) { "0" }
                val date = data.getOrElse(0) { "" }
                val txId = data.getOrElse(3) { "" }

                cardView.findViewById<TextView>(R.id.txTitle).text = "Sent to $vpa"
                cardView.findViewById<TextView>(R.id.txSubtitle).text = "$date • $txId"
                cardView.findViewById<TextView>(R.id.txAmount).text = "₹$amount"

                // Long press to delete
                cardView.setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete History")
                        .setMessage("Remove this transaction?")
                        .setPositiveButton("Delete") { _, _ ->
                            entries.removeAt(position)
                            prefs.edit().putString("history", entries.joinToString(";")).apply()
                            // Refresh
                            if (entries.isEmpty()) {
                                emptyState.visibility = View.VISIBLE
                                listView.visibility = View.GONE
                            }
                            notifyDataSetChanged()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                return cardView
            }
        }
        listView.adapter = adapter
    }
}
