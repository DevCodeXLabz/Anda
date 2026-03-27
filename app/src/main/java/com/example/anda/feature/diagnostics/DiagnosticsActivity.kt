package com.example.anda.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anda.BuildConfig
import com.example.anda.R
import androidx.core.content.FileProvider
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.stability.DiagnosticsReportMode
import com.example.anda.databinding.ActivityDiagnosticsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.refreshDiagnosticsButton.setOnClickListener { renderDiagnostics() }
        binding.copyDiagnosticsButton.setOnClickListener { copyFullDiagnostics() }
        binding.copyPrivateDiagnosticsButton.setOnClickListener { copyPrivateDiagnostics() }
        binding.exportDiagnosticsButton.setOnClickListener { exportDiagnosticsTxt(DiagnosticsReportMode.FULL) }
        binding.sharePrivateDiagnosticsButton.setOnClickListener {
            exportDiagnosticsTxt(DiagnosticsReportMode.SHAREABLE)
        }
        binding.enableSafeModeButton.setOnClickListener {
            CrashShield.enableSafeModeFor(this)
            Toast.makeText(this, "Modo seguro reativado por 30 minutos", Toast.LENGTH_SHORT).show()
            renderDiagnostics()
        }
        binding.disableSafeModeButton.setOnClickListener {
            CrashShield.disableSafeMode(this)
            Toast.makeText(this, "Modo seguro desativado", Toast.LENGTH_SHORT).show()
            renderDiagnostics()
        }
        binding.clearDiagnosticsButton.setOnClickListener {
            CrashShield.clearDiagnostics(this)
            Toast.makeText(this, "Diagnostico local limpo", Toast.LENGTH_SHORT).show()
            renderDiagnostics()
        }

        renderDiagnostics()
    }

    private fun renderDiagnostics() {
        val safeModeUntil = CrashShield.safeModeUntil(this)
        val safeMode = CrashShield.isSafeModeActive(this)
        val crashCount = CrashShield.crashCountInCurrentWindow(this)
        val lastFatal = CrashShield.peekLastFatalReport(this)
        val recoverable = CrashShield.peekRecoverableErrors(this)

        binding.safeModeStatusText.text = if (safeMode) {
            val until = safeModeUntil?.let { dateFormat.format(Date(it)) } ?: "-"
            val remainingMs = CrashShield.remainingSafeModeMs(this)
            val remainingMin = (remainingMs / 60_000L).coerceAtLeast(0L)
            "Modo seguro: ATIVO ate $until (restante: ${remainingMin} min)"
        } else {
            "Modo seguro: INATIVO"
        }

        binding.crashWindowText.text = "Crashes na janela atual: $crashCount"
        binding.lastCrashText.text = if (lastFatal.isNullOrBlank()) {
            "Ultimo crash fatal: sem registro"
        } else {
            "Ultimo crash fatal:\n${lastFatal.take(2000)}"
        }

        binding.recoverableText.text = if (recoverable.isEmpty()) {
            "Erros recuperaveis recentes: nenhum"
        } else {
            val preview = recoverable.takeLast(5).reversed().joinToString("\n\n") { entry ->
                "${dateFormat.format(Date(entry.timestamp))} | ${entry.origin}\n${entry.errorType}: ${entry.message.ifBlank { "(sem mensagem)" }}"
            }
            "Erros recuperaveis recentes:\n$preview"
        }
    }

    private fun copyFullDiagnostics() {
        val report = CrashShield.buildDiagnosticsReport(this, DiagnosticsReportMode.FULL)

        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("anda_diagnostics_full", report))
        Toast.makeText(this, getString(R.string.diagnostics_copy_full_success), Toast.LENGTH_SHORT).show()
    }

    private fun copyPrivateDiagnostics() {
        val report = CrashShield.buildDiagnosticsReport(this, DiagnosticsReportMode.SHAREABLE)

        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("anda_diagnostics_private", report))
        Toast.makeText(this, getString(R.string.diagnostics_copy_private_success), Toast.LENGTH_SHORT).show()
    }

    private fun exportDiagnosticsTxt(mode: DiagnosticsReportMode) {
        runCatching {
            val report = CrashShield.buildDiagnosticsReport(this, mode)
            val exportDir = File(filesDir, "exports").apply { mkdirs() }
            val suffix = if (mode == DiagnosticsReportMode.FULL) "full" else "shareable"
            val out = File(exportDir, "anda-diagnostics-$suffix-${System.currentTimeMillis()}.txt")
            out.writeText(report)

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "ANDA Diagnostico Tecnico")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(Intent.createChooser(intent, "Compartilhar diagnostico"))
            val doneMessage = if (mode == DiagnosticsReportMode.FULL) {
                getString(R.string.diagnostics_export_full_success)
            } else {
                getString(R.string.diagnostics_export_shareable_success)
            }
            Toast.makeText(this, doneMessage, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, getString(R.string.diagnostics_export_error), Toast.LENGTH_SHORT).show()
        }
    }
}

