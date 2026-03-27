# 🚀 ANDA - INSTRUÇÕES PARA SEMANA 2

**Arquivo:** Leia este documento para continuar de onde paramos.

---

## 📌 CONTEXTO ATUAL

### O Que Foi Feito (Semana 1)
✅ Estratégia completa de mercado  
✅ Domain model SST completo  
✅ Gerador de documentos (ASO + PCMSO)  
✅ Build compilando sem erros  
✅ 5 documentos de planejamento  

### Status Atual
🟢 **MVP CORE: 70% ARQUITETURA PRONTA**  
🔧 **PRÓXIMO: Implementar autofill + PDF + Database**  

---

## 🎯 TAREFAS SEMANA 2

### Tarefa 1: Autofill Service (Prioridade: ALTA)
**Arquivo:** `app/src/main/java/com/example/anda/data/services/AutofillService.kt`

```kotlin
// Função 1: Criar ASO com autofill automático
fun createASOFromCompany(
    company: CompanyProfile,
    employee: EmployeeProfile,
    occupationalDoctor: String,
    occupationalDoctorCRM: String,
    clinicName: String,
    clinicCNPJ: String
): OccupationalHealthCertificate

// Função 2: Mapear riscos baseado em CNAE
private fun mapRisksByCANAE(cnae: String): List<OccupationalRisk>

// Função 3: Definir cronograma de exames
private fun defineExaminationSchedule(
    risks: List<OccupationalRisk>
): Map<EmployeeExaminationType, String>

// Função 4: Sugerir exames complementares
private fun suggestComplementaryExams(
    risks: List<OccupationalRisk>
): List<String>
```

**Tempo estimado:** 4 horas  
**Teste:** 100 CNAEs mapeados com riscos corretos

---

### Tarefa 2: PDF Export Service (Prioridade: ALTA)
**Arquivo:** `app/src/main/java/com/example/anda/data/services/PdfExportService.kt`

```kotlin
// Função 1: Gerar PDF de ASO
fun generateASO_PDF(
    htmlContent: String,
    filename: String,
    outputDir: File
): File

// Função 2: Gerar PDF de PCMSO
fun generatePCMSO_PDF(
    htmlContent: String,
    filename: String,
    outputDir: File
): File

// Função 3: Salvar localmente
fun savePdfToFile(
    pdfBytes: ByteArray,
    filename: String
): String  // retorna caminho
```

**Dependência:** Adicionar iText em `build.gradle.kts`
```kotlin
implementation("com.itextpdf:itext-core:8.1.0")
```

**Tempo estimado:** 6 horas  
**Teste:** Exportar ASO + PCMSO como PDF válido

---

### Tarefa 3: Room Database (Prioridade: ALTA)
**Arquivo:** `app/src/main/java/com/example/anda/data/db/AppDatabase.kt`

#### 3.1 Database
```kotlin
@Database(
    entities = [
        CompanyEntity::class,
        EmployeeEntity::class,
        DocumentEntity::class,
        TemplateEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase()
```

#### 3.2 Entities
- **CompanyEntity:** CNPJ, nome, CNAE, riscos, vencimentos
- **EmployeeEntity:** CPF, nome, riscos expostos, exames vencidos
- **DocumentEntity:** ID, tipo, conteúdo, status, timestamp
- **TemplateEntity:** ID, tipo doc, conteúdo, CNAE aplicável
- **SyncQueueEntity:** ID, documento, status, retry count

#### 3.3 DAOs
```kotlin
@Dao interface CompanyDao { ... }
@Dao interface EmployeeDao { ... }
@Dao interface DocumentDao { ... }
@Dao interface TemplateDao { ... }
@Dao interface SyncQueueDao { ... }
```

**Tempo estimado:** 8 horas  
**Teste:** Salvar + recuperar 100 documentos

---

### Tarefa 4: UI ASO Activity (Prioridade: MEDIA)
**Arquivo:** `app/src/main/java/com/example/anda/feature/aso/AsoActivity.kt`

```kotlin
class AsoActivity : AppCompatActivity() {
    private val viewModel: AsoViewModel by viewModels()
    private lateinit var binding: ActivityAsoBinding
    
    // UI elements:
    // - Dropdown: Selecionar empresa (lookup CNPJ)
    // - Dropdown: Selecionar funcionário
    // - Dropdown: Tipo de exame (Admissional, etc)
    // - EditText: Resultado (APT/INAPTO)
    // - EditText: Restrições
    // - Button: Gerar documento
    // - Button: Exportar PDF
}

class AsoViewModel : ViewModel() {
    // Lógica para preencher ASO e integrar services
}
```

**Layout:** `activity_aso.xml`

**Tempo estimado:** 12 horas  
**Teste:** Criar 10 ASOs e exportar como PDF

---

## 🛠️ DEPENDÊNCIAS A ADICIONAR

### build.gradle.kts (app)

