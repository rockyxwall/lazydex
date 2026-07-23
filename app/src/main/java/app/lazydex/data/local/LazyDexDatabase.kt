package app.lazydex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN genres TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN author TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN startDate INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN endDate INTEGER DEFAULT NULL")
    }
}

class Migration2To3(private val context: Context) : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val prefs = context.getSharedPreferences("lazydex_settings", Context.MODE_PRIVATE)
        val oldFormat = prefs.getString("score_format", "POINT_10_DECIMAL") ?: "POINT_10_DECIMAL"

        val multiplier = when (oldFormat) {
            "POINT_10_DECIMAL", "POINT_10" -> 10.0
            "POINT_5" -> 20.0
            "POINT_100" -> 1.0
            "POINT_3" -> 33.33
            else -> 10.0
        }

        db.beginTransaction()
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `media_items_new` (
                    `id` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `alternativeTitles` TEXT NOT NULL,
                    `sourceUrl` TEXT,
                    `coverImagePath` TEXT NOT NULL,
                    `coverImageUrl` TEXT,
                    `currentProgress` INTEGER NOT NULL,
                    `totalItems` INTEGER,
                    `userStatus` TEXT NOT NULL,
                    `rating` INTEGER,
                    `notes` TEXT NOT NULL,
                    `genres` TEXT NOT NULL DEFAULT '[]',
                    `tags` TEXT NOT NULL DEFAULT '[]',
                    `author` TEXT NOT NULL DEFAULT '',
                    `description` TEXT NOT NULL DEFAULT '',
                    `startDate` INTEGER,
                    `endDate` INTEGER,
                    `lastUpdated` INTEGER NOT NULL,
                    `dateAdded` INTEGER NOT NULL,
                    `localUpdatedAt` INTEGER NOT NULL DEFAULT 0,
                    `lastSyncedAt` INTEGER DEFAULT NULL,
                    `anilistListEntryId` INTEGER DEFAULT NULL,
                    `isPrivate` INTEGER NOT NULL DEFAULT 0,
                    `mediaFormat` TEXT DEFAULT NULL,
                    `rawFormat` TEXT DEFAULT NULL,
                    `publishingStatus` TEXT DEFAULT NULL,
                    `season` TEXT DEFAULT NULL,
                    `totalVolumes` INTEGER DEFAULT NULL,
                    `progressVolumes` INTEGER NOT NULL DEFAULT 0,
                    `durationMinutes` INTEGER DEFAULT NULL,
                    `sourceMaterial` TEXT DEFAULT NULL,
                    `isAdult` INTEGER NOT NULL DEFAULT 0,
                    `isDoujin` INTEGER NOT NULL DEFAULT 0,
                    `syncPendingAction` TEXT DEFAULT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO `media_items_new` (
                    id, category, title, alternativeTitles, sourceUrl, coverImagePath, coverImageUrl,
                    currentProgress, totalItems, userStatus, rating, notes, genres, tags, author,
                    description, startDate, endDate, lastUpdated, dateAdded,
                    localUpdatedAt, lastSyncedAt, anilistListEntryId, isPrivate, mediaFormat,
                    rawFormat, publishingStatus, season, totalVolumes, progressVolumes,
                    durationMinutes, sourceMaterial, isAdult, isDoujin, syncPendingAction
                )
                SELECT 
                    id, category, title, alternativeTitles, sourceUrl, coverImagePath, coverImageUrl,
                    currentProgress, totalItems, userStatus,
                    CASE 
                        WHEN rating IS NULL THEN NULL 
                        ELSE CAST(ROUND(rating * $multiplier) AS INTEGER) 
                    END,
                    notes, genres, tags, author, description, startDate, endDate, lastUpdated, dateAdded,
                    COALESCE(lastUpdated, 0), NULL, NULL, 0, NULL,
                    NULL, NULL, NULL, NULL, 0,
                    NULL, NULL, 0, 0, NULL
                FROM `media_items`
            """.trimIndent())

            db.execSQL("DROP TABLE `media_items`")
            db.execSQL("ALTER TABLE `media_items_new` RENAME TO `media_items`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_items_sourceUrl` ON `media_items` (`sourceUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_anilistListEntryId` ON `media_items` (`anilistListEntryId`)")

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

@Database(entities = [MediaItemEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
}
