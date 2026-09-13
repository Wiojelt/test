package com.pltmustafa.inatbox.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class Vk : ExtractorApi() {
    override val name: String = "Vk"
    override val mainUrl: String = "https://vk.com/"
    override val requiresReferer: Boolean = false

    private val m3u8Regex = Regex("\"([^\"]*m3u8[^\"]*)\"")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val response = app.get(url, headers = headers, referer = mainUrl)
        if (!response.isSuccessful) {
            return
        }
        val matchResult = m3u8Regex.find(response.text) ?: return
        val m3u8SourceUrl = matchResult.groupValues.getOrNull(1)?.replace("\\/", "/") ?: return
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = m3u8SourceUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = mapOf("Referer" to mainUrl)
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
