package com.pltmustafa.inatbox

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.pltmustafa.inatbox.extractors.CDNJWPlayer
import com.pltmustafa.inatbox.extractors.DiskYandexComTr
import com.pltmustafa.inatbox.extractors.Dzen
import com.pltmustafa.inatbox.extractors.DzenRu
import com.pltmustafa.inatbox.extractors.FilmizleeeeeExtractor
import com.pltmustafa.inatbox.extractors.Vk

@CloudstreamPlugin
class InatBoxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(InatBox())
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(Dzen())
        registerExtractorAPI(DzenRu())
        registerExtractorAPI(CDNJWPlayer())
        registerExtractorAPI(FilmizleeeeeExtractor())
    }
}
