package com.wiojelt.turkstream.deokwave

import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URLEncoder
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Deokwave(private val tokenProvider: () -> String = { DEFAULT_TOKEN }) : MainAPI() {
    override var mainUrl = "https://deokwave.com"
    override var name = "Deokwave Test"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        const val DEFAULT_TOKEN = "d0TZt3KNAcgYZFJooDdJK2CHLYP6GRHv5elScntRqwEaa6vBSoBfALDZNS48nMnf7wMLoDU6mkuuoRBXd3GCNLNvEknDFq9VsPyz"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private fun getHeaders(): Map<String, String> {
        val token = tokenProvider().trim()
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )
        if (token.isNotBlank()) {
            headers["Cookie"] = "dk_ses=$token"
        }
        return headers
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/animeler/?sort=popularity&page=" to "Popüler Animeler",
        "${mainUrl}/animeler/?sort=newest&page=" to "Yeni Eklenen Animeler",
        "${mainUrl}/animeler/?sort=rating&page=" to "En Yüksek Puanlılar",
        "${mainUrl}/animeler/?type=movie&page=" to "Anime Filmleri (4K)",
        "${mainUrl}/animeler/?genre=Action&page=" to "Aksiyon",
        "${mainUrl}/animeler/?genre=Comedy&page=" to "Komedi",
        "${mainUrl}/animeler/?genre=Fantasy&page=" to "Fantastik",
        "${mainUrl}/animeler/?genre=Romance&page=" to "Romantizm",
        "${mainUrl}/animeler/?genre=Drama&page=" to "Dram",
        "${mainUrl}/animeler/?genre=Horror&page=" to "Korku",
        "${mainUrl}/animeler/?genre=Sci-Fi%20%26%20Fantasy&page=" to "Bilim Kurgu & Fantastik"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}", headers = getHeaders()).document
        val home = document.select("a.anime-card").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.card-title")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val img = this.selectFirst("img")
        val posterUrl = fixUrlNull(img?.attr("data-src")) ?: fixUrlNull(img?.attr("src"))
        val isMovie = this.selectFirst("span.badge-type")?.text()?.contains("Film", true) == true
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search_api.php?q=${URLEncoder.encode(query, "UTF-8")}"
        val response = app.get(url, headers = getHeaders()).text
        val json = JSONObject(response)
        val animes = json.optJSONArray("animes") ?: return emptyList()
        val results = mutableListOf<SearchResponse>()
        for (i in 0 until animes.length()) {
            val item = animes.optJSONObject(i) ?: continue
            val id = item.optString("animeid")
            val title = item.optString("name_english").ifBlank { item.optString("title") }
            if (title.isBlank()) continue
            val poster = item.optString("poster").takeIf { it.isNotBlank() }
            val isMovie = item.optString("type").equals("movie", ignoreCase = true)
            val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
            val href = "$mainUrl/anime/$id/"
            results.add(
                newAnimeSearchResponse(title, href, type) {
                    this.posterUrl = poster
                }
            )
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val headers = getHeaders()
        val document = app.get(url, headers = headers).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
            ?: fixUrlNull(document.selectFirst("img.lazy, .card-poster img")?.attr("data-src"))
            ?: fixUrlNull(document.selectFirst("img.lazy, .card-poster img")?.attr("src"))
        val backdrop = fixUrlNull(document.selectFirst(".backdrop img, meta[property='og:image']")?.attr("content"))
        val description = document.selectFirst(".anime-synopsis, .synopsis, .description, p.desc")?.text()?.trim()
        val tags = document.select("a[href*='genre='], .genre, .tag").map { it.text().trim() }.distinct()
        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(document.text())?.groupValues?.get(1)?.toIntOrNull()

        val watchLink = document.selectFirst("a[href*='/watch/']")?.attr("href") ?: url
        val watchUrl = fixUrl(watchLink)

        val watchDoc = app.get(watchUrl, headers = headers).document
        val appConfigEl = watchDoc.getElementById("appConfig")

        if (appConfigEl != null) {
            val rawConfig = appConfigEl.attr("data-config")
            val conf = JSONObject(rawConfig)
            val isMovie = conf.optBoolean("isMovie")
            val animeId = conf.optString("animeId")

            if (isMovie) {
                return newMovieLoadResponse(title, url, TvType.AnimeMovie, watchUrl) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.year = year
                    this.plot = description
                    this.tags = tags
                }
            } else {
                val seasonsObj = conf.optJSONObject("seasons")
                val episodeList = mutableListOf<Episode>()

                if (seasonsObj != null) {
                    val sKeys = mutableListOf<Int>()
                    val sIter = seasonsObj.keys()
                    while (sIter.hasNext()) {
                        sIter.next().toIntOrNull()?.let { sKeys.add(it) }
                    }
                    sKeys.sort()

                    for (sNum in sKeys) {
                        val epsObj = seasonsObj.optJSONObject(sNum.toString()) ?: continue
                        val eKeys = mutableListOf<Int>()
                        val eIter = epsObj.keys()
                        while (eIter.hasNext()) {
                            eIter.next().toIntOrNull()?.let { eKeys.add(it) }
                        }
                        eKeys.sort()

                        for (eNum in eKeys) {
                            val epWatchUrl = "$mainUrl/watch/$animeId/season/$sNum/episode/$eNum"
                            val epThumbnail = epsObj.optJSONArray(eNum.toString())?.optJSONObject(0)?.optString("thumbnail")
                            episodeList.add(
                                newEpisode(epWatchUrl) {
                                    this.season = sNum
                                    this.episode = eNum
                                    this.name = "Sezon $sNum Bölüm $eNum"
                                    this.posterUrl = fixUrlNull(epThumbnail)
                                }
                            )
                        }
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.Anime, episodeList) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.year = year
                    this.plot = description
                    this.tags = tags
                }
            }
        }

        return newMovieLoadResponse(title, url, TvType.Anime, watchUrl) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.year = year
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val token = tokenProvider().trim()
        val headers = getHeaders()
        val watchUrl = fixUrl(data)
        val html = app.get(watchUrl, headers = headers).text

        val vtMatch = Regex("""window\.__VT__\s*=\s*['"]([a-f0-9]+)['"]""").find(html)
        val vt = vtMatch?.groupValues?.get(1) ?: return false

        val configMatch = Regex("""id=['"]appConfig['"][^>]*data-config=['"]([^'"]+)['"]""").find(html)
            ?: Regex("""data-config=['"]([^'"]+)['"][^>]*id=['"]appConfig['"]""").find(html)
        val rawJson = configMatch?.groupValues?.get(1)?.replace("&quot;", "\"") ?: return false
        val conf = JSONObject(rawJson)

        var foundLinks = false
        val fansubs = conf.optJSONArray("fansubs")

        val streamHeaders = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )
        if (token.isNotBlank()) {
            streamHeaders["Cookie"] = "dk_ses=$token"
        }

        if (fansubs != null && fansubs.length() > 0) {
            for (i in 0 until fansubs.length()) {
                val fs = fansubs.optJSONObject(i) ?: continue
                val fsName = fs.optString("name", "Deokwave")
                val vid = fs.optString("videoid")
                val isSibnet = fs.optBoolean("isSibnet")
                val key = fs.optString("key")

                if (isSibnet || key.startsWith("sibnet_")) {
                    val sibnetId = key.removePrefix("sibnet_")
                    if (sibnetId.isNotBlank()) {
                        loadExtractor("https://video.sibnet.ru/shell.php?videoid=$sibnetId", watchUrl, subtitleCallback, callback)
                        foundLinks = true
                    }
                }

                if (vid.isNotBlank()) {
                    val qualities = fs.optJSONArray("qualities")
                    if (qualities != null && qualities.length() > 0) {
                        for (q in 0 until qualities.length()) {
                            val qLabel = qualities.optString(q)
                            val qNum = qLabel.replace("p", "").trim()
                            val streamUrl = "https://sw2.deokwave.com/v/$vid/$qNum/?vt=$vt"
                            val displayName = when (qLabel) {
                                "2160p" -> "Deokwave - $fsName (4K Ultra HD / HDR)"
                                "1080p" -> "Deokwave - $fsName (1080p Full HD)"
                                else -> "Deokwave - $fsName ($qLabel)"
                            }

                            callback.invoke(
                                newExtractorLink(
                                    name = displayName,
                                    source = displayName,
                                    url = streamUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = getQualityFromName(qLabel)
                                    this.headers = streamHeaders
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
            }
        }

        val fallbackVid = conf.optString("videoId")
        val fallbackQualities = conf.optJSONArray("qualities")
        if (fallbackVid.isNotBlank() && fallbackQualities != null && fallbackQualities.length() > 0) {
            for (q in 0 until fallbackQualities.length()) {
                val qLabel = fallbackQualities.optString(q)
                val qNum = qLabel.replace("p", "").trim()
                val streamUrl = "https://sw2.deokwave.com/v/$fallbackVid/$qNum/?vt=$vt"
                val displayName = when (qLabel) {
                    "2160p" -> "Deokwave - 4K Ultra HD (HDR Orijinal 4K)"
                    "1080p" -> "Deokwave - 1080p Full HD"
                    else -> "Deokwave - $qLabel"
                }

                callback.invoke(
                    newExtractorLink(
                        name = displayName,
                        source = displayName,
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getQualityFromName(qLabel)
                        this.headers = streamHeaders
                    }
                )
                foundLinks = true
            }
        }

        return foundLinks
    }
}
