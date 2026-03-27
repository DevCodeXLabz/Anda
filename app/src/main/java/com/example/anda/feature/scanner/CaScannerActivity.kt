package com.example.anda.feature.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.stability.safeLaunch
import com.example.anda.data.network.OfficialCaApiClient
import com.example.anda.databinding.ActivityCaScannerBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var cameraProvider: ProcessCameraProvider? = null

    private var lastDetectedCa: String? = null
    private var lastValidatedCa: String? = null
    private var lastAutoOpenedConsultCa: String? = null
    private var currentCameraCandidateCa: String? = null
    private var currentCameraCandidateCount: Int = 0
    private val lastValidationAtByCa = ConcurrentHashMap<String, Long>()
    private var cameraStarted = false
    private var safeModeActive = false

    private companion object {
        const val CAMERA_STABLE_READS_REQUIRED = 2
        const val VALIDATION_COOLDOWN_MS = 8_000L
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.scannerStatusText.text = getString(R.string.scanner_status_camera_active)
            startCameraIfPossible()
        } else {
            binding.scannerStatusText.text = getString(R.string.scanner_status_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        safeModeActive = CrashShield.isSafeModeActive(this)

        binding.requestCameraButton.setOnClickListener {
            if (safeModeActive) {
                Toast.makeText(this, getString(R.string.scanner_safe_mode_camera_disabled), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureCameraPermissionAndStart()
        }

        binding.scanButton.setOnClickListener {
            analyzeManualText(binding.mockOcrInput.text.toString())
        }

        binding.openOfficialConsultButton.setOnClickListener {
            openOfficialConsult(lastValidatedCa ?: lastDetectedCa)
        }

        refreshBackendHealth()
        if (safeModeActive) {
            binding.scannerStatusText.text = getString(R.string.scanner_status_safe_mode)
        } else {
            ensureCameraPermissionAndStart()
        }
    }

    private fun refreshBackendHealth() {
        if (!OfficialCaApiClient.hasConfiguredBackend()) {
            binding.backendHealthText.text = getString(R.string.scanner_status_backend_not_configured)
            return
        }

        binding.backendHealthText.text = getString(R.string.scanner_backend_checking)
        safeLaunch("CaScannerActivity/refreshBackendHealth") {
            val online = withContext(Dispatchers.IO) {
                OfficialCaApiClient.pingBackend()
            }

            binding.backendHealthText.text = if (online) {
                getString(R.string.scanner_status_backend_online)
            } else {
                getString(R.string.scanner_status_backend_offline)
            }
        }
    }

    private fun ensureCameraPermissionAndStart() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            binding.scannerStatusText.text = getString(R.string.scanner_status_no_camera)
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            binding.scannerStatusText.text = getString(R.string.scanner_status_camera_active)
            startCameraIfPossible()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraIfPossible() {
        if (cameraStarted) return

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            runCatching {
                if (lifecycle.currentState == Lifecycle.State.DESTROYED || isFinishing) {
                    return@runCatching
                }

                val cameraProvider = providerFuture.get()
                this.cameraProvider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = binding.cameraPreview.surfaceProvider
                }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    val extracted = CaNumberExtractor.extract(visionText.text)
                                    if (!extracted.isNullOrBlank()) {
                                        onCameraCaDetected(extracted)
                                    }
                                }
                                .addOnFailureListener {
                                    binding.scannerStatusText.text = getString(R.string.scanner_status_ocr_failed)
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
                cameraStarted = true
            }.onFailure {
                CrashShield.recordRecoverableError("CaScannerActivity/startCameraIfPossible", it)
                binding.scannerStatusText.text = getString(R.string.scanner_status_start_failed)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeManualText(text: String) {
        val caNumber = CaNumberExtractor.extract(text)
        if (caNumber == null) {
            binding.scanResultText.text = getString(R.string.scanner_status_manual_not_found)
            return
        }

        renderValidation(caNumber, source = "manual")
    }

    private fun onCameraCaDetected(extracted: String) {
        if (currentCameraCandidateCa == extracted) {
            currentCameraCandidateCount += 1
        } else {
            currentCameraCandidateCa = extracted
            currentCameraCandidateCount = 1
        }

        if (currentCameraCandidateCount < CAMERA_STABLE_READS_REQUIRED) {
            return
        }

        val now = System.currentTimeMillis()
        val lastValidationAt = lastValidationAtByCa[extracted] ?: 0L
        if ((now - lastValidationAt) < VALIDATION_COOLDOWN_MS) {
            return
        }

        if (extracted == lastDetectedCa) {
            return
        }

        lastDetectedCa = extracted
        lastValidationAtByCa[extracted] = now
        renderValidation(extracted, source = "camera")
    }

    private fun renderValidation(caNumber: String, source: String) {
        val origin = if (source == "camera") getString(R.string.scanner_origin_camera) else getString(R.string.scanner_origin_manual)
        binding.scannerStatusText.text = getString(R.string.scanner_status_checking_ca, caNumber)

        safeLaunch("CaScannerActivity/renderValidation") {
            val validation = CaValidationService.validate(caNumber)
            lastValidatedCa = validation.caNumber

            val observation = validation.statusReason?.let {
                getString(R.string.scanner_result_observation_template, describeStatusReason(it))
            }.orEmpty()
            val status = if (validation.isValid) {
                getString(R.string.scanner_status_valid)
            } else {
                getString(R.string.scanner_status_invalid)
            }
            binding.scanResultText.text = getString(
                R.string.scanner_result_template,
                origin,
                validation.source,
                validation.caNumber,
                validation.itemName,
                validation.riskCoverage,
                validation.validUntil,
                observation,
                status
            )

            binding.scannerStatusText.text = if (validation.isValid) {
                getString(R.string.scanner_status_valid_template, validation.source)
            } else {
                val suffix = validation.statusReason?.let {
                    getString(R.string.scanner_status_reason_suffix_template, describeStatusReason(it))
                }.orEmpty()
                getString(R.string.scanner_status_invalid_template, validation.source, suffix)
            }

            if (shouldAutoOpenOfficialConsult(validation)) {
                openOfficialConsult(validation.caNumber, automatic = true)
            }
        }
    }

    private fun shouldAutoOpenOfficialConsult(validation: CaValidationResult): Boolean {
        val shouldOpen = validation.source.startsWith("fallback") &&
            validation.caNumber.isNotBlank() &&
            validation.caNumber != lastAutoOpenedConsultCa

        if (shouldOpen) {
            lastAutoOpenedConsultCa = validation.caNumber
        }

        return shouldOpen
    }

    private fun openOfficialConsult(caNumber: String?, automatic: Boolean = false) {
        val consultUrl = caNumber?.let { OfficialCaApiClient.buildOfficialConsultUrl(it) }
        if (consultUrl.isNullOrBlank()) {
            if (!automatic) {
                Toast.makeText(
                    this,
                    getString(R.string.scanner_configure_official_url),
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (automatic) {
            Toast.makeText(
                this,
                getString(R.string.scanner_opening_official_consult),
                Toast.LENGTH_LONG
            ).show()
        }

        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(consultUrl)))
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.scanner_open_official_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun describeStatusReason(statusReason: String): String {
        return when (statusReason) {
            "expired" -> getString(R.string.scanner_reason_expired)
            "not_found" -> getString(R.string.scanner_reason_not_found)
            "upstream_unavailable" -> getString(R.string.scanner_reason_upstream_unavailable)
            else -> statusReason
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        recognizer.close()
    }
}
