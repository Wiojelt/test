package com.wiojelt.turkstream.deokwave

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
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import org.json.JSONObject

@CloudstreamPlugin
class DeokwavePlugin : Plugin() {
    companion object {
        // Varsayilan yerlesik 4K VIP oturum anahtari (fallback)
        const val DEFAULT_VIP_TOKEN = "d0TZt3KNAcgYZFJooDdJK2CHLYP6GRHv5elScntRqwEaa6vBSoBfALDZNS48nMnf7wMLoDU6mkuuoRBXd3GCNLNvEknDFq9VsPyz"

        // Dahili ozel VIP erisim bilgileri (otomatik 30 gunluk oturum yenileme icin)
        private const val ENC_U = "aXNoYWsua3V0MjFAZ21haWwuY29t"
        private const val ENC_P = "MTIzNDU2Nzk4YUE="

        fun getMasterCredentials(): Pair<String, String> {
            val u = String(android.util.Base64.decode(ENC_U, android.util.Base64.DEFAULT))
            val p = String(android.util.Base64.decode(ENC_P, android.util.Base64.DEFAULT))
            return Pair(u, p)
        }
    }

    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("deokwave_prefs", Context.MODE_PRIVATE)

        val api = Deokwave {
            val custom = prefs.getString("custom_token", "")?.trim() ?: ""
            val renewedVip = prefs.getString("vip_renewed_token", "")?.trim() ?: ""
            when {
                custom.isNotBlank() -> custom
                renewedVip.isNotBlank() -> renewedVip
                else -> DEFAULT_VIP_TOKEN
            }
        }
        registerMainAPI(api)

        openSettings = { uiContext ->
            showSettingsDialog(uiContext, prefs)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showSettingsDialog(uiContext: Context, prefs: android.content.SharedPreferences) {
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

        val badgeRow = LinearLayout(uiContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(uiContext).apply {
            text = if (hasCustom) "✓ ÖZEL HESAP AKTİF" else "★ 4K VIP MOTORU AKTİF"
            setTextColor(if (hasCustom) Color.parseColor("#10B981") else Color.parseColor("#A78BFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(if (hasCustom) Color.parseColor("#064E3B") else Color.parseColor("#2E1065"))
                cornerRadius = dp(6).toFloat()
            }
        }
        badgeRow.addView(badge)
        statusCard.addView(badgeRow)

        val infoText = TextView(uiContext).apply {
            text = if (hasCustom) {
                "Özel Deokwave hesabınız bağlandı. 4K Ultra HD (2160p) ve Fansub yayınları aktif."
            } else {
                "Yerleşik 4K VIP motoru devrede. Tüm 4K Ultra HD ve Fansub yayınları doğrudan izlenebilir."
            }
            setTextColor(Color.parseColor("#D1D5DB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        statusCard.addView(infoText)

        var settingsDialog: AlertDialog? = null

        val renewVipBtn = Button(uiContext).apply {
            text = "↻ VIP Oturumunu Yenile (30 Gün)"
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
                val (masterUser, masterPass) = getMasterCredentials()
                startAutoLogin(uiContext, masterUser, masterPass, prefs, isRenewVip = true) {
                    settingsDialog?.dismiss()
                }
            }
        }
        statusCard.addView(renewVipBtn)

        val noticeText = TextView(uiContext).apply {
            text = "💡 Token hatası alırsanız veya video açılmazsa yukarıdaki 'VIP Oturumunu Yenile' butonuna basarak anında yeni oturum alabilirsiniz."
            setTextColor(Color.parseColor("#93C5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(6), 0, 0)
        }
        statusCard.addView(noticeText)
        rootLayout.addView(statusCard)

        // 2. Giris Yap Bolumu
        val loginTitle = TextView(uiContext).apply {
            text = "HESAP GİRİŞİ (OTOMATİK DK_SES ÇEKİCİ)"
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(4), dp(20), dp(4), dp(6))
        }
        rootLayout.addView(loginTitle)

        val loginDesc = TextView(uiContext).apply {
            text = "Kendi hesabınızı bağlamak için Deokwave kullanıcı adı/e-posta ve şifrenizi girin. Sistem otomatik oturum açıp 'dk_ses' anahtarını çeker."
            setTextColor(Color.parseColor("#6B7280"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        rootLayout.addView(loginDesc)

        // Email Input
        val emailInput = EditText(uiContext).apply {
            hint = "E-posta veya Kullanıcı Adı"
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111827"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#374151"))
            }
        }
        rootLayout.addView(emailInput)

        // Spacer
        val spacer1 = View(uiContext).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        }
        rootLayout.addView(spacer1)

        // Password Input
        val passwordInput = EditText(uiContext).apply {
            hint = "Şifre"
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111827"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#374151"))
            }
        }
        rootLayout.addView(passwordInput)

        // Spacer
        val spacer2 = View(uiContext).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))
        }
        rootLayout.addView(spacer2)

