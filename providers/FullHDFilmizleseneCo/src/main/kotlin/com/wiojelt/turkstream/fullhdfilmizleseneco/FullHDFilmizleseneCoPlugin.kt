package com.wiojelt.turkstream.fullhdfilmizleseneco

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FullHDFilmizleseneCoPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FullHDFilmizleseneCo())
        registerExtractorAPI(Vidmixi())
    }
}
