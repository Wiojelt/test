package com.wiojelt.turkstream.izlesenefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class IzleseneFilm : MainAPI() {
    private val logTag = "TS-IzleseneFilm"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://www.izlesene.film"
    override var name = "IzleseneFilm Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://www.izlesene.film/en-cok-begenilen-filmler" to "Film ve Diziler",
        "https://www.izlesene.film/film-arsivi" to "Film Deposu",
        "https://www.izlesene.film/imdb-en-iyiler" to "IMDb Sıralama",
        "https://www.izlesene.film/turkce-altyazili-filmler" to "Türkçe Altyazılı",
        "https://www.izlesene.film/turkce-dublaj-filmler" to "Türkçe Dublaj",
        "https://www.izlesene.film/yerli-filmler" to "Yerli",
        "https://www.izlesene.film/izle/dram" to "Dram",
        "https://www.izlesene.film/izle/gerilim" to "Gerilim",
        "https://www.izlesene.film/izle/korku" to "Korku",
        "https://www.izlesene.film/yil/2006" to "2006",
        "https://www.izlesene.film/izle/bilim-kurgu" to "Bilim Kurgu",
        "https://www.izlesene.film/yil/2020" to "2020",
        "https://www.izlesene.film/izle/aksiyon" to "Aksiyon",
        "https://www.izlesene.film/izle/fantastik" to "Fantastik",
        "https://www.izlesene.film/izle/macera" to "Macera",
        "https://www.izlesene.film/yil/2017" to "2017",
        "https://www.izlesene.film/izle/aile" to "Aile",
        "https://www.izlesene.film/izle/animasyon" to "Animasyon",
        "https://www.izlesene.film/izle/belgesel" to "Belgesel",
        "https://www.izlesene.film/izle/gizem" to "Gizem",
        "https://www.izlesene.film/izle/komedi" to "Komedi",
        "https://www.izlesene.film/izle/muzik" to "Müzik",
        "https://www.izlesene.film/izle/romantik" to "Romantik",
        "https://www.izlesene.film/izle/savas" to "Savaş",
        "https://www.izlesene.film/izle/suc" to "Suç"
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
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://www.izlesene.film/en-cok-begenilen-filmler"
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

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        trace("loadLinks start data=$data")
        val pageUrl = data.substringBefore('|')
        val wantedPart = data.substringAfter('|', "").ifBlank { null }
        val document = app.get(pageUrl).document
        val selectedInline = wantedPart?.let { decodedPlayers(document.html(), it) }.orEmpty()
        val initial = (selectedInline + (listOfNotNull(fixUrlNull(document.selectFirst("iframe[src], iframe[data-src], video[src], source[src]")?.attr("src")?.trim())) + mediaCandidates(document))).filterNot(::isTrailer).distinct()
        val alternateStreams = document.select("a.post-page-numbers[href]").mapNotNull { fixUrlNull(it.attr("href")) }.distinct().take(20).flatMap { alt ->
            runCatching { mediaCandidates(app.get(alt, referer = pageUrl).document) }.getOrDefault(emptyList())
        }
        val streams = (initial + alternateStreams + expandPlayerPages(initial, pageUrl)).filterNot(::isTrailer).distinct()
        if (streams.isEmpty()) throw ErrorLoadingException("Video kaynağı bulunamadı")
        streams.forEach { stream ->
            if (stream.contains(".m3u8") || stream.contains(".mpd") || stream.contains(".mp4")) callback(newExtractorLink(name, name, stream, when {
                stream.contains(".m3u8") -> ExtractorLinkType.M3U8
                stream.contains(".mpd") -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }) { referer = data; quality = Qualities.Unknown.value })
            else loadExtractor(stream, data, subtitleCallback, callback)
        }
        trace("loadLinks candidates=${streams.size}")
        return true
    }
}
