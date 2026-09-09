package com.wiojelt.turkstream.sonhdfilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SonHDFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SonHDFilm())
    }
}
