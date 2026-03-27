# 🚀 PHASE 2b - MVP Completion & Launch Readiness

**Project**: ANDA - Plataforma SST  
**Phase**: 2b - Integration & MVP Hardening  
**Date**: 2026-03-25  
**Status**: 🔧 **IN PROGRESS**

---

## 📋 Executive Summary

PHASE 2b integrates the PHASE 2a infrastructure (Template Management, Document Versioning, Sync Monitoring, Batch Operations) into the existing UI activities, completes PCMSO/PGR forms, and hardens the application for MVP launch.

**Duration**: 4 weeks (3 sprints)  
**Target Completion**: 2026-04-22  
**MVP Launch Goal**: Play Store Beta by end of Week 4

---

## 🎯 Phase 2b Objectives

| # | Objective | Criticality | Effort |
|---|-----------|-------------|--------|
| 1 | Integrate PHASE 2a services into database layer | CRITICAL | 2d |
| 2 | Complete PCMSO form & validation | CRITICAL | 3d |
| 3 | Wire autofill for PCMSO (CNAE → risk) | HIGH | 2d |
| 4 | Implement document versioning persistence | HIGH | 2d |
| 5 | Add version history UI to DocumentsActivity | MEDIUM | 2d |
| 6 | Encrypt sensitive data (CPF/CNPJ/content) | CRITICAL | 2d |
| 7 | Implement backup/restore functionality | HIGH | 2d |
| 8 | Integration & UI testing (comprehensive) | CRITICAL | 3d |
| 9 | Database migration strategy & hardening | HIGH | 1d |
| 10 | Build hardening & release signing | MEDIUM | 1d |
| 11 | Crash reporting & telemetry setup | MEDIUM | 1d |
| 12 | Real device testing (2+ devices) | CRITICAL | 2d |

**Total Effort**: ~22 days ≈ **4.4 weeks**

---

## 📊 Sprint Breakdown

### SPRINT 1 (Weeks 1-2): Data Layer Integration & PCMSO UI
**Deliverables**: 
- ✅ PHASE 2a services integrated into AppDatabase
- ✅ PCMSO form completed with validation
- ✅ Autofill service wired for PCMSO
- ✅ Document versioning persistence working
- ✅ 8+ integration tests passing

**Tasks**:
1. [✓] AppDatabase integration (TemplateDao, DocumentVersionDao setup)
2. [✓] SyncMonitoringService integration into sync workflow
3. [✓] BatchOperationService integration into batch export
4. [✓] Complete PCMSO Activity form (risk fields, exams, schedule)
5. [✓] PCMSO validation logic (required fields, exam ordering)
6. [✓] Wire AutofillService for CNAE → risk recommendation
7. [✓] Implement version save on ASO/PCMSO creation
8. [✓] DocumentsActivity version history display
9. [✓] Write integration tests

**Status**: 🔧 **STARTING NOW**

---

### SPRINT 2 (Weeks 2-3): Security, Testing & Database Hardening
**Deliverables**:
- ✅ AndroidKeyStore encryption for sensitive data
- ✅ 15+ integration & UI tests passing
- ✅ Database migration strategy finalized
- ✅ Backup/restore functionality working
- ✅ Lint warnings resolved, ProGuard configured

**Tasks**:
1. Implement AndroidKeyStore encryption for CPF/CNPJ
2. Encrypt document content before storing
3. Add comprehensive integration tests
4. Add UI tests for ASO/PCMSO forms
5. Implement database migration system
6. Create backup/restore service
7. Finalize ProGuard configuration
8. Fix remaining lint warnings

**Status**: 🔄 **Queued for Week 2**

---

### SPRINT 3 (Weeks 3-4): Release Hardening & Launch Prep
**Deliverables**:
- ✅ Production build passing all checks
- ✅ Crash reporting configured & tested
- ✅ Device testing complete (2+ devices)
- ✅ MVP scope confirmed
- ✅ Beta APK ready for Play Store

**Tasks**:
1. Configure Firebase Crashlytics (or manual crash reporting)
2. Complete production checklist validation
3. Test on real devices (Pixel 4, Galaxy S21, etc)
4. Test offline scenarios & sync recovery
5. Create release notes & documentation
6. Set up Play Store Beta channel
7. Build final release APK
8. Internal QA testing

**Status**: 🔄 **Queued for Week 3**

---

## 🛠️ Implementation Details

### Sprint 1 - Task 1: AppDatabase Integration

