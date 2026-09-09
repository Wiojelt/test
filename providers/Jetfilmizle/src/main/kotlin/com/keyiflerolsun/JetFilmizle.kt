package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

private data class VipHlsSource(val file: String? = null)
private data class VipMp4Source(val file: String? = null, val label: String? = null)
private data class VipResponse(
    @JsonProperty("hlsSource") val hlsSource: VipHlsSource? = null,
    @JsonProperty("mp4Sources") val mp4Sources: List<VipMp4Source>? = null,
)
private data class TitanSubtitle(val label: String? = null, val file: String? = null)
private data class TitanResponse(
    @JsonProperty("stream_url") val streamUrl: String? = null,
    val subtitles: List<TitanSubtitle>? = null,
)

class JetFilmizle : MainAPI() {
    override var mainUrl = "https://jetfilmizle.now"
    override var name = "Jetfilmizle Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        mainUrl to "Ana Sayfa",
        "$mainUrl/filmler" to "Tüm Filmler",
        "$mainUrl/turkce-dublaj" to "Türkçe Dublaj",
        "$mainUrl/turkce-altyazili" to "Türkçe Altyazılı",
        "$mainUrl/diziler" to "Tüm Diziler",
        "$mainUrl/yerli-filmler" to "Yerli Filmler",
        "$mainUrl/yabanci-diziler" to "Yabancı Diziler",
        "$mainUrl/tur/aksiyon" to "Aksiyon",
        "$mainUrl/tur/animasyon" to "Animasyon",
        "$mainUrl/tur/belgesel" to "Belgesel",
        "$mainUrl/tur/bilim-kurgu" to "Bilim Kurgu",
        "$mainUrl/tur/dram" to "Dram",
        "$mainUrl/tur/fantastik" to "Fantastik",
        "$mainUrl/tur/gerilim" to "Gerilim",
        "$mainUrl/tur/gizem" to "Gizem",
        "$mainUrl/tur/komedi" to "Komedi",
        "$mainUrl/tur/korku" to "Korku",
        "$mainUrl/tur/macera" to "Macera",
        "$mainUrl/tur/romantik" to "Romantik",
        "$mainUrl/tur/suc" to "Suç"
    )

    private val logTag = "TS-Jetfilmizle"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private fun trace(message: String) = Log.d(logTag, message)
    private fun requestHeaders(referer: String) = mapOf("User-Agent" to userAgent, "Referer" to referer)

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst(".film-title-line a") ?: selectFirst(".card-title a")
            ?: select("a[href]").firstOrNull { it.text().isNotBlank() } ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val title = anchor.text().trim().substringBeforeLast(" izle").trim().ifBlank { return null }
        val image = selectFirst(".film-poster img, img")
        val poster = fixUrlNull(image?.attr("data-src")?.takeIf(String::isNotBlank) ?: image?.attr("src"))
        val type = if (href.contains("/dizi/", true)) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) { posterUrl = poster }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data, headers = requestHeaders(mainUrl)).document
        val selectors = listOf(".latest-films", ".nette-ilkler", ".diziler-section")
        val sections = selectors.mapNotNull { selector ->
            val section = document.selectFirst(selector) ?: return@mapNotNull null
            val title = section.selectFirst("h1, h2, h3, h4")?.text()?.trim().orEmpty()
                .ifBlank { selector.removePrefix(".").replace('-', ' ') }
            val items = section.select(".film-card").mapNotNull { it.toSearchResult() }
            if (items.isEmpty()) null else HomePageList(title, items, true)
        }
        trace("getMainPage sections=${sections.map { it.name + ':' + it.list.size }}")
        if (sections.isNotEmpty()) return newHomePageResponse(sections, false)
        return newHomePageResponse("Film ve Diziler", document.select(".film-card").mapNotNull { it.toSearchResult() })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val target = "$mainUrl/arama?q=${URLEncoder.encode(query, "UTF-8")}" 
        val results = app.get(target, headers = requestHeaders(mainUrl)).document.select(".film-card").mapNotNull { it.toSearchResult() }
        trace("search query=$query results=${results.size}")
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = requestHeaders(mainUrl)).document
        val titleNode = document.selectFirst("h1.film-title") ?: return null
        val title = titleNode.ownText().trim().ifBlank { titleNode.text().substringBefore("(").trim() }
        val image = document.selectFirst("img.film-poster")
        val poster = fixUrlNull(image?.attr("data-src")?.takeIf(String::isNotBlank) ?: image?.attr("src"))
        val plot = document.selectFirst(".description-text, .film-description, [itemprop=description]")?.text()?.trim()
        val year = Regex("""\b(19|20)\d{2}\b""").find(document.text())?.value?.toIntOrNull()
        val tags = document.select(".film-categories a, .catss a, [itemprop=genre]").map { it.text().trim() }.filter(String::isNotBlank)
        val actors = document.select(".cast-item, .oyuncu").mapNotNull {
            val actorName = it.selectFirst(".actor-name, .name")?.text()?.trim() ?: return@mapNotNull null
            Actor(actorName, fixUrlNull(it.selectFirst("img")?.attr("src")))
        }
        val trailer = document.select("iframe, [data-video_url]").map { node -> listOf("data-video_url", "data-vsrc", "data-src", "data-litespeed-src", "src").map { node.attr(it) }.firstOrNull { it.contains("youtube", true) } }.filterNotNull().firstOrNull()
        val recommendations = document.select("#benzers .film-card, .similar-films .film-card").mapNotNull { it.toSearchResult() }

        if (url.contains("/dizi/", true)) {
            val episodes = document.select(".player-source-btn[data-season][data-episode]").mapNotNull { button ->
                val season = button.attr("data-season").toIntOrNull() ?: return@mapNotNull null
                val episode = button.attr("data-episode").toIntOrNull() ?: return@mapNotNull null
                season to episode
            }.distinct().sortedWith(compareBy({ it.first }, { it.second })).map { (season, episode) ->
                newEpisode("$url|$season|$episode") {
                    this.name = "$episode. Bölüm"; this.season = season; this.episode = episode; this.posterUrl = poster
                }
            }
            trace("load series title=$title episodes=${episodes.size}")
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster; this.plot = plot; this.year = year; this.tags = tags
                this.recommendations = recommendations; addActors(actors); addTrailer(trailer)
            }
        }
        trace("load movie title=$title")
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster; this.plot = plot; this.year = year; this.tags = tags
            this.recommendations = recommendations; addActors(actors); addTrailer(trailer)
        }
    }

    private suspend fun emitVideoPark(
        iframeUrl: String, label: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val html = app.get(iframeUrl, headers = requestHeaders(mainUrl)).text
        if (iframeUrl.contains("/vip/")) {
            fun variable(key: String) = Regex("""(?:const|let|var)\s+$key\s*=\s*[\"']([^\"']+)[\"']""").find(html)?.groupValues?.get(1)
            val worker = variable("WORKER_URL")?.trimEnd('/') ?: return false
            val pubId = variable("PUB_ID") ?: return false
            val publisherId = variable("PUBLISHER_ID") ?: return false
            val videoTitle = variable("VIDEO_TITLE") ?: name
            val apiUrl = "$worker/api/stream?pubId=${URLEncoder.encode(pubId, "UTF-8")}&title=${URLEncoder.encode(videoTitle, "UTF-8")}&publisherId=${URLEncoder.encode(publisherId, "UTF-8")}" 
            val payload = AppUtils.parseJson<VipResponse>(app.get(apiUrl, headers = requestHeaders(iframeUrl)).text)
            payload.hlsSource?.file?.takeIf(String::isNotBlank)?.let { stream ->
                callback(newExtractorLink(name, "$label HLS", stream, ExtractorLinkType.M3U8) {
                    referer = iframeUrl; quality = Qualities.Unknown.value; headers = requestHeaders(iframeUrl)
                })
            }
            payload.mp4Sources.orEmpty().forEach { source ->
                val stream = source.file?.takeIf(String::isNotBlank) ?: return@forEach
                callback(newExtractorLink(name, "$label ${source.label.orEmpty()}".trim(), stream, ExtractorLinkType.VIDEO) {
                    referer = iframeUrl; quality = getQualityFromName(source.label); headers = requestHeaders(iframeUrl)
                })
            }
            return payload.hlsSource?.file?.isNotBlank() == true || !payload.mp4Sources.isNullOrEmpty()
        }
        if (iframeUrl.contains("/titan/")) {
            val json = Regex("""var\s+_sd\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: return false
            val payload = AppUtils.parseJson<TitanResponse>(json)
            payload.subtitles.orEmpty().forEach { sub ->
                val file = sub.file?.takeIf(String::isNotBlank) ?: return@forEach
                subtitleCallback(SubtitleFile(sub.label?.ifBlank { "Altyazı" } ?: "Altyazı", fixUrl(file)))
            }
            val stream = payload.streamUrl?.takeIf(String::isNotBlank) ?: return false
            callback(newExtractorLink(name, label, stream, if (stream.contains(".mpd")) ExtractorLinkType.DASH else ExtractorLinkType.M3U8) {
                referer = iframeUrl; quality = Qualities.Unknown.value; headers = requestHeaders(iframeUrl)
            })
            return true
        }
        return false
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val parts = data.split('|', limit = 3)
        val detailUrl = parts[0]
        val wantedSeason = parts.getOrNull(1)?.toIntOrNull()
        val wantedEpisode = parts.getOrNull(2)?.toIntOrNull()
        val document = app.get(detailUrl, headers = requestHeaders(mainUrl)).document
        val filmId = document.selectFirst("input[name=film_id]")?.attr("value")?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException("film_id bulunamadı")
        val buttons = document.select(".player-source-btn").filter { button ->
            wantedSeason == null || (button.attr("data-season").toIntOrNull() == wantedSeason && button.attr("data-episode").toIntOrNull() == wantedEpisode)
        }.distinctBy { it.attr("data-source-index") + '|' + it.attr("data-player-type") }
        trace("loadLinks filmId=$filmId season=$wantedSeason episode=$wantedEpisode sources=${buttons.size}")
        var emitted = false
        buttons.forEach { button ->
            val index = button.attr("data-source-index").ifBlank { "0" }
            val type = button.attr("data-player-type").ifBlank { "dublaj" }
            val buttonName = button.text().trim().ifBlank { "Kaynak ${index.toIntOrNull()?.plus(1) ?: index}" }
            val language = if (type.contains("altyazi", true)) "Altyazılı" else "Dublaj"
            runCatching {
                val response = app.post(
                    "$mainUrl/jetplayer",
                    headers = requestHeaders(detailUrl) + mapOf("Origin" to mainUrl, "X-Requested-With" to "XMLHttpRequest", "Content-Type" to "application/x-www-form-urlencoded"),
                    data = mapOf("film_id" to filmId, "source_index" to index, "player_type" to type),
                ).text
                val iframe = Jsoup.parse(response, mainUrl).selectFirst("iframe[src]")?.absUrl("src")?.takeIf(String::isNotBlank) ?: return@runCatching
                val label = "$language - $buttonName"
                trace("source index=$index type=$type iframe=$iframe")
                val handled = iframe.contains("videopark.top") && emitVideoPark(iframe, label, subtitleCallback, callback)
                if (!handled) loadExtractor(iframe, detailUrl, subtitleCallback, callback)
                emitted = true
            }.onFailure { Log.e(logTag, "source failed index=$index type=$type", it) }
        }
        return emitted
    }
}
