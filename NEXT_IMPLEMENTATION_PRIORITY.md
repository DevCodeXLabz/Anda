# ANDA Next Implementation Priority (Strict Order, No Estimates)
Last updated: 2026-04-02
Source of truth: `PROJECT_MASTER_STATUS.md`

## Execution Rules
- No work starts out of order.
- No P1/P2 item starts before all P0 acceptance criteria are met.
- Every completed item must include evidence (test report, log, screenshot, or artifact path).
- If evidence is missing, item remains PARTIAL.

## P0 - Must Complete First

### P0-01 End-to-end request lifecycle correctness
Outcome:
- request creation -> assignment -> document draft -> signed/pdf -> completed status works with no manual patching.
Files/symbols:
- `DocumentLocalRepository.kt`
- `ServiceRequestLifecyclePolicy.kt`
- `ServiceRequestsActivity.kt`
Acceptance:
- deterministic end-to-end validation report exists and is reproducible.

### P0-02 Offline/sync reliability
Outcome:
- offline create/sign/save works and reconnect sync does not lose or duplicate data.
Files/symbols:
- `SyncScheduler.kt`
- `SyncPendingRecordsWorker.kt`
- sync queue repository/dao layer
Acceptance:
- conflict/idempotency scenarios validated with evidence.

### P0-03 PDF + digital signature integrity
Outcome:
- exported PDF contains expected content and signature metadata is verifiable.
Files/symbols:
- `PdfExportService.kt`
- `DigitalSignatureManager.kt`
Acceptance:
- validation artifacts saved and linked from docs.

### P0-04 Accessibility and neurofriendly baseline
Outcome:
- critical screens pass baseline accessibility checks (contrast, scaling, focus order, labels, screen reader flow).
Files/symbols:
- main role home layouts and document entry forms under `app/src/main/res/layout/`
- theme/colors under `app/src/main/res/values/`
Acceptance:
- accessibility checklist completed with no critical blocker.

### P0-05 CI quality gate enforcement
Outcome:
- PR merge requires passing unit tests and baseline quality checks.
Files/symbols:
- `.github/workflows/android-ci.yml`
Acceptance:
- branch protection aligned with CI checks.

## P1 - Complete After P0

### P1-01 Role isolation and security behavior
Outcome:
- verify no cross-role data leakage between technician/clinic/company paths.
Acceptance:
- test evidence for role-separation scenarios.

### P1-02 Notifications and deep links
Outcome:
- notifications open correct request context deterministically.
Acceptance:
- androidTest/manual evidence for navigation scenarios.

### P1-03 LGPD operational flows
Outcome:
- export and deletion workflows are validated and documented.
Acceptance:
- compliance checklist evidence attached.

### P1-04 UX consistency hardening
Outcome:
- buttons, forms, errors, and states follow one UI system across modules.
Acceptance:
- UI consistency checklist completed.

## P2 - Strategic Differentiators (After Stability)

### P2-01 Advanced analytics quality
Outcome:
- dashboards provide reliable operational KPIs and drill-down behavior.

### P2-02 Template and productivity features
Outcome:
- reusable templates and autofill improvements reduce repetitive typing.

### P2-03 Market-level differentiators
Outcome:
- optional integrations/features that clearly beat baseline competitors.

## Required Evidence Format (for every completed item)
- command(s) run
- environment/device
- expected result
- actual result
- artifact/report path


