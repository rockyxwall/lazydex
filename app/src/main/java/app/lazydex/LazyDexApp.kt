package app.lazydex

import android.app.Application
import androidx.work.Configuration
import app.lazydex.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LazyDexApp : Application(), Configuration.Provider {

    private val koinWorkerFactory: KoinWorkerFactory by inject()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(koinWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LazyDexApp)
            modules(appModule)
        }
    }
}
