package app.lazydex.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaStats(
    val totalCount: Int,
    val completedCount: Int,
    val totalProgress: Int,
    val meanRating: Double?,
    val inProgressCount: Int,
    val novelCount: Int,
    val mangaCount: Int,
    val animeCount: Int,
    val gameCount: Int
)
