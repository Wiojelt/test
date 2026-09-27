package dev.wiojelt.kooltv

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.util.Locale

internal data class KoolCountry(val label: String, val groups: List<String>, val language: String, val region: String)
internal data class KoolChannel(val id: String, val name: String, val playUrl: String, val logo: String?, val category: String)

class KoolTVProvider(private val preferences: SharedPreferences) : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Kool TV"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val mainPage = mainPageOf("all" to "Kanallar")

    private var cacheCountry = ""
    private var cacheAt = 0L
    private var cache = emptyList<KoolChannel>()
    var checkedAt = 0L
        private set

    fun countries(): List<String> = COUNTRIES.map { it.label }
    fun selectedCountry(): String = preferences.getString(KEY_COUNTRY, null)?.let { stored ->
        COUNTRIES.firstOrNull { it.label == stored }?.label
    } ?: DEFAULT_COUNTRY

    fun setCountry(country: String) {
        val selected = COUNTRIES.firstOrNull { it.label == country } ?: return
        preferences.edit().putString(KEY_COUNTRY, selected.label).apply()
        cacheCountry = ""; cacheAt = 0L; cache = emptyList()
    }

    suspend fun refresh(): Int { cacheAt = 0L; return rows(true).size }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(emptyList(), false)
        val current = rows()
        val shelves = CATEGORY_ORDER.mapNotNull { category ->
            current.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let {
                HomePageList(category, it.map(::result), true)
            }
        }
        return newHomePageResponse(shelves, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val needle = query.trim().lowercase(TR_LOCALE)
        if (needle.isBlank()) return emptyList()
        return rows().filter { it.name.lowercase(TR_LOCALE).contains(needle) }.map(::result)
    }
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val channelId = url.substringAfterLast("/").substringBefore("?")
        val row = rows().firstOrNull { it.id == channelId } ?: throw ErrorLoadingException("Kanal bulunamadı.")
        return newLiveStreamLoadResponse(row.name, url, row.playUrl) {
            posterUrl = row.logo
            plot = selectedCountry() + " · " + row.category
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val id = extractId(data)
        val resolved = resolveStream(id, data) ?: throw ErrorLoadingException("Kool yayın bağlantısı çözülemedi.")
        callback(newExtractorLink(name, "Kool TV", resolved, if (resolved.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
            quality = Qualities.Unknown.value
        })
        return true
    }

    private fun result(channel: KoolChannel): SearchResponse =
        newLiveSearchResponse(channel.name, mainUrl + "/channel/" + channel.id, TvType.Live) { posterUrl = channel.logo }

    private suspend fun rows(force: Boolean = false): List<KoolChannel> {
        val country = COUNTRIES.firstOrNull { it.label == selectedCountry() } ?: COUNTRIES.first()
        val now = System.currentTimeMillis()
        if (!force && cacheCountry == country.label && cache.isNotEmpty() && now - cacheAt < CACHE_MS) return cache

        val loaded = country.groups.flatMap { fetchGroup(country, it) }
            .distinctBy { it.id + "|" + it.name }
            .sortedWith(compareBy<KoolChannel> {
                CATEGORY_ORDER.indexOf(it.category).let { idx -> if (idx < 0) 99 else idx }
            }.thenBy { it.name.lowercase(Locale.ROOT) })

        if (loaded.isEmpty()) throw ErrorLoadingException(country.label + " kanal listesi alınamadı.")
        cacheCountry = country.label; cache = loaded; cacheAt = now; checkedAt = now
        return loaded
    }

    private suspend fun fetchGroup(country: KoolCountry, group: String): List<KoolChannel> {
        val out = mutableListOf<KoolChannel>()
        val seen = mutableSetOf<String>()
        var cursor = 0L
        repeat(MAX_PAGES) {
            val body = JSONObject()
                .put("language", country.language).put("region", country.region)
                .put("catalogId", "iptv").put("id", "").put("adult", false)
                .put("search", "").put("sort", "name")
                .put("filter", JSONObject().put("group", group)).put("cursor", cursor).toString()
            val response = app.post(mainUrl + "/mediahubmx-catalog.json", requestBody = body, headers = CATALOG_HEADERS, timeout = 18)
            if (response.code !in 200..299) throw ErrorLoadingException("Kool katalog yanıtı: HTTP " + response.code)
            val json = JSONObject(response.text)
            val items = json.optJSONArray("items") ?: return out
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optJSONObject("ids")?.optString("id").orEmpty().trim()
                val title = item.optString("name").trim().ifBlank { id }
                val playUrl = item.optString("url").trim()
                if (!validId(id) || title.isBlank() || !playUrl.startsWith("https://")) continue
                if (!seen.add(id + "|" + title)) continue
                val logo = item.optString("logo").trim().takeIf { it.startsWith("https://") }
                out += KoolChannel(id, title, playUrl, logo, classify(title))
            }
            val next = json.opt("nextCursor")
            val nextCursor = when (next) {
                is Number -> next.toLong()
                is String -> next.toLongOrNull()
                else -> null
            }
            if (items.length() == 0 || nextCursor == null || nextCursor <= 0L || nextCursor == cursor) return out
            cursor = nextCursor
        }
        return out
    }

    private suspend fun resolveStream(id: String, playUrl: String): String? {
        if (!validId(id) || !playUrl.startsWith("https://")) return null
        val headers = mapOf(
            "Accept" to "*/*", "Accept-Encoding" to "gzip",
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cache-Control" to "no-cache", "Content-Type" to "application/json; charset=utf-8",
            "Origin" to mainUrl, "Pragma" to "no-cache",
            "Referer" to mainUrl + "/watch?live=" + id, "User-Agent" to BROWSER_UA,
        )
        var httpFallback: String? = null
        repeat(5) {
            val response = runCatching {
                app.post(mainUrl + "/mediahubmx-resolve.json",
                    requestBody = JSONObject().put("language", "de").put("region", "DE").put("url", playUrl).toString(),
                    headers = headers, timeout = 12)
            }.getOrNull() ?: return@repeat
            if (response.code !in 200..299) return@repeat
            val text = response.text.trim()
            val url = runCatching {
                if (text.startsWith("[")) org.json.JSONArray(text).optJSONObject(0)?.optString("url")
                else JSONObject(text).let { it.optString("url").takeIf(String::isNotBlank) ?: it.optJSONObject("data")?.optString("url") }
            }.getOrNull()?.trim()
            if (url?.startsWith("https://") == true) return url
            if (httpFallback == null && url?.startsWith("http://") == true) httpFallback = url
        }
        return httpFallback
    }

    private fun extractId(playUrl: String): String = playUrl.substringBefore("?").trimEnd('/').substringAfterLast('/').substringBefore("~")
    private fun validId(value: String) = value.length in 1..128 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun classify(title: String): String {
        val s = normalize(title)
        fun has(vararg words: String) = words.any { s.contains(it) }
        return when {
            has(" sport","sports","spor","bein","dazn","eurosport","espn","arena ","football","futbol","calcio","laliga","bundesliga","nba","nfl","tennis","racing","motorsport","sky sport","tnt sport","match","canal sport") -> "Spor"
            has("news","haber","info","euronews","cnn","bbc news","sky news","ntv","n-tv","welt","tagesschau","rai news","24h","24 news") -> "Haber"
            has("kids","kid ","junior","cartoon","nickelodeon","nick jr","disney","cocuk","kika","boomerang","rai gulp","minika") -> "Çocuk"
            has("discovery","documentary","documentaire","doku","belgesel","national geographic","nat geo","history","science","wild","animal planet") -> "Belgesel"
            has("music","muzik","mtv","vh1","radio","hits","musik","musica") -> "Müzik"
            has("cinema","cine","movie","movies","film","films","series","serie","dizi","sinema","hbo","action","comedy") -> "Sinema & Dizi"
            else -> "Genel"
        }
    }

    private fun normalize(value: String): String = " " + value.lowercase(Locale.ROOT)
        .replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c') + " "

    private companion object {
        const val BASE_URL = "https://kool.ws"
        const val DEFAULT_COUNTRY = "Türkiye"
        const val KEY_COUNTRY = "kool_country"
        const val CACHE_MS = 60 * 60 * 1000L
        const val MAX_PAGES = 50
        val TR_LOCALE = Locale("tr", "TR")
        val CATEGORY_ORDER = listOf("Genel","Spor","Haber","Sinema & Dizi","Çocuk","Belgesel","Müzik")
        val CATALOG_HEADERS = mapOf("User-Agent" to "MediaHubMX/2","Content-Type" to "application/json","Accept" to "application/json")
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36"
        val COUNTRIES = listOf(
            KoolCountry("Türkiye", listOf("Turkey"), "tr", "TR"),
            KoolCountry("Fransa", listOf("France","France Sport"), "fr", "FR"),
            KoolCountry("Birleşik Krallık", listOf("United Kingdom"), "en", "GB"),
            KoolCountry("Almanya", listOf("Germany"), "de", "DE"),
            KoolCountry("İtalya", listOf("Italy"), "it", "IT"),
            KoolCountry("İspanya", listOf("Spain"), "es", "ES"),
            KoolCountry("Portekiz", listOf("Portugal"), "pt", "PT"),
            KoolCountry("Hollanda", listOf("Netherlands"), "nl", "NL"),
            KoolCountry("Polonya", listOf("Poland"), "pl", "PL"),
            KoolCountry("Romanya", listOf("Romania"), "ro", "RO"),
            KoolCountry("Bulgaristan", listOf("Bulgaria"), "bg", "BG"),
            KoolCountry("Hırvatistan", listOf("Croatia"), "hr", "HR"),
            KoolCountry("Arnavutluk", listOf("Albania"), "sq", "AL"),
            KoolCountry("Balkanlar", listOf("Balkans"), "en", "RS"),
            KoolCountry("Arap Kanalları", listOf("Arabia"), "ar", "SA"),
            KoolCountry("Rusya", listOf("Russia"), "ru", "RU"),
        )
    }
}
