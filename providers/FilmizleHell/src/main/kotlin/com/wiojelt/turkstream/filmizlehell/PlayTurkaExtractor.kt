package com.wiojelt.turkstream.filmizlehell

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class PlayTurka : ExtractorApi() {
    override val name = "PlayTurka"
    override val mainUrl = "https://p.playturka.space"
    override val requiresReferer = true
    private fun decodeChar(c: Char): Char = when (c) {
        in 'A'..'Z' -> ('Z'.code - (c.code - 'A'.code)).toChar()
        in 'a'..'z' -> ('z'.code - (c.code - 'a'.code)).toChar()
        in '0'..'4' -> (c.code + 5).toChar()
        in '5'..'9' -> (c.code - 5).toChar()
        '-' -> '+'; '_' -> '/'; else -> c
    }
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile)->Unit, callback: (ExtractorLink)->Unit) {
        val id=url.substringAfter('#').substringBefore('?').ifBlank { throw ErrorLoadingException("Video kimliği bulunamadı") }
        val encrypted=app.get("$mainUrl/api/video-url?id=$id", referer=referer).text.trim()
        val json=base64Decode(encrypted.map(::decodeChar).joinToString(""))
        val payload=AppUtils.parseJson<Response>(json)
        val stream=payload.files?.masterUrl ?: throw ErrorLoadingException("Video akışı bulunamadı")
        payload.files.subtitles.orEmpty().forEach { (lang, sub) -> subtitleCallback(SubtitleFile(lang, sub)) }
        callback(newExtractorLink(name,name,stream,ExtractorLinkType.M3U8){ this.referer=url; quality=Qualities.Unknown.value })
    }
    data class Response(val status:String?=null,val files:Files?=null)
    data class Files(@JsonProperty("masterUrl") val masterUrl:String?=null,val subtitles:Map<String,String>?=null)
}
