package com.wiojelt.turkstream.hdfilmizlebest

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HDFilmizleBestPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmizleBest())
    }
}
