package com.wiojelt.turkstream.deokwave

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DeokwavePlugin : Plugin() {
    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("deokwave_prefs", Context.MODE_PRIVATE)
        val defaultToken = "d0TZt3KNAcgYZFJooDdJK2CHLYP6GRHv5elScntRqwEaa6vBSoBfALDZNS48nMnf7wMLoDU6mkuuoRBXd3GCNLNvEknDFq9VsPyz"
        if (!prefs.contains("dk_ses") || prefs.getString("dk_ses", "")?.isBlank() == true) {
            prefs.edit().putString("dk_ses", defaultToken).apply()
        }

        val api = Deokwave { prefs.getString("dk_ses", defaultToken) ?: defaultToken }
        registerMainAPI(api)

        openSettings = { uiContext ->
            val currentToken = prefs.getString("dk_ses", defaultToken) ?: defaultToken
            val layout = LinearLayout(uiContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 32, 48, 16)
            }

            val statusText = TextView(uiContext).apply {
                text = "Giriş Yapılan Hesap: ishak.kut21@gmail.com (wioland)\nDurum: 4K Ultra HD & Fansub Yayınları Aktif\n\nOturum Anahtarı (dk_ses):"
                textSize = 14f
            }
            layout.addView(statusText)

            val input = EditText(uiContext).apply {
                setText(currentToken)
                setSingleLine(true)
            }
            layout.addView(input)

            AlertDialog.Builder(uiContext)
                .setTitle("Deokwave Hesap Ayarları")
                .setView(layout)
                .setPositiveButton("Kaydet") { _, _ ->
                    val newToken = input.text.toString().trim()
                    if (newToken.isNotEmpty()) {
                        prefs.edit().putString("dk_ses", newToken).apply()
                    }
                }
                .setNeutralButton("Varsayılana Sıfırla") { _, _ ->
                    prefs.edit().putString("dk_ses", defaultToken).apply()
                }
                .setNegativeButton("Kapat", null)
                .show()
        }
    }
}
