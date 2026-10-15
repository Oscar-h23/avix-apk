package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Query(
        """
        SELECT * FROM incidencias
        WHERE operatorId = :operatorId
          AND ownerPlazaId = :plazaId
          AND serverOrigin = :server
        ORDER BY timestamp DESC
        """
    )
    fun getAllFlow(
        operatorId: String,
        plazaId: Long,
        server: String
    ): Flow<List<IncidentEntity>>

    @Query(
        """
        SELECT * FROM incidencias
        WHERE operatorId = :operatorId
          AND ownerPlazaId = :plazaId
          AND serverOrigin = :server
          AND estadoSincronizacion IN (
              'PENDIENTE',
              'SINCRONIZANDO'
          )
        ORDER BY timestamp ASC
        """
    )
    suspend fun getPendientes(
        operatorId: String,
        plazaId: Long,
        server: String
    ): List<IncidentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM incidencias
        WHERE operatorId IS NULL
           OR ownerPlazaId IS NULL
           OR serverOrigin IS NULL
        """
    )
    fun getUnassignedCountFlow(): Flow<Int>

    @Query(
        "SELECT * FROM incidencias WHERE id = :id LIMIT 1"
    )
    suspend fun getById(
        id: String
    ): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        entity: IncidentEntity
    ): Long

    @Update
    suspend fun update(
        entity: IncidentEntity
    )

    @Query(
        "DELETE FROM incidencias WHERE id = :id"
    )
    suspend fun delete(
        id: String
    )
}
