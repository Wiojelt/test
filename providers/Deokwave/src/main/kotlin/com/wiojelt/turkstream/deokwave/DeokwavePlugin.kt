package com.wiojelt.turkstream.deokwave

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DeokwavePlugin : Plugin() {
    companion object {
        // Varsayılan gizli 4K VIP oturumu
        const val DEFAULT_VIP_TOKEN = "d0TZt3KNAcgYZFJooDdJK2CHLYP6GRHv5elScntRqwEaa6vBSoBfALDZNS48nMnf7wMLoDU6mkuuoRBXd3GCNLNvEknDFq9VsPyz"
    }

    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("deokwave_prefs", Context.MODE_PRIVATE)

        val api = Deokwave {
            val custom = prefs.getString("custom_token", "")?.trim() ?: ""
            if (custom.isNotBlank()) custom else DEFAULT_VIP_TOKEN
        }
        registerMainAPI(api)

        openSettings = { uiContext ->
            val hasCustom = prefs.getString("custom_token", "")?.isNotBlank() == true

            fun dp(value: Int): Int = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                uiContext.resources.displayMetrics
            ).toInt()

            val scroll = ScrollView(uiContext).apply {
                isFillViewport = true
            }

            val rootLayout = LinearLayout(uiContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(12))
            }
            scroll.addView(rootLayout)

            // Status Card
            val statusCard = LinearLayout(uiContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A1D24"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#2E3440"))
                }
            }

            val badgeRow = LinearLayout(uiContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val badge = TextView(uiContext).apply {
                text = if (hasCustom) "ÖZEL HESAP AKTİF" else "4K VIP MOTORU AKTİF"
                setTextColor(Color.parseColor("#10B981"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#064E3B"))
                    cornerRadius = dp(6).toFloat()
                }
            }
            badgeRow.addView(badge)
            statusCard.addView(badgeRow)

            val infoText = TextView(uiContext).apply {
                text = if (hasCustom) {
                    "Özel hesabınız ile oturum açıldı. 4K Ultra HD ve Fansub yayınları aktif."
                } else {
                    "Tüm 4K Ultra HD (2160p), HDR ve tüm Fansub yayınları yerleşik VIP motoru üzerinden açık ve kullanıma hazır."
                }
                setTextColor(Color.parseColor("#D8DEE9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            statusCard.addView(infoText)
            rootLayout.addView(statusCard)

            // Divider
            val divider = TextView(uiContext).apply {
                text = "FARKLI HESAP KULLAN (İSTEĞE BAĞLI)"
                setTextColor(Color.parseColor("#7E8B9B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(4), dp(18), dp(4), dp(8))
            }
            rootLayout.addView(divider)

            // Input description
            val descText = TextView(uiContext).apply {
                text = "Kendi Deokwave hesabınızı bağlamak isterseniz tarayıcınızdaki 'dk_ses' oturum anahtarınızı aşağıya yapıştırın. Boş bırakırsanız otomatik VIP motoru çalışır."
                setTextColor(Color.parseColor("#9AA4B2"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(4), 0, dp(4), dp(10))
            }
            rootLayout.addView(descText)

            // Input Field (Password-masked)
            val tokenInput = EditText(uiContext).apply {
                hint = if (hasCustom) "••••••••••••••••••••••••" else "Özel dk_ses anahtarı girin..."
                setHintTextColor(Color.parseColor("#5A6577"))
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#13161C"))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.parseColor("#374151"))
                }
            }
            rootLayout.addView(tokenInput)

            AlertDialog.Builder(uiContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Deokwave Ayarları")
                .setView(scroll)
                .setPositiveButton("Kaydet") { _, _ ->
                    val newToken = tokenInput.text.toString().trim()
                    if (newToken.isNotEmpty()) {
                        prefs.edit().putString("custom_token", newToken).apply()
                        Toast.makeText(uiContext, "Özel hesap anahtarı kaydedildi!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton("VIP'e Sıfırla") { _, _ ->
                    prefs.edit().remove("custom_token").apply()
                    Toast.makeText(uiContext, "Varsayılan 4K VIP oturumuna dönüldü.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Kapat", null)
                .show()
        }
    }
}
