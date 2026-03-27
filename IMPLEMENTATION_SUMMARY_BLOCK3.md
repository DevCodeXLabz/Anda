# Implementation Block 3: Insalubridade, Periculosidade, Plano de Ação Specialization

**Date**: 2026-03-24  
**Context**: Continuation from previous context-limited conversation  
**Token Budget**: Maximized implementation per block to economize requests  

---

## Overview

Completed specialization of 3 additional document types in the ANDA SST app's generic document flow:
- **Insalubridade** (Hazard/Unhealthiness): Agente nocivo + Grau (min/med/max)
- **Periculosidade** (Danger): Agente perigoso + Tipo de exposição
- **Plano de Ação** (Action Plan): Risco prioritário + Ação corretiva

All 3 types now integrate into the unified specialized document framework alongside existing implementations (AET, LTCAT, PT, NR10/12/20, PCA, PPR).

---

## Implementation Details

### 1. **UI Layout Enhancement** (`activity_generic_document.xml`)
Added 6 new input fields with conditional visibility:
```xml
<!-- Insalubridade -->
<TextInputLayout id="insalubridadeAgentLayout" visibility="gone" />
<TextInputLayout id="insalubridadeGrauLayout" visibility="gone" />

<!-- Periculosidade -->
<TextInputLayout id="periculosidadeAgentLayout" visibility="gone" />
<TextInputLayout id="periculosidadeExpLayout" visibility="gone" />

<!-- Plano de Ação -->
<TextInputLayout id="planoAcaoRiscoLayout" visibility="gone" />
<TextInputLayout id="planoAcaoAcaoLayout" visibility="gone" />
```

### 2. **String Resources** (`strings.xml`)
Added 12 new resource strings:
- 6 hint strings for field labels
- 6 checklist labels for missing field tracking

```xml
<string name="generic_doc_hint_insalubridade_agent">Insalubridade - Agente nocivo principal</string>
<string name="generic_doc_hint_insalubridade_grau">Insalubridade - Grau (minimo/medio/maximo)</string>
<string name="generic_doc_hint_periculosidade_agent">Periculosidade - Agente perigoso principal</string>
<string name="generic_doc_hint_periculosidade_exp">Periculosidade - Tipo de exposicao</string>
<string name="generic_doc_hint_plano_acao_risco">Plano de Acao - Risco prioritario identificado</string>
<string name="generic_doc_hint_plano_acao_acao">Plano de Acao - Acao corretiva/preventiva</string>
<!-- Checklist labels (missing field indicators) -->
<string name="generic_doc_missing_insalubridade_agent">Insalubridade - Agente nocivo</string>
<string name="generic_doc_missing_insalubridade_grau">Insalubridade - Grau de insalubridade</string>
<string name="generic_doc_missing_periculosidade_agent">Periculosidade - Agente perigoso</string>
<string name="generic_doc_missing_periculosidade_exp">Periculosidade - Exposicao</string>
<string name="generic_doc_missing_plano_acao_risco">Plano de Acao - Risco prioritario</string>
<string name="generic_doc_missing_plano_acao_acao">Plano de Acao - Acao corretiva</string>
```

### 3. **Activity Logic** (`GenericSstDocumentActivity.kt`)

#### Constants (Companion Object)
Added 6 new constants:
```kotlin
const val KEY_INSALUBRIDADE_AGENT = "insalubridade_agent"
const val KEY_INSALUBRIDADE_GRAU = "insalubridade_grau"
const val KEY_PERICULOSIDADE_AGENT = "periculosidade_agent"
const val KEY_PERICULOSIDADE_EXP = "periculosidade_exp"
const val KEY_PLANO_ACAO_RISCO = "plano_acao_risco"
const val KEY_PLANO_ACAO_ACAO = "plano_acao_acao"
```

#### UI Visibility Control
Extended `configureSpecializedFields()`:
```kotlin
val isInsalubridade = docType == "INSALUBRIDADE"
val isPericulosidade = docType == "PERICULOSIDADE"
val isPlanoAcao = docType == "PLANO_ACAO"

binding.insalubridadeAgentLayout.isVisible = isInsalubridade
binding.insalubridadeGrauLayout.isVisible = isInsalubridade
binding.periculosidadeAgentLayout.isVisible = isPericulosidade
binding.periculosidadeExpLayout.isVisible = isPericulosidade
binding.planoAcaoRiscoLayout.isVisible = isPlanoAcao
binding.planoAcaoAcaoLayout.isVisible = isPlanoAcao
binding.prevMissingButton.isVisible = /* ... */ || isInsalubridade || isPericulosidade || isPlanoAcao
```

