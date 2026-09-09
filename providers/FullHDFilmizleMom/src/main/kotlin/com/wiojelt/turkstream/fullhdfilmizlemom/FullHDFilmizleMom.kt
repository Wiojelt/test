package com.wiojelt.turkstream.fullhdfilmizlemom

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import kotlin.random.Random
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class FullHDFilmizleMom : MainAPI() {
    private val logTag = "TS-FullHDFilmizleMom"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://www.fullhdfilmizle.mom"
    override var name = "FullHDFilmizleMom Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://www.fullhdfilmizle.mom/turkce-dublaj-filmler/" to "Film ve Diziler",
        "https://www.fullhdfilmizle.mom/turkce-dublaj-filmler/" to "Türkçe Dublaj Filmler",
        "https://www.fullhdfilmizle.mom/turkce-altyazili-filmler/" to "Türkçe Altyazılı Filmler",
        "https://www.fullhdfilmizle.mom/yerli-filmler/" to "Yerli Filmler",
        "https://www.fullhdfilmizle.mom/imdb-en-iyiler/" to "IMDb En İyiler",
        "https://www.fullhdfilmizle.mom/tur/aile/" to "Aile",
        "https://www.fullhdfilmizle.mom/tur/animasyon/" to "Animasyon",
        "https://www.fullhdfilmizle.mom/tur/belgesel/" to "Belgesel",
        "https://www.fullhdfilmizle.mom/tur/bilim-kurgu/" to "Bilim Kurgu",
        "https://www.fullhdfilmizle.mom/tur/biyografi/" to "Biyografi",
        "https://www.fullhdfilmizle.mom/tur/dini/" to "Dini",
        "https://www.fullhdfilmizle.mom/tur/dram/" to "Dram",
        "https://www.fullhdfilmizle.mom/tur/fantastik/" to "Fantastik",
        "https://www.fullhdfilmizle.mom/tur/gerilim/" to "Gerilim",
        "https://www.fullhdfilmizle.mom/tur/gizem/" to "Gizem",
        "https://www.fullhdfilmizle.mom/tur/komedi/" to "Komedi",
        "https://www.fullhdfilmizle.mom/tur/korku/" to "Korku",
        "https://www.fullhdfilmizle.mom/tur/macera/" to "Macera",
        "https://www.fullhdfilmizle.mom/tur/muzik/" to "Müzik",
        "https://www.fullhdfilmizle.mom/tur/romantik/" to "Romantik",
        "https://www.fullhdfilmizle.mom/tur/savas/" to "Savaş",
        "https://www.fullhdfilmizle.mom/tur/spor/" to "Spor",
        "https://www.fullhdfilmizle.mom/tur/suc/" to "Suç",
        "https://www.fullhdfilmizle.mom/tur/tarih/" to "Tarih",
        "https://www.fullhdfilmizle.mom/tur/tv-film/" to "TV film"
    )

    private fun mediaCandidates(document: org.jsoup.nodes.Document): List<String> {
        val fromNodes = document.select("iframe, video, source").flatMap { node ->
            listOf("data-vsrc", "data-src", "data-litespeed-src", "data-original", "src", "ysrc")
                .map { node.attr(it).trim() }.filter { it.isNotBlank() && it != "about:blank" }
        }
        // Bazı siteler iframe'i JS ile sonradan basıyor; açık player URL'lerini inline HTML'den de al.
        val fromHtml = Regex("""https?://[^"'<>\s]+""").findAll(document.html()).map { it.value }.filter { value ->
            value.contains("player", true) || value.contains("video", true) || value.contains("embed", true) || value.contains("play", true)
        }.toList()
        return (fromNodes + fromHtml + decodedPlayers(document.html()))
            .filter { value -> value != "about:blank" && !value.startsWith("data:") && !value.contains("youtube.com/embed", true) && !value.contains("youtube-nocookie.com", true) }
            .mapNotNull { fixUrlNull(it) }.distinct()
    }

    private fun decodedJsonFrames(html: String): List<String> = Regex("""atob\(["']([^"']+)["']\)""").findAll(html).flatMap { match ->
        runCatching { Regex("""https?://[^"'\\]+""").findAll(base64Decode(match.groupValues[1]).replace("\\/", "/")).map { it.value }.toList() }.getOrDefault(emptyList()).asSequence()
    }.filterNot { it.contains("youtube", true) }.mapNotNull { fixUrlNull(it) }.distinct().toList()

    private fun decodedPlayers(html: String, wanted: String? = null): List<String> {
        val values = mutableListOf<Pair<String, String>>()
        Regex("""var\s+ilkpartkod\s*=\s*['"]([^'"]+)""").find(html)?.groupValues?.getOrNull(1)?.let { values += "0" to it }
        Regex("""pdata\[['"]prt_([^'"]+)['"]]\s*=\s*['"]([^'"]+)""").findAll(html).forEach { values += it.groupValues[1] to it.groupValues[2] }
        return values.filter { wanted == null || it.first == wanted }.mapNotNull { (_, raw) ->
            runCatching {
                val payload = if (raw.startsWith("PG")) raw else "PG" + "BSZtFmcmlGP".reversed().removePrefix("PG") + raw
                val decoded = base64Decode(payload)
                Regex("""src=["']([^"']+)""").find(decoded)?.groupValues?.get(1)
            }.getOrNull()
        }.filterNot { it.contains("youtube", true) }.mapNotNull { fixUrlNull(it) }.distinct()
    }

    private fun isTrailer(value: String): Boolean = value.contains("youtube.com", true) ||
        value.contains("youtube-nocookie.com", true) || value.contains("youtu.be", true)

    private suspend fun expandPlayerPages(candidates: List<String>, referer: String): List<String> =
        candidates.filter { it.startsWith(mainUrl, ignoreCase = true) && it.contains("vr_set", true) }
            .flatMap { player ->
                try { mediaCandidates(app.get(player, referer = referer).document) }
                catch (_: Exception) { emptyList() }
            }

    private fun textOrAttr(item: Element, selector: String, attr: String): String? {
        val target = if (selector.isBlank()) item else item.selectFirst(selector) ?: return null
        return (if (attr == "text") target.text() else target.attr(attr)).trim().ifBlank { null }
    }

    private fun itemTitle(item: Element) = item.selectFirst(".film-ismi a")?.text()?.trim()
    private fun itemUrl(item: Element) = fixUrlNull(item.selectFirst("a[href]")?.attr("href")?.trim())
    private fun itemPoster(item: Element): String? {
        val node = item.selectFirst("img") ?: return null
        val candidate = node.attr("data-src").ifBlank { node.attr("data-lazy-src") }
            .ifBlank { node.attr("src") }.ifBlank { node.attr("data-original") }
        return fixUrlNull(candidate)
    }
    private fun itemCategory(item: Element) = (null)?.ifBlank { null } ?: "Film ve Diziler"

    private fun Element.toResult(): SearchResponse? {
        val title = itemTitle(this) ?: return null
        val url = itemUrl(this) ?: return null
        return newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = itemPoster(this@toResult) }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        trace("getMainPage start page=$page url=${request.data}")
        val document = app.get(request.data).document
        if ("".isNotBlank()) {
            val pageSections = document.select("").mapNotNull { container ->
                val sectionName = container.selectFirst("h1, h2, h3, h4")?.text()?.trim().orEmpty()
                val rows = container.select(".listmovie").mapNotNull { it.toResult() }
                if (rows.isEmpty()) null else HomePageList(sectionName.ifBlank { request.name }, rows, true)
            }
            trace("getMainPage parsed sections=${pageSections.size}")
            return newHomePageResponse(pageSections, false)
        }
        val rows = document.select(".listmovie").mapNotNull { it.toResult() }
        trace("getMainPage parsed rows=${rows.size}")
        return newHomePageResponse(request.name, rows)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val target = "".takeIf { it.isNotBlank() }
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://www.fullhdfilmizle.mom/turkce-dublaj-filmler/"
        trace("search query=$query url=$target")
        return app.get(target).document.select(".listmovie").mapNotNull { it.toResult() }
            .filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        trace("load start url=$url")
        val document = app.get(url).document
        val titleElement: Element = document.selectFirst("h1, [property='og:title']") ?: return null
        val title = (if (titleElement.hasAttr("content")) titleElement.attr("content") else titleElement.text())
            .trim().ifBlank { return null }
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val plot = document.selectFirst(".description, .card-text, [itemprop=description], .film-description, .ackl")?.text()?.trim()
        val trailer = document.select("iframe, [data-video_url]").map { node -> listOf("data-video_url", "data-vsrc", "data-src", "data-litespeed-src", "src").map { node.attr(it) }.firstOrNull { it.contains("youtube", true) } }.filterNotNull().firstOrNull()
        trace("load parsed title=$title poster=${poster != null} plot=${!plot.isNullOrBlank()}")
        val inlineEpisodeNodes = document.select("li.psec[id]").filterNot { it.id().contains("fragman", true) }
        if (inlineEpisodeNodes.size > 1) {
            val episodes = inlineEpisodeNodes.mapIndexed { index, node -> newEpisode("$url|${node.id()}") { name = node.text().trim().ifBlank { "${index + 1}. Bölüm" }; episode = index + 1; posterUrl = poster } }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) { posterUrl = poster; this.plot = plot; addTrailer(trailer) }
        }
        val looksSeries = url.contains("/dizi/", true) || title.contains("tüm bölümleri", true) || document.select("a[href*='/bolum/']").isNotEmpty()
        if (looksSeries) {
            val links = document.select("a[href]").mapNotNull { a ->
                val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
                val label = a.text().trim()
                if (href.contains("/bolum/", true) || label.contains("bölüm", true) || Regex("/\\d+/?$").containsMatchIn(href)) href to label else null
            }.distinctBy { it.first }
            val episodes = links.mapIndexed { index, pair -> newEpisode(pair.first) { this.name = pair.second.ifBlank { "${index + 1}. Bölüm" }; episode = index + 1; this.posterUrl = poster } }
            if (episodes.isNotEmpty()) return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster; this.plot = plot; addTrailer(trailer)
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            this.plot = plot
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("HDF", "data » ${data}")
        val response = app.get(data)
        val document = response.document
        val nonce = Regex("""(?:video|nonce)\s*[:=]\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
            .find(response.text)?.groupValues?.getOrNull(1)
            ?: document.selectFirst("#playex[data-nonce]")?.attr("data-nonce")
        val postId = document.selectFirst("[data-post-id]")?.attr("data-post-id") ?: document.selectFirst("[data-part]")?.attr("data-part") ?: document.selectFirst("#comment_post_ID")?.attr("value")
        val players = document.select("[data-post-id][data-player-name]")
            .map {
                Triple(
                    it.attr("data-post-id"),
                    it.attr("data-player-name"),
                    it.attr("data-part-key"),
                )
            }
            .filter { (id, player, _) -> id.isNotBlank() && player.isNotBlank() }
            .ifEmpty {
                if (!postId.isNullOrBlank()) listOf(Triple(postId, "SetPlay", "")) else emptyList()
            }
            .distinct()

        var found = false
        if (!nonce.isNullOrBlank()) {
            players.forEach { (id, player, partKey) ->
                val ajax = runCatching {
                    app.post(
                        "$mainUrl/wp-admin/admin-ajax.php",
                        referer = data,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data = mapOf(
                            "action" to "get_video_url",
                            "nonce" to nonce,
                            "post_id" to id,
                            "player_name" to player,
                            "part_key" to partKey,
                        ),
                    ).parsedSafe<VideoAjaxResponse>()
                }.getOrNull()
                val iframe = ajax?.data?.url ?: return@forEach
                found = if (iframe.contains("setplay.", true)) {
                    resolveSetPlay(iframe, data, callback) || found
                } else {
                    loadExtractor(iframe, data, subtitleCallback, callback) || found
                }
            }
        }

        if (!found) {
            document.select("iframe[src], iframe[data-src]").forEach { frame ->
                val iframe = frame.attr("data-src").ifBlank { frame.attr("src") }
                if (iframe.isNotBlank() && !iframe.contains("youtube.com/embed")) {
                    found = loadExtractor(iframe, data, subtitleCallback, callback) || found
                }
            }
        }

        return found
    }

    private suspend fun resolveSetPlay(
        setPlayUrl: String,
        pageReferer: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val setPlay = app.get(setPlayUrl, referer = pageReferer)
        val frameArgs = Regex(
            """SPG\.cerceve\([^,]+,\s*[\"]([^\"]+)[\"]\s*,\s*[\"]([^\"]+)[\"]"""
        ).find(setPlay.text) ?: return false
        val encrypted = base64DecodeArray(frameArgs.groupValues[1])
        val key = base64DecodeArray(frameArgs.groupValues[2])
        if (key.isEmpty()) return false
        val fastPlayUrl = encrypted.mapIndexed { index, byte ->
            (byte.toInt() xor key[index % key.size].toInt()).toByte()
        }.toByteArray().toString(Charsets.UTF_8).substringBefore('|')
        if (!fastPlayUrl.startsWith("http")) return false

        val fastPlay = app.get(fastPlayUrl, referer = setPlayUrl)
        val sp = Regex("""[\"]sp[\"]\s*:\s*[\"]([^\"]+)[\"]""")
            .find(fastPlay.text)?.groupValues?.getOrNull(1) ?: return false
        val spTime = Regex("""[\"]spT[\"]\s*:\s*(\d+)""")
            .find(fastPlay.text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return false
        val stream = Regex("""stream\s*:\s*[\"]([^\"]+)[\"]""")
            .find(fastPlay.text)?.groupValues?.getOrNull(1)
            ?.replace("&amp;", "&") ?: return false
        val origin = fastPlayUrl.substringBefore("/video/")
        val manifest = if (stream.startsWith("http")) stream else "$origin/${stream.trimStart('/')}"
        val randomPart = Random.nextLong(2_176_782_336L).toString(36)
        val proof = "$sp|$spTime|$randomPart"
        var hash = 0x811c9dc5u
        proof.forEach { hash = (hash xor it.code.toUInt()) * 0x01000193u }
        val xSp = "$spTime.$randomPart.${hash.toString(16)}"

        callback(
            newExtractorLink(name, "$name - SetPlay", manifest, ExtractorLinkType.M3U8) {
                this.referer = fastPlayUrl
                this.quality = Qualities.Unknown.value
                this.headers = mapOf("X-Sp" to xSp)
            }
        )
        return true
    }

    private data class VideoAjaxResponse(
        @JsonProperty("success") val success: Boolean = false,
        @JsonProperty("data") val data: VideoAjaxData? = null,
    )

    private data class VideoAjaxData(
        @JsonProperty("url") val url: String? = null,
    )

    private data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
}
