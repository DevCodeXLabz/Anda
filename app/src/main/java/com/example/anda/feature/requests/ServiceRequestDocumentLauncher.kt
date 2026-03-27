package com.example.anda.feature.requests

import android.content.Context
import android.content.Intent
import com.example.anda.feature.apr.AprActivity
import com.example.anda.feature.aso.AsoActivity
import com.example.anda.feature.cat.CatActivity
import com.example.anda.feature.documents.GenericSstDocumentActivity
import com.example.anda.feature.os.OsActivity
import com.example.anda.feature.pcmso.PcmsoActivity
import com.example.anda.feature.pgr.PgrActivity
import com.example.anda.feature.ppp.PppActivity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.domain.DocumentTypeNormalizer

object ServiceRequestIntentContract {
    const val EXTRA_REQUEST_CODE = "extra_request_code"
    const val EXTRA_REQUEST_COMPANY_CNPJ = "extra_request_company_cnpj"
    const val EXTRA_REQUEST_COMPANY_NAME = "extra_request_company_name"

    fun applyRequestContext(intent: Intent, request: ServiceRequestEntity): Intent {
        return intent
            .putExtra(EXTRA_REQUEST_CODE, request.requestCode)
            .putExtra(EXTRA_REQUEST_COMPANY_CNPJ, request.contractorCnpj)
            .putExtra(EXTRA_REQUEST_COMPANY_NAME, request.contractorName)
    }
}

object ServiceRequestDocumentLauncher {

    fun buildIntent(context: Context, request: ServiceRequestEntity): Intent {
        val normalizedType = DocumentTypeNormalizer.normalize(request.requestedDocumentType)
        val baseIntent = when (normalizedType) {
            "ASO" -> Intent(context, AsoActivity::class.java)
            "PCMSO" -> Intent(context, PcmsoActivity::class.java)
            "PGR" -> Intent(context, PgrActivity::class.java)
            "APR" -> Intent(context, AprActivity::class.java)
            "CAT" -> Intent(context, CatActivity::class.java)
            "PPP" -> Intent(context, PppActivity::class.java)
            "OS" -> Intent(context, OsActivity::class.java)
            else -> Intent(context, GenericSstDocumentActivity::class.java)
                .putExtra(GenericSstDocumentActivity.EXTRA_DOCUMENT_TYPE, normalizedType)
                .putExtra(GenericSstDocumentActivity.EXTRA_DOCUMENT_TITLE, buildGenericTitle(normalizedType))
                .putExtra(GenericSstDocumentActivity.EXTRA_DOCUMENT_SUBTITLE, buildGenericSubtitle(normalizedType))
        }
        return ServiceRequestIntentContract.applyRequestContext(baseIntent, request)
    }

    private fun buildGenericTitle(type: String): String = when (type) {
        "AET" -> "AET - Analise Ergonomica do Trabalho"
        "LTCAT" -> "LTCAT - Laudo Tecnico de Condicoes Ambientais"
        "INVENTARIO" -> "Inventario de Riscos"
        "MAPA_RISCO" -> "Mapa de Riscos (NR-5)"
        "LAUDO_NR10" -> "Laudo NR-10"
        "LAUDO_NR12" -> "Laudo NR-12"
        "LAUDO_NR20" -> "Laudo NR-20"
        "PCA" -> "PCA - Programa de Conservacao Auditiva"
        "PPR" -> "PPR - Programa de Protecao Respiratoria"
        "PT" -> "PT - Permissao de Trabalho"
        "INSALUBRIDADE" -> "Laudo de Insalubridade"
        "PERICULOSIDADE" -> "Laudo de Periculosidade"
        "RESGATE" -> "Plano de Resgate"
        "PLANO_ACAO" -> "Plano de Acao SST"
        "PGRTR" -> "PGRTR"
        "CERTIFICADO_EPI" -> "Certificado / Controle de EPI"
        else -> "$type - Documento SST"
    }

    private fun buildGenericSubtitle(type: String): String = when (type) {
        "AET" -> "NR-17"
        "LTCAT" -> "INSS / Previdenciario"
        "INVENTARIO" -> "NR-01 GRO/PGR"
        "MAPA_RISCO" -> "CIPA - Comissao Interna de Prevencao de Acidentes"
        "LAUDO_NR10" -> "Seguranca em instalacoes eletricas"
        "LAUDO_NR12" -> "Seguranca em maquinas e equipamentos"
        "LAUDO_NR20" -> "Inflamaveis e combustiveis"
        "PCA" -> "Saude ocupacional"
        "PPR" -> "Protecao respiratoria"
        "PT" -> "Controle operacional"
        "INSALUBRIDADE" -> "NR-15"
        "PERICULOSIDADE" -> "NR-16"
        "RESGATE" -> "Trabalho em altura e espaco confinado"
        "PLANO_ACAO" -> "Gestao corretiva e preventiva"
        "PGRTR" -> "Gerenciamento de riscos especifico"
        "CERTIFICADO_EPI" -> "Controle e rastreabilidade de EPI"
        else -> "Documento tecnico operacional"
    }
}

