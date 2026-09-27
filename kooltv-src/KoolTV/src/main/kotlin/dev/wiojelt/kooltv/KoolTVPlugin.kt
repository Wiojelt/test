package dev.wiojelt.kooltv

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@CloudstreamPlugin
class KoolTVPlugin : Plugin() {
    override fun load(context: Context) {
        val preferences = context.getSharedPreferences("kooltv_settings", Context.MODE_PRIVATE)
        val provider = KoolTVProvider(preferences)
        registerMainAPI(provider)
        openSettings = { ui ->
            val countries = provider.countries()
            val selectedIndex = countries.indexOf(provider.selectedCountry()).coerceAtLeast(0)
            AlertDialog.Builder(ui)
                .setTitle("Kool TV · Ülke")
                .setSingleChoiceItems(countries.toTypedArray(), selectedIndex) { dialog, which ->
                    provider.setCountry(countries[which])
                    dialog.dismiss()
                    CoroutineScope(Dispatchers.IO).launch { runCatching { provider.refresh() } }
                }
                .setNegativeButton("Kapat", null)
                .show()
        }
    }
}
