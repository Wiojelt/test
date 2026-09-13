package com.pltmustafa.inatbox.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class DzenRu : ExtractorApi() {
    override val name: String = "Dzen"
    override val mainUrl: String = "https://dzen.ru"
    override val requiresReferer: Boolean = false

    private data class StreamCandidate(
        val url: String,
        val type: ExtractorLinkType,
        val score: Int
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        if (!response.isSuccessful) {
            return
        }
        val document = response.text
        val regex = Regex("\\{\"url\":\"([^\"]*)\",\"type\":\"([^\"]*)\"\\}", RegexOption.IGNORE_CASE)
        val candidates = regex.findAll(document).mapNotNull { match ->
            val videoUrl = match.groupValues.getOrNull(1)?.replace("\\/", "/") ?: return@mapNotNull null
            val typeStr = match.groupValues.getOrNull(2) ?: ""
            val type = when {
                typeStr.contains("dash", ignoreCase = true) -> ExtractorLinkType.DASH
                typeStr.contains("hls", ignoreCase = true) -> ExtractorLinkType.M3U8
                else -> ExtractorLinkType.VIDEO
            }
            val qualitySuffix = videoUrl.substringAfterLast("=", "")
            val qualityScore = when {
                qualitySuffix.contains("fullhd", ignoreCase = true) -> 6
                qualitySuffix.contains("high", ignoreCase = true) -> 5
                qualitySuffix.contains("medium", ignoreCase = true) -> 4
                qualitySuffix.contains("low", ignoreCase = true) -> 3
                qualitySuffix.contains("tiny", ignoreCase = true) -> 1
                else -> 2
            }
            val typeScore = if (type == ExtractorLinkType.M3U8) 1 else 0
            val totalScore = qualityScore * 10 + typeScore
            StreamCandidate(videoUrl, type, totalScore)
        }.toList()

        val bestCandidate = candidates.maxByOrNull { it.score } ?: return
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = bestCandidate.url,
                type = bestCandidate.type
            )
        )
    }
}
