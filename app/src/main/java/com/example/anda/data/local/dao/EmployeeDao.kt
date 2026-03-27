package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.anda.data.local.entity.EmployeeEntity

@Dao
interface EmployeeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(employee: EmployeeEntity): Long

    @Delete
    suspend fun delete(employee: EmployeeEntity)

    @Query("SELECT * FROM employees ORDER BY name ASC")
    suspend fun listAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE companyCnpj = :cnpj ORDER BY name ASC")
    suspend fun listByCompany(cnpj: String): List<EmployeeEntity>

    @Query(
        "SELECT * FROM employees WHERE " +
        "name LIKE '%' || :query || '%' OR cpf LIKE '%' || :query || '%' " +
        "ORDER BY name ASC"
    )
    suspend fun search(query: String): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE cpf = :cpf LIMIT 1")
    suspend fun findByCpf(cpf: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByExactName(name: String): EmployeeEntity?

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM employees
        WHERE (:companyCnpj = '' OR companyCnpj = :companyCnpj OR companyCnpj = '')
          AND (
            collaboratorType = 'internal'
            OR (collaboratorType = 'freelancer' AND approvalStatus = 'approved')
          )
        ORDER BY name ASC
        """
    )
    suspend fun listAssignableByCompany(companyCnpj: String): List<EmployeeEntity>

    @Query(
        "UPDATE employees SET lastAsoDate = :date, nextAsoDate = :nextDate, " +
        "updatedAt = :now WHERE cpf = :cpf"
    )
    suspend fun updateAsoDate(
        cpf: String,
        date: Long,
        nextDate: Long,
        now: Long = System.currentTimeMillis()
    )
}

