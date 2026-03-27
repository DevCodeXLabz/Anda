package com.example.anda.data.services
import android.content.Context
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.CompanyEntity
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.EmployeeEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * LGPD / GDPR Data Export Service.
 * Implements the right to data portability (LGPD Art. 18, VI).
 */
class GdprDataExportService(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dtFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    suspend fun exportForEmployee(cpf: String): String? = withContext(Dispatchers.IO) {
        try {
            val normCpf = cpf.replace(Regex("[^0-9]"), "")
            val employee: EmployeeEntity? = db.employeeDao().findByCpf(cpf)
            val documents: List<DocumentEntity> = db.documentDao().getAllByEmployee(cpf)
            val requests: List<ServiceRequestEntity> = db.serviceRequestDao().findByAssignedEmployee(cpf)
            val root = JSONObject().apply {
                put("export_type", "employee_data_portability")
                put("lgpd_basis", "Art. 18, VI – LGPD (Lei 13.709/2018)")
                put("exported_at", dtFmt.format(Date()))
                put("data_subject_cpf_hash", normCpf.sha256hex())
                put("employee", employee?.toPortabilityJson() ?: JSONObject.NULL)
                put("documents", documents.map { it.toPortabilityJson() }.toJSONArray())
                put("service_requests", requests.map { it.toPortabilityJson() }.toJSONArray())
            }
            val dir = File(context.getExternalFilesDir("lgpd_export"), "").also { it.mkdirs() }
            val file = File(dir, "data_export_${System.currentTimeMillis()}.json")
            FileWriter(file).use { it.write(root.toString(2)) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    suspend fun exportForCompany(cnpj: String): String? = withContext(Dispatchers.IO) {
        try {
            val normCnpj = cnpj.replace(Regex("[^0-9]"), "")
            val company: CompanyEntity? = db.companyDao().findByCnpj(cnpj)
            val requests: List<ServiceRequestEntity> = db.serviceRequestDao().findByContractorCnpj(cnpj)
            val documents: List<DocumentEntity> = db.documentDao().getAllByCompany(cnpj)
            val root = JSONObject().apply {
                put("export_type", "company_data_portability")
                put("lgpd_basis", "Art. 18, VI – LGPD (Lei 13.709/2018)")
                put("exported_at", dtFmt.format(Date()))
                put("data_subject_cnpj_hash", normCnpj.sha256hex())
                put("company", company?.toPortabilityJson() ?: JSONObject.NULL)
                put("service_requests", requests.map { it.toPortabilityJson() }.toJSONArray())
                put("documents", documents.map { it.toPortabilityJson() }.toJSONArray())
            }
            val dir = File(context.getExternalFilesDir("lgpd_export"), "").also { it.mkdirs() }
            val file = File(dir, "company_export_${System.currentTimeMillis()}.json")
            FileWriter(file).use { it.write(root.toString(2)) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    private fun EmployeeEntity.toPortabilityJson() = JSONObject().apply {
        put("name", name)
        put("cpf_hash", cpf.replace(Regex("[^0-9]"), "").sha256hex())
        put("role", role)
        put("collaborator_type", collaboratorType)
        put("company_cnpj_hash", companyCnpj.replace(Regex("[^0-9]"), "").sha256hex())
        put("registered_at", dtFmt.format(Date(createdAt)))
    }
    private fun CompanyEntity.toPortabilityJson() = JSONObject().apply {
        put("legal_name", legalName)
        put("trade_name", tradeName)
        put("cnpj_hash", cnpj.replace(Regex("[^0-9]"), "").sha256hex())
        put("cnae", cnae)
        put("city", city)
        put("state", state)
    }
    private fun DocumentEntity.toPortabilityJson() = JSONObject().apply {
        put("document_id", documentId)
        put("document_type", documentType)
        put("created_at", dtFmt.format(Date(createdAt)))
        put("signed", signedAt != null)
        put("synced", isSynced)
    }
    private fun ServiceRequestEntity.toPortabilityJson() = JSONObject().apply {
        put("request_code", requestCode)
        put("document_type", requestedDocumentType)
        put("status", status)
        put("created_at", dtFmt.format(Date(createdAt)))
        put("updated_at", dtFmt.format(Date(updatedAt)))
    }
    private fun List<JSONObject>.toJSONArray(): JSONArray {
        val arr = JSONArray()
        forEach { arr.put(it) }
        return arr
    }
    private fun String.sha256hex(): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
