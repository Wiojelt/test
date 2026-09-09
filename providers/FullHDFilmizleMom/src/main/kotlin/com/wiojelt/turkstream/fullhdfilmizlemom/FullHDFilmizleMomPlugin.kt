package com.wiojelt.turkstream.fullhdfilmizlemom

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FullHDFilmizleMomPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FullHDFilmizleMom())
    }
}
