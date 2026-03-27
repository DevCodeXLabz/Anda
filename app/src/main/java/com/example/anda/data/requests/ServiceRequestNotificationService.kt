package com.example.anda.data.requests

import android.content.Context
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.anda.R
import com.example.anda.feature.requests.ServiceRequestDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service Request Assignment & Notification System
 *
 * Handles:
 * - Automatic assignment of requests to technicians
 * - Notification delivery (local push notifications)
 * - Request status workflow (OPEN → ASSIGNED → IN_PROGRESS → COMPLETED)
 * - Document linking to requests
 */
class ServiceRequestNotificationService(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "anda_service_requests"

    /**
     * Create notification channel (required for Android 8+).
     */
    fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            channelId,
            "Solicitações de Serviço",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificações sobre solicitações de serviço"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Notify technician that a new service request has been assigned to them.
     */
    fun notifyTechnicianAssignment(
        technicianCpf: String,
        technicianName: String,
        companyName: String,
        requestType: String,
        requestId: String
    ) {
        val deepLinkUri = android.net.Uri.parse("anda://app/request?requestCode=$requestId")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, ServiceRequestDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nova Solicitação")
            .setContentText("$companyName solicita: $requestType")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(requestId.hashCode(), notification)
    }

    /**
     * Notify company that a technician has started working on their request.
     */
    fun notifyCompanyRequestStarted(
        companyEmail: String,
        companyName: String,
        technicianName: String,
        requestId: String
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Serviço em andamento")
            .setContentText("$technicianName está trabalhando na sua solicitação")
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestId.hashCode() + 1000, notification)
    }

    /**
     * Notify company that their request has been completed.
     */
    fun notifyCompanyRequestCompleted(
        companyEmail: String,
        companyName: String,
        requestType: String,
        requestId: String,
        documentCount: Int
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Serviço Concluído")
            .setContentText("$requestType - $documentCount documentos criados")
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Sua solicitação foi concluída com sucesso. Você tem $documentCount documentos para download."))
            .build()

        notificationManager.notify(requestId.hashCode() + 2000, notification)
    }
}

/**
 * Assignment algorithm for matching requests to technicians.
 *
 * Considers:
 * - Technician skills (which NR they're certified for)
 * - Current workload (fewer active requests = higher priority)
 * - Geographic proximity (optional, if location data available)
 * - Response time history (who responds faster)
 */
class ServiceRequestAssignmentEngine {

    /**
     * Find the best technician for a request based on skills and availability.
     *
     * @param requestType The type of document needed (ASO, PCMSO, NR10, etc.)
     * @param availableTechs List of technicians in the company with their skills
     * @return The best matching technician CPF, or null if no match
     */
    fun findBestTechnician(
        requestType: String,
        availableTechs: List<TechnicianProfile>
    ): String? {
        // Step 1: Filter technicians who are certified for this request type
        val qualified = availableTechs.filter { tech ->
            tech.certifications.contains(requestType) || tech.certifications.contains("ALL")
        }

        if (qualified.isEmpty()) return null

        // Step 2: Sort by availability (least busy first)
        return qualified
            .sortedBy { it.activeRequestCount }
            .firstOrNull()
            ?.cpf
    }

    /**
     * Calculate assignment score (0-100) for a technician.
     * Higher = better match.
     */
    fun calculateAssignmentScore(
        tech: TechnicianProfile,
        requestType: String
    ): Int {
        var score = 50  // Base score

        // Certification match
        if (tech.certifications.contains(requestType)) {
            score += 30
        } else if (tech.certifications.contains("ALL")) {
            score += 20
        }

        // Availability bonus (fewer active requests)
        if (tech.activeRequestCount == 0) {
            score += 20
        } else if (tech.activeRequestCount < 3) {
            score += 10
        }

        // Experience bonus (if available)
        score += (tech.yearsExperience * 2).coerceAtMost(10)

        return score.coerceIn(0, 100)
    }
}

/**
 * Technician profile for assignment logic.
 */
data class TechnicianProfile(
    val cpf: String,
    val name: String,
    val certifications: List<String>,  // ASO, PCMSO, NR10, etc.
    val activeRequestCount: Int = 0,   // How many requests they're working on
    val yearsExperience: Int = 0,
    val responseTimeMinutes: Int = 30  // Average time to accept new request
)

