package com.example.anda.data.services
import android.content.Context
import com.example.anda.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * LGPD / GDPR Data Deletion Service.
 *
 * Implements the right to erasure (LGPD Art. 18, VI):
 * - Anonymises all personal data linked to a CPF or CNPJ
 * - Preserves audit-trail skeleton (no PII, only event timestamps) for legal obligations
 * - Returns a [DeletionReport] summarising what was erased / anonymised
 */
class GdprDataDeletionService(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dtFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    data class DeletionReport(
        val requestedAt: String,
        val success: Boolean,
        val employeeRowsAnonymised: Int = 0,
        val documentsAnonymised: Int = 0,
        val requestsAnonymised: Int = 0,
        val errorMessage: String? = null
    )
    /**
     * Anonymise all PII for an employee identified by [cpf].
     */
    suspend fun deleteForEmployee(cpf: String): DeletionReport = withContext(Dispatchers.IO) {
        val requestedAt = dtFmt.format(Date())
        return@withContext try {
            val placeholder = "ANONYMISED_${System.currentTimeMillis()}"
            val employeeRows = db.employeeDao().anonymiseByCpf(
                cpf = cpf,
                anonName = placeholder,
                anonCpf = "000.000.000-00"
            )
            val documentRows = db.documentDao().anonymiseByCpf(cpf, placeholder)
            val requestRows = db.serviceRequestDao().anonymiseAssigneeByCpf(cpf, placeholder)
            DeletionReport(
                requestedAt = requestedAt,
                success = true,
                employeeRowsAnonymised = employeeRows,
                documentsAnonymised = documentRows,
                requestsAnonymised = requestRows
            )
        } catch (e: Exception) {
            DeletionReport(requestedAt = requestedAt, success = false, errorMessage = e.message)
        }
    }
    /**
     * Anonymise all PII for a company identified by [cnpj].
     */
    suspend fun deleteForCompany(cnpj: String): DeletionReport = withContext(Dispatchers.IO) {
        val requestedAt = dtFmt.format(Date())
        return@withContext try {
            val placeholder = "ANONYMISED_${System.currentTimeMillis()}"
            val companyRows = db.companyDao().anonymiseByCnpj(
                cnpj = cnpj,
                anonName = placeholder,
                anonCnpj = "00.000.000/0000-00"
            )
            val requestRows = db.serviceRequestDao().anonymiseContractorByCnpj(cnpj, placeholder)
            DeletionReport(
                requestedAt = requestedAt,
                success = true,
                employeeRowsAnonymised = companyRows,
                requestsAnonymised = requestRows
            )
        } catch (e: Exception) {
            DeletionReport(requestedAt = requestedAt, success = false, errorMessage = e.message)
        }
    }
}
