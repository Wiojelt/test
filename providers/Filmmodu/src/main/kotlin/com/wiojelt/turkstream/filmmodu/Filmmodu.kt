package com.wiojelt.turkstream.filmmodu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Filmmodu : MainAPI() {
    private val logTag = "TS-Filmmodu"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://www.filmmodu.one"
    override var name = "Filmmodu Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://www.filmmodu.one/hd-populer-filmler" to "Film ve Diziler",
        "https://www.filmmodu.one/arsiv-filmler" to "Arşiv",
        "https://www.filmmodu.one/hd-populer-filmler" to "En Çok İzlenen Filmler",
        "https://www.filmmodu.one/boxset-seri-filmler" to "Seri Filmler",
        "https://www.filmmodu.one/turkce-altyazili-hd-filmler-izle" to "Altyazılı Filmler",
        "https://www.filmmodu.one/turkce-dublaj-hd-film-izle" to "Türkçe Dublaj Filmler",
        "https://www.filmmodu.one/film-tur/4k-film-izle" to "4K",
        "https://www.filmmodu.one/film-tur/aile-filmleri" to "Aile",
        "https://www.filmmodu.one/film-tur/aksiyon" to "Aksiyon",
        "https://www.filmmodu.one/film-tur/animasyon" to "Animasyon",
        "https://www.filmmodu.one/film-tur/belgeseller" to "Belgesel",
        "https://www.filmmodu.one/film-tur/bilim-kurgu-filmleri" to "Bilim-Kurgu",
        "https://www.filmmodu.one/film-tur/dram-filmleri" to "Dram",
        "https://www.filmmodu.one/film-tur/fantastik-filmler" to "Fantastik",
        "https://www.filmmodu.one/film-tur/gerilim" to "Gerilim",
        "https://www.filmmodu.one/film-tur/gizem-filmleri" to "Gizem",
        "https://www.filmmodu.one/film-tur/hd-hint-filmleri" to "Hint Filmleri",
        "https://www.filmmodu.one/film-tur/kisa-film" to "Kısa Film",
        "https://www.filmmodu.one/film-tur/hd-komedi-filmleri" to "Komedi",
        "https://www.filmmodu.one/film-tur/korku-filmleri" to "Korku",
        "https://www.filmmodu.one/film-tur/kult-filmler-izle" to "Kült Filmler",
        "https://www.filmmodu.one/film-tur/macera-filmleri" to "Macera",
        "https://www.filmmodu.one/film-tur/muzik" to "Müzik",
        "https://www.filmmodu.one/film-tur/odullu-filmler-izle" to "Oscar Ödüllü Filmler",
        "https://www.filmmodu.one/film-tur/romantik-filmler" to "Romantik"
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
                val rows = container.select("div.movie").mapNotNull { it.toResult() }
                if (rows.isEmpty()) null else HomePageList(sectionName.ifBlank { request.name }, rows, true)
            }
            trace("getMainPage parsed sections=${pageSections.size}")
            return newHomePageResponse(pageSections, false)
        }
        val rows = document.select("div.movie").mapNotNull { it.toResult() }
        trace("getMainPage parsed rows=${rows.size}")
        return newHomePageResponse(request.name, rows)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val target = "".takeIf { it.isNotBlank() }
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://www.filmmodu.one/hd-populer-filmler"
        trace("search query=$query url=$target")
        return app.get(target).document.select("div.movie").mapNotNull { it.toResult() }
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
        val initial = (listOfNotNull(fixUrlNull(document.selectFirst("iframe[src], iframe[data-src], video[src], source[src]")?.attr("src")?.trim())) + mediaCandidates(document))
            .filterNot(::isTrailer).distinct()
        val streams = (initial + expandPlayerPages(initial, data)).filterNot(::isTrailer).distinct()
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