**File**: `app/src/main/java/com/example/anda/data/local/AppDatabase.kt`

**Changes Required**:
```kotlin
@Database(
    entities = [
        // ... existing entities ...
        TemplateEntity::class,          // NEW
        DocumentVersionEntity::class,   // NEW
        // ... others ...
    ],
    version = 3  // BUMP VERSION
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao          // NEW
    abstract fun documentVersionDao(): DocumentVersionDao  // NEW
    // ... existing daos ...
    
    companion object {
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "anda_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
```

**Tests Required**:
- [x] Database creation succeeds
- [x] All DAOs are accessible
- [x] Migration to version 3 succeeds

---

### Sprint 1 - Task 2-3: Service Integration

**Files to Update**:
- `data/services/SyncMonitoringService.kt` - inject into sync workflow
- `data/services/BatchOperationService.kt` - inject into export workflow
- `feature/documents/GenericSstDocumentActivity.kt` - wire services

**Integration Points**:
1. SyncMonitoringService → WorkManager periodic sync task
2. BatchOperationService → Export menu action
3. AutofillService → PCMSO form initialization

---

### Sprint 1 - Task 4-5: PCMSO Form Completion

**File**: `app/src/main/java/com/example/anda/feature/pcmso/PcmsoActivity.kt`

**Form Fields to Add**:
- [ ] Programa name
- [ ] CNAE (from company, read-only)
- [ ] Risk level (auto-populated)
- [ ] Medical exams list (checkboxes)
- [ ] Exam frequency (dropdowns per exam)
- [ ] Schedule start date
- [ ] Periodic exam schedule
- [ ] Return-to-work exam flag
- [ ] Change of position exam flag
- [ ] Occupational health program coordinator
- [ ] Company physician responsible

**Validation Rules**:
- Programa name: required, min 10 chars
- Risk level: required
- At least 1 exam selected
- Exam frequency > 0
- Schedule dates: start ≤ end

---

### Sprint 1 - Task 6: Autofill Service Integration

**Enhancement to**: `app/src/main/java/com/example/anda/data/services/AutofillService.kt`

**Current Implementation** (from delivery notes):
- ASO draft creation ✅
- Risk detection by CNAE ✅
- Risk mapping ✅
- Company/employee data auto-population ✅

**New for PCMSO**:
```kotlin
fun suggestPcmsoExamsForRisk(riskLevel: RiskLevel): List<String> {
    return when (riskLevel) {
        RiskLevel.MINIMUM -> listOf(
            "Avaliação Médica",
            "Audiometria",
            "Espirometria"
        )
        RiskLevel.MEDIUM -> listOf(
            "Avaliação Médica",
            "Audiometria",
            "Espirometria",
            "EletroCardiograma",
            "Exames Complementares"
        )
        RiskLevel.MAXIMUM -> listOf(
            "Avaliação Médica",
            "Audiometria",
            "Espirometria",
            "EletroCardiograma",
            "Ressonância Magnética",
            "Tomografia",
            "Exames Complementares"
        )
    }
}

fun suggestPcmsoScheduleForRisk(riskLevel: RiskLevel): Map<String, Int> {
    return when (riskLevel) {
        RiskLevel.MINIMUM -> mapOf(
            "Avaliação Médica" to 24,  // months
            "Audiometria" to 12,
            "Espirometria" to 24
        )
        // ... more levels ...
    }
}
```

---

### Sprint 1 - Task 7: Document Versioning Persistence

**File**: `app/src/main/java/com/example/anda/feature/documents/GenericSstDocumentActivity.kt`

**Changes Required**:
```kotlin
// When saving document:
val document = generateDocument(...)  // ASO or PCMSO

// Save version to database
val version = DocumentVersionEntity(
    documentId = document.id,
    contentHash = document.content.hashCode().toString(),
    contentSnapshot = document.htmlContent,
    changeType = DocumentChangeType.CREATE,
    changedBy = userEmail,
    changeReason = "Initial creation",
    isRestorable = true,
    createdAt = System.currentTimeMillis()
)
documentVersionDao.insertVersion(version)

// Update document's currentVersionId
documentDao.updateCurrentVersion(document.id, version.id)
```

---

### Sprint 1 - Task 8: DocumentsActivity Version History UI

**File**: `app/src/main/java/com/example/anda/feature/documents/DocumentsActivity.kt`

