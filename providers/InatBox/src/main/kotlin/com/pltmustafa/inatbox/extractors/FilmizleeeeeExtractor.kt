package com.pltmustafa.inatbox.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.pltmustafa.inatbox.utils.InatBoxCrypto

class FilmizleeeeeExtractor : ExtractorApi() {
    override val name: String = "Filmizleeeee"
    override val mainUrl: String = "https://embed.filmizleeeee.cfd"
    override val requiresReferer: Boolean = true

    companion object {
        private const val TAG = "FilmizleeeeeExtractor"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = url.replace("/web.php", "/exo.php")
        Log.d(TAG, "getUrl: targetUrl=$targetUrl, referer=$referer")
        val signedHeaders = InatBoxCrypto.getSignedHeaders(targetUrl, "GET", "").toMutableMap()
        if (!referer.isNullOrBlank()) {
            signedHeaders["Referer"] = referer
        }
        val response = app.get(targetUrl, headers = signedHeaders)
        Log.d(TAG, "getUrl: response status=${response.code}")
        if (!response.isSuccessful) {
            return
        }
        val m3u8 = response.text.trim()
        Log.d(TAG, "getUrl: extracted m3u8=$m3u8")
        val streamHeaders = mutableMapOf<String, String>()
        if (!referer.isNullOrBlank()) {
            streamHeaders["Referer"] = referer
        }
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = m3u8,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = streamHeaders
            }
        )
    }
}
