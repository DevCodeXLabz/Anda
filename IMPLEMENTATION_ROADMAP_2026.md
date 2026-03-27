# 🎯 ANDA APP - IMPLEMENTATION ROADMAP 2026
**Last Updated**: 2026-03-26  
**Status**: Ready for Phase 3 (Completion & Excellence)

---

## 📊 CURRENT STATE (What's Done ✅)

### Database & Persistence (100%)
- ✅ Room Database configured with 15+ DAOs
- ✅ AppDatabase integration complete
- ✅ Document versioning + audit trail
- ✅ Offline-first sync architecture
- ✅ WorkManager background sync

### Document Generation (100%)
- ✅ DocumentGenerationService: 1,278 lines
- ✅ Support for 12+ document types (ASO, PCMSO, PT, PGR, APR, CAT, etc.)
- ✅ Template system + autofill
- ✅ PDF export via iText library
- ✅ Form validation for all document types

### Authentication & Profiles (95%)
- ✅ PIN-based unlock with biometric fallback
- ✅ Multiple user profile support
- ✅ Profile selection screen
- ✅ Company/Clinic/Technician profile types
- ⚠️ **MISSING**: Enterprise employee assignment system (partial)

### UI/UX (60%)
- ✅ Foundational ANDA theme (colors, buttons)
- ✅ 29 layout files created
- ✅ Material 3 design applied
- ⚠️ **MISSING**: Premium polish (spacing, shadows, animations)
- ⚠️ **MISSING**: Home screen refinement for all 3 profiles
- ⚠️ **MISSING**: Dashboard/Analytics screens

### Features (70%)
- ✅ CNPJ lookup (BrasilAPI)
- ✅ C.A. scanning (CameraX + ML Kit OCR)
- ✅ Employee management
- ✅ EPI inventory
- ✅ Service request system (base)
- ⚠️ **MISSING**: Advanced analytics/reporting
- ⚠️ **MISSING**: SmartFill (cross-document data sync)
- ⚠️ **MISSING**: Marketplace/Integration dashboard

---

## 🔴 CRITICAL MISSING PIECES (MUST DO)

### 1. **Document Validators** (HIGH PRIORITY)
All documents need proper validation classes similar to `PcmsoFormValidator.kt`

Currently have validators for:
- ✅ PCMSO
- ✅ PGR
- ✅ APR
- ✅ CAT
- ⚠️ **MISSING**: NR10, NR12, NR20, LTCAT, AET, INSALUBRIDADE, PERICULOSIDADE, PPP, PT

**Action**: Create validator classes for remaining 11 documents

### 2. **Smart Autofill System** (HIGH PRIORITY)
- ⚠️ Current: Manual form filling
- **Goal**: When user fills ANY field, it auto-populates related documents

**Example Flow**:
1. User fills "Company CNPJ" in ASO → Auto-fills all other documents
2. User adds "Employee CPF" → Auto-fills all medical/employment docs
3. System suggests which documents need updates based on changes

**Action**: Create `SmartAutofillService` that indexes all document fields

