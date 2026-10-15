package com.example.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class
)
class AviDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 preserves legacy incident without assigning owner`() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        val dbName =
            "avix-migration-${System.nanoTime()}.db"

        val helper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(dbName)
                        .callback(
                            object :
                                SupportSQLiteOpenHelper.Callback(1) {

                                override fun onCreate(
                                    db: SupportSQLiteDatabase
                                ) = Unit

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int
                                ) = Unit
                            }
                        )
                        .build()
                )

        val sqlite =
            helper.writableDatabase

        try {
            sqlite.execSQL(
                """
                CREATE TABLE incidencias (
                    id TEXT NOT NULL PRIMARY KEY,
                    placa TEXT NOT NULL,
                    via INTEGER,
                    accion TEXT NOT NULL,
                    plazaId TEXT NOT NULL,
                    fechaHoraEvento TEXT NOT NULL,
                    textoReconocido TEXT NOT NULL,
                    estadoSincronizacion TEXT NOT NULL,
                    intentosSincronizacion INTEGER NOT NULL,
                    ultimoError TEXT,
                    fechaHoraRecepcion TEXT,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )

            sqlite.execSQL(
                """
                INSERT INTO incidencias (
                    id,
                    placa,
                    via,
                    accion,
                    plazaId,
                    fechaHoraEvento,
                    textoReconocido,
                    estadoSincronizacion,
                    intentosSincronizacion,
                    ultimoError,
                    fechaHoraRecepcion,
                    timestamp
                ) VALUES (
                    'legacy-1',
                    'ABC123',
                    101,
                    'FUGA',
                    'P3',
                    '2026-10-08T20:00:00-05:00',
                    'dictado',
                    'PENDIENTE',
                    0,
                    NULL,
                    NULL,
                    123456
                )
                """.trimIndent()
            )

            AviDatabase.MIGRATION_1_2
                .migrate(sqlite)

            sqlite.query(
                """
                SELECT
                    id,
                    placa,
                    operatorId,
                    ownerPlazaId,
                    serverOrigin
                FROM incidencias
                WHERE id = 'legacy-1'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(
                    cursor.moveToFirst()
                )
                assertEquals(
                    "legacy-1",
                    cursor.getString(0)
                )
                assertEquals(
                    "ABC123",
                    cursor.getString(1)
                )
                assertTrue(
                    cursor.isNull(2)
                )
                assertTrue(
                    cursor.isNull(3)
                )
                assertTrue(
                    cursor.isNull(4)
                )
            }

            val columns =
                mutableSetOf<String>()

            sqlite.query(
                "PRAGMA table_info(incidencias)"
            ).use { cursor ->
                while (
                    cursor.moveToNext()
                ) {
                    columns +=
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                "name"
                            )
                        )
                }
            }

            assertTrue(
                "operatorId" in columns
            )
            assertTrue(
                "ownerPlazaId" in columns
            )
            assertTrue(
                "serverOrigin" in columns
            )

            var indexFound = false

            sqlite.query(
                "PRAGMA index_list(incidencias)"
            ).use { cursor ->
                while (
                    cursor.moveToNext()
                ) {
                    if (
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                "name"
                            )
                        ) ==
                        "index_incident_owner_sync"
                    ) {
                        indexFound = true
                    }
                }
            }

            assertTrue(indexFound)
        } finally {
            helper.close()
            assertFalse(sqlite.isOpen)
            context.deleteDatabase(dbName)
        }
    }
}
