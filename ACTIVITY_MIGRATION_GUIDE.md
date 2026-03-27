# SmartAutofill Activity Migration Blueprint

**Pattern**: Established via PGR & PCMSO migrations (Phase 2-3)  
**Remaining Activities**: PPP, OS, APR, CAT, NR10, NR12, NR20, LTCAT, AET, INSALUBRIDADE, PERICULOSIDADE  
**Est. Time Per Activity**: 5-10 minutes  

## 4-Step Pattern

### 1. Add Import & Initialize
```kotlin
import com.example.anda.data.integration.ServiceIntegrationHelper

private lateinit var integrationHelper: ServiceIntegrationHelper
private var currentCompanyScope: String? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityXxxBinding.inflate(layoutInflater)
    integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)
    // ... rest
}
```

### 2. Update pickCompanyLauncher
Add after setting fields:
```kotlin
val cleanCnpj = cnpj.filter(Char::isDigit)
currentCompanyScope = cleanCnpj
if (cleanCnpj.isNotEmpty()) {
    integrationHelper.onFieldsChangedNormalized(
        mapOf(
            ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to cnpj,
            ServiceIntegrationHelper.FieldIds.COMPANY_NAME to name
        ),
        sourceDocument = "XXX",  // Document type
        companyScope = cleanCnpj
    )
}
```

### 3. Update applyRequestContextFromIntent
Add before preloadLegalContext:
```kotlin
currentCompanyScope = requestCompanyCnpj
if (requestCompanyName.isNotBlank()) {
    integrationHelper.onFieldsChangedNormalized(
        mapOf(
            ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to requestCompanyCnpj,
            ServiceIntegrationHelper.FieldIds.COMPANY_NAME to requestCompanyName
        ),
        sourceDocument = "XXX",
        companyScope = requestCompanyCnpj
    )
}
```

### 4. Update generate() Method
Add before document generation:
```kotlin
integrationHelper.onFieldsChangedNormalized(
    mapOf(
        ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to cnpj,
        ServiceIntegrationHelper.FieldIds.COMPANY_NAME to companyName,
        // Add relevant fields: HAZARD_TYPE, MEDICAL_EXAMINATION, etc.
    ),
    sourceDocument = "XXX",
    companyScope = currentCompanyScope ?: cnpj
)
```

## Available Field IDs
- COMPANY_CNPJ, COMPANY_NAME (all documents)
- EMPLOYEE_CPF, EMPLOYEE_NAME (PPP)
- HAZARD_TYPE, RISK_GRADE (PGR, PCMSO, APR)
- MEDICAL_EXAMINATION (PCMSO, PPP)
- REGULATION_NR (technical docs)

## Document Type Strings
ASO, PCMSO, PGR, PPP, OS, APR, CAT, NR10, NR12, NR20, LTCAT, AET

## Test After Each Migration
- `./gradlew test` (all pass?)
- Company selection publishes?
- No logcat errors?
- Company scope tracked?

**See PHASE2_3_COMPLETION_SUMMARY.md for detailed examples.**

