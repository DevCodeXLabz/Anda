# ANDA APP - Next Implementation Priority Matrix
**Date**: March 26, 2026  
**Current Status**: Ready for Feature Validation & UX Polish

---

## 🎯 Implementation Priority Framework

### Tier 1: Critical Path (Must Complete Before MVP Release)

#### 1.1 End-to-End Workflow Validation
**Status**: 🔴 NOT TESTED  
**Complexity**: HIGH  
**Time Estimate**: 2-3 hours  
**Description**: Test complete service request lifecycle from creation to completion

**Steps**:
1. Create a test service request (e.g., ASO document request)
2. Assign to a technician via auto-assignment engine
3. Technician creates and signs ASO document
4. Verify request auto-transitions to COMPLETED
5. Test on all 3 devices

**Expected Outcome**: 
- ✅ Request auto-completion on document signature
- ✅ No manual status updates needed
- ✅ Audit trail records all transitions

**Files Involved**:
- `ServiceRequestsActivity.kt` (UI)
- `ServiceRequestLifecyclePolicy.kt` (Logic)
- `DocumentLocalRepository.kt` (Event hooks)
- `ServiceRequestAssignmentEngine.kt` (Auto-assignment)

---

#### 1.2 PDF Export & Digital Signature
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: HIGH  
**Time Estimate**: 4-6 hours  
**Description**: Generate PDF documents with valid digital signatures

**Blockers**:
- PDF generation library selection (iText, PDFBox, or built-in?)
- Digital signature implementation (RSA integration)
- Audit trail logging for signed documents

**Expected Files**:
- `PdfExportService.kt` (NEW)
- `DigitalSignatureManager.kt` (NEW)
- `DocumentSigningRepository.kt` (UPDATE)

**Acceptance Criteria**:
- ✅ Generated PDFs contain all document data
- ✅ Digital signature embedded in PDF
- ✅ Signature valid per LGPD standards
- ✅ Audit trail logged with timestamp

---

#### 1.3 Multi-Role Home Screens
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 2-3 hours  
**Description**: Validate and polish the home screens for all 3 user roles

**Roles**:
1. **Technician Home**: Shows assigned requests, upcoming tasks, statistics
2. **Clinic Manager Home**: Shows company analytics, pending requests, staff dashboard
3. **Contracting Company Home**: Shows service status, cost tracking, compliance calendar

**Testing Checklist**:
- [ ] Technician can see their assigned requests
- [ ] Clinic manager can see all requests across technicians
- [ ] Company can see requests they've created
- [ ] All charts/graphs render correctly
- [ ] No data leakage between roles (security)
- [ ] Layout works on all screen sizes (phones + tablet)

**Files Involved**:
- `feature/home/TechnicianHomeActivity.kt`
- `feature/home/ClinicManagerHomeActivity.kt`
- `feature/home/CompanyHomeActivity.kt`
- `analytics/AnalyticsDashboardActivity.kt`

---

#### 1.4 Deep Linking & Notifications
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 1-2 hours  
**Description**: Verify that notifications correctly deep-link to requests

**Scenarios**:
1. Technician receives "New request assigned" notification
2. Tapping notification opens request details (focused view)
3. Request details show full request + recommended documents
4. Technician can start document creation from request view

**Test Steps**:
1. Create service request from clinic
2. Verify notification sent to technician
3. Tap notification and verify navigation
4. Check that request is highlighted/focused in list

**Files Involved**:
- `ServiceRequestNotificationService.kt`
- `ServiceRequestsActivity.kt` (deep-link handling)
- `ServiceRequestDetailActivity.kt` (NEW if needed)

---

### Tier 2: Quality Assurance (Before Beta Release)

#### 2.1 Document Pre-fill from History
**Status**: 🔴 NOT IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 3-4 hours  
**Description**: Auto-populate documents with data from previous documents

**Scenario**:
- User filled company info (CNPJ, legal name, city) in ASO document
- User creates PPP document
- System pre-fills company info automatically

**Implementation Approach**:
- Extend `SmartAutofillService` (already exists!)
- Add "previous value" cache per company
- Show suggestions when opening new document

**Expected Improvement**: Reduces data entry time by ~60%

---

#### 2.2 Technician Assignment Heuristic Validation
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 2-3 hours  
**Description**: Test auto-assignment with various technician profiles

**Test Cases**:
1. Multiple technicians with same certification → Assign to least busy
2. No technician with matching cert → Assign to most experienced
3. Mix of busy and idle technicians → Assign to idle
4. No available technicians → Create unassigned request

**Success Metrics**:
- ✅ Assignments are fair and balanced
- ✅ Certifications are correctly inferred
- ✅ Workload is distributed evenly

**Files Involved**:
- `ServiceRequestAssignmentEngine.kt`
- `ServiceRequestsActivity.kt` (assignment display)

---

#### 2.3 Encryption & LGPD Compliance Audit
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 3-4 hours  
**Description**: Verify all sensitive data is encrypted

