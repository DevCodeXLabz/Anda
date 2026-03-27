package com.example.anda.feature.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature

/**
 * Assinatura Eletrônica com validade jurídica conforme Lei 14.063/2020.
 * Nível: Assinatura Eletrônica Avançada — chave RSA 2048 no AndroidKeyStore,
 * protegida por autenticação biométrica forte ou credencial do dispositivo.
 * Referências: Lei 14.063/2020 Art. 4º, III; LGPD Art. 46.
 */
data class SignatureAuditRecord(
    val contentHash: String,
    val signedAt: Long,
    val signedBy: String,
    val deviceAuthType: String = "ANDROIDKEYSTORE_RSA_SHA256_BIOMETRIC",
    val signatureB64: String = "",
    val publicKeyFingerprint: String = "",
    val legalFramework: String = "Lei 14.063/2020 — Assinatura Eletrônica Avançada"
)

object DocumentSignatureHelper {

    private const val KEY_ALIAS = "anda_document_signer_v2"

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Obtém ou cria o par de chaves RSA no AndroidKeyStore.
     * A chave privada NÃO pode ser exportada e só é acessível após autenticação biométrica.
     */
    private fun getOrCreateSigningKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    /**
     * Prepara o objeto Signature para ser autenticado via BiometricPrompt.
     * Deve ser chamado na thread principal antes de authenticate().
     */
    fun prepareSignatureForBiometric(): Signature? {
        return runCatching {
            getOrCreateSigningKey()
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            Signature.getInstance("SHA256withRSA").apply { initSign(privateKey) }
        }.getOrNull()
    }

    fun getPublicKeyFingerprint(): String {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return@runCatching "N/A"
            sha256(Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
        }.getOrElse { "N/A" }
    }

    /**
     * Assina o documento com chave RSA do AndroidKeyStore protegida por biometria.
     * Gera Assinatura Eletrônica Avançada conforme Lei 14.063/2020.
     */
    fun signWithBiometric(
        activity: FragmentActivity,
        payload: String,
        signedBy: String,
        onSuccess: (SignatureAuditRecord) -> Unit,
        onError: (String) -> Unit
    ) {
        val signature = prepareSignatureForBiometric()
        if (signature == null) {
            onError("Chave de assinatura não disponível. Tente novamente.")
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Assinar documento com validade jurídica")
            .setSubtitle("Lei 14.063/2020 — Assinatura Eletrônica Avançada")
            .setDescription("Autentique para assinar $signedBy com chave criptográfica segura do dispositivo.")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val executor = ContextCompat.getMainExecutor(activity)
        val cryptoObject = BiometricPrompt.CryptoObject(signature)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                runCatching {
                    val sig = result.cryptoObject?.signature
                        ?: throw IllegalStateException("CryptoObject ausente após autenticação")
                    sig.update(payload.toByteArray(Charsets.UTF_8))
                    val sigBytes = sig.sign()
                    val sigB64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)
                    val record = SignatureAuditRecord(
                        contentHash = sha256(payload),
                        signedAt = System.currentTimeMillis(),
                        signedBy = signedBy,
                        deviceAuthType = "ANDROIDKEYSTORE_RSA_SHA256_BIOMETRIC",
                        signatureB64 = sigB64,
                        publicKeyFingerprint = getPublicKeyFingerprint(),
                        legalFramework = "Lei 14.063/2020 — Assinatura Eletrônica Avançada"
                    )
                    onSuccess(record)
                }.onFailure { e ->
                    onError("Falha ao assinar: ${e.message}")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onError("Autenticação biométrica não reconhecida")
            }
        }

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo, cryptoObject)
    }

    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
