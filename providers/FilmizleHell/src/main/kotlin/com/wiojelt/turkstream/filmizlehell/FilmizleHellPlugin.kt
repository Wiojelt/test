package com.wiojelt.turkstream.filmizlehell

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmizleHellPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmizleHell())
        registerExtractorAPI(PlayTurka())
    }
}
