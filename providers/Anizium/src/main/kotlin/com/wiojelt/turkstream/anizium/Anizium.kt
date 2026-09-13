package com.wiojelt.turkstream.anizium

import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone
import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Anizium(private val sessionProvider: () -> String = { "" }) : MainAPI() {
    override var mainUrl = "https://anizium.co"
    override var name = "Anizium Test"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        const val API_BASE = "https://api.anizium.co"
        const val TOKEN_KEY = "hlxjl1c2w281ax473rt1ofgrvhyjvi"
        const val CLIENT_KEY = "16ghkdz5qnwinkyebwopbd94b49xhs"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun xorEncrypt(text: String, key: String): String {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder(textBytes.size * 2)
            for (i in textBytes.indices) {
                val b = textBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()
                val hex = (b and 0xFF).toString(16).padStart(2, '0')
                sb.append(hex)
            }
            return sb.toString()
        }

        fun generateCfControl(): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+3"))
            val days = arrayOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
            val weekday = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val key = "${TOKEN_KEY}_${weekday}"
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val randStr = (1..6).map { chars.random() }.joinToString("")
            val payload = "{\"$randStr\":${System.currentTimeMillis()}}"
            return xorEncrypt(payload, key)
        }

        fun encryptBody(jsonStr: String): String {
            return xorEncrypt(jsonStr, CLIENT_KEY)
        }
    }

    private fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "device" to "browser",
            "language" to "tr",
            "site" to "main",
            "Cf-Control" to generateCfControl()
        )
        val ses = sessionProvider().trim()
        if (ses.isNotBlank()) {
            headers["user-session"] = ses
        }
        return headers
    }

    override val mainPage = mainPageOf(
        "$API_BASE/page/last-added-episodes?page=" to "Son Eklenen Bölümler",
        "$API_BASE/page/home#top" to "Öne Çıkanlar",
        "$API_BASE/page/home#middle" to "Popüler Animeler",
        "$API_BASE/page/home#lower" to "Gündemdeki Animeler",
        "$API_BASE/page/home#special" to "Özel Listeler",
        "$API_BASE/page/calendar" to "Takvim / Yakında Gelecekler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val results = mutableListOf<SearchResponse>()
        if (request.data.startsWith("$API_BASE/page/last-added-episodes")) {
            val url = "${request.data}$page"
            val res = app.get(url, headers = getHeaders()).text
            val json = JSONObject(res)
            val pageObj = json.optJSONObject("page")
            val list = pageObj?.optJSONArray("data")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val id = item.optString("ID")
                    val name = item.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    val poster = fixUrlNull(item.optString("poster")) ?: fixUrlNull(item.optString("details_banner"))
                    val ep = item.optInt("episode", 0)
                    val s = item.optInt("season", 1)
                    val epText = if (ep > 0) " (S${s} B${ep})" else ""
                    results.add(
                        newAnimeSearchResponse("$name$epText", "$mainUrl/anime/$id", TvType.Anime) {
                            this.posterUrl = poster
                        }
                    )
                }
            }
        } else if (request.data.startsWith("$API_BASE/page/home")) {
            val section = request.data.substringAfter("#")
            val res = app.get("$API_BASE/page/home", headers = getHeaders()).text
            val json = JSONObject(res)
            val arrayKey = when (section) {
                "top" -> "settlement_top"
                "middle" -> "settlement_middle"
                "lower" -> "settlement_lower"
                "special" -> "special_list"
                else -> "settlement_top"
            }
            val list = json.optJSONArray(arrayKey)
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val id = item.optString("ID")
                    val name = item.optString("name").ifBlank { item.optString("name_tr") }
                    if (id.isBlank() || name.isBlank()) continue
                    val poster = fixUrlNull(item.optString("poster"))
                        ?: fixUrlNull(item.optString("banner"))
                        ?: fixUrlNull(item.optString("details_banner"))
                    val typeStr = item.optString("type")
                    val type = if (typeStr.equals("movie", ignoreCase = true)) TvType.AnimeMovie else TvType.Anime
                    results.add(
                        newAnimeSearchResponse(name, "$mainUrl/anime/$id", type) {
                            this.posterUrl = poster
                        }
                    )
                }
            }
        } else if (request.data.startsWith("$API_BASE/page/calendar")) {
            val res = app.get("$API_BASE/page/calendar", headers = getHeaders()).text
            val json = JSONObject(res)
            val list = json.optJSONArray("data")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val content = item.optJSONObject("content")
                    val id = item.optString("content_id").ifBlank { item.optString("ID") }
                    val name = content?.optString("name") ?: item.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    val poster = fixUrlNull(content?.optString("poster"))
                        ?: fixUrlNull(content?.optString("banner"))
                        ?: fixUrlNull(content?.optString("details_banner"))
                    val typeStr = item.optString("content_type").ifBlank { item.optString("type") }
                    val type = if (typeStr.equals("movie", ignoreCase = true)) TvType.AnimeMovie else TvType.Anime
                    results.add(
                        newAnimeSearchResponse(name, "$mainUrl/anime/$id", type) {
                            this.posterUrl = poster
                        }
                    )
                }
            }
        }
        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$API_BASE/page/search?value=$encoded&page=1"
        val res = app.get(url, headers = getHeaders()).text
        val json = JSONObject(res)
        val pageObj = json.optJSONObject("page") ?: return emptyList()
        val list = pageObj.optJSONArray("data") ?: return emptyList()
        val results = mutableListOf<SearchResponse>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val id = item.optString("ID")
            val name = item.optString("name").ifBlank { item.optString("name_tr") }
            if (id.isBlank() || name.isBlank()) continue
            val poster = fixUrlNull(item.optString("poster"))
                ?: fixUrlNull(item.optString("banner"))
                ?: fixUrlNull(item.optString("details_banner"))
            val typeStr = item.optString("type")
            val type = if (typeStr.equals("movie", ignoreCase = true)) TvType.AnimeMovie else TvType.Anime
            results.add(
                newAnimeSearchResponse(name, "$mainUrl/anime/$id", type) {
                    this.posterUrl = poster
                }
            )
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = Regex("""\b(\d{5,20})\b""").find(url)?.groupValues?.get(1) ?: url.substringAfterLast("/")
        val apiUrl = "$API_BASE/anime/get?id=$id"
        val res = app.get(apiUrl, headers = getHeaders()).text
        val root = JSONObject(res)
        val data = root.optJSONObject("data") ?: return null

        val name = data.optString("name").ifBlank { data.optString("name_tr") }.ifBlank { data.optString("name_jp") }
        val rawOverview = data.optString("overview").ifBlank { data.optString("overview_short") }
        val overview = if (rawOverview.startsWith("{")) {
            try {
                val j = JSONObject(rawOverview)
                j.optString("overview").ifBlank { j.optString("name") }
            } catch (_: Exception) {
                rawOverview
            }
        } else {
            rawOverview
        }

        val poster = fixUrlNull(data.optString("poster")) ?: fixUrlNull(data.optString("mobile_poster_link"))
        val banner = fixUrlNull(data.optString("banner")) ?: fixUrlNull(data.optString("details_banner"))
        val year = data.optInt("release_year", 0).takeIf { it > 0 }
        val typeStr = data.optString("type")
        val isMovie = typeStr.equals("movie", ignoreCase = true)

        fun extractNames(arr: JSONArray?): List<String> {
            val list = mutableListOf<String>()
            if (arr == null) return list
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val str = when (item) {
                    is JSONObject -> item.optString("name").ifBlank { item.optString("name_tr") }
                    is String -> {
                        if (item.startsWith("{")) {
                            try {
                                val j = JSONObject(item)
                                j.optString("name").ifBlank { j.optString("name_tr") }
                            } catch (_: Exception) {
                                item
                            }
                        } else {
                            item
                        }
                    }
                    else -> null
                }?.trim()
                if (!str.isNullOrBlank() && !list.contains(str)) {
                    list.add(str)
                }
            }
            return list
        }

        val tags = mutableListOf<String>()
        tags.addAll(extractNames(data.optJSONArray("genre")))
        extractNames(data.optJSONArray("tag")).forEach {
            if (!tags.contains(it)) tags.add(it)
        }

        if (isMovie) {
            val movieData = JSONObject().apply {
                put("id", id)
                put("isMovie", true)
            }.toString()
            return newMovieLoadResponse(name, url, TvType.AnimeMovie, movieData) {
                this.posterUrl = poster
                this.backgroundPosterUrl = banner
                this.year = year
                this.plot = overview
                this.tags = tags
            }
        } else {
            val seasonsArray = data.optJSONArray("seasons")
            val episodes = mutableListOf<Episode>()
            if (seasonsArray != null) {
                for (sIdx in 0 until seasonsArray.length()) {
                    val sObj = seasonsArray.optJSONObject(sIdx) ?: continue
                    val sNum = sObj.optInt("number", sIdx + 1)
                    val epsArray = sObj.optJSONArray("episodes") ?: continue
                    for (eIdx in 0 until epsArray.length()) {
                        val epObj = epsArray.optJSONObject(eIdx) ?: continue
                        val eNum = epObj.optInt("number", eIdx + 1)
                        val rawEpName = epObj.optString("name")
                        val epName = if (rawEpName.isBlank() || rawEpName.equals("null", ignoreCase = true)) {
                            "Bölüm $eNum"
                        } else {
                            rawEpName
                        }
                        val epThumb = fixUrlNull(epObj.optString("banner_link"))
                        val epOverview = epObj.optString("overview")
                        val epData = JSONObject().apply {
                            put("id", id)
                            put("season", sNum)
                            put("episode", eNum)
                            put("isMovie", false)
                        }.toString()
                        episodes.add(
                            newEpisode(epData) {
                                this.name = epName
                                this.season = sNum
                                this.episode = eNum
                                this.posterUrl = epThumb
                                this.description = epOverview
                            }
                        )
                    }
                }
            }
            return newTvSeriesLoadResponse(name, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = banner
                this.year = year
                this.plot = overview
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = JSONObject(data)
        val id = json.optString("id")
        val isMovie = json.optBoolean("isMovie", false)
        val season = json.optInt("season", 1)
        val episode = json.optInt("episode", 1)

        var sourceJson: JSONObject? = null
        val serverList = listOf("2", "beta", "1")
        for (srv in serverList) {
            val sourceUrl = if (isMovie) {
                "$API_BASE/anime/source?id=$id&site=main&plan=&server=$srv"
            } else {
                "$API_BASE/anime/source?id=$id&site=main&plan=&season=$season&episode=$episode&server=$srv"
            }
            try {
                val res = app.get(sourceUrl, headers = getHeaders()).text
                val testJson = JSONObject(res)
                if (testJson.optBoolean("success", false)) {
                    val groups = testJson.optJSONArray("groups")
                    if (groups != null && groups.length() > 0) {
                        sourceJson = testJson
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (sourceJson == null) return false

        val groups = sourceJson.optJSONArray("groups")
        if (groups != null) {
            for (gIdx in 0 until groups.length()) {
                val grp = groups.optJSONObject(gIdx) ?: continue
                val grpGroup = grp.optString("group")
                val grpName = grp.optString("name")
                val items = grp.optJSONArray("items") ?: continue
                if (items.length() == 0) continue

                val isDub = grpGroup.contains("dub", ignoreCase = true) || grpName.contains("Türkçe", ignoreCase = true)
                val dubSuffix = if (isDub && !grpName.contains("Dublaj", ignoreCase = true)) " (Dublaj)" else ""
                val sourceLabel = "Anizium - $grpName$dubSuffix"

                val hlsItems = mutableListOf<JSONObject>()
                val mp4Items = mutableListOf<JSONObject>()

                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val link = it.optString("link")
                    if (link.isBlank()) continue
                    val itType = it.optString("type")
                    if (itType.equals("hls", ignoreCase = true) || link.contains(".m3u8")) {
                        hlsItems.add(it)
                    } else {
                        mp4Items.add(it)
                    }
                }

                if (hlsItems.isNotEmpty()) {
                    hlsItems.sortByDescending { it.optInt("quality", 1080) }
                    val lines = mutableListOf(
                        "#EXTM3U",
                        "#EXT-X-VERSION:3",
                        "#EXT-X-INDEPENDENT-SEGMENTS"
                    )
                    for (it in hlsItems) {
                        val qInt = it.optInt("quality", 1080)
                        val link = it.optString("link")
                        val (res, bw) = when (qInt) {
                            2160 -> Pair("3840x2160", 16000000)
                            1440 -> Pair("2560x1440", 8000000)
                            1080 -> Pair("1920x1080", 4500000)
                            720  -> Pair("1280x720", 2200000)
                            480  -> Pair("854x480", 1000000)
                            else -> Pair("1920x${qInt}", qInt * 4000)
                        }
                        val qualName = when (qInt) {
                            2160 -> "4K"
                            1440 -> "2K"
                            1080 -> "1080p"
                            720  -> "720p"
                            480  -> "480p"
                            else -> "${qInt}p"
                        }
                        lines.add("#EXT-X-STREAM-INF:BANDWIDTH=$bw,RESOLUTION=$res,NAME=\"$qualName\"")
                        lines.add(link)
                    }
                    val masterContent = lines.joinToString("\n") + "\n"
                    val b64 = Base64.encodeToString(masterContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    val dataUri = "data:application/vnd.apple.mpegurl;base64,$b64"

                    callback.invoke(
                        newExtractorLink(
                            source = sourceLabel,
                            name = sourceLabel,
                            url = dataUri,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                } else if (mp4Items.isNotEmpty()) {
                    mp4Items.sortByDescending { it.optInt("quality", 1080) }
                    val bestMp4 = mp4Items.first()
                    callback.invoke(
                        newExtractorLink(
                            source = sourceLabel,
                            name = sourceLabel,
                            url = bestMp4.optString("link"),
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        }

        val subs = sourceJson.optJSONArray("subtitles")
        if (subs != null) {
            for (sIdx in 0 until subs.length()) {
                val s = subs.optJSONObject(sIdx) ?: continue
                val sName = s.optString("name").ifBlank { s.optString("group") }
                val sUrl = s.optString("link")
                if (sUrl.isBlank()) continue
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = sName,
                        url = sUrl
                    )
                )
            }
        }
        return true
    }
}
