package com.wiojelt.turkstream.hdfilmizleink

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HDFilmizleInkPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmizleInk())
    }
}
