package com.wiojelt.turkstream.filmkovasi

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmkovasiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Filmkovasi())
    }
}
