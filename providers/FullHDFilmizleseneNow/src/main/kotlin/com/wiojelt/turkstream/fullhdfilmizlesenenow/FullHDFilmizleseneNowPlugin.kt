package com.wiojelt.turkstream.fullhdfilmizlesenenow

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FullHDFilmizleseneNowPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FullHDFilmizleseneNow())
    }
}
