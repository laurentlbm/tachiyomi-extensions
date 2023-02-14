package eu.kanade.tachiyomi.extension.en.poorlydrawnlines

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PoorlyDrawnLinesContentDto(
    val rendered: String = "",
)

@Serializable
data class PoorlyDrawnLinesChapterDto(
    @SerialName("date_gmt") val date: String = "",
    @SerialName("title") val title: PoorlyDrawnLinesContentDto,
    @SerialName("link") val link: String = "",
)
