package com.example.anda.data.requests

import android.content.Context
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.ServiceRequestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service Request Orchestrator
 *
 * Coordinates the entire request lifecycle:
 * 1. Company submits request
 * 2. System auto-assigns to best technician
 * 3. Technician receives notification
 * 4. Technician creates documents
 * 5. Company receives completion notification
 */
class ServiceRequestOrchestrator(
    private val context: Context,
    private val database: AppDatabase
) {

    private val notificationService = ServiceRequestNotificationService(context)
    private val assignmentEngine = ServiceRequestAssignmentEngine()
    private val requestDao = database.serviceRequestDao()

    init {
        notificationService.createNotificationChannel()
    }

    /**
     * Main entry point: Process a new service request.
     *
     * Flow:
     * 1. Create request in database
     * 2. Find best matching technician
     * 3. Assign request to technician
     * 4. Send notification to technician
     * 5. Notify company that request was received
     */
    suspend fun processNewRequest(
        companyId: String,
        companyName: String,
        requestType: String,  // ASO, PCMSO, NR10, etc.
        description: String,
        availableTechs: List<TechnicianProfile>
    ) = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create request in database
            val requestId = java.util.UUID.randomUUID().toString()
            val request = ServiceRequestEntity(
                requestCode = requestId,
                contractorName = companyName,
                contractorCnpj = companyId,
                requestedDocumentType = requestType,
                notes = description,
                status = ServiceRequestStatus.OPEN,
                assignedEmployeeCpf = "",
                assignedEmployeeName = ""
            )
            val dbId = requestDao.upsert(request)

            // Step 2: Find best matching technician
            val bestTechCpf = assignmentEngine.findBestTechnician(requestType, availableTechs)

            if (bestTechCpf != null) {
                // Step 3: Assign request to technician
                val assignedTech = availableTechs.find { it.cpf == bestTechCpf }
                if (assignedTech != null) {
                    val updatedRequest = request.copy(
                        status = ServiceRequestStatus.ASSIGNED,
                        assignedEmployeeCpf = bestTechCpf,
                        assignedEmployeeName = assignedTech.name
                    )
                    requestDao.updateAssignment(
                        id = dbId,
                        status = updatedRequest.status,
                        assignedEmployeeCpf = updatedRequest.assignedEmployeeCpf,
                        assignedEmployeeName = updatedRequest.assignedEmployeeName
                    )

                    // Step 4: Send notification to technician
                    notificationService.notifyTechnicianAssignment(
                        technicianCpf = bestTechCpf,
                        technicianName = assignedTech.name,
                        companyName = companyName,
                        requestType = requestType,
                        requestId = requestId
                    )
                }
            } else {
                // No technician available, mark as pending
                android.util.Log.w("ServiceRequest", "No technician available for $requestType")
            }

            requestId
        } catch (e: Exception) {
            android.util.Log.e("ServiceRequest", "Error processing request", e)
            throw e
        }
    }

    /**
     * Mark request as IN_PROGRESS when technician starts working.
     */
    suspend fun startRequestWork(
        requestId: String,
        technicianCpf: String,
        companyName: String
    ) = withContext(Dispatchers.IO) {
        try {
            val request = requestDao.findByRequestCode(requestId) ?: return@withContext

            requestDao.updateStatus(id = request.id, status = ServiceRequestStatus.IN_PROGRESS)

            // Notify company that work has started
            notificationService.notifyCompanyRequestStarted(
                companyEmail = "",  // Would come from database
                companyName = companyName,
                technicianName = request.assignedEmployeeName,
                requestId = requestId
            )
        } catch (e: Exception) {
            android.util.Log.e("ServiceRequest", "Error starting work", e)
        }
    }

    /**
     * Mark request as COMPLETED when technician finishes and uploads documents.
     */
    suspend fun completeRequest(
        requestId: String,
        documentCount: Int,
        requestType: String
    ) = withContext(Dispatchers.IO) {
        try {
            val request = requestDao.findByRequestCode(requestId) ?: return@withContext

            requestDao.updateStatus(id = request.id, status = ServiceRequestStatus.COMPLETED)

            // Notify company that request is complete
            notificationService.notifyCompanyRequestCompleted(
                companyEmail = "",
                companyName = "",  // Would come from company DAO
                requestType = requestType,
                requestId = requestId,
                documentCount = documentCount
            )

            android.util.Log.d("ServiceRequest", "Request $requestId completed with $documentCount docs")
        } catch (e: Exception) {
            android.util.Log.e("ServiceRequest", "Error completing request", e)
        }
    }
}

/**
 * Extension to ServiceRequestEntity to add completion tracking.
 */
val ServiceRequestEntity.isCompleted: Boolean
    get() = ServiceRequestStatus.isCompleted(status)

val ServiceRequestEntity.isAssigned: Boolean
    get() = !ServiceRequestStatus.isOpen(status) && assignedEmployeeCpf.isNotBlank()