**Checklist**:
- [x] Company CNPJ encrypted
- [x] Employee CPF encrypted
- [x] Salary data encrypted
- [x] Audit trail data encrypted
- [ ] Test decryption on demand
- [ ] Test GDPR data export functionality
- [ ] Test right-to-deletion functionality

**Files Involved**:
- `AesGcmFieldEncryptor.kt`
- `LocalDataProtection.kt`
- `GdprDataExportService.kt` (NEW)
- `GdprDataDeletionService.kt` (NEW)

---

#### 2.4 Offline-First Functionality
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: HIGH  
**Time Estimate**: 4-6 hours  
**Description**: Verify app works completely offline, syncs on reconnect

**Test Scenarios**:
1. Create document offline → Sign offline → Save offline
2. Go online → Verify auto-sync to cloud (if implemented)
3. Create duplicate offline → Merge conflicts on sync
4. Offline for extended period → No data loss

**Files Involved**:
- `SyncScheduler.kt`
- `SyncPendingRecordsWorker.kt`
- `WorkManager` integration

---

### Tier 3: Advanced Features (Post-MVP)

#### 3.1 Marketplace Integration (Phase 2)
**Description**: Browse and discover service providers  
**Status**: 🔴 NOT IMPLEMENTED  
**Complexity**: VERY HIGH  
**Time Estimate**: 6-8 hours  
**Dependencies**: Backend API

#### 3.2 Advanced Analytics & Reporting
**Description**: Custom report generation, export to Excel/PDF  
**Status**: 🟡 PARTIALLY IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 3-4 hours

#### 3.3 Template Management
**Description**: Create and save document templates per company  
**Status**: 🔴 NOT IMPLEMENTED  
**Complexity**: MEDIUM  
**Time Estimate**: 3-4 hours

#### 3.4 Real-time Collaboration
**Description**: Multiple technicians can edit same document  
**Status**: 🔴 NOT IMPLEMENTED  
**Complexity**: VERY HIGH  
**Time Estimate**: 8-10 hours

---

## 📊 Recommended Implementation Sequence

### Phase 4A: Core Workflow (This Sprint) - ~8-10 hours
1. ✅ CNPJ lookup testing (COMPLETED)
2. 🔄 End-to-end workflow validation (START HERE)
3. 🔄 PDF export & digital signature (PARALLEL)
4. 🔄 Multi-role home screen polish (PARALLEL)

### Phase 4B: Quality & Compliance (Next Sprint) - ~10-15 hours
1. 🔄 Deep linking & notifications
2. 🔄 Technician assignment validation
3. 🔄 Document pre-fill from history
4. 🔄 LGPD encryption audit
5. 🔄 Offline-first testing

### Phase 5: Advanced Features (After MVP) - TBD
1. Marketplace integration
2. Advanced analytics
3. Template management
4. Real-time collaboration

---

## 🚀 Recommended Next Action

**IMMEDIATE NEXT STEP**: **End-to-End Workflow Test**

This is the highest-priority validation because it tests the core value proposition:
- Service request creation
- Technician auto-assignment
- Document generation
- Automatic request completion

**Expected Duration**: 2-3 hours  
**Devices to Test**: All 3 (Xiaomi + Samsung + Emulator)  
**Success Criteria**: Service request auto-completes after document signature

---

## 📋 Quick Status Summary

| Feature | Status | Priority | Est. Time |
|---------|--------|----------|-----------|
| CNPJ Lookup | ✅ Done | P0 | Completed |
| End-to-End Workflow | 🔴 TODO | P0 | 2-3h |
| PDF Export | 🟡 Partial | P0 | 4-6h |
| Home Screens | 🟡 Partial | P0 | 2-3h |
| Deep Linking | 🟡 Partial | P0 | 1-2h |
| Pre-fill History | 🔴 TODO | P1 | 3-4h |
| Assignment Testing | 🔴 TODO | P1 | 2-3h |
| LGPD Audit | 🟡 Partial | P1 | 3-4h |
| Offline-First | 🟡 Partial | P1 | 4-6h |
| Marketplace | 🔴 TODO | P2 | 6-8h |

---

## ✅ Session Completion Checklist

- [x] CNPJ lookup tested and validated
- [x] Unit tests created and passing
- [x] Python validation script created
- [x] APK built and deployed to 3 devices
- [x] App launches without crashes
- [x] Testing guide created
- [x] Next steps prioritized

**Current Session**: ✅ **COMPLETE**  
**Ready for Next Session**: 🚀 **YES**

---

## 📞 How to Use This Document

1. **To start next task**: Go to "Recommended Next Action" section
2. **To understand priorities**: Check "Implementation Priority Matrix"
3. **To see full feature list**: Scroll to "Quick Status Summary"
4. **To plan a sprint**: Use "Recommended Implementation Sequence"

---

**Last Updated**: March 26, 2026, 06:50 UTC  
**Next Review**: Before starting "End-to-End Workflow Test"

