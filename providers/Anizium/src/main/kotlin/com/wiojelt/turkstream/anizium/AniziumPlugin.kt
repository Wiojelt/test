package com.wiojelt.turkstream.anizium

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@CloudstreamPlugin
class AniziumPlugin : Plugin() {
    companion object {
        // Dahili ozel VIP erisim bilgileri (otomatik oturum yenileme icin)
        private const val ENC_U = "aXNoYWsua3V0MjFAZ21haWwuY29t"
        private const val ENC_P = "MTIzNDU2Nzk4YUE="

        fun getMasterCredentials(): Pair<String, String> {
            val u = String(android.util.Base64.decode(ENC_U, android.util.Base64.DEFAULT))
            val p = String(android.util.Base64.decode(ENC_P, android.util.Base64.DEFAULT))
            return Pair(u, p)
        }

        fun doLogin(
            username: String,
            pass: String,
            onResult: (Boolean, String, String) -> Unit
        ) {
            Thread {
                try {
                    val url = URL("${Anizium.API_BASE}/user/login")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    conn.setRequestProperty("device", "browser")
                    conn.setRequestProperty("language", "tr")
                    conn.setRequestProperty("site", "main")
                    conn.setRequestProperty("Cf-Control", Anizium.generateCfControl())
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.doOutput = true

                    val loginPayload = "{\"value\":\"$username\",\"password\":\"$pass\",\"date\":${System.currentTimeMillis()}}"
                    val encD = Anizium.encryptBody(loginPayload)
                    val postData = JSONObject().apply {
                        put("d", encD)
                    }.toString()

                    OutputStreamWriter(conn.outputStream, "UTF-8").use {
                        it.write(postData)
                        it.flush()
                    }

                    val resp = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(resp)
                    val success = json.optBoolean("success", false)
                    val session = json.optString("session", "")
                    val msg = json.optString("msg", if (success) "Giriş başarılı" else "Giriş başarısız")

                    Handler(Looper.getMainLooper()).post {
                        onResult(success, session, msg)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        onResult(false, "", e.message ?: "Bağlantı hatası")
                    }
                }
            }.start()
        }
    }

    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("anizium_prefs", Context.MODE_PRIVATE)

        // Eger onceden kaydedilmis oturum yoksa arka planda sessizce oturum al
        val existingSession = prefs.getString("saved_session", "")
        if (existingSession.isNullOrBlank()) {
            val (masterU, masterP) = getMasterCredentials()
            doLogin(masterU, masterP) { success, session, _ ->
                if (success && session.isNotBlank()) {
                    prefs.edit().putString("saved_session", session).apply()
                }
            }
        }

        val api = Anizium {
            val custom = prefs.getString("custom_session", "")?.trim() ?: ""
            val saved = prefs.getString("saved_session", "")?.trim() ?: ""
            when {
                custom.isNotBlank() -> custom
                saved.isNotBlank() -> saved
                else -> ""
            }
        }
        registerMainAPI(api)

