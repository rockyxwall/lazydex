package app.lazydex.data.local

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

@Database(entities = [MediaItemEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
}

