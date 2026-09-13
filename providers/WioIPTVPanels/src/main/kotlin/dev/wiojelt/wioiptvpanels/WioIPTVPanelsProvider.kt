package dev.wiojelt.wioiptvpanels

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class WioIPTVPanelsProvider : MainAPI() {
    override var mainUrl = "https://wioiptv.local"
    override var name = "WioIPTVPanels"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "tr_spor" to "⚽ Türk Spor Kanalları",
        "tr_ulusal" to "🇹🇷 Türk Ulusal Kanalları",
        "world_sports_uk_us" to "🇬🇧 / 🇺🇸 Sky Sports, TNT & ESPN",
        "world_sports_eu" to "🇪🇺 Avrupa Spor Kanalları (Canal+, DAZN, Polsat, Ziggo)"
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
        val channel = ChannelDatabase.getChannelById(channelId) ?: return null

        return newLiveStreamLoadResponse(
            name = channel.name,
            url = url,
            dataUrl = channel.id
        ) {
            this.posterUrl = channel.logo
            this.plot = "${channel.name} · ${channel.description}\n\nAktif Paneller: Eagle, 8kGold, Spor 20x, World Sport (${channel.streams.size} alternatif yayın)"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channel = ChannelDatabase.getChannelById(data) ?: return false

        val context = WioIPTVPanelsPlugin.appContext
        val (eagle, gold, spor, world) = if (context != null) {
            val prefs = context.getSharedPreferences(WioIPTVPanelsPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            listOf(
                prefs.getBoolean(WioIPTVPanelsPlugin.KEY_EAGLE, true),
                prefs.getBoolean(WioIPTVPanelsPlugin.KEY_8KGOLD, true),
                prefs.getBoolean(WioIPTVPanelsPlugin.KEY_SPOR20X, true),
                prefs.getBoolean(WioIPTVPanelsPlugin.KEY_WORLDSPORT, true)
            )
        } else {
            listOf(true, true, true, true)
        }

        val activeStreams = channel.streams.filter { s ->
            when (s.panel) {
                "Eagle" -> eagle
                "8kGold" -> gold
                "Spor20x" -> spor
                "WorldSport" -> world
                else -> true
            }
        }

        for (stream in activeStreams) {
            val qualVal = when (stream.quality) {
                "4K" -> Qualities.P2160.value
                "FHD" -> Qualities.P1080.value
                "HD" -> Qualities.P720.value
                else -> Qualities.Unknown.value
            }
            val isM3u8 = stream.url.contains(".m3u8", ignoreCase = true) || stream.url.contains("extension=m3u8", ignoreCase = true)
            callback.invoke(
                newExtractorLink(
                    source = "[${stream.panel}]",
                    name = stream.label,
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
