package com.wiojelt.turkstream.sonhdfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class FirePlayerExtractor : ExtractorApi() {
    override val name = "HDPlayerX"
    override val mainUrl = "https://hdplayerx.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val pageUrl = url.substringBefore('?').trimEnd('/')
        val id = pageUrl.substringAfterLast('/')
        app.get(pageUrl, referer = referer ?: mainUrl)
        val response = app.post(
            "$pageUrl?do=getVideo",
            headers = mapOf("Referer" to pageUrl, "X-Requested-With" to "XMLHttpRequest"),
            data = mapOf("data" to id, "do" to "getVideo"),
        ).text
        val source = Regex(""""videoSrc"\s*:\s*"([^"]+)"""").find(response)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: throw ErrorLoadingException("HDPlayerX kaynağı bulunamadı")
        if (source.contains("odnoklassniki.ru", true)) {
            val embed = app.get(source, headers = mapOf("User-Agent" to USER_AGENT)).text
                .replace("&quot;", "\"")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
            val videos = Regex("""\{"name":"([^"]+)","url":"([^"]+)"""").findAll(embed).toList()
            if (videos.isEmpty()) throw ErrorLoadingException("Odnoklassniki videosu bulunamadı")
            videos.forEach { match ->
                val label = match.groupValues[1]
                callback(newExtractorLink(name, "$name $label", match.groupValues[2], INFER_TYPE) {
                    this.referer = "https://odnoklassniki.ru/"
                    quality = getQualityFromName(label)
                })
            }
        } else {
            loadExtractor(source, pageUrl, subtitleCallback, callback)
        }
    }
}
