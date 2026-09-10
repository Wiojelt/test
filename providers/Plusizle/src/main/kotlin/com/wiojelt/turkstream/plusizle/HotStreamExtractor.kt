// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.wiojelt.turkstream.plusizle

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

open class HotStream : ExtractorApi() {
    override val name            = "HotStream"
    override val mainUrl         = "https://hotstream.club"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val m3uLink:String?
        val extRef  = referer ?: ""
        val iSource = app.get(url, referer=extRef).text

        val bePlayer = Regex("""bePlayer\('([^']+)',\s*'(\{[^}]+\})'\);""").find(iSource)?.groupValues
        if (bePlayer != null) {
            val bePlayerPass = bePlayer[1]
            val bePlayerData = bePlayer[2]
            val encrypted    = AesHelper.cryptoAESHandler(bePlayerData, bePlayerPass.toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")
            Log.d("Kekik_${this.name}", "encrypted » $encrypted")
            val payload = jacksonObjectMapper().readTree(encrypted)
            m3uLink = payload.path("video_location").asText().takeIf(String::isNotBlank)
            payload.path("strSubtitles").forEach { track ->
                val file = track.path("file").asText().takeIf(String::isNotBlank) ?: return@forEach
                val label = track.path("label").asText().ifBlank { track.path("language").asText().ifBlank { "Altyazı" } }
                subtitleCallback(SubtitleFile(label, if (file.startsWith("http")) file else fixUrl(file)))
            }
        } else {
            m3uLink = Regex("""file:"([^"]+)""").find(iSource)?.groupValues?.get(1)

            val trackStr = Regex("""tracks:\[([^]]+)""").find(iSource)?.groupValues?.get(1)
            if (trackStr != null) {
                val tracks:List<Track> = jacksonObjectMapper().readValue("[${trackStr}]")

                for (track in tracks) {
                    if (track.file == null || track.label == null) continue
                    if (track.label.contains("Forced")) continue

                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = track.label,
                            url  = fixUrl(mainUrl + track.file)
                        )
                    )
                }
            }
        }

        val stream = m3uLink ?: throw ErrorLoadingException("m3u link not found")
        val streamHeaders = mapOf("Referer" to url, "Origin" to mainUrl, "User-Agent" to USER_AGENT)
        M3u8Helper.generateM3u8(name, stream, url, headers = streamHeaders).forEach(callback)

    }

    data class Track(
        @JsonProperty("file")     val file: String?,
        @JsonProperty("label")    val label: String?,
        @JsonProperty("kind")     val kind: String?,
        @JsonProperty("language") val language: String?,
        @JsonProperty("default")  val default: String?
    )
}
