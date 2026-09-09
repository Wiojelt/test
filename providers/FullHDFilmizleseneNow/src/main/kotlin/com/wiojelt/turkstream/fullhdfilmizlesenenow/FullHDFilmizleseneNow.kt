package com.wiojelt.turkstream.fullhdfilmizlesenenow

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Document
import android.util.Base64
import org.jsoup.nodes.Element
import java.net.URLEncoder

class FullHDFilmizleseneNow : MainAPI() {
    private val logTag = "TS-FullHDFilmizleseneNow"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://www.fullhdfilmizlesene.now"
    override var name = "FullHDFilmizleseneNow Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://www.fullhdfilmizlesene.now/filmizle/aile-filmleri" to "Film ve Diziler",
        "https://www.fullhdfilmizlesene.now/filmizle/aile-filmleri" to "Aile Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/aksiyon-filmleri" to "Aksiyon Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/animasyon-filmleri" to "Animasyon Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/belgesel-filmleri" to "Belgeseller",
        "https://www.fullhdfilmizlesene.now/filmizle/bilim-kurgu-filmleri" to "Bilim Kurgu Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/bluray-filmler" to "Blu Ray Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/cizgi-filmler" to "Çizgi Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/dram-filmler-izle" to "Dram Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/fantastik-filmler" to "Fantastik Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/gerilim-filmleri" to "Gerilim Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/gizem-filmleri" to "Gizem Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/hint-filmleri" to "Hint Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/komedi-filmleri" to "Komedi Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/korku-filmleri" to "Korku Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/macera-filmleri" to "Macera Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/muzikal-filmler" to "Müzikal Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/polisiye-filmleri" to "Polisiye Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/psikolojik-filmler" to "Psikolojik Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/romantik-filmler" to "Romantik Filmler",
        "https://www.fullhdfilmizlesene.now/filmizle/savas-filmleri" to "Savaş Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/suc-filmleri" to "Suç Filmleri",
        "https://www.fullhdfilmizlesene.now/filmizle/tarih-filmleri" to "Tarih Filmleri"
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

    private fun itemTitle(item: Element) = item.selectFirst("h2")?.text()?.trim()
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
                val rows = container.select(".film").mapNotNull { it.toResult() }
                if (rows.isEmpty()) null else HomePageList(sectionName.ifBlank { request.name }, rows, true)
            }
            trace("getMainPage parsed sections=${pageSections.size}")
            return newHomePageResponse(pageSections, false)
        }
        val rows = document.select(".film").mapNotNull { it.toResult() }
        trace("getMainPage parsed rows=${rows.size}")
        return newHomePageResponse(request.name, rows)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val target = "".takeIf { it.isNotBlank() }
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://www.fullhdfilmizlesene.now/filmizle/aile-filmleri"
        trace("search query=$query url=$target")
        return app.get(target).document.select(".film").mapNotNull { it.toResult() }
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

    private fun atob(s: String): String {
        return String(Base64.decode(s, Base64.DEFAULT))
    }

    private fun rtt(s: String): String {
        fun rot13Char(c: Char): Char {
            return when (c) {
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                else -> c
            }
        }

        return s.map { rot13Char(it) }.joinToString("")
    }

    private fun getVideoLinks(document: Document): List<Map<String, String>> {
        val scriptElement = document.select("script").firstOrNull { it.data().contains("scx =") || it.html().contains("scx =") }
        val scriptContent = scriptElement?.data()?.trim() ?: return emptyList()

        val scxData         = Regex("scx = (.*?);").find(scriptContent)?.groupValues?.get(1) ?: return emptyList()
        val scxMap: SCXData = jacksonObjectMapper().readValue(scxData)
        val keys             = listOf("atom", "advid", "advidprox", "proton", "fast", "fastly", "tr", "en")

        val linkList = mutableListOf<Map<String, String>>()

        for (key in keys) {
            val t = when (key) {
                "atom"      -> scxMap.atom?.sx?.t
                "advid"     -> scxMap.advid?.sx?.t
                "advidprox" -> scxMap.advidprox?.sx?.t
                "proton"    -> scxMap.proton?.sx?.t
                "fast"      -> scxMap.fast?.sx?.t
                "fastly"    -> scxMap.fastly?.sx?.t
                "tr"        -> scxMap.tr?.sx?.t
                "en"        -> scxMap.en?.sx?.t
                else        -> null
            }

            when (t) {
                is List<*> -> {
                    val links = t.filterIsInstance<String>().map { link -> atob(rtt(link)) }
                    linkList.add(mapOf(key to links.joinToString(",")))
                }
                is Map<*, *> -> {
                    val links = t.mapValues { (_, value) ->
                        if (value is String) atob(rtt(value)) else ""
                    }
                    val safeLinks = links.mapKeys { (key, _) ->
                        key?.toString() ?: "Unknown"
                    }
                    linkList.add(safeLinks)
                }
            }
        }

        return linkList
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FHD", "data » $data")
        val document    = app.get(data).document
        val videoLinks = getVideoLinks(document)
        Log.d("FHD", "videoLinks » $videoLinks")
        if (videoLinks.isEmpty()) return false


        for (videoMap in videoLinks) {
            for ((key, value) in videoMap) {
                val videoUrl = fixUrlNull(value) ?: continue
                if (videoUrl.contains("turbo.imgz.me")) {
                    loadExtractor("${key}||${videoUrl}", "${mainUrl}/", subtitleCallback, callback)
                } else {
                    loadExtractor(videoUrl, "${mainUrl}/", subtitleCallback, callback)
                }
            }
        }

        return true
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SCXData(
        @JsonProperty("atom")      val atom: AtomData?      = null,
        @JsonProperty("advid")     val advid: AtomData?     = null,
        @JsonProperty("advidprox") val advidprox: AtomData? = null,
        @JsonProperty("proton")    val proton: AtomData?    = null,
        @JsonProperty("fast")      val fast: AtomData?      = null,
        @JsonProperty("fastly")    val fastly: AtomData?    = null,
        @JsonProperty("tr")        val tr: AtomData?        = null,
        @JsonProperty("en")        val en: AtomData?        = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AtomData(
        @JsonProperty("sx") var sx: SXData
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SXData(
        @JsonProperty("t") var t: Any
    )
}
