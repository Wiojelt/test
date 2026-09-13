package com.pltmustafa.inatbox

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.pltmustafa.inatbox.models.ChContent
import com.pltmustafa.inatbox.models.Kategoriler
import com.pltmustafa.inatbox.models.SSportResponse
import com.pltmustafa.inatbox.utils.InatBoxCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class InatBox : MainAPI() {
    override var name: String = "InatBox"
    override var lang: String = "tr"
    override val hasMainPage: Boolean = true
    override val hasQuickSearch: Boolean = false
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)

    private val urlToSearchResponse: MutableMap<String, SearchResponse> = LinkedHashMap()
    private val catalogMutex = Mutex()
    private var cachedHomePageResponse: HomePageResponse? = null

    override val mainPage: List<MainPageData> = mainPageOf(
        InatBoxCrypto.DEFAULT_CATEGORY_URL to "InatBox"
    )

    companion object {
        private const val TAG = "InatBox"
    }

    private suspend fun makeInatRequest(url: String): String? {
        Log.d(TAG, "makeInatRequest: url=$url")
        return try {
            val randomKey = InatBoxCrypto.getRandomAlphaNumeric(16)
            val requestBodyString = "1=$randomKey&0=$randomKey"
            val signedHeaders = InatBoxCrypto.getSignedHeaders(url, "POST", requestBodyString)
            val requestBody = requestBodyString.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull())
            val response = app.post(
                url,
                headers = signedHeaders,
                requestBody = requestBody
            )
            Log.d(TAG, "makeInatRequest: status=${response.code} successful=${response.isSuccessful} for url=$url")
            if (!response.isSuccessful) {
                return null
            }
            val responseText = try {
                response.body.string().trim()
            } catch (e: Exception) {
                response.text.trim()
            }
            val decrypted = InatBoxCrypto.decryptDoubleAesCbc(responseText, randomKey)
            Log.d(TAG, "makeInatRequest: decrypted length=${decrypted.length} for url=$url")
            decrypted
        } catch (e: Exception) {
            Log.e(TAG, "makeInatRequest error for url=$url: ${e.message}", e)
            null
        }
    }

    private fun inatContentAllowed(item: JSONObject): Boolean {
        val type = if (item.has("diziType")) item.optString("diziType") else item.optString("chType")
        if (type.contains("link", true) || type.contains("destek", true) || type.contains("yok", true) || type.contains("web_basic", true)) {
            return false
        }
        val name = if (item.has("diziName")) item.optString("diziName") else item.optString("chName")
        if (name.contains("inattv", true) || name.contains("@", true) || name.contains("Hata Bildir", true) || name.contains("Telegram", true)) {
            return false
        }
        return true
    }

    private fun vkSourceFix(url: String): String {
        if (url.startsWith("act", ignoreCase = true)) {
            return "https://vk.com/al_video.php?$url"
        }
        return url
    }

    private fun parseToChContent(item: JSONObject): ChContent {
        return ChContent(
            chName = item.optString("chName"),
            chUrl = vkSourceFix(item.optString("chUrl")),
            chImg = item.optString("chImg"),
            chHeaders = item.optString("chHeaders"),
            chReg = item.optString("chReg"),
            chType = item.optString("chType")
        )
    }

    private fun isDirectStream(url: String): Boolean {
        return url.contains(".m3u8", true) || url.contains(".mpd", true) || url.contains(".mp4", true) || url.contains(".webm", true)
    }

    private fun getSearchResponseList(jsonResponse: String, maxItems: Int = -1): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()
        try {
            val jsonArray = JSONArray(jsonResponse)
            val limit = if (maxItems > 0) minOf(jsonArray.length(), maxItems) else jsonArray.length()
            for (i in 0 until limit) {
                try {
                    val item = jsonArray.getJSONObject(i)
                    if (!inatContentAllowed(item)) {
                        continue
                    }
                    if (item.has("diziType")) {
                        val name = item.optString("diziName")
                        val type = item.optString("diziType")
                        val posterUrl = item.optString("diziImg")
                        val searchResponse = if (type.contains("dizi", true)) {
                            newTvSeriesSearchResponse(name, item.toString(), TvType.TvSeries) {
                                this.posterUrl = posterUrl
                            }
                        } else if (type.contains("film", true)) {
                            newMovieSearchResponse(name, item.toString(), TvType.Movie) {
                                this.posterUrl = posterUrl
                            }
                        } else {
                            null
                        }
                        if (searchResponse != null) {
                            searchResults.add(searchResponse)
                        }
                    } else if (item.has("chName") && item.has("chUrl")) {
                        val name = item.optString("chName")
                        val posterUrl = item.optString("chImg")
                        val chType = item.optString("chType")
                        val isLive = chType.contains("live", true) || chType.contains("cable", true) ||
                                chType.contains("tekli_regex_lb_sh_3_s", true) || chType.contains("tekli_regex_lb_sh_3_u", true)
                        val searchResponse = if (isLive) {
                            newLiveSearchResponse(name, item.toString(), TvType.Live) {
                                this.posterUrl = posterUrl
                            }
                        } else {
                            newMovieSearchResponse(name, item.toString(), TvType.Movie) {
                                this.posterUrl = posterUrl
                            }
                        }
                        searchResults.add(searchResponse)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSearchResponseList parse error: ${e.message}", e)
        }
        return searchResults
    }

    private suspend fun ensureCatalogLoaded(): Boolean {
        if (urlToSearchResponse.isNotEmpty() && cachedHomePageResponse != null) {
            return true
        }
        return catalogMutex.withLock {
            if (urlToSearchResponse.isNotEmpty() && cachedHomePageResponse != null) {
                return@withLock true
            }
            loadCatalog()
        }
    }

    private suspend fun loadCatalog(): Boolean {
        val targetCategoryUrl = try {
            val bootstrapDomain = InatBoxCrypto.fetchBootstrapDomain()
            bootstrapDomain?.dc2 ?: InatBoxCrypto.DEFAULT_CATEGORY_URL
        } catch (e: Exception) {
            InatBoxCrypto.DEFAULT_CATEGORY_URL
        }
        Log.d(TAG, "loadCatalog: targetCategoryUrl=$targetCategoryUrl")

        val decryptedCategoriesJson = makeInatRequest(targetCategoryUrl)
        if (decryptedCategoriesJson == null) {
            Log.e(TAG, "loadCatalog: failed to fetch categories")
            return false
        }

        val allCategories: List<Kategoriler> = try {
            jacksonObjectMapper().readValue(decryptedCategoriesJson)
        } catch (e: Exception) {
            Log.e(TAG, "loadCatalog: failed to parse categories", e)
            return false
        }
        Log.d(TAG, "loadCatalog: allCategories total=${allCategories.size}")

        val filteredCategories = allCategories.filter { kategori ->
            val catType = kategori.catType ?: ""
            val catName = kategori.catName ?: ""
            val catUrl = kategori.catUrl ?: ""
            val isUnwanted = catType == "link" || catType == "destek" ||
                    catName == "Hata Bildir" || catName == "Derbiler" ||
                    catUrl.contains("destek_mode", true) ||
                    catUrl.contains("inattv", true) ||
                    catUrl.contains("x.com/", true) ||
                    catName.contains("4k", true) ||
                    catUrl.contains("/4k/", true) ||
                    catName.contains("Liste 3", true) ||
                    catUrl.contains("list3.php", true)
            !isUnwanted && catUrl.isNotBlank()
        }
        Log.d(TAG, "loadCatalog: filteredCategories count=${filteredCategories.size}")

        val homePageLists = coroutineScope {
            filteredCategories.map { kategori ->
                async(Dispatchers.IO) {
                    val catUrl = kategori.catUrl ?: return@async null
                    val jsonResponse = makeInatRequest(catUrl)
                    if (jsonResponse == null) {
                        Log.w(TAG, "loadCatalog: category response null for '${kategori.catName}' at $catUrl")
                        return@async null
                    }
                    val allItems = getSearchResponseList(jsonResponse, -1)
                    Log.d(TAG, "loadCatalog: loaded '${kategori.catName}' with ${allItems.size} items")
                    synchronized(urlToSearchResponse) {
                        for (result in allItems) {
                            if (!urlToSearchResponse.containsKey(result.url)) {
                                urlToSearchResponse[result.url] = result
                            }
                        }
                    }
                    val homeItems = allItems.take(60)
                    val catType = (kategori.catType ?: "").lowercase(Locale.ROOT)
                    val catName = (kategori.catName ?: "").lowercase(Locale.ROOT)
                    val isHorizontal = catType.contains("live") || catType.contains("iptv") ||
                            catType.contains("tv") || catName.contains("canlı") ||
                            catName.contains("spor") || catType.contains("tv_mode")
                    val displayName = kategori.catName ?: "İsimsiz"
                    HomePageList(displayName, homeItems, isHorizontalImages = isHorizontal)
                }
            }.awaitAll().filterNotNull()
        }

        cachedHomePageResponse = newHomePageResponse(homePageLists)
        Log.d(TAG, "loadCatalog: completed with ${homePageLists.size} lists, total indexed=${urlToSearchResponse.size}")
        return true
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureCatalogLoaded()
        return cachedHomePageResponse ?: throw ErrorLoadingException("Kategorilere ulaşılamadı!")
    }

    private fun normalizeForSearch(text: String): String {
        return text.lowercase(Locale("tr", "TR"))
            .replace("ı", "i")
            .replace("ğ", "g")
            .replace("ü", "u")
            .replace("ş", "s")
            .replace("ö", "o")
            .replace("ç", "c")
            .replace("[^a-z0-9 ]".toRegex(), "")
            .trim()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return emptyList()
        }
        if (urlToSearchResponse.isEmpty()) {
            ensureCatalogLoaded()
        }
        val normalizedQuery = normalizeForSearch(trimmedQuery)
        val cachedResults = synchronized(urlToSearchResponse) {
            urlToSearchResponse.values.filter {
                val normalizedName = normalizeForSearch(it.name)
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                        (normalizedQuery.isNotEmpty() && normalizedName.contains(normalizedQuery))
            }.distinctBy { normalizeForSearch(it.name) }
        }
        Log.d(TAG, "search: query='$trimmedQuery' totalIndexed=${urlToSearchResponse.size} found=${cachedResults.size}")
        return cachedResults
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "load: received url=$url")
        val item = try {
            JSONObject(url)
        } catch (e: Exception) {
            Log.e(TAG, "load: parse JSONObject failed for url=$url", e)
            return null
        }
        if (!inatContentAllowed(item)) {
            Log.w(TAG, "load: item not allowed")
            return null
        }
        if (item.optString("chType").contains("SsprDrm", ignoreCase = true)) {
            Log.d(TAG, "load: routing to SSport")
            return parseSSportResponse(item)
        }
        if (item.has("diziType")) {
            val type = item.optString("diziType")
            Log.d(TAG, "load: item has diziType='$type', name='${item.optString("diziName")}'")
            if (type.contains("dizi", ignoreCase = true)) {
                return parseTvSeriesResponse(item)
            }
            if (type.contains("film", ignoreCase = true)) {
                return parseMovieResponse(item)
            }
            return null
        }
        if (item.has("chName")) {
            val chType = item.optString("chType")
            val chName = item.optString("chName")
            val chUrl = item.optString("chUrl")
            Log.d(TAG, "load: item has chName='$chName', chType='$chType', chUrl='$chUrl'")
            if (chType.contains("live", ignoreCase = true) || chType.contains("cable", ignoreCase = true)) {
                return parseLiveStreamLoadResponse(item)
            }
            val is4kFilm = chUrl.contains("/4k/", ignoreCase = true) || chType.contains("4k", ignoreCase = true)
            val isSportsOrLive = !is4kFilm && (
                    chType.contains("tekli_regex_lb_sh_3_s", ignoreCase = true) ||
                    chType.contains("tekli_regex_lb_sh_3_u", ignoreCase = true) ||
                    chName.contains("spor", ignoreCase = true) ||
                    chName.contains("bein", ignoreCase = true) ||
                    chName.contains("canlı", ignoreCase = true) ||
                    chName.contains("tivibu", ignoreCase = true) ||
                    chName.contains("smart spor", ignoreCase = true) ||
                    chName.contains("tv", ignoreCase = true) ||
                    chName.contains(" | ", ignoreCase = true) ||
                    chName.contains("kanal", ignoreCase = true)
            )
            if (isSportsOrLive) {
                return parseLiveSportsStreamLoadResponse(item)
            }
            return parseMovieResponse(item)
        }
        return null
    }

    private suspend fun parseSSportResponse(item: JSONObject): LoadResponse? {
        val sSportUrl = "https://sprspr.help/CDN/SSP/bir-p-no-cron.php"
        val headers = mapOf(
            "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15",
            "x-requested-with" to "XMLHttpRequest"
        )
        val response = app.get(sSportUrl, headers = headers, referer = "https://google.com/")
        if (!response.isSuccessful) {
            Log.e(TAG, "parseSSportResponse: request failed with ${response.code}")
            return null
        }
        val sSportData: SSportResponse = jacksonObjectMapper().readValue(response.text)
        val firstCategory = sSportData.categories?.firstOrNull() ?: return null
        val contents = firstCategory.contents ?: return null

        val episodes = contents.mapIndexed { index, content ->
            val data = (content.id ?: index).toString()
            val title = content.title ?: "Etkinlik ${index + 1}"
            val description = content.description ?: content.tags ?: ""
            val poster = content.medias?.firstOrNull { it.type == 84 }?.url
            newEpisode(data) {
                this.name = title
                this.description = description
                this.episode = index + 1
                this.posterUrl = poster
            }
        }

        val posterUrl = contents.firstOrNull()?.medias?.firstOrNull { it.type == 84 }?.url
        return newTvSeriesLoadResponse("S Sport Plus", sSportUrl, TvType.TvSeries, episodes) {
            this.posterUrl = posterUrl
            this.plot = "Canlı spor yayınları güncel içeriği almak için eklentiyi tazelemeyi deneyebilirsiniz."
        }
    }

    private suspend fun parseTvSeriesResponse(item: JSONObject): LoadResponse? {
        val name = item.optString("diziName")
        val diziUrl = item.optString("diziUrl")
        val posterUrl = item.optString("diziImg")
        val plot = item.optString("diziDetay")
        Log.d(TAG, "parseTvSeriesResponse: name='$name', diziUrl='$diziUrl'")

        val jsonResponse = makeInatRequest(diziUrl)
        if (jsonResponse == null) {
            Log.e(TAG, "parseTvSeriesResponse: makeInatRequest failed for diziUrl=$diziUrl")
            return null
        }
        val jsonArray = JSONArray(jsonResponse)
        val seasonDataList = mutableListOf<SeasonData>()
        val episodeList = mutableListOf<Episode>()

        val firstItem = if (jsonArray.length() > 0) jsonArray.optJSONObject(0) else null
        val hasSeasons = firstItem != null && (firstItem.has("seasonUrl") || firstItem.has("sezonUrl") || firstItem.has("diziUrl")) && !firstItem.has("chUrl")
        Log.d(TAG, "parseTvSeriesResponse: hasSeasons=$hasSeasons count=${jsonArray.length()}")

        if (hasSeasons) {
            for (i in 0 until jsonArray.length()) {
                val seasonItem = jsonArray.getJSONObject(i)
                val seasonName = seasonItem.optString("seasonName", seasonItem.optString("sezonName", seasonItem.optString("diziName", "Sezon ${i + 1}")))
                val seasonUrl = seasonItem.optString("seasonUrl", seasonItem.optString("sezonUrl", seasonItem.optString("diziUrl")))
                val seasonImg = seasonItem.optString("seasonImg", seasonItem.optString("diziImg", posterUrl))
                seasonDataList.add(SeasonData(i + 1, seasonName))
                if (seasonUrl.isBlank()) continue
                val seasonResponse = makeInatRequest(seasonUrl)
                if (seasonResponse == null) {
                    Log.w(TAG, "parseTvSeriesResponse: failed seasonResponse for $seasonName at $seasonUrl")
                    continue
                }
                val seasonEpisodesArray = JSONArray(seasonResponse)
                Log.d(TAG, "parseTvSeriesResponse: season '$seasonName' has ${seasonEpisodesArray.length()} episodes")
                for (j in 0 until seasonEpisodesArray.length()) {
                    val epItem = seasonEpisodesArray.getJSONObject(j)
                    val epName = epItem.optString("chName", "Bölüm ${j + 1}")
                    val epImg = epItem.optString("chImg", seasonImg)
                    episodeList.add(
                        newEpisode(epItem.toString()) {
                            this.name = epName
                            this.posterUrl = epImg
                            this.season = i + 1
                            this.episode = j + 1
                        }
                    )
                }
            }
        } else {
            for (j in 0 until jsonArray.length()) {
                val epItem = jsonArray.getJSONObject(j)
                val epName = epItem.optString("chName", "Bölüm ${j + 1}")
                val epImg = epItem.optString("chImg", posterUrl)
                episodeList.add(
                    newEpisode(epItem.toString()) {
                        this.name = epName
                        this.posterUrl = epImg
                        this.season = 1
                        this.episode = j + 1
                    }
                )
            }
        }
        Log.d(TAG, "parseTvSeriesResponse: total episodes created=${episodeList.size}")

        return newTvSeriesLoadResponse(name, item.toString(), TvType.TvSeries, episodeList) {
            this.posterUrl = posterUrl
            this.plot = plot
            if (seasonDataList.isNotEmpty()) {
                this.seasonNames = seasonDataList
            }
        }
    }

    private suspend fun parseMovieResponse(item: JSONObject): LoadResponse? {
        if (!item.has("diziType")) {
            val name = item.optString("chName")
            val posterUrl = item.optString("chImg")
            Log.d(TAG, "parseMovieResponse: direct movie name='$name'")
            return newMovieLoadResponse(name, item.toString(), TvType.Movie, item.toString()) {
                this.posterUrl = posterUrl
            }
        }
        val name = item.optString("diziName")
        val diziUrl = item.optString("diziUrl")
        val posterUrl = item.optString("diziImg")
        val plot = item.optString("diziDetay")
        Log.d(TAG, "parseMovieResponse: dizi movie name='$name', diziUrl='$diziUrl'")
        val jsonResponse = makeInatRequest(diziUrl) ?: return null
        return newMovieLoadResponse(name, item.toString(), TvType.Movie, jsonResponse) {
            this.posterUrl = posterUrl
            this.plot = plot
        }
    }

    private suspend fun parseLiveStreamLoadResponse(item: JSONObject): LoadResponse? {
        val chContent = parseToChContent(item)
        if (chContent.chType.contains("inattvapk", true)) {
            return null
        }
        Log.d(TAG, "parseLiveStreamLoadResponse: chName='${chContent.chName}'")
        return newLiveStreamLoadResponse(
            name = chContent.chName,
            url = item.toString(),
            dataUrl = item.toString()
        ) {
            this.posterUrl = chContent.chImg
        }
    }

    private suspend fun parseLiveSportsStreamLoadResponse(item: JSONObject): LoadResponse? {
        val chContent = parseToChContent(item)
        Log.d(TAG, "parseLiveSportsStreamLoadResponse: chName='${chContent.chName}'")
        return newLiveStreamLoadResponse(
            name = chContent.chName,
            url = item.toString(),
            dataUrl = item.toString()
        ) {
            this.posterUrl = chContent.chImg
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val trimmed = data.trim()
        Log.d(TAG, "loadLinks: data=${trimmed.take(150)}")
        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val chContent = parseToChContent(obj)
                loadChContentLinks(chContent, subtitleCallback, callback)
            }
            return true
        }
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val chContent = parseToChContent(obj)
            loadChContentLinks(chContent, subtitleCallback, callback)
            return true
        }
        return false
    }

    private suspend fun loadChContentLinks(
        chContent: ChContent,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "loadChContentLinks: name='${chContent.chName}', type='${chContent.chType}', url='${chContent.chUrl}'")

        val headers = mutableMapOf<String, String>()
        if (chContent.chHeaders.isNotBlank() && chContent.chHeaders != "null") {
            try {
                val jsonHeaders = JSONArray(chContent.chHeaders).getJSONObject(0)
                if (jsonHeaders.has("UserAgent")) {
                    headers["User-Agent"] = jsonHeaders.getString("UserAgent")
                } else if (jsonHeaders.has("User-Agent")) {
                    headers["User-Agent"] = jsonHeaders.getString("User-Agent")
                }
                if (jsonHeaders.has("Referer")) {
                    headers["Referer"] = jsonHeaders.getString("Referer")
                }
                if (jsonHeaders.has("XRequestedWith")) {
                    headers["X-Requested-With"] = jsonHeaders.getString("XRequestedWith")
                } else if (jsonHeaders.has("X-Requested-With")) {
                    headers["X-Requested-With"] = jsonHeaders.getString("X-Requested-With")
                }
                val keys = jsonHeaders.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!headers.containsKey(key)) {
                        headers[key] = jsonHeaders.optString(key)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadChContentLinks error parsing headers: ${e.message}")
            }
        }

        var regex1 = ""
        var regex2 = ""
        var regex2p: String? = null
        if (chContent.chReg.isNotBlank() && chContent.chReg != "null") {
            try {
                val jsonReg = JSONArray(chContent.chReg).getJSONObject(0)
                regex1 = jsonReg.optString("Regex1", "")
                regex2 = jsonReg.optString("Regex2", "")
                if (jsonReg.has("Regex2p")) {
                    regex2p = jsonReg.optString("Regex2p")
                }
                if (jsonReg.has("playSH2")) {
                    headers["Cookie"] = jsonReg.optString("playSH2")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadChContentLinks error parsing reg: ${e.message}")
            }
        }

        if (chContent.chType.contains("tekli_regex_lb_sh", ignoreCase = true)) {
            Log.d(TAG, "loadChContentLinks: handling tekli_regex_lb_sh")
            val signedHeaders = InatBoxCrypto.getSignedHeaders(chContent.chUrl, "GET", "").toMutableMap()
            for ((k, v) in headers) {
                signedHeaders[k] = v
            }
            val response = app.get(chContent.chUrl, headers = signedHeaders)
            if (response.isSuccessful) {
                val encryptedText = response.text.trim()
                val decryptedJson = InatBoxCrypto.decryptChannelStream(encryptedText, regex1, regex2, regex2p)
                val streamJson = JSONObject(decryptedJson)
                val streamUrl = streamJson.optString("chUrl", "")
                if (streamUrl.isNotBlank()) {
                    Log.d(TAG, "loadChContentLinks: decrypted streamUrl=$streamUrl")
                    val streamHeaders = mutableMapOf<String, String>()
                    for ((k, v) in headers) {
                        streamHeaders[k] = v
                    }
                    if (streamJson.has("playSH2")) {
                        streamHeaders["Cookie"] = streamJson.optString("playSH2")
                    }
                    callback.invoke(
                        newExtractorLink(
                            source = chContent.chName,
                            name = name,
                            url = streamUrl,
                            type = if (streamUrl.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.headers = streamHeaders
                        }
                    )
                    return
                }
            } else {
                Log.e(TAG, "loadChContentLinks: tekli_regex_lb_sh request failed code=${response.code}")
            }
        }

        if (chContent.chUrl.contains("filmizleeeee.cfd", ignoreCase = true)) {
            val targetUrl = chContent.chUrl.replace("/web.php", "/exo.php")
            Log.d(TAG, "loadChContentLinks: handling filmizleeeee targetUrl=$targetUrl")
            val signedHeaders = InatBoxCrypto.getSignedHeaders(targetUrl, "GET", "").toMutableMap()
            for ((k, v) in headers) {
                signedHeaders[k] = v
            }
            val response = app.get(targetUrl, headers = signedHeaders)
            if (response.isSuccessful) {
                val rawText = response.text.trim()
                val streamUrl = if (regex1.isNotBlank() && regex1 != "(.*)") {
                    val m = Regex(regex1).find(rawText)
                    (m?.groups?.get(1)?.value ?: m?.value ?: rawText).replace("\\/", "/")
                } else {
                    rawText
                }
                Log.d(TAG, "loadChContentLinks: 4K extracted streamUrl=$streamUrl")
                if (streamUrl.startsWith("http")) {
                    callback.invoke(
                        newExtractorLink(
                            source = chContent.chName,
                            name = "4K Film",
                            url = streamUrl,
                            type = if (streamUrl.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.headers = headers
                        }
                    )
                    return
                }
            } else {
                Log.e(TAG, "loadChContentLinks: filmizleeeee request failed code=${response.code}")
            }
        }

        if (chContent.chType.contains("tekli_regex_mode", ignoreCase = true) || chContent.chType.contains("tekli_regex_no_sh", ignoreCase = true)) {
            Log.d(TAG, "loadChContentLinks: handling tekli_regex_mode for url=${chContent.chUrl}")
            val response = app.get(chContent.chUrl, headers = headers)
            if (response.isSuccessful) {
                val responseText = response.text.trim()
                val streamUrl = if (regex1.isNotBlank()) {
                    val m = Regex(regex1).find(responseText)
                    (m?.groups?.get(1)?.value ?: m?.value)?.replace("\\/", "/")
                } else {
                    null
                }
                Log.d(TAG, "loadChContentLinks: regex extracted streamUrl=$streamUrl")
                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                    if (isDirectStream(streamUrl)) {
                        callback.invoke(
                            newExtractorLink(
                                source = chContent.chName,
                                name = name,
                                url = streamUrl,
                                type = if (streamUrl.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.headers = headers
                            }
                        )
                        return
                    } else {
                        loadExtractor(streamUrl, headers["Referer"], subtitleCallback, callback)
                        return
                    }
                }
            } else {
                Log.e(TAG, "loadChContentLinks: regex request failed code=${response.code}")
            }
        }

        val finalUrl = chContent.chUrl
        if (isDirectStream(finalUrl)) {
            Log.d(TAG, "loadChContentLinks: direct stream finalUrl=$finalUrl")
            val linkType = when {
                finalUrl.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                finalUrl.contains(".mpd", true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            callback.invoke(
                newExtractorLink(
                    source = chContent.chName,
                    name = name,
                    url = finalUrl,
                    type = linkType
                ) {
                    this.headers = headers
                }
            )
            return
        }

        val referer = headers["Referer"]
        Log.d(TAG, "loadChContentLinks: delegating to loadExtractor finalUrl=$finalUrl, referer=$referer")
        loadExtractor(finalUrl, referer, subtitleCallback, callback)
    }
}
