package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.IncidentRepository
import com.example.data.SyncOutcome
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class SyncRegistroWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {

    override suspend fun doWork():
        Result {
        Log.d(
            TAG,
            "Iniciando sincronización AVIX."
        )

        val repository =
            IncidentRepository.getInstance(
                applicationContext
            )

        return try {
            when (
                repository
                    .sincronizarPendientesDesdeWorker()
            ) {
                SyncOutcome.COMPLETE -> {
                    Log.d(
                        TAG,
                        "Cola AVIX procesada."
                    )
                    Result.success()
                }

                SyncOutcome.AUTH_REQUIRED -> {
                    // El login vuelve a programar la cola del propietario.
                    Log.i(
                        TAG,
                        "Sincronización pausada hasta renovar sesión."
                    )
                    Result.success()
                }

                SyncOutcome.RETRY -> {
                    Log.w(
                        TAG,
                        "Fallo temporal; WorkManager reintentará."
                    )
                    Result.retry()
                }
            }
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error temporal en sincronización: ${e.message}",
                e
            )
            Result.retry()
        }
    }

    companion object {
        private const val TAG =
            "SyncRegistroWorker"

        private const val UNIQUE_ONE_TIME_WORK =
            "avi_sync_pending_one_time"

        private const val UNIQUE_PERIODIC_WORK =
            "avi_sync_periodic"

        fun enqueueImmediateSync(
            context: Context
        ) {
            try {
                val constraints =
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.CONNECTED
                        )
                        .build()

                val syncRequest =
                    OneTimeWorkRequestBuilder<
                        SyncRegistroWorker
                        >()
                        .setConstraints(
                            constraints
                        )
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            15,
                            TimeUnit.SECONDS
                        )
                        .build()

                // Serializa operaciones y evita reemplazar una ejecución activa.
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(
                        UNIQUE_ONE_TIME_WORK,
                        ExistingWorkPolicy
                            .APPEND_OR_REPLACE,
                        syncRequest
                    )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "WorkManager no disponible: ${e.message}"
                )
            }
        }

        fun schedulePeriodicSync(
            context: Context
        ) {
            try {
                val constraints =
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.CONNECTED
                        )
                        .build()

                val periodicRequest =
                    PeriodicWorkRequestBuilder<
                        SyncRegistroWorker
                        >(
                        15,
                        TimeUnit.MINUTES
                    )
                        .setConstraints(
                            constraints
                        )
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            30,
                            TimeUnit.SECONDS
                        )
                        .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_PERIODIC_WORK,
                        ExistingPeriodicWorkPolicy
                            .KEEP,
                        periodicRequest
                    )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Sincronización periódica no disponible: ${e.message}"
                )
            }
        }
    }
}
