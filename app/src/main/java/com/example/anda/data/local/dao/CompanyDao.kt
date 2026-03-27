package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.anda.data.local.entity.CompanyEntity

@Dao
interface CompanyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE cnpj = :cnpj LIMIT 1")
    suspend fun findByCnpj(cnpj: String): CompanyEntity?

    @Query("SELECT * FROM companies ORDER BY legalName ASC")
    suspend fun listAll(): List<CompanyEntity>

    @Query("""
        SELECT * FROM companies
        WHERE legalName LIKE '%' || :query || '%'
           OR cnpj LIKE '%' || :query || '%'
           OR tradeName LIKE '%' || :query || '%'
        ORDER BY legalName ASC
    """)
    suspend fun search(query: String): List<CompanyEntity>

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ─── LGPD / GDPR ──────────────────────────────────────────────────────────

    @Query(
        "UPDATE companies SET legalName = :anonName, tradeName = :anonName, " +
        "cnpj = :anonCnpj, contactWhatsapp = '', updatedAt = :now WHERE cnpj = :cnpj"
    )
    suspend fun anonymiseByCnpj(
        cnpj: String,
        anonName: String,
        anonCnpj: String,
        now: Long = System.currentTimeMillis()
    ): Int
}