```kotlin
dependencies {
    // ✅ Já existe
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    
    // 🔧 ADICIONAR - PDF
    implementation("com.itextpdf:itext-core:8.1.0")
    
    // 🔧 ADICIONAR - Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // 🔧 ADICIONAR - Hilt (opcional, mas recomendado depois)
    // implementation("com.google.dagger:hilt-android:2.51")
    // ksp("com.google.dagger:hilt-compiler:2.51")
    
    // 🔧 ADICIONAR - Testes
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.2.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
```

---

## 📋 CHECKLIST SEMANA 2

### Day 1-2: Autofill
- [ ] Criar `AutofillService.kt`
- [ ] Implementar `mapRisksByCANAE()` para 20+ CNAEs
- [ ] Implementar `defineExaminationSchedule()`
- [ ] Testes: 100 CNAEs mappings OK
- [ ] Commit: "feat: autofill service core"

### Day 3-4: PDF Export
- [ ] Adicionar iText dependency
- [ ] Criar `PdfExportService.kt`
- [ ] Implementar `generateASO_PDF()`
- [ ] Implementar `generatePCMSO_PDF()`
- [ ] Testes: 10 PDFs gerados OK
- [ ] Commit: "feat: pdf export service"

### Day 5-6: Database
- [ ] Criar `AppDatabase.kt`
- [ ] Criar 5 Entities
- [ ] Criar 5 DAOs
- [ ] Migrations setup
- [ ] Testes: CRUD operações OK
- [ ] Commit: "feat: room database setup"

### Day 7: UI ASO
- [ ] Criar `AsoActivity.kt`
- [ ] Criar `AsoViewModel.kt`
- [ ] Layout `activity_aso.xml`
- [ ] Integração com services
- [ ] Testes: Criar 10 ASOs
- [ ] Commit: "feat: aso activity ui"

### End of Week
- [ ] Code review
- [ ] Merge to main
- [ ] Build final check
- [ ] Deploy beta (opcional)

---

## 🔧 COMO CONTINUAR

### Opção 1: Continuar com IA (RECOMENDADO)
```
Você diz: "COMEÇA AGORA COM SEMANA 2"
IA faz: Autofill + PDF + Database + UI
Tempo: 3-4 horas de espera
Custo: Tokens premium
```

### Opção 2: Você implementa (AUTOSSUFICIENTE)
```
Você copia as specs acima
Você code
Você testa localmente
Depois submete para review
Tempo: 35 horas desenvolvimento
Custo: 0 (seu tempo)
```

### Opção 3: Híbrido (BALANCEADO)
```
Você faz: Autofill + Database
IA faz: PDF + UI
Tempo: 20 horas você + 5 horas IA
Custo: Tokens moderados
```

---

## 📊 MÉTRICAS PARA VALIDAR SEMANA 2

| Métrica | Target | Teste |
|---------|--------|-------|
| Autofill accuracy | 100% | 20 CNAEs corretos |
| PDF valid | 100% | Adobe Reader abre |
| Database CRUD | 100% | Insert/Read/Update/Delete |
| UI responsiveness | < 500ms | 10 ops |
| Sync queue | 100% | Pending items salvos |
| App stability | 0 crashes | 1h uso |

---

## 📞 DÚVIDAS COMUNS

### P: Preciso seguir a ordem (Autofill → PDF → DB)?
**R:** Não, pode parallelizar. Autofill e PDF são independentes de DB.

### P: Devo usar Hilt dependency injection?
**R:** Roadmap Q2. MVP roda sem Hilt, adiciona depois.

### P: E o Firebase?
**R:** Room offline-first é suficiente. Backend em Node.js existente.

### P: Quanto tempo leva tudo?
**R:** 1 dev full-time = 4-5 dias. Com IA = 1-2 dias.

### P: Qual é o arquivo principal onde começo?
**R:** `AutofillService.kt` - é o menor, mais rápido validar.

---

## 🎯 PRÓXIMO PASSO

**Você quer começar agora com IA ou prefere revisar primeiro?**

Se quer começar:
1. Confirme que quer usar **USE_PREMIUM_MODELS**
2. Eu gero Autofill + PDF + Database em 2-3 horas
3. Você testa e aprova
4. Terça-feira próxima: UI ASO pronta

Se quer revisar:
1. Leia este documento
2. Vira todos os `.md` em `/docs/`
3. Quando estiver pronto: "COMEÇA AGORA"

---

## ✅ VOCÊ ESTÁ PRONTO PARA SEMANA 2

- ✅ Arquitetura definida
- ✅ Dependências listadas
- ✅ Especificações claras
- ✅ Tarefas priorizadas
- ✅ Métricas definidas

**Agora é só executar!** 🚀

---

**Documentado por:** GitHub Copilot  
**Data:** 23/03/2026  
**Para:** Próxima sprint (Semana 2)  
**Status:** PRONTO PARA IMPLEMENTAÇÃO

