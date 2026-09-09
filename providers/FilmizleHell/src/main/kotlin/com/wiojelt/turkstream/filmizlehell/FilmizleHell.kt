package com.wiojelt.turkstream.filmizlehell

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class FilmizleHell : MainAPI() {
    private val logTag = "TS-FilmizleHell"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://filmizlehell.net"
    override var name = "FilmizleHell Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://filmizlehell.net/filmler" to "Film ve Diziler",
        "https://filmizlehell.net/tur/1-aksiyon-002" to "Aksiyon",
        "https://filmizlehell.net/tur/7-macera-001" to "Macera",
        "https://filmizlehell.net/tur/8-bilim-kurgu-001" to "Bilim-Kurgu",
        "https://filmizlehell.net/tur/9-savas-001" to "Savaş",
        "https://filmizlehell.net/tur/10-dram-001" to "Dram",
        "https://filmizlehell.net/tur/11-yerli-film-001" to "Yerli Film",
        "https://filmizlehell.net/tur/12-gerilim-001" to "Gerilim",
        "https://filmizlehell.net/tur/13-komedi-001" to "Komedi",
        "https://filmizlehell.net/tur/14-tv-film-001" to "TV film",
        "https://filmizlehell.net/tur/15-belgesel-001" to "Belgesel",
        "https://filmizlehell.net/tur/16-aile-001" to "Aile",
        "https://filmizlehell.net/tur/17-fantastik-001" to "Fantastik",
        "https://filmizlehell.net/seriler" to "Seriler",
        "https://filmizlehell.net/sayfa/imdb-puani-yuksek-filmler" to "IMDb Puanı Yüksek Filmler",
        "https://filmizlehell.net/sayfa/imdb-puani-7-filmler" to "IMDb Puanı 7+ Filmler",
        "https://filmizlehell.net/tur/18-animasyon-001" to "Animasyon 256",
        "https://filmizlehell.net/tur/19-romantik-001" to "Romantik 500",
        "https://filmizlehell.net/tur/20-korku-001" to "Korku 483",
        "https://filmizlehell.net/tur/21-suc-001" to "Suç 503",
        "https://filmizlehell.net/tur/22-vahsi-bati-001" to "Vahşi Batı 43",
        "https://filmizlehell.net/tur/23-tarih-001" to "Tarih 188",
        "https://filmizlehell.net/tur/24-gizem-001" to "Gizem 290",
        "https://filmizlehell.net/tur/25-muzik-001" to "Müzik 95"
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

    private fun itemTitle(item: Element) = item.selectFirst("a")?.text()?.trim()
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
                val rows = container.select(".movie").mapNotNull { it.toResult() }
                if (rows.isEmpty()) null else HomePageList(sectionName.ifBlank { request.name }, rows, true)
            }
            trace("getMainPage parsed sections=${pageSections.size}")
            return newHomePageResponse(pageSections, false)
        }
        val rows = document.select(".movie").mapNotNull { it.toResult() }
        trace("getMainPage parsed rows=${rows.size}")
        return newHomePageResponse(request.name, rows)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val target = "".takeIf { it.isNotBlank() }
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://filmizlehell.net/filmler"
        trace("search query=$query url=$target")
        return app.get(target).document.select(".movie").mapNotNull { it.toResult() }
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
