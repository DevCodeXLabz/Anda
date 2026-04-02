package com.example.anda

import android.app.Application
import android.util.Log
import com.example.anda.core.security.LocalDataProtection
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.telemetry.CrashReporter
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.sync.SyncQueueRepository
import com.example.anda.data.sync.SyncScheduler
import com.example.anda.data.requests.ServiceRequestNotificationService

class AndaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { LocalDataProtection.initialize(this) }
            .onFailure { Log.e("AndaApplication", "Falha ao iniciar protecao local", it) }

        runCatching { CrashShield.install(this) }
            .onFailure { Log.e("AndaApplication", "Falha ao iniciar CrashShield", it) }

        runCatching { CrashReporter.initialize(this) }
            .onFailure { Log.e("AndaApplication", "Falha ao iniciar CrashReporter", it) }

        // Initialize notification channels
        runCatching {
            val notificationService = ServiceRequestNotificationService(this)
            notificationService.createNotificationChannel()
            // Compliance alerts channel
            val complianceNotifier = com.example.anda.notifications.ComplianceNotificationService(this)
            complianceNotifier.createNotificationChannel()
            Log.d("AndaApplication", "Notification channels initialized")
        }.onFailure { throwable ->
            CrashShield.recordRecoverableError("AndaApplication/NotificationService.initialize", throwable)
        }

        runCatching { SstRepository.initialize(this) }
            .onFailure { throwable ->
                CrashShield.recordRecoverableError("AndaApplication/SstRepository.initialize", throwable)
            }

        runCatching { DocumentLocalRepository.initialize(this) }
            .onFailure { throwable ->
                CrashShield.recordRecoverableError("AndaApplication/DocumentLocalRepository.initialize", throwable)
            }

        runCatching { SyncQueueRepository.initialize(this) }
            .onFailure { throwable ->
                CrashShield.recordRecoverableError("AndaApplication/SyncQueueRepository.initialize", throwable)
            }

        if (CrashShield.isSafeModeActive(this)) {
            Log.w("AndaApplication", "Modo seguro ativo: agendamento de sync pesado temporariamente desativado")
        } else {
            runCatching {
                SyncScheduler.schedulePeriodicSync(this)
                SyncScheduler.scheduleImmediateSync(this)
                // Schedule compliance alerts periodic check
                com.example.anda.data.sync.ComplianceAlertScheduler.schedulePeriodicChecks(this)
            }.onFailure {
                CrashShield.recordRecoverableError("AndaApplication/SyncScheduler", it)
                Log.e("AndaApplication", "Falha no agendamento automatico de sincronizacao", it)
            }
        }
    }
}