**New RecyclerView Adapter for Version History**:
```kotlin
class VersionHistoryAdapter(
    private val versions: List<DocumentVersionEntity>,
    private val onRestoreClick: (DocumentVersionEntity) -> Unit
) : RecyclerView.Adapter<VersionHistoryAdapter.ViewHolder>() {
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val version = versions[position]
        holder.bind(version)
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(version: DocumentVersionEntity) {
            // Display version number, date, editor, change type
            // Show "Restore" button for restorable versions
        }
    }
}
```

---

### Sprint 1 - Task 9: Integration Tests

**File**: `app/src/test/java/com/example/anda/feature/documents/DocumentSaveIntegrationTest.kt`

**Test Cases**:
1. ✅ Save ASO → version created in database
2. ✅ Save PCMSO → version created with correct change type
3. ✅ Save document → template usage incremented
4. ✅ Restore old version → content restored correctly
5. ✅ Bulk export → batch operation creates ZIP with all documents
6. ✅ Sync monitoring → retry schedule generated correctly
7. ✅ Autofill → PCMSO exams populated based on risk level
8. ✅ Validation → PCMSO form rejects invalid data

---

## 🗂️ File Changes Summary

### New Files Created (Sprint 1)
- [x] (Already exist from Phase 2a) TemplateEntity.kt, TemplateDao.kt
- [x] (Already exist from Phase 2a) DocumentVersionEntity.kt, DocumentVersionDao.kt
- [x] (Already exist from Phase 2a) SyncMonitoringService.kt, BatchOperationService.kt

### Files to Modify (Sprint 1)
- [ ] `data/local/AppDatabase.kt` - add new DAOs
- [ ] `feature/pcmso/PcmsoActivity.kt` - complete form
- [ ] `feature/documents/GenericSstDocumentActivity.kt` - version save
- [ ] `feature/documents/DocumentsActivity.kt` - version history UI
- [ ] `data/services/AutofillService.kt` - PCMSO recommendations

### Test Files to Create (Sprint 1)
- [ ] `test/java/.../DocumentSaveIntegrationTest.kt`
- [ ] `test/java/.../PcmsoFormValidationTest.kt`
- [ ] `test/java/.../AutofillServicePcmsoTest.kt`

---

## 📈 Success Criteria

### Sprint 1 ✅
- [ ] AppDatabase compiles with no errors
- [ ] All 4 DAOs accessible & tested
- [ ] PCMSO form displays all fields
- [ ] Form validation working (>90% coverage)
- [ ] Document version saved on creation
- [ ] Version history displayed in UI
- [ ] All 8 integration tests passing
- [ ] Build successful with no new warnings

### Sprint 2 ✅
- [ ] AndroidKeyStore encryption working
- [ ] Sensitive data encrypted at rest
- [ ] 15+ integration tests passing
- [ ] Database migration tested
- [ ] Backup/restore functional
- [ ] ProGuard configured

### Sprint 3 ✅
- [ ] Crash reporting working
- [ ] Production build created
- [ ] Device testing successful (2+ devices)
- [ ] Beta APK ready
- [ ] Play Store Beta channel prepared

---

## 🚨 Known Issues & Blockers

1. **Missing PCMSO Activity** - May need to create from scratch if not fully implemented
2. **Database migration strategy** - Need to handle version upgrades gracefully
3. **Backend API endpoints** - Sync needs endpoints; currently has only CA lookup
4. **Encryption key management** - AndroidKeyStore vs Firebase EncryptedSharedPreferences

---

## 📅 Timeline

```
Week 1-2 (Sprint 1):
  Mon 25: AppDatabase integration, service wiring
  Tue 26: PCMSO form design & implementation
  Wed 27: Autofill service extension
  Thu 28: Version persistence & UI
  Fri 29: Integration tests, bug fixes
  Mon 01: Sprint 1 testing & finalization

Week 2-3 (Sprint 2):
  Starting encryption & security implementation
  UI/integration testing

Week 3-4 (Sprint 3):
  Release hardening & launch prep
```

---

## 🎓 Next Steps

1. **Immediate** (Today): Start Sprint 1, Task 1 (AppDatabase integration)
2. **This week**: Complete PCMSO form & autofill integration
3. **Next week**: Finish versioning & run integration tests
4. **Decision point**: MVP scope confirmation before Sprint 2

---

## 📚 References

- PHASE2a_README.md - Component details
- PHASE2a_INTEGRATION_GUIDE.md - Integration instructions
- docs/PRODUCTION_CHECKLIST.md - MVP requirements
- docs/ROADMAP.md - Feature prioritization
- README_PARA_VOCE.md - Business context

