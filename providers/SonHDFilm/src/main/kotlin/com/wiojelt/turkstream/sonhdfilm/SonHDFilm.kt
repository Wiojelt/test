package com.wiojelt.turkstream.sonhdfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class SonHDFilm : MainAPI() {
    private val logTag = "TS-SonHDFilm"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://sonhdfilm.net"
    override var name = "SonHDFilm Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://sonhdfilm.net/seri-filmler/" to "Film ve Diziler",
        "https://sonhdfilm.net/turkce-altyazili-filmler/" to "Türkçe Altyazılı Filmler",
        "https://sonhdfilm.net/turkce-dublaj-filmler/" to "Türkçe Dublaj Filmler",
        "https://sonhdfilm.net/film-arsivi/" to "Film Arşivi",
        "https://sonhdfilm.net/yil/2026/" to "2026",
        "https://sonhdfilm.net/yil/2025/" to "2025",
        "https://sonhdfilm.net/yil/2024/" to "2024",
        "https://sonhdfilm.net/yil/2023/" to "2023",
        "https://sonhdfilm.net/yil/2022/" to "2022",
        "https://sonhdfilm.net/yil/2021/" to "2021",
        "https://sonhdfilm.net/yil/2020/" to "2020",
        "https://sonhdfilm.net/yil/2019/" to "2019",
        "https://sonhdfilm.net/yil/2018/" to "2018",
        "https://sonhdfilm.net/yil/2017/" to "2017",
        "https://sonhdfilm.net/yil/2016/" to "2016",
        "https://sonhdfilm.net/yil/2015/" to "2015",
        "https://sonhdfilm.net/yil/2014/" to "2014",
        "https://sonhdfilm.net/yil/2013/" to "2013",
        "https://sonhdfilm.net/yil/2012/" to "2012",
        "https://sonhdfilm.net/yil/2011/" to "2011",
        "https://sonhdfilm.net/yil/2010/" to "2010",
        "https://sonhdfilm.net/yil/2009/" to "2009",
        "https://sonhdfilm.net/yil/2008/" to "2008",
        "https://sonhdfilm.net/yil/2007/" to "2007",
        "https://sonhdfilm.net/yil/2006/" to "2006"
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

    private fun cleanTitle(raw: String): String = raw.substringBefore('|')
        .replace(Regex("""\s*\([^)]*\)"""), "")
        .replace(Regex("""\s+(?:Film\s+)?Serisi\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(?:19|20)\d{2}\s*$"""), "")
        .replace(Regex("""\s+izle\s*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun itemTitle(item: Element) = item.selectFirst(".film-ismi a")?.text()?.let(::cleanTitle)?.takeIf(String::isNotBlank)
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
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://sonhdfilm.net/seri-filmler/"
        trace("search query=$query url=$target")
        return app.get(target).document.select(".listmovie").mapNotNull { it.toResult() }
            .filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        trace("load start url=$url")
        val document = app.get(url).document
        val titleElement: Element = document.selectFirst("h1, [property='og:title']") ?: return null
        val title = cleanTitle(if (titleElement.hasAttr("content")) titleElement.attr("content") else titleElement.text())
            .ifBlank { return null }
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