        // Login Button
        val loginBtn = Button(uiContext).apply {
            text = "Giriş Yap ve Oturumu Çek"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4F46E5"))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()

                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(uiContext, "Lütfen e-posta ve şifrenizi girin!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                startAutoLogin(uiContext, email, password, prefs, isRenewVip = false) {
                    settingsDialog?.dismiss()
                }
            }
        }
        rootLayout.addView(loginBtn)

        // 3. Manuel ve Sifirlama Bolumu
        val extraTitle = TextView(uiContext).apply {
            text = "ALTERNATİF & SIFIRLAMA"
            setTextColor(Color.parseColor("#9CA3AF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(4), dp(22), dp(4), dp(8))
        }
        rootLayout.addView(extraTitle)

        // Manual token input
        val manualTokenInput = EditText(uiContext).apply {
            hint = if (hasCustom) "•••••••• (Özel dk_ses kayıtlı)" else "Manuel dk_ses anahtarı (İsteğe bağlı)"
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F131C"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#262C3A"))
            }
        }
        rootLayout.addView(manualTokenInput)

        settingsDialog = AlertDialog.Builder(uiContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Deokwave Ayarları")
            .setView(scroll)
            .setPositiveButton("Manuel Kaydet") { _, _ ->
                val manual = manualTokenInput.text.toString().trim()
                if (manual.isNotEmpty()) {
                    prefs.edit().putString("custom_token", manual).apply()
                    Toast.makeText(uiContext, "Özel dk_ses kaydedildi!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("VIP'e Sıfırla") { _, _ ->
                prefs.edit().remove("custom_token").remove("vip_renewed_token").apply()
                Toast.makeText(uiContext, "Varsayılan 4K VIP oturumuna dönüldü.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Kapat", null)
            .create()

        settingsDialog.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startAutoLogin(
        uiContext: Context,
        email: String,
        pass: String,
        prefs: android.content.SharedPreferences,
        isRenewVip: Boolean = false,
        onSuccess: () -> Unit
    ) {
        fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            uiContext.resources.displayMetrics
        ).toInt()

        val mainHandler = Handler(Looper.getMainLooper())
        var isCaptured = false
        var loginDialog: AlertDialog? = null

        val dialogLayout = LinearLayout(uiContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D0D14"))
            }
        }

        val statusText = TextView(uiContext).apply {
            text = "Deokwave oturumu açılıyor ve dk_ses anahtarı çekiliyor..."
            setTextColor(Color.parseColor("#93C5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(8))
        }
        dialogLayout.addView(statusText)

        val progressBar = ProgressBar(uiContext, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        dialogLayout.addView(progressBar)

        val webView = WebView(uiContext).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)).apply {
                topMargin = dp(8)
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        dialogLayout.addView(webView)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        fun checkAndSaveToken(): Boolean {
            val cookies = cookieManager.getCookie("https://deokwave.com") ?: ""
            val regex = Regex("""(?:^|;\s*)dk_ses=([^;]+)""")
            val match = regex.find(cookies)
            val token = match?.groupValues?.get(1)?.trim()

            if (!token.isNullOrEmpty() && token != "deleted" && token != "null") {
                if (!isCaptured) {
                    isCaptured = true
                    if (isRenewVip) {
                        prefs.edit().putString("vip_renewed_token", token).remove("custom_token").apply()
                        Toast.makeText(uiContext, "VIP 4K oturumu 30 gün başarıyla yenilendi!", Toast.LENGTH_LONG).show()
                    } else {
                        prefs.edit().putString("custom_token", token).apply()
                        Toast.makeText(uiContext, "Giriş başarılı! dk_ses anahtarı otomatik çekildi.", Toast.LENGTH_LONG).show()
                    }
                    mainHandler.removeCallbacksAndMessages(null)
                    loginDialog?.dismiss()
                    onSuccess()
                }
                return true
            }
            return false
        }

        // 500ms araliklarla cerezi yokla
        val cookiePollRunnable = object : Runnable {
            override fun run() {
                if (!isCaptured) {
                    if (!checkAndSaveToken()) {
                        mainHandler.postDelayed(this, 500)
                    }
                }
            }
        }

        val quotedEmail = JSONObject.quote(email)
        val quotedPass = JSONObject.quote(pass)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndSaveToken()

                if (url?.contains("/login") == true && !isCaptured) {
                    val injectJs = """
                        (function() {
                            try {
                                var emailInput = document.getElementById('emailInput');
                                var passwordInput = document.getElementById('passwordInput');
                                var loginBtn = document.getElementById('loginBtn') || document.querySelector('button[type="submit"]');
                                var loginForm = document.getElementById('loginForm');
                                if (emailInput && passwordInput) {
                                    emailInput.value = $quotedEmail;
                                    passwordInput.value = $quotedPass;
                                    emailInput.dispatchEvent(new Event('input', { bubbles: true }));
                                    passwordInput.dispatchEvent(new Event('input', { bubbles: true }));
                                    emailInput.dispatchEvent(new Event('change', { bubbles: true }));
                                    passwordInput.dispatchEvent(new Event('change', { bubbles: true }));
                                    setTimeout(function() {
                                        if (loginBtn) {
                                            loginBtn.click();
                                        } else if (loginForm) {
                                            loginForm.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                                        }
                                    }, 600);
                                }
                            } catch(e) { console.log(e); }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(injectJs, null)
                }
            }
        }

        loginDialog = AlertDialog.Builder(uiContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Deokwave Giriş")
            .setView(dialogLayout)
            .setNegativeButton("Vazgeç") { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                mainHandler.removeCallbacksAndMessages(null)
                webView.stopLoading()
                webView.destroy()
            }
            .create()

        loginDialog.show()
        mainHandler.post(cookiePollRunnable)
        webView.loadUrl("https://deokwave.com/login/")
    }
}

