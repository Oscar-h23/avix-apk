package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [IncidentEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AviDatabase : RoomDatabase() {

    abstract fun incidentDao(): IncidentDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(
                    db: SupportSQLiteDatabase
                ) {
                    // Las filas antiguas se preservan, pero no se
                    // atribuyen al siguiente operador que inicie sesión.
                    db.execSQL(
                        "ALTER TABLE incidencias ADD COLUMN operatorId TEXT"
                    )
                    db.execSQL(
                        "ALTER TABLE incidencias ADD COLUMN ownerPlazaId INTEGER"
                    )
                    db.execSQL(
                        "ALTER TABLE incidencias ADD COLUMN serverOrigin TEXT"
                    )
                    db.execSQL(
                        """
                        CREATE INDEX index_incident_owner_sync
                        ON incidencias (
                            operatorId,
                            ownerPlazaId,
                            serverOrigin,
                            estadoSincronizacion,
                            timestamp
                        )
                        """.trimIndent()
                    )
                }
            }

        @Volatile
        private var INSTANCE: AviDatabase? = null

        fun getInstance(
            context: Context
        ): AviDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AviDatabase::class.java,
                    "avi_sigo_incidencias.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
