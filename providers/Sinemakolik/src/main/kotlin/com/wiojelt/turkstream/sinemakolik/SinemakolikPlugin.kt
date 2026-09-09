package com.wiojelt.turkstream.sinemakolik

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SinemakolikPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sinemakolik())
    }
}
