package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["sourceUrl"], unique = true),
        Index(value = ["anilistListEntryId"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val category: String,            // Stored as uppercase string (e.g. "NOVEL"), no ambiguity
    val title: String,
    val alternativeTitles: String,   // JSON array string: ["Alt 1", "Alt 2", ...]
    val sourceUrl: String?,          // Nullable — multiple NULLs allowed under UNIQUE index
    val coverImagePath: String,      // Local file path (not URL), empty if no cover
    val coverImageUrl: String?,      // Nullable original URL of the cover
    val currentProgress: Int,
    val totalItems: Int?,
    val userStatus: String,          // Stored as uppercase string (e.g. "READING")
    val rating: Int?,                // 0–100 integer score, null = unrated
    val notes: String,
    @ColumnInfo(defaultValue = "[]") val genres: String = "[]",
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
    @ColumnInfo(defaultValue = "") val author: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val lastUpdated: Long,
    val dateAdded: Long,

    // v0.0.3 extended fields
    @ColumnInfo(defaultValue = "0") val localUpdatedAt: Long = 0,
    @ColumnInfo(defaultValue = "NULL") val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val anilistListEntryId: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val mediaFormat: String? = null,
    @ColumnInfo(defaultValue = "NULL") val rawFormat: String? = null,
    @ColumnInfo(defaultValue = "NULL") val publishingStatus: String? = null,
    @ColumnInfo(defaultValue = "NULL") val season: String? = null,
    @ColumnInfo(defaultValue = "NULL") val totalVolumes: Int? = null,
    @ColumnInfo(defaultValue = "0") val progressVolumes: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceMaterial: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDoujin: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val syncPendingAction: String? = null
)