        openSettings = { uiContext ->
            showSettingsDialog(uiContext, prefs)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showSettingsDialog(uiContext: Context, prefs: android.content.SharedPreferences) {
        val hasCustom = prefs.getString("custom_session", "")?.isNotBlank() == true

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
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        scroll.addView(rootLayout)

        // 1. Durum Karti
        val statusCard = LinearLayout(uiContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151922"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#2A3142"))
            }
        }

        val badge = TextView(uiContext).apply {
            text = if (hasCustom) "✓ ÖZEL HESAP AKTİF" else "★ 4K / 2K VIP MOTORU AKTİF"
            setTextColor(if (hasCustom) Color.parseColor("#10B981") else Color.parseColor("#A78BFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(if (hasCustom) Color.parseColor("#064E3B") else Color.parseColor("#2E1065"))
                cornerRadius = dp(6).toFloat()
            }
        }
        statusCard.addView(badge)

        val infoText = TextView(uiContext).apply {
            text = if (hasCustom) {
                "Özel Anizium hesabınız bağlı. 4K Ultra HD (2160p), 2K Quad HD (1440p) ve Türkçe Dublaj yayınları aktif."
            } else {
                "Yerleşik VIP motoru devrede. 4K (2160p), 2K (1440p), Türkçe Dublaj ve çoklu altyazılar doğrudan izlenebilir."
            }
            setTextColor(Color.parseColor("#D1D5DB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        statusCard.addView(infoText)

        var settingsDialog: AlertDialog? = null

        val renewVipBtn = Button(uiContext).apply {
            text = "↻ VIP Oturumunu Yenile (Anında Giriş)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6366F1"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                isEnabled = false
                text = "Giriş yapılıyor..."
                val (masterU, masterP) = getMasterCredentials()
                doLogin(masterU, masterP) { success, session, msg ->
                    isEnabled = true
                    text = "↻ VIP Oturumunu Yenile (Anında Giriş)"
                    if (success && session.isNotBlank()) {
                        prefs.edit().putString("saved_session", session).apply()
                        Toast.makeText(uiContext, "✓ Oturum yenilendi: $msg", Toast.LENGTH_SHORT).show()
                        settingsDialog?.dismiss()
                    } else {
                        Toast.makeText(uiContext, "Hata: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        statusCard.addView(renewVipBtn)

        val noticeText = TextView(uiContext).apply {
            text = "💡 Token/bağlantı hatası alırsanız veya video başlamazsa yukarıdaki butona basarak anında yeni oturum alabilirsiniz."
            setTextColor(Color.parseColor("#93C5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(8), 0, 0)
        }
        statusCard.addView(noticeText)
        rootLayout.addView(statusCard)

        // 2. Ozel Giris Karti
        val loginCard = LinearLayout(uiContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151922"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#2A3142"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }

        val loginTitle = TextView(uiContext).apply {
            text = "Kendi Anizium Hesabınla Giriş Yap"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        loginCard.addView(loginTitle)

        val userInput = EditText(uiContext).apply {
            hint = "E-posta veya Kullanıcı Adı"
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F131A"))
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.parseColor("#374151"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        loginCard.addView(userInput)

        val passInput = EditText(uiContext).apply {
            hint = "Şifre"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F131A"))
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.parseColor("#374151"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        loginCard.addView(passInput)

        val customLoginBtn = Button(uiContext).apply {
            text = "Giriş Yap ve Özel Hesabı Kullan"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                val u = userInput.text.toString().trim()
                val p = passInput.text.toString().trim()
                if (u.isBlank() || p.isBlank()) {
                    Toast.makeText(uiContext, "Lütfen e-posta ve şifrenizi girin!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isEnabled = false
                text = "Giriş yapılıyor..."
                doLogin(u, p) { success, session, msg ->
                    isEnabled = true
                    text = "Giriş Yap ve Özel Hesabı Kullan"
                    if (success && session.isNotBlank()) {
                        prefs.edit().putString("custom_session", session).apply()
                        Toast.makeText(uiContext, "✓ Başarılı: $msg", Toast.LENGTH_SHORT).show()
                        settingsDialog?.dismiss()
                    } else {
                        Toast.makeText(uiContext, "Giriş hatası: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        loginCard.addView(customLoginBtn)

        if (hasCustom) {
            val resetBtn = Button(uiContext).apply {
                text = "Varsayılan VIP Hesaba Geri Dön"
                setTextColor(Color.parseColor("#EF4444"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1F2937"))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
                setOnClickListener {
                    prefs.edit().remove("custom_session").apply()
                    Toast.makeText(uiContext, "Varsayılan VIP hesaba dönüldü.", Toast.LENGTH_SHORT).show()
                    settingsDialog?.dismiss()
                }
            }
            loginCard.addView(resetBtn)
        }

        rootLayout.addView(loginCard)

        settingsDialog = AlertDialog.Builder(uiContext)
            .setTitle("Anizium Ayarları")
            .setView(scroll)
            .setPositiveButton("Kapat") { d, _ -> d.dismiss() }
            .create()

        settingsDialog.show()
    }
}
