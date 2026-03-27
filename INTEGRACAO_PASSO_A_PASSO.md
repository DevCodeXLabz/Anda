# 🔧 GUIA DE INTEGRAÇÃO - PASSO A PASSO

**Status**: 90% do código pronto, agora conectando tudo  
**Tempo estimado**: 2-3 horas  
**Dificuldade**: Fácil (copy-paste principalmente)

---

## 📋 O QUE VOCÊ VAI FAZER

Vou te guiar por **4 passos simples** para conectar os serviços criados:

### Passo 1: Adicionar SmartAutofill a AsoActivity
### Passo 2: Adicionar Analytics a DocumentGenerationService  
### Passo 3: Adicionar notificações a CompanyHomeActivity
### Passo 4: Testes em dispositivo real

---

## PASSO 1: SmartAutofill em AsoActivity (15 mins)

### 1.1 Abra o arquivo:
```
app/src/main/java/com/example/anda/feature/aso/AsoActivity.kt
```

### 1.2 Após os imports, adicione (procure a linha com "import com.example.anda..."):

```kotlin
import com.example.anda.data.integration.ServiceIntegrationHelper
import com.example.anda.data.integration.setupSmartAutofill
```

### 1.3 Na classe AsoActivity, após "private lateinit var binding", adicione:

```kotlin
private lateinit var integrationHelper: ServiceIntegrationHelper
```

### 1.4 No método `onCreate`, após `binding = ActivityAsoBinding.inflate(...)`, adicione:

```kotlin
// Initialize SmartAutofill helper
integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)

// Prefill form with previously entered data
integrationHelper.prefillForm("ASO", mapOf(
    "company_cnpj" to binding.asoC npjInput,
    "company_name" to binding.asoCompanyNameInput,
    "employee_cpf" to binding.asoEmployeeCpfInput,
    "employee_name" to binding.asoEmployeeNameInput
))
```

### 1.5 Encontre onde os EditTexts têm listeners (procure por "doAfterTextChanged"):

Substitua linhas como:
```kotlin
// OLD:
binding.asoC npjInput.doAfterTextChanged { ... }
```

Por:
```kotlin
// NEW: Add SmartAutofill
binding.asoC npjInput.doAfterTextChanged { text ->
    integrationHelper.onFieldChanged("company_cnpj", text.toString(), "ASO")
    // ... existing logic ...
}

binding.asoC npjInput.setupSmartAutofill(integrationHelper, "company_cnpj", "ASO")
```

---

## PASSO 2: Analytics em DocumentGenerationService (20 mins)

### 2.1 Abra:
```
app/src/main/java/com/example/anda/data/services/DocumentGenerationService.kt
```

### 2.2 Adicione import:
```kotlin
import com.example.anda.data.analytics.EnterpriseAnalyticsService
```

### 2.3 Na classe, adicione campo:
```kotlin
private val analyticsService: EnterpriseAnalyticsService? = null
```

### 2.4 Encontre o método `generate*Document()` (ex: `generateAsoDocument()`):

Após a linha onde o documento é criado com sucesso, adicione:

```kotlin
// Log to analytics
analyticsService?.let { service ->
    lifecycleScope.launch {
        service.recordDocumentCreation(
            technicianCpf = technicianId,
            companyId = companyId,
            documentType = "ASO",
            timeSpentMinutes = calculateTimeSpent()
        )
    }
}
```

---

## PASSO 3: Notificações em CompanyHomeActivity (15 mins)

### 3.1 Abra:
```
app/src/main/java/com/example/anda/feature/home/CompanyHomeActivity.kt
```

### 3.2 Adicione imports:
```kotlin
import com.example.anda.data.requests.ServiceRequestOrchestrator
import com.example.anda.data.local.AppDatabase
```

### 3.3 Na classe, adicione:
```kotlin
private lateinit var orchestrator: ServiceRequestOrchestrator

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize orchestrator
    val db = AppDatabase.getInstance(this)
    orchestrator = ServiceRequestOrchestrator(this, db)
    
    // ... rest of onCreate ...
}
```

### 3.4 Encontre o botão "Novo Serviço" ou "Nova Solicitação":

Adicione ao click listener:
```kotlin
binding.newRequestButton.setOnClickListener {
    // Trigger assignment workflow
    lifecycleScope.launch {
        val requestId = orchestrator.processNewRequest(
            companyId = getCurrentCompanyId(),
            companyName = getCurrentCompanyName(),
            requestType = "ASO",  // Get from user input
            description = "Request for medical exam documents",
            availableTechs = getAvailableTechnicians()  // From DB query
        )
        Toast.makeText(this@CompanyHomeActivity, "Solicitação enviada!", Toast.LENGTH_SHORT).show()
    }
}
```

---

## PASSO 4: Testes em Dispositivo Real (1-2 hours)

### 4.1 Fazer build:
```bash
./gradlew clean build
```

**Esperado**: `BUILD SUCCESSFUL`

Se tiver erro, procure por:
- `error: unresolved reference` → Faltou import
- `Cannot resolve symbol` → Verificar digitação do nome

### 4.2 Testar no Samsung Tablet:

1. Abrir app → Técnico SST
2. Criar ASO com CNPJ: `12.345.678/0001-90`
3. **Verificar**: CNPJ foi salvo?
4. Voltar → Criar PCMSO
5. **VERIFICAR IMPORTANTE**: CNPJ aparece pré-preenchido? ✅ = SmartAutofill funcionando!
6. Preencher resto do PCMSO
7. Salvar
8. Ir para Analytics dashboard → Verificar se docs aparecem

### 4.3 Testar no Xiaomi:

Mesmas etapas acima. Focar em:
- [ ] Nenhum crash ao abrir app
- [ ] SmartAutofill funciona
- [ ] Analytics mostra dados
- [ ] Notificações funcionam (se empresa)

---

## 🆘 SE ALGO DER ERRADO

### Erro: "Cannot resolve symbol 'ServiceIntegrationHelper'"
**Solução**: Verifique se o arquivo foi criado:
```
app/src/main/java/com/example/anda/data/integration/ServiceIntegrationHelper.kt
```

Se não existir, crie manualmente a pasta `integration` e o arquivo.

### Erro: Build falha com "Method not found"
**Solução**: Limpar build:
```bash
./gradlew clean build --refresh-dependencies
```

### App abre e fecha
**Solução**: Verificar Crashlytics/logs para mensagem de erro real. Pode ser falta de import ou digitação errada.

---

## ✅ CHECKLIST DE CONCLUSÃO

- [ ] SmartAutofill adicionado a AsoActivity
- [ ] Campos de forma são pré-preenchidos
- [ ] Analytics registra docs criados
- [ ] NotificationService initializado
- [ ] Build bem-sucedido (sem warnings)
- [ ] Testado no Samsung Tablet
- [ ] Testado no Xiaomi
- [ ] Nenhum crash
- [ ] SmartAutofill funciona (CNPJ aparece em novo doc)
- [ ] Analytics mostra dados

---

## 📞 PRÓXIMO PASSO?

Depois de completar integração:
1. Fazer testes finais em ambos dispositivos
2. Melhorar home screens (visual Polish - OPCIONAL)
3. **DEPLOY**: `./gradlew assemble release`

---

**Tempo Total**: 2-3 horas  
**Dificuldade**: Média (copy-paste + ajustes pequenos)  
**Resultado**: APP 100% FUNCIONAL!


