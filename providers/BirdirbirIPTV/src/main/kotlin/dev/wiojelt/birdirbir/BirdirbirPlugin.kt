package dev.wiojelt.birdirbir

import android.app.AlertDialog
import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BirdirbirPlugin : Plugin() {
    companion object {
        const val PREFS_NAME = "birdirbir_iptv_prefs"
        const val KEY_EAGLE = "panel_eagle"
        const val KEY_8KGOLD = "panel_8kgold"
        const val KEY_SPOR20X = "panel_spor20x"
        const val KEY_WORLDSPORT = "panel_worldsport"

        var appContext: Context? = null
    }

    override fun load(context: Context) {
        appContext = context.applicationContext ?: context
        registerMainAPI(BirdirbirProvider())

        openSettings = { uiContext ->
            val prefs = uiContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val panelNames = arrayOf(
                "Eagle (TR Ulusal, Haber & Belgesel)",
                "8kGold (TR 4K & Ulusal Yayınlar)",
                "Spor 20x (TR Spor Kanalları & Alternatifler)",
                "World Sport (Tüm Dünya Spor Kanalları)"
            )
            val keys = arrayOf(KEY_EAGLE, KEY_8KGOLD, KEY_SPOR20X, KEY_WORLDSPORT)
            val checked = BooleanArray(4) { i -> prefs.getBoolean(keys[i], true) }

            AlertDialog.Builder(uiContext)
                .setTitle("Birdirbir IPTV Panel Yönetimi")
                .setMultiChoiceItems(panelNames, checked) { _, which, isChecked ->
                    prefs.edit().putBoolean(keys[which], isChecked).apply()
                }
                .setPositiveButton("Kaydet & Kapat", null)
                .show()
        }
    }
}
