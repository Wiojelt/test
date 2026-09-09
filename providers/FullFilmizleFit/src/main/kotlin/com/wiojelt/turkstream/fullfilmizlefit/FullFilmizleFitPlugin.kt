package com.wiojelt.turkstream.fullfilmizlefit

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FullFilmizleFitPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FullFilmizleFit())
    }
}
