package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.anda.data.local.entity.ServiceRequestEntity

@Dao
interface ServiceRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: ServiceRequestEntity): Long

    @Query("SELECT * FROM service_requests ORDER BY updatedAt DESC")
    suspend fun listAll(): List<ServiceRequestEntity>

    @Query("SELECT * FROM service_requests WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun listByStatus(status: String): List<ServiceRequestEntity>

    @Query(
        """
        SELECT * FROM service_requests
        WHERE contractorCnpj = :contractorCnpj
          AND status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS')
        ORDER BY updatedAt DESC
        """
    )
    suspend fun listActiveByContractorCnpj(contractorCnpj: String): List<ServiceRequestEntity>

    @Query("SELECT * FROM service_requests WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ServiceRequestEntity?

    @Query("SELECT * FROM service_requests WHERE requestCode = :requestCode LIMIT 1")
    suspend fun findByRequestCode(requestCode: String): ServiceRequestEntity?

    @Query(
        """
        SELECT COUNT(*) FROM service_requests
        WHERE assignedEmployeeCpf = :employeeCpf
          AND status IN ('ASSIGNED', 'IN_PROGRESS')
        """
    )
    suspend fun countActiveByAssignee(employeeCpf: String): Int

    @Query(
        """
        UPDATE service_requests
        SET status = :status,
            assignedEmployeeCpf = :assignedEmployeeCpf,
            assignedEmployeeName = :assignedEmployeeName,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateAssignment(
        id: Long,
        status: String,
        assignedEmployeeCpf: String,
        assignedEmployeeName: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE service_requests
        SET status = :status,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE service_requests
        SET notes = :notes,
            status = :status,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateNotesAndStatus(
        id: Long,
        notes: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE service_requests
        SET status = 'COMPLETED',
            updatedAt = :updatedAt
        WHERE status = 'DONE'
        """
    )
    suspend fun normalizeLegacyCompletedStatuses(updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM service_requests WHERE id = :id")
    suspend fun deleteById(id: Long)
}

