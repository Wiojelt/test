package dev.wiojelt.birdirbir

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class BirdirbirProvider : MainAPI() {
    override var mainUrl = "https://birdirbir.iptv"
    override var name = "Birdirbir IPTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "tr_spor_bein" to "⚽ beIN Sports Kanalları",
        "tr_spor_ssport_tivibu" to "🏆 S Sport & Tivibu Spor",
        "tr_spor_diger" to "🥊 Ulusal & Diğer Spor Kanalları",
        "tr_ulusal_genel" to "🇹🇷 Türk Ulusal Kanalları",
        "tr_ulusal_haber" to "📰 Haber Kanalları",
        "tr_ulusal_belgesel_cocuk" to "🦁 Belgesel & Çocuk Kanalları",
        "world_spor_uk" to "🇬🇧 İngiltere Spor (Sky & TNT)",
        "world_spor_us" to "🇺🇸 ABD Spor (ESPN & Diğer)",
        "world_spor_eu" to "🇪🇺 Avrupa Spor (Canal+, DAZN, Sky, Polsat)",
        "world_ulusal" to "🌍 Dünya Ulusal Kanalları"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val catId = request.data
        val channels = ChannelDatabase.getChannelsByCategory(catId)
        val searchResponses = channels.map { ch ->
            newLiveSearchResponse(
                name = ch.name,
                url = "${mainUrl}/channel/${ch.id}",
                type = TvType.Live
            ) {
                this.posterUrl = ch.logo
            }
        }
        return newHomePageResponse(request.name, searchResponses)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return ChannelDatabase.searchChannels(query).map { ch ->
            newLiveSearchResponse(
                name = ch.name,
                url = "${mainUrl}/channel/${ch.id}",
                type = TvType.Live
            ) {
                this.posterUrl = ch.logo
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val channelId = url.substringAfterLast("/")
        val channel = ChannelDatabase.getChannel(channelId) ?: return null

        return newLiveStreamLoadResponse(
            name = channel.name,
            url = url,
            dataUrl = channel.id
        ) {
            this.posterUrl = channel.logo
            this.plot = "${channel.name} · Birdirbir IPTV Canlı Yayın\n\nToplam ${channel.streams.size} alternatif akış (Eagle, 8kGold, Spor 20x, World Sport)"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channel = ChannelDatabase.getChannel(data) ?: return false

        val context = BirdirbirPlugin.appContext
        val (eagle, gold, spor, world) = if (context != null) {
            val prefs = context.getSharedPreferences(BirdirbirPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            listOf(
                prefs.getBoolean(BirdirbirPlugin.KEY_EAGLE, true),
                prefs.getBoolean(BirdirbirPlugin.KEY_8KGOLD, true),
                prefs.getBoolean(BirdirbirPlugin.KEY_SPOR20X, true),
                prefs.getBoolean(BirdirbirPlugin.KEY_WORLDSPORT, true)
            )
        } else {
            listOf(true, true, true, true)
        }

        val activeStreams = channel.streams.filter { s ->
            when (s.panel.lowercase()) {
                "eagle" -> eagle
                "8kgold" -> gold
                "spor20x" -> spor
                "worldsport" -> world
                else -> true
            }
        }

        for (stream in activeStreams) {
            val nameLower = stream.name.lowercase()
            val qualVal = when {
                nameLower.contains("4k") || nameLower.contains("2160") -> Qualities.P2160.value
                nameLower.contains("fhd") || nameLower.contains("1080") -> Qualities.P1080.value
                nameLower.contains("hd") || nameLower.contains("720") -> Qualities.P720.value
                else -> Qualities.Unknown.value
            }
            val isM3u8 = stream.url.contains(".m3u8", ignoreCase = true) || stream.url.contains("m3u8", ignoreCase = true)
            callback.invoke(
                newExtractorLink(
                    source = "[${stream.panel}]",
                    name = stream.name,
                    url = stream.url,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.quality = qualVal
                }
            )
        }

        return true
    }
}
