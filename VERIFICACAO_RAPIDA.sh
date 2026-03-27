#!/bin/bash
# ANDA APP - VERIFICAÇÃO RÁPIDA
# Arquivo para verificar se tudo foi criado corretamente
# Use: cd Anda && bash VERIFICACAO_RAPIDA.sh

echo "╔════════════════════════════════════════════════════════╗"
echo "║     ANDA APP - VERIFICAÇÃO DE ARQUIVOS 2026-03-26      ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# Contadores
total=0
encontrados=0

# Função para verificar arquivo
verificar_arquivo() {
    if [ -f "$1" ]; then
        echo "✅ $1"
        ((encontrados++))
    else
        echo "❌ FALTA: $1"
    fi
    ((total++))
}

# Função para verificar diretório
verificar_dir() {
    if [ -d "$1" ]; then
        echo "✅ $1 (diretório)"
        ((encontrados++))
    else
        echo "❌ FALTA: $1 (diretório)"
    fi
    ((total++))
}

echo "📁 VALIDADORES:"
verificar_arquivo "app/src/main/java/com/example/anda/feature/aet/AetFormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/ltcat/LtcatFormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/nr10/Nr10FormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/nr12/Nr12FormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/nr20/Nr20FormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/pt/PtFormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/insalubridade/InsalubridadeFormValidator.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/periculosidade/PericulosidadeFormValidator.kt"

echo ""
echo "🧪 TESTES VALIDADORES:"
verificar_arquivo "app/src/test/java/com/example/anda/feature/aet/AetFormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/ltcat/LtcatFormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/nr10/Nr10FormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/nr12/Nr12FormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/nr20/Nr20FormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/pt/PtFormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/insalubridade/InsalubridadeFormValidatorTest.kt"
verificar_arquivo "app/src/test/java/com/example/anda/feature/periculosidade/PericulosidadeFormValidatorTest.kt"

echo ""
echo "📱 SMARTAUTOFILL:"
verificar_arquivo "app/src/main/java/com/example/anda/data/autofill/SmartAutofillService.kt"
verificar_arquivo "app/src/test/java/com/example/anda/data/autofill/SmartAutofillServiceTest.kt"

echo ""
echo "📊 ANALYTICS:"
verificar_arquivo "app/src/main/java/com/example/anda/data/analytics/AnalyticsDao.kt"
verificar_arquivo "app/src/main/java/com/example/anda/data/analytics/EnterpriseAnalyticsService.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/analytics/EnterpriseAnalyticsActivity.kt"
verificar_arquivo "app/src/main/java/com/example/anda/feature/analytics/EnterpriseAnalyticsViewModel.kt"
verificar_arquivo "app/src/main/res/layout/activity_enterprise_analytics.xml"
verificar_arquivo "app/src/main/res/values/arrays.xml"

echo ""
echo "🔔 NOTIFICAÇÕES & ATRIBUIÇÃO:"
verificar_arquivo "app/src/main/java/com/example/anda/data/requests/ServiceRequestNotificationService.kt"
verificar_arquivo "app/src/main/java/com/example/anda/data/requests/ServiceRequestAssignmentEngine.kt"
verificar_arquivo "app/src/main/java/com/example/anda/data/requests/ServiceRequestOrchestrator.kt"
verificar_arquivo "app/src/test/java/com/example/anda/data/requests/ServiceRequestAssignmentEngineTest.kt"

echo ""
echo "🎨 DESIGN SYSTEM:"
verificar_arquivo "app/src/main/java/com/example/anda/ui/design/DesignSystem.kt"

echo ""
echo "📚 DOCUMENTAÇÃO:"
verificar_arquivo "IMPLEMENTATION_ROADMAP_2026.md"
verificar_arquivo "PHASE3_COMPLETION_STATUS.md"
verificar_arquivo "FINAL_INTEGRATION_CHECKLIST.md"
verificar_arquivo "EXECUTIVE_SUMMARY_2026.md"
verificar_arquivo "COMECE_AQUI_RESUMO_HOJE.md"
verificar_arquivo "MANIFESTO_DE_ARQUIVOS.md"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "📊 RESULTADO FINAL:"
echo "   Arquivos encontrados: $encontrados/$total"
if [ $encontrados -eq $total ]; then
    echo "   Status: ✅ TUDO OK!"
    echo ""
    echo "🚀 Próximo passo: ./gradlew clean build"
else
    echo "   Status: ⚠️  $((total - encontrados)) arquivo(s) faltando"
fi
echo "═══════════════════════════════════════════════════════"

