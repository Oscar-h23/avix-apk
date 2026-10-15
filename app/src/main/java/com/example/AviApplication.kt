package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.data.IncidentRepository
import com.example.worker.SyncRegistroWorker

class AviApplication : Application(), Configuration.Provider {

    lateinit var repository: IncidentRepository
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = IncidentRepository.getInstance(this)

        try {
            // Programar sincronización periódica de registros con WorkManager
            SyncRegistroWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            Log.w("AviApplication", "Inicialización de WorkManager diferida: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: AviApplication
            private set
    }
}
