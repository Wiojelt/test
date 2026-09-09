package com.wiojelt.turkstream.filmmodu

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmmoduPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Filmmodu())
    }
}
