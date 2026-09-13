package com.pltmustafa.inatbox.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Domain(
    @JsonProperty("DC1") val dc1: String? = null,
    @JsonProperty("DC2") val dc2: String? = null,
    @JsonProperty("DC10") val dc10: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Kategoriler(
    @JsonProperty("catName") val catName: String? = null,
    @JsonProperty("catUrl") val catUrl: String? = null,
    @JsonProperty("catType") val catType: String? = null,
    @JsonProperty("catDataType") val catDataType: String? = null,
    @JsonProperty("catHost") val catHost: String? = null,
    @JsonProperty("catSha") val catSha: String? = null,
    @JsonProperty("catImg") val catImg: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChContent(
    @JsonProperty("chName") val chName: String = "",
    @JsonProperty("chUrl") val chUrl: String = "",
    @JsonProperty("chImg") val chImg: String = "",
    @JsonProperty("chHeaders") val chHeaders: String = "",
    @JsonProperty("chReg") val chReg: String = "",
    @JsonProperty("chType") val chType: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Genre(
    @JsonProperty("ID") val id: Int? = null,
    @JsonProperty("Title") val title: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Media(
    @JsonProperty("URL") val url: String? = null,
    @JsonProperty("Type") val type: Int? = null,
    @JsonProperty("FileUID") val fileUID: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contents(
    @JsonProperty("ID") val id: Int? = null,
    @JsonProperty("Title") val title: String? = null,
    @JsonProperty("Description") val description: String? = null,
    @JsonProperty("ContentType") val contentType: Int? = null,
    @JsonProperty("Tags") val tags: String? = null,
    @JsonProperty("ScheduledStart") val scheduledStart: Long? = null,
    @JsonProperty("State") val state: Int? = null,
    @JsonProperty("Genre") val genre: List<Genre>? = null,
    @JsonProperty("Medias") val medias: List<Media>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Categories(
    @JsonProperty("Contents") val contents: List<Contents>? = null,
    @JsonProperty("State") val state: Int? = null,
    @JsonProperty("Type") val type: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SSportResponse(
    @JsonProperty("Categories") val categories: List<Categories>? = null
)