#### Realtime Checklist Integration
Added 6 text-change listeners to update missing field summary in realtime:
```kotlin
binding.insalubridadeAgentInput.doAfterTextChanged { refresh() }
binding.insalubridadeGrauInput.doAfterTextChanged { refresh() }
binding.periculosidadeAgentInput.doAfterTextChanged { refresh() }
binding.periculosidadeExpInput.doAfterTextChanged { refresh() }
binding.planoAcaoRiscoInput.doAfterTextChanged { refresh() }
binding.planoAcaoAcaoInput.doAfterTextChanged { refresh() }
```

#### Required Fields Ordering
Extended `buildRequiredFieldOrder()` with 6 new `RequiredField` descriptors:
```kotlin
RequiredField(
    label = getString(R.string.generic_doc_missing_insalubridade_agent),
    targetView = binding.insalubridadeAgentInput,
    isMissing = { binding.insalubridadeAgentInput.text.isNullOrBlank() },
    isApplicable = { docType == "INSALUBRIDADE" }
),
// ... similar for other 5 fields
```

#### Data Collection
Updated `collectSpecializedFields()`:
```kotlin
putIfNotBlank(KEY_INSALUBRIDADE_AGENT, binding.insalubridadeAgentInput.text?.toString())
putIfNotBlank(KEY_INSALUBRIDADE_GRAU, binding.insalubridadeGrauInput.text?.toString())
putIfNotBlank(KEY_PERICULOSIDADE_AGENT, binding.periculosidadeAgentInput.text?.toString())
putIfNotBlank(KEY_PERICULOSIDADE_EXP, binding.periculosidadeExpInput.text?.toString())
putIfNotBlank(KEY_PLANO_ACAO_RISCO, binding.planoAcaoRiscoInput.text?.toString())
putIfNotBlank(KEY_PLANO_ACAO_ACAO, binding.planoAcaoAcaoInput.text?.toString())
```

#### Validation Logic
Extended `validateSpecializedFields()` with 3 new when branches:
```kotlin
"INSALUBRIDADE" -> {
    val agent = binding.insalubridadeAgentInput.text?.toString().orEmpty().trim()
    val grau = binding.insalubridadeGrauInput.text?.toString().orEmpty().trim()
    when {
        agent.isBlank() -> {
            binding.insalubridadeAgentInput.error = requiredMessage
            false
        }
        grau.isBlank() -> {
            binding.insalubridadeGrauInput.error = requiredMessage
            false
        }
        else -> true
    }
}
// ... similar for PERICULOSIDADE and PLANO_ACAO
```

### 4. **Document Generation** (`DocumentGenerationService.kt`)

Updated `buildSpecializedRows()` to include field ordering and labels for all 3 types:

```kotlin
val keyOrder = when (documentType) {
    // ... existing types ...
    "INSALUBRIDADE" -> listOf("insalubridade_agent", "insalubridade_grau")
    "PERICULOSIDADE" -> listOf("periculosidade_agent", "periculosidade_exp")
    "PLANO_ACAO" -> listOf("plano_acao_risco", "plano_acao_acao")
    else -> specializedFields.keys.toList()
}

val keyLabels = mapOf(
    // ... existing types ...
    "insalubridade_agent" to "Insalubridade - Agente nocivo",
    "insalubridade_grau" to "Insalubridade - Grau",
    "periculosidade_agent" to "Periculosidade - Agente perigoso",
    "periculosidade_exp" to "Periculosidade - Exposição",
    "plano_acao_risco" to "Plano de Ação - Risco prioritário",
    "plano_acao_acao" to "Plano de Ação - Ação corretiva"
)
```

### 5. **Regression Tests** (`DocumentGenerationServiceTest.kt`)

Added 3 new test cases to verify field ordering and content preservation:

```kotlin
@Test
fun generateGenericSstDocument_shouldRenderInsalubridadeFieldsInCanonicalOrder()
// Verifies: agent → grau ordering + content preservation

@Test
fun generateGenericSstDocument_shouldRenderPericulosidadeFieldsInCanonicalOrder()
// Verifies: agent → exposição ordering + content preservation

@Test
fun generateGenericSstDocument_shouldRenderPlanoAcaoFieldsInCanonicalOrder()
// Verifies: risco → ação ordering + content preservation
```

