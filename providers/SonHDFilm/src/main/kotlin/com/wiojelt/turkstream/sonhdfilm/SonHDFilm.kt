package com.wiojelt.turkstream.sonhdfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
        val fromNodes = document.select("iframe, video, source").mapNotNull { node ->
            listOf("src", "data-src", "data-vsrc", "ysrc", "data-litespeed-src")
                .asSequence().map { node.attr(it).trim() }.firstOrNull { it.isNotBlank() }
        }
        // Bazı siteler iframe'i JS ile sonradan basıyor; açık player URL'lerini inline HTML'den de al.
        val fromHtml = Regex("""https?://[^"'<>\s]+""").findAll(document.html()).map { it.value }.filter { value ->
            value.contains("player", true) || value.contains("video", true) || value.contains("embed", true) || value.contains("play", true)
        }.toList()
        return (fromNodes + fromHtml)
            .filter { value -> !value.contains("youtube.com/embed", true) && !value.contains("youtube-nocookie.com", true) }
            .mapNotNull { fixUrlNull(it) }.distinct()
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
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://sonhdfilm.net/seri-filmler/"
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
        val plot = document.selectFirst(".description, .card-text")?.text()?.trim()
        trace("load parsed title=$title poster=${poster != null} plot=${!plot.isNullOrBlank()}")
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        trace("loadLinks start data=$data")
        val document = app.get(data).document
        val streams = (listOfNotNull(fixUrlNull(document.selectFirst("iframe[src], iframe[data-src], video[src], source[src]")?.attr("src")?.trim())) + mediaCandidates(document)).distinct()
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
