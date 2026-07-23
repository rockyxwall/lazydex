package app.lazydex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.anilist.AnilistApi
import app.lazydex.data.anilist.AnilistInterceptor
import app.lazydex.data.anilist.AnilistRateLimiter
import app.lazydex.data.anilist.AnilistSyncManager
import app.lazydex.data.anilist.AnilistSyncQueueWorker
import app.lazydex.data.anilist.AnilistTokenStore
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.MIGRATION_1_2
import app.lazydex.data.local.Migration2To3
import app.lazydex.data.local.ThemePreferences
import app.lazydex.data.repository.MediaRepositoryImpl
import app.lazydex.domain.repository.MediaRepository
import app.lazydex.scraper.MetadataScraper
import app.lazydex.scraper.SafeDns
import app.lazydex.scraper.source.AniListSource
import app.lazydex.scraper.source.GenericSource
import app.lazydex.scraper.source.MangaDexSource
import app.lazydex.scraper.source.NovelUpdatesSource
import app.lazydex.scraper.source.RoyalRoadSource
import app.lazydex.scraper.source.SourceRegistry
import app.lazydex.scraper.source.WtrLabSource
import app.lazydex.ui.addedit.UnifiedAddEditViewModel
import app.lazydex.ui.browser.BrowserViewModel
import app.lazydex.ui.dex.DexViewModel
import app.lazydex.ui.settings.SettingsViewModel
import app.lazydex.ui.statistics.StatisticsViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.time.Duration

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            LazyDexDatabase::class.java,
            "lazydex_db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_1_2, Migration2To3(androidContext()))
        .fallbackToDestructiveMigration()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                android.util.Log.w("LazyDexDatabase", "Room database was wiped due to a destructive migration!")
            }
        })
        .build()
    }

    single { get<LazyDexDatabase>().mediaItemDao() }
}

val repositoryModule = module {
    single<MediaRepository> {
        MediaRepositoryImpl(
            dao = get(),
            localCoversDir = get(named("coversDir"))
        )
    }
}

val preferencesModule = module {
    single { ThemePreferences(androidContext()) }
}

val scraperModule = module {
    single {
        OkHttpClient.Builder()
            .dns(SafeDns())
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(10))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    single { WtrLabSource(get()) }
    single { NovelUpdatesSource(get()) }
    single { RoyalRoadSource(get()) }
    single { MangaDexSource(get()) }
    single { AniListSource(get()) }
    single { GenericSource(get()) }
    single {
        SourceRegistry(
            listOf(
                get<WtrLabSource>(),
                get<NovelUpdatesSource>(),
                get<RoyalRoadSource>(),
                get<MangaDexSource>(),
                get<AniListSource>(),
                get<GenericSource>()
            )
        )
    }
    single { MetadataScraper(get(), get()) }
}

val anilistModule = module {
    single { AnilistTokenStore(androidContext()) }
    single { AnilistRateLimiter(androidContext()) }
    single { AnilistInterceptor(androidContext(), get()) }
    single(named("anilistHttpClient")) {
        OkHttpClient.Builder()
            .dns(SafeDns())
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(10))
            .addInterceptor(get<AnilistRateLimiter>())
            .addInterceptor(get<AnilistInterceptor>())
            .build()
    }
    single { AnilistApi(get(named("anilistHttpClient"))) }
    single { AnilistSyncManager(get(), get(), get()) }
    workerOf(::AnilistSyncQueueWorker)
}

val storageModule = module {
    single(named("cacheDir")) { androidContext().cacheDir }
    single(named("coversDir")) { File(androidContext().filesDir, "covers") }
}

val viewModelModule = module {
    viewModel { DexViewModel(get()) }
    viewModel { StatisticsViewModel(get()) }
    viewModel { BrowserViewModel(get()) }
    viewModel {
        UnifiedAddEditViewModel(
            savedStateHandle = get(),
            repository = get(),
            scraper = get(),
            okHttpClient = get(),
            cacheDir = get(named("cacheDir")),
            localCoversDir = get(named("coversDir")),
            anilistApi = get(),
            syncManager = get()
        )
    }
    viewModel {
        SettingsViewModel(
            repository = get(),
            themePreferences = get(),
            localCoversDir = get(named("coversDir")),
            tokenStore = get(),
            syncManager = get(),
            dao = get()
        )
    }
}

val appModule = listOf(
    databaseModule,
    repositoryModule,
    preferencesModule,
    scraperModule,
    anilistModule,
    storageModule,
    viewModelModule
)
