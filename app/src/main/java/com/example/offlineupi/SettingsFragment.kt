package com.example.offlineupi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dark mode toggle
        val darkModeSwitch = view.findViewById<MaterialSwitch>(R.id.darkModeSwitch)
        val mainActivity = activity as? MainActivity
        darkModeSwitch.isChecked = mainActivity?.isDarkMode() ?: false
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            mainActivity?.toggleDarkMode(isChecked)
        }

        // Developer link
        view.findViewById<LinearLayout>(R.id.btnDeveloper).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/parthkhxndelwal"))
            startActivity(intent)
        }

        // NPCI Helpline
        view.findViewById<LinearLayout>(R.id.btnHelpline).setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:18001201740"))
            startActivity(intent)
        }
    }
}
