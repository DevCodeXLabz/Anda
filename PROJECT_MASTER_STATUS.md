# ANDA Project Master Status (Single Source of Truth)
Last updated: 2026-04-02
Owner mode: Product + Engineering execution
Status policy: This file is the official project truth. If any other status file conflicts, this file wins.

## 1) Current Snapshot
- Project state: Stable base, not final-pro yet.
- Build/test reality known from recent sessions:
  - Unit tests: passing in local runs.
  - Connected android tests: passing in local runs on connected device.
- Architecture strengths: offline-first persistence, request lifecycle, document pipeline, encryption/signature foundation.
- Main gap now: production-grade hardening (testing depth, accessibility, CI quality gates, release readiness).

## 2) What Is Already Done (Confirmed)
- Core app flows exist: onboarding, role navigation, request/document modules.
- Document-request link fallback logic improved (explicit request code + CNPJ formatted/normalized fallback).
- Utilities and tests added for CNPJ/document lookup behavior.
- Local helper scripts added for clean build and test keystore setup.
- Basic CI workflow file added at `.github/workflows/android-ci.yml` (unit tests + lint + assemble debug).

## 3) Strict Execution Order To Reach 10/10
Rule: Do not start next block before acceptance criteria of the current block is met.

### Block A - Quality Gate Foundation (must finish first)
1. Make `:app:testDebugUnitTest` green and stable with no flaky tests.
2. Keep `:app:connectedDebugAndroidTest` green on at least one physical device profile.
3. Enforce CI gate for PRs (unit tests required to merge).
4. Produce one reproducible command set in docs for local validation.
Acceptance evidence:
- CI checks green on PR.
- Local command logs attached in PR description.

### Block B - Critical Product Correctness
5. Validate end-to-end lifecycle: request creation -> assignment -> document draft -> signed/pdf -> request completion.
6. Validate sync/offline behavior: create/sign offline, reconnect, sync, no duplication/conflict loss.
7. Validate signature/PDF integrity: generated file, signature metadata, verification workflow.
Acceptance evidence:
- Test report in `TEST_GAP_MATRIX.md` updated to PASS for critical rows.
- At least one androidTest or integration test per critical flow.

### Block C - Accessibility and Neurofriendly Pro Level
8. Apply full accessibility checklist (contrast, text scaling, focus order, screen reader labels).
9. Add missing semantic labels (`contentDescription`, actionable labels, form helper text).
10. Add accessibility test pass criteria and manual validation script.
Acceptance evidence:
- Accessibility checklist complete with PASS/FAIL entries.
- No major screen reader blockers in critical screens.

### Block D - UX/Interface Professional Polish
11. Standardize component system (button hierarchy, spacing scale, states, error/success patterns).
12. Validate role home screens with consistent information architecture and no data leakage.
13. Add dark theme readiness and readability checks.
Acceptance evidence:
- UI consistency checklist complete.
- Design decisions logged in `docs/decision_log.md`.

### Block E - Release and Market Readiness
14. Harden release process (signing flow, rollback plan, crash/telemetry monitoring).
15. Final LGPD operational checklist and incident response playbook.
16. Pilot validation package (operations runbook + support script + KPI baseline).
Acceptance evidence:
- Release checklist fully checked.
- Production checklist sections marked done with evidence links.

## 4) Test Coverage Status (High-Level)
See full matrix: `TEST_GAP_MATRIX.md`
- Unit: good baseline, needs broader critical-flow assertions.
- Integration/androidTest: basic passing runs exist, still missing stronger E2E coverage.
- Accessibility testing: insufficient for pro level.
- Security/compliance tests: partial, requires explicit verification scenarios.

## 5) Open Risks
- Documentation drift risk due to many legacy status files.
- False confidence risk if only unit tests are green without full flow validation.
- Accessibility debt risk (can block enterprise adoption and quality perception).
- Release gate risk if CI policy is not enforced on every PR.

## 6) Handoff For Next Assistant (Do First)
1. Read this file first.
2. Read `NEXT_IMPLEMENTATION_PRIORITY.md` for ordered backlog details.
3. Read `TEST_GAP_MATRIX.md` and execute top P0 tests.
4. Update this file at end of session with:
   - What changed
   - What was verified
   - What remains blocked

## 7) Session Log (append-only)
- 2026-04-02: Consolidated master status; defined strict no-estimate execution order; created test gap matrix; marked legacy status docs as historical references.

