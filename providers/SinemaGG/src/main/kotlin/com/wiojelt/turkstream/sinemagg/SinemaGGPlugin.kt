package com.wiojelt.turkstream.sinemagg

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SinemaGGPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SinemaGG())
    }
}
