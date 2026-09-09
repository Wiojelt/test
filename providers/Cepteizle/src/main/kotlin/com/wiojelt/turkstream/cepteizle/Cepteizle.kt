package com.wiojelt.turkstream.cepteizle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Cepteizle : MainAPI() {
    private val logTag = "TS-Cepteizle"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://cepteizle.net"
    override var name = "Cepteizle Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://cepteizle.net/yil/2025/" to "Film ve Diziler",
        "https://cepteizle.net/tur/aksiyon/" to "Aksiyon",
        "https://cepteizle.net/tur/korku/" to "Korku",
        "https://cepteizle.net/tur/komedi/" to "Komedi",
        "https://cepteizle.net/tur/macera/" to "Macera",
        "https://cepteizle.net/yil/2025/" to "2025 Filmleri",
        "https://cepteizle.net/yil/2026/" to "2026 Filmleri",
        "https://cepteizle.net/tur/bilim-kurgu/" to "Bilim Kurgu",
        "https://cepteizle.net/tur/suc/" to "Suç",
        "https://cepteizle.net/tur/netflix-filmleri-izle/" to "Netflix Filmleri izle",
        "https://cepteizle.net/tur/tur/yerli-filmler-izle/" to "Yerli Filmler",
        "https://cepteizle.net/tur/aile/" to "Aile",
        "https://cepteizle.net/tur/animasyon/" to "Animasyon",
        "https://cepteizle.net/tur/anime-izle/" to "Anime izle",
        "https://cepteizle.net/tur/belgesel/" to "Belgesel",
        "https://cepteizle.net/tur/blutv-filmleri/" to "BluTV Filmleri",
        "https://cepteizle.net/tur/cizgi-filmler/" to "Çizgi Filmler",
        "https://cepteizle.net/tur/dram/" to "Dram",
        "https://cepteizle.net/tur/fantastik/" to "Fantastik",
        "https://cepteizle.net/tur/filmakinesi/" to "Filmakinesi",
        "https://cepteizle.net/tur/gerilim/" to "Gerilim",
        "https://cepteizle.net/tur/gizem/" to "Gizem",
        "https://cepteizle.net/tur/hdfilmcehennemi/" to "Hdfilmcehennemi",
        "https://cepteizle.net/tur/hdfilmcenneti/" to "Hdfilmcenneti",
        "https://cepteizle.net/tur/imdb-top-250/" to "IMDB TOP 250"
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

    private fun itemTitle(item: Element) = item.selectFirst(".title")?.text()?.trim()
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
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://cepteizle.net/yil/2025/"
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