### 3. **Enterprise Dashboard** (HIGH PRIORITY)
For Clinic/Company home screen:
- Employee roster with assignment tracking
- Document production metrics (# docs created/month)
- Revenue tracking (if billing enabled)
- Team productivity dashboard
- Service request fulfillment timeline

**Action**: Create `EnterpriseAnalyticsActivity` + DAO queries

### 4. **Service Request Assignment** (MEDIUM PRIORITY)
Current: `ServiceRequestsActivity` exists but incomplete

**Missing**:
- Auto-assignment of requests to technicians
- Notification system (local + push)
- Request status tracking (OPEN → ASSIGNED → IN_PROGRESS → COMPLETED)
- Document generation linked to requests
- Technician receipt notification

**Action**: Complete `ServiceRequestsActivity` + add work notifications

### 5. **UI/UX Polish** (MEDIUM PRIORITY)
Current state: Functional but basic

**Needed Improvements**:
- Premium button styling (Pill format with gradients)
- Card elevation + shadows for depth
- Smooth transitions & animations
- Status indicators (badges for document status)
- Loading states (spinners, skeletons)
- Error states (proper error messages)

**Action**: Create `DesignSystem.kt` composable helpers + apply theme

### 6. **Heuristic ML (Local AI)** (LOW PRIORITY - POST-MVP)
Optional local ML using TFLite:
- Risk assessment suggestion based on company info
- Auto-categorization of incidents
- Anomaly detection (unusual activity patterns)
- Recommendations for next documents to create

**Action**: Evaluate TFLite models + integration

---

## 📋 DOCUMENTS REQUIRING COMPLETION

### Medicina do Trabalho (Medical)
| Document | Form | Validator | Generator | Status |
|----------|------|-----------|-----------|--------|
| ASO | ✅ | ✅ | ✅ | DONE |
| PCMSO | ✅ | ✅ | ✅ | DONE |
| PPP | ⚠️ | ❌ | ✅ | **Missing Validator** |

### Análises & Laudos (Analysis & Reports)
| Document | Form | Validator | Generator | Status |
|----------|------|-----------|-----------|--------|
| PGR | ✅ | ✅ | ✅ | DONE |
| APR | ✅ | ✅ | ✅ | DONE |
| AET | ⚠️ | ❌ | ✅ | **Missing Validator** |
| LTCAT | ⚠️ | ❌ | ✅ | **Missing Validator** |
| NR10 | ⚠️ | ❌ | ✅ | **Missing Validator** |
| NR12 | ⚠️ | ❌ | ✅ | **Missing Validator** |
| NR20 | ⚠️ | ❌ | ✅ | **Missing Validator** |
| PT | ⚠️ | ❌ | ✅ | **Missing Validator** |

### Administrative
| Document | Form | Validator | Generator | Status |
|----------|------|-----------|-----------|--------|
| CAT | ✅ | ✅ | ✅ | DONE |
| O.S. | ✅ | ✅ | ✅ | DONE |

### Specialized Hazards
| Document | Form | Validator | Generator | Status |
|----------|------|-----------|-----------|--------|
| INSALUBRIDADE | ⚠️ | ❌ | ✅ | **Missing Validator** |
| PERICULOSIDADE | ⚠️ | ❌ | ✅ | **Missing Validator** |

---

## 🎯 IMPLEMENTATION PLAN (PHASES)

### PHASE 3A: Validators (2-3 hours)
**Goal**: All documents have proper form validation

1. ✅ Analyze existing validators (PCMSO, PGR, APR, CAT)
2. ⚠️ Create 8 new validators for missing documents
3. ⚠️ Add unit tests for each validator
4. ⚠️ Integrate validators into respective Activities

**Deliverable**: 8 validator files + 8 test classes

### PHASE 3B: SmartAutofill System (4-5 hours)
**Goal**: Cross-document data synchronization

1. Create `SmartAutofillService` 
2. Index all document fields by type (Company, Employee, Medical, etc.)
3. Implement change listeners on forms
4. Auto-populate linked fields across documents
5. Show "N documents will update" confirmation

**Deliverable**: SmartAutofillService + integration tests

### PHASE 3C: Enterprise Dashboard (5-6 hours)
**Goal**: Analytics for clinics/companies

1. Create `EnterpriseAnalyticsActivity`
2. Add analytics DAOs (ProductionMetrics, RevenueMetrics, etc.)
3. Build dashboard layouts (charts, tables)
4. Integrate WorkManager for daily metric calculations
5. Add date-range filtering

**Deliverable**: Full analytics screen + reports

### PHASE 3D: Service Requests (3-4 hours)
**Goal**: Complete assignment workflow

1. Finish `ServiceRequestsActivity` 
2. Create assignment algorithm (match request type to technician skills)
3. Add notification system (local notifications)
4. Create request status workflow
5. Link document generation to requests

**Deliverable**: Complete request lifecycle

### PHASE 3E: UI/UX Polish (3-4 hours)
**Goal**: Premium visual appearance

1. Create `DesignSystem.kt` with reusable components
2. Add animations (fade in, slide, scale)
3. Improve all home screens with status cards
4. Add loading + error states everywhere
5. Polish colors + button styling

**Deliverable**: Refined UI across entire app

### PHASE 3F: Local ML/Heuristics (2-3 hours, OPTIONAL)
**Goal**: Intelligence without server

1. Create risk assessment model
2. Integrate TFLite or manual heuristics
3. Add suggestions to document forms
4. Track anomalies in user behavior

**Deliverable**: ML-enhanced forms + suggestions

---

## 🚀 IMPLEMENTATION STRATEGY

### Token Economy (Maximize efficiency)
- **Batch edits**: Multiple file changes per tool call
- **Code reuse**: Validators follow same pattern
- **Tests together**: Test files created same time as implementation
- **Documentation inline**: Comments explain logic in code

### Testing Strategy
- ✅ Unit tests for all validators
- ✅ Integration tests for SmartAutofill
- ✅ Manual testing on both devices (Samsung tablet + Xiaomi)
- ❌ No Espresso UI tests (manual better for this case)

### Quality Gates
- **Code**: All functions must have KDoc comments
- **Tests**: Minimum 70% coverage for new code
- **Build**: No warnings, lint score = A
- **Performance**: App starts in <3 seconds, documents generate in <2 seconds

---

## 📱 ARCHITECTURE DECISIONS

### Database Approach
- ✅ Room with proper migrations
- ✅ Offline-first (sync queue pattern)
- ✅ Type-safe DAOs (no string queries)

### Service Layer
- Current: Repository pattern with MutableStateFlow
- **Upgrade needed**: Extract services (Analytics, Autofill, Notifications)
- Keep ViewModels thin

### Navigation
- Current: Intent-based (old Android style)
- Consider: Keep as-is for stability (don't refactor mid-project)

### Testing
- Unit: JUnit 4 + mockito
- Integration: Room + in-memory database
- Avoid: Espresso (manual testing sufficient)

---

## 🎨 UX/UI GUIDELINES

### Color System (Already defined)
- **Primary**: Anda Navy #003366 (trust, stability)
- **Secondary**: Anda Green #4CAF50 (action, success)
- **Accent**: Anda Gold #FACC15 (premium, alerts)
- **Background**: Light Gray #F5F5F5 (minimal white-burn)

### Component Library (To create)
```
DesignSystem.kt:
├── Button styles (Primary, Secondary, Tertiary)
├── Card with elevation
├── Section headers
├── Badge/Chips
├── Loading indicators
├── Error states
└── Bottom sheets
```

### Typography
- **Titles**: ExtraBold, 20sp+
- **Labels**: SemiBold, 14sp
- **Body**: Regular, 14sp
- **Small**: Regular, 12sp

---

## 💾 DATA MIGRATION (If needed)
Current: In-memory + local file cache
Future: Full Room persistence (already implemented)

**No migration needed**: Room is working alongside existing system.

---

## 🔒 SECURITY CHECKLIST
- ✅ PIN + Biometric unlocking
- ✅ All code runs local (no sensitive data to server)
- ✅ Offline mode (no network = no risk)
- ⚠️ **TODO**: LGPD consent (discrete placement needed)
- ⚠️ **TODO**: Data export/deletion per user

---

## 📊 SUCCESS METRICS
- Build time: < 1 minute
- App startup: < 3 seconds
- Document generation: < 2 seconds
- Document display: < 500ms
- Test coverage: > 70%
- Zero crashes (Crashlytics)

---

## 🎯 NEXT IMMEDIATE STEPS

### TODAY (Phase 3A):
1. ✅ Review this roadmap
2. ⏳ Create 8 validator classes for missing documents
3. ⏳ Create corresponding test classes
4. ⏳ Integrate validators into Activities

### THIS WEEK (Phase 3B-C):
5. ⏳ Build SmartAutofillService
6. ⏳ Create EnterpriseAnalyticsActivity
7. ⏳ Polish UI/UX

### NEXT WEEK (Phase 3D-E):
8. ⏳ Complete Service Request workflow
9. ⏳ Add local ML (optional)
10. ⏳ Final testing + deployment

---

## 📞 CLARIFICATIONS NEEDED
- [ ] Should we implement email/WhatsApp notifications for requests?
- [ ] Do we need PDF export to cloud storage (Google Drive, OneDrive)?
- [ ] Should technician be able to work offline + sync later?
- [ ] Do we need multi-language support (EN, ES)?

---

**Generated for**: ANDA SST Platform  
**Target**: Brazil's #1 App for Occupational Safety & Health  
**Timeline**: MVP complete by March 31, 2026  
**Status**: 🟢 Ready to Code

