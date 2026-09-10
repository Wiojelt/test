package com.wiojelt.turkstream.hdfilmizlebest

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder

class HDFilmizleBest : MainAPI() {
    private val logTag = "TS-HDFilmizleBest"
    private fun trace(message: String) = Log.d(logTag, message)
    override var mainUrl = "https://www.hdfilmizle.best"
    override var name = "HDFilmizleBest Test"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "https://www.hdfilmizle.best/film/" to "Film ve Diziler",
        "https://www.hdfilmizle.best/dizi/" to "Diziler",
        "https://www.hdfilmizle.best/tur/aksiyon/" to "Aksiyon",
        "https://www.hdfilmizle.best/tur/aile/" to "Aile",
        "https://www.hdfilmizle.best/tur/animasyon/" to "Animasyon",
        "https://www.hdfilmizle.best/tur/bilim-kurgu/" to "Bilim Kurgu",
        "https://www.hdfilmizle.best/tur/biyografi/" to "Biyografi",
        "https://www.hdfilmizle.best/tur/dram/" to "Dram",
        "https://www.hdfilmizle.best/tur/fantastik/" to "Fantastik",
        "https://www.hdfilmizle.best/tur/gerilim/" to "Gerilim",
        "https://www.hdfilmizle.best/tur/gizem/" to "Gizem",
        "https://www.hdfilmizle.best/tur/komedi/" to "Komedi",
        "https://www.hdfilmizle.best/tur/korku/" to "Korku",
        "https://www.hdfilmizle.best/tur/macera/" to "Macera",
        "https://www.hdfilmizle.best/tur/muzik/" to "Müzik",
        "https://www.hdfilmizle.best/tur/romantik/" to "Romantik",
        "https://www.hdfilmizle.best/tur/savas/" to "Savaş",
        "https://www.hdfilmizle.best/tur/spor/" to "Spor",
        "https://www.hdfilmizle.best/tur/suc/" to "Suç",
        "https://www.hdfilmizle.best/tur/tarih/" to "Tarih",
        "https://www.hdfilmizle.best/tur/western/" to "Western",
        "https://www.hdfilmizle.best/yil/2026/" to "2026",
        "https://www.hdfilmizle.best/yil/2025/" to "2025"
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

    private fun cleanTitle(raw: String): String = raw
        .substringBefore('|')
        .substringBefore(" – ")
        .replace(Regex("""\s*\(\d{4}\)\s*(?:izle)?\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(?:yabancı\s+film\s+)?izle\s*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun itemTitle(item: Element) = item.selectFirst(".title")?.text()?.let(::cleanTitle)?.takeIf(String::isNotBlank)
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
                val rows = container.select(".hdf-item-relative").mapNotNull { it.toResult() }
                if (rows.isEmpty()) null else HomePageList(sectionName.ifBlank { request.name }, rows, true)
            }
            trace("getMainPage parsed sections=${pageSections.size}")
            return newHomePageResponse(pageSections, false)
        }
        val rows = document.select(".hdf-item-relative").mapNotNull { it.toResult() }
        trace("getMainPage parsed rows=${rows.size}")
        return newHomePageResponse(request.name, rows)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val target = "".takeIf { it.isNotBlank() }
            ?.replace("{query}", URLEncoder.encode(query, "UTF-8")) ?: "https://www.hdfilmizle.best/film/"
        trace("search query=$query url=$target")
        return app.get(target).document.select(".hdf-item-relative").mapNotNull { it.toResult() }
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
        if (url.contains("/dizi/", true)) {
            val episodes = document.select("a[href*='/bolum/']").mapIndexedNotNull { index, a ->
                val href = fixUrlNull(a.attr("href")) ?: return@mapIndexedNotNull null
                newEpisode(href) { name = a.text().trim().ifBlank { "${index + 1}. Bölüm" }; episode = index + 1; posterUrl = poster }
            }.distinctBy { it.data }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
        val response = app.get(data)
        val document = response.document
        val nonce = Regex("""var _hdfNonce_ = [\"']([^\"']+)""").find(response.text)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Oynatıcı anahtarı bulunamadı")
        val postId = document.selectFirst("[data-post]")?.attr("data-post")
            ?: document.selectFirst("input[name=post_id]")?.attr("value")
            ?: throw ErrorLoadingException("İçerik kimliği bulunamadı")
        val body = app.post(
            "$mainUrl/ajax/videosrc/?id=$postId&lang=tr&mr=0",
            headers = mapOf("X-HDF-Nonce" to nonce), referer = data
        ).text.replace("\\/", "/")
        Regex("""\"src\":\"([^\"]+\.vtt[^\"]*)\"[^}]*\"label\":\"([^\"]+)""").findAll(body).forEach {
            subtitleCallback(SubtitleFile(it.groupValues[2].replace("\\u0131", "ı").replace("\\u00fc", "ü"), it.groupValues[1]))
        }
        val stream = Regex("""\"src\":\"([^\"]+)\"""").findAll(body).map { it.groupValues[1] }
            .lastOrNull { it.contains(".m3u8", true) || it.contains(".mp4", true) }
            ?: throw ErrorLoadingException("Video akışı bulunamadı")
        callback(newExtractorLink(name, name, stream, if (stream.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
            referer = data; quality = Qualities.Unknown.value
        })
        return true
        /*
        val initial = (listOfNotNull(fixUrlNull(document.selectFirst("iframe[src], iframe[data-src], video[src], source[src]")?.attr("src")?.trim())) + mediaCandidates(document))).filterNot(::isTrailer).distinct()
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
        */
    }
}
