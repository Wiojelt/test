package com.wiojelt.turkstream.fullfilmizlefit

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class FullFilmizleFit : MainAPI() {
    private val logTag = "TS-FullFilmizleFit"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://fullfilmizle.fit"
    override var name = "FullFilmizleFit Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://fullfilmizle.fit/yapim/2024/" to "Film ve Diziler",
        "https://fullfilmizle.fit/film-arsivi/" to "Film Arşivi",
        "https://fullfilmizle.fit/filmizle/yerli-filmler/" to "Yerli Filmler",
        "https://fullfilmizle.fit/filmizle/turkce-altyazili-filmler/" to "Türkçe Altyazılı Filmler",
        "https://fullfilmizle.fit/filmizle/turkce-dublaj-film/" to "Türkçe Dublaj Filmler",
        "https://fullfilmizle.fit/filmizle/18-erotik-filmler/" to "+18 Filmler",
        "https://fullfilmizle.fit/filmizle/aile-filmleri/" to "Aile Filmleri",
        "https://fullfilmizle.fit/filmizle/aksiyon-filmleri/" to "Aksiyon Filmleri",
        "https://fullfilmizle.fit/filmizle/animasyon-filmleri/" to "Animasyon Filmleri",
        "https://fullfilmizle.fit/filmizle/belgesel/" to "Belgesel",
        "https://fullfilmizle.fit/filmizle/bilim-kurgu-filmleri/" to "Bilim Kurgu Filmleri",
        "https://fullfilmizle.fit/filmizle/biyografi-filmleri/" to "Biyografi Filmleri",
        "https://fullfilmizle.fit/filmizle/dram-filmleri/" to "Dram Filmleri",
        "https://fullfilmizle.fit/filmizle/fantastik-filmler/" to "Fantastik Filmler",
        "https://fullfilmizle.fit/filmizle/fragman/" to "Fragman",
        "https://fullfilmizle.fit/filmizle/genclik-filmleri/" to "Gençlik Filmleri",
        "https://fullfilmizle.fit/filmizle/gerilim-filmleri/" to "Gerilim Filmleri",
        "https://fullfilmizle.fit/filmizle/gizem-filmleri/" to "Gizem Filmleri",
        "https://fullfilmizle.fit/filmizle/hint-filmleri/" to "Hint Filmleri",
        "https://fullfilmizle.fit/filmizle/komedi-filmleri/" to "Komedi Filmleri",
        "https://fullfilmizle.fit/filmizle/korku-filmleri/" to "Korku Filmleri",
        "https://fullfilmizle.fit/filmizle/macera-filmleri/" to "Macera Filmleri",
        "https://fullfilmizle.fit/filmizle/muzikal-filmler/" to "Müzikal Filmler",
        "https://fullfilmizle.fit/filmizle/psikolojik-filmler/" to "Psikolojik Filmler",
        "https://fullfilmizle.fit/filmizle/romantik-filmler/" to "Romantik Filmler"
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
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://fullfilmizle.fit/yapim/2024/"
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
