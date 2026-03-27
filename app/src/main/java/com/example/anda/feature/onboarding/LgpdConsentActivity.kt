package com.example.anda.feature.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.databinding.ActivityLgpdConsentBinding

class LgpdConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLgpdConsentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasAccepted(this)) {
            routeAfterConsent()
            return
        }

        binding = ActivityLgpdConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.showFullPolicyLink.setOnClickListener {
            showFullPolicyDialog()
        }

        binding.acceptButton.setOnClickListener {
            saveConsent()
            routeAfterConsent()
        }

        binding.refuseButton.setOnClickListener {
            finishAffinity()
        }
    }

    private fun routeAfterConsent() {
        val destination = OnboardingRouteResolver.resolve(
            hasProfile = ProfileManager.hasProfile(this),
            hasPin = ProfileManager.hasPin(this)
        )

        val nextIntent = when (destination) {
            OnboardingDestination.PIN_UNLOCK -> Intent(this, PinUnlockActivity::class.java)
            OnboardingDestination.PIN_SETUP -> Intent(this, PinSetupActivity::class.java)
                .putExtra(PinSetupActivity.EXTRA_PROFILE, ProfileManager.getProfile(this)?.storageValue)
            OnboardingDestination.PROFILE_SELECTION -> Intent(this, ProfileSelectionActivity::class.java)
        }
        startActivity(nextIntent)
        finish()
    }

    private fun showFullPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lgpd_full_policy_title))
            .setMessage(buildTermsText())
            .setPositiveButton(getString(R.string.lgpd_full_policy_close), null)
            .show()
    }

    private fun saveConsent() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPTED, true)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun buildTermsText(): CharSequence {
        val sb = SpannableStringBuilder()

        fun addTitle(text: String) {
            val start = sb.length
            sb.append(text).append("\n\n")
            sb.setSpan(StyleSpan(Typeface.BOLD), start, start + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun addBullet(text: String) {
            val start = sb.length
            sb.append("   $text\n")
            sb.setSpan(BulletSpan(16), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun addParagraph(text: String) {
            sb.append(text).append("\n\n")
        }

        addTitle("ANDA — Política de Privacidade e Termos de Uso")
        addParagraph(
            "Bem-vindo ao ANDA, plataforma de Segurança e Medicina do Trabalho (SST). " +
            "Este documento descreve como tratamos seus dados conforme a " +
            "Lei Geral de Proteção de Dados (Lei 13.709/2018 — LGPD)."
        )

        addTitle("1. Dados Coletados")
        addBullet("Dados da empresa: CNPJ, razão social, CNAE, endereço.")
        addBullet("Dados do trabalhador: nome, CPF, RG, função, setor.")
        addBullet("Dados de saúde ocupacional: exames, ASO, riscos, PPP (dados sensíveis — Art. 11 LGPD).")
        addBullet("Dados de acidente do trabalho (CAT).")
        addBullet("Dados do responsável técnico: nome, CRM/CREA, CPF.")
        sb.append("\n")

        addTitle("2. Base Legal (LGPD Art. 7º e 11)")
        addBullet("Consentimento explícito do titular (Art. 7º, I).")
        addBullet("Execução de contrato (Art. 7º, V).")
        addBullet("Cumprimento de obrigação legal — NR-7, NR-1 (Art. 7º, II).")
        addBullet("Proteção da saúde do trabalhador (Art. 11, II, f).")
        sb.append("\n")

        addTitle("3. Armazenamento e Segurança")
        addBullet("Dados armazenados localmente no dispositivo, criptografados com AES-256.")
        addBullet("Assinatura digital com chave RSA-SHA256 no AndroidKeyStore (hardware-backed).")
        addBullet("Sincronização opcional com servidor backend via HTTPS.")
        addBullet("Nenhum dado é compartilhado com terceiros sem seu consentimento.")
        sb.append("\n")

        addTitle("4. Seus Direitos (LGPD Art. 18)")
        addBullet("Acesso e confirmação do tratamento dos seus dados.")
        addBullet("Correção de dados incompletos ou incorretos.")
        addBullet("Eliminação dos dados mediante solicitação.")
        addBullet("Portabilidade dos dados.")
        addBullet("Revogação do consentimento a qualquer momento.")
        sb.append("\n")

        addTitle("5. Assinatura Digital — Validade Jurídica")
        addParagraph(
            "A assinatura eletrônica gerada pelo ANDA é do tipo Assinatura Eletrônica Avançada " +
            "conforme Lei 14.063/2020, Art. 4º, III, utilizando chave RSA-2048 armazenada " +
            "no AndroidKeyStore protegida por biometria. É juridicamente válida para " +
            "documentos SST do setor privado (ASO, OS, APR, PCMSO, PGR)."
        )

        addTitle("6. Responsável pelo Tratamento")
        addParagraph(
            "Controlador: usuário/empresa responsável pela operação do app. " +
            "Para exercer seus direitos: acesse as configurações do app."
        )

        return sb
    }

    companion object {
        const val PREFS_NAME = "anda_lgpd"
        const val KEY_ACCEPTED = "lgpd_accepted"
        const val KEY_ACCEPTED_AT = "lgpd_accepted_at"

        fun hasAccepted(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACCEPTED, false)
        }
    }
}