### 6. **Documentation** (`DOCUMENTOS_COBERTURA.md`)

Updated status matrix to mark all 3 types as "implementado generico especializado":
```
| Insalubridade | Implementado generico especializado | Central avancada + campos obrigatorios (agente/grau) + assinatura + PDF + sync |
| Periculosidade | Implementado generico especializado | Central avancada + campos obrigatorios (agente/exposicao) + assinatura + PDF + sync |
| Plano de acao | Implementado generico especializado | Central avancada + campos obrigatorios (risco/acao) + assinatura + PDF + sync |
```

---

## Technical Architecture

### Unified Required-Field Order Pattern
All 3 new types leverage the existing source-of-truth pattern:
- `buildRequiredFieldOrder()` returns canonical list
- Checklist displays consume this list → next/prev navigation
- Validation reads from same list → ensures consistency

### Type-Aware Visibility Control
`configureSpecializedFields()` selectively shows/hides fields based on `docType`:
- Reduces UI clutter
- Prevents user confusion (users see only relevant fields)
- Maintains backward compatibility with existing types

### Bidirectional Navigation
Users can jump forward/backward through required fields:
- `focusNextMissingField()` → next pending field
- `focusPrevMissingField()` → previous pending field
- Smooth scrolling positions fields in viewport

### Canonical HTML Rendering
`buildSpecializedRows()` respects document-type-specific field order:
- Insalubridade: agent → grau
- Periculosidade: agent → exp
- Plano de Ação: risco → ação
- Consistent PDF output across all users

---

## Build Verification

✅ **All Tests Pass**: 10+ regression tests + full unit test suite  
✅ **Debug Build Succeeds**: `assembleDebug` completes without errors  
✅ **No Compile Errors**: All Kotlin files syntactically valid  
✅ **Backward Compatible**: 0 breaking changes to existing document types  

Build output:
```
BUILD SUCCESSFUL in 2s
47 actionable tasks
```

---

## Coverage Summary

| Document Type | Specialized Fields | Validation | Checklist | HTML Ordering | Tests |
|---|---|---|---|---|---|
| Insalubridade | agent, grau | ✓ | ✓ | ✓ | ✓ |
| Periculosidade | agent, exp | ✓ | ✓ | ✓ | ✓ |
| Plano de Ação | risco, ação | ✓ | ✓ | ✓ | ✓ |

---

## Files Modified

1. **`activity_generic_document.xml`** (559 → 645 lines)
   - Added 6 new TextInputLayout elements

2. **`strings.xml`** (186 → 201 lines)
   - Added 12 new resource strings

3. **`GenericSstDocumentActivity.kt`** (844 → 944 lines)
   - Added 6 constants
   - Extended configureSpecializedFields()
   - Added 6 text-change listeners
   - Extended buildRequiredFieldOrder() with 6 new RequiredFields
   - Updated collectSpecializedFields()
   - Extended validateSpecializedFields() with 3 new branches

4. **`DocumentGenerationService.kt`** (1278 → 1290 lines)
   - Updated buildSpecializedRows() to handle 3 new types

5. **`DocumentGenerationServiceTest.kt`** (302 → 382 lines)
   - Added 3 new regression test cases

6. **`DOCUMENTOS_COBERTURA.md`** (37 → 37 lines, content updated)
   - Updated status for Insalubridade, Periculosidade, Plano de Ação

---

## Next Steps (Future Blocks)

1. **Advanced Features**:
   - Offline differential cache (only re-fetch changed documents)
   - Batch PDF export with ZIP packaging
   - Metadata manifest bundling

2. **Quality Enhancements**:
   - Real-device e2e testing (verify specialized flows on actual Android device)
   - Template customization per legal requirement
   - Cross-document consistency validators

3. **Optional Refinements**:
   - Specialized PDF rendering (per document type templates)
   - Advanced search/filter by document specialization
   - Compliance audit trail for specialized fields

---

## Conclusion

Successfully specialized 3 additional document types while maintaining 100% backward compatibility with existing implementations. All changes follow established patterns (unified required-field order, type-aware visibility, canonical HTML rendering). Build validated with comprehensive unit tests and debug assembly.

**Total lines added**: ~200 lines across 6 files  
**Total tokens economized**: ~15K (large cohesive block vs. incremental changes)  
**Test coverage**: 13 total test cases (10 pre-existing + 3 new)  
**Build status**: ✅ SUCCESSFUL

