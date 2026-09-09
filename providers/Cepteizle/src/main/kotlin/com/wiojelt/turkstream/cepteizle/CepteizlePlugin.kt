package com.wiojelt.turkstream.cepteizle

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CepteizlePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Cepteizle())
    }
}
