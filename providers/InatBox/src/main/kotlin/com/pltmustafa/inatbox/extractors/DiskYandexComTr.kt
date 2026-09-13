package com.pltmustafa.inatbox.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.regex.Pattern

class DiskYandexComTr : ExtractorApi() {
    override val name: String = "DiskYandexComTr"
    override val mainUrl: String = "https://disk.yandex.com.tr"
    override val requiresReferer: Boolean = false

    private val masterPlaylistRegex: Pattern = Pattern.compile("https?://[^\\s\"]*?master-playlist\\.m3u8")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val response = app.get(url, headers = headers, referer = "https://disk.yandex.com.tr/")
        if (!response.isSuccessful) {
            return
        }
        val matcher = masterPlaylistRegex.matcher(response.text)
        if (matcher.find()) {
            val masterPlaylistUrl = matcher.group()
            callback.invoke(
                newExtractorLink(
                    source = "Yandex Disk",
                    name = "Yandex Disk",
                    url = masterPlaylistUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = mapOf("Referer" to "https://disk.yandex.com.tr/")
                }
            )
        }
    }
}
