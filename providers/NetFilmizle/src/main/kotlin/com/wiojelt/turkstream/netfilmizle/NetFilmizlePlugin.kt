package com.wiojelt.turkstream.netfilmizle

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NetFilmizlePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NetFilmizle())
    }
}
