# ANDA Test Gap Matrix
Last updated: 2026-04-02
Purpose: Track what is covered vs what is still missing, in strict priority order.

Legend:
- PASS: implemented and validated recently
- PARTIAL: exists but not enough depth/evidence
- GAP: missing or not reliable yet

| Area | Unit | Integration | AndroidTest | Manual E2E | Accessibility | Security/Compliance | Priority | Current Status |
|---|---|---|---|---|---|---|---|---|
| Request lifecycle transitions | PASS | PARTIAL | PARTIAL | PARTIAL | GAP | PARTIAL | P0 | Needs stronger end-to-end proof |
| Document->request fallback (CNPJ/explicit) | PASS | PARTIAL | PASS | PARTIAL | N/A | PARTIAL | P0 | Core unit coverage improved |
| PDF generation pipeline | PARTIAL | PARTIAL | PARTIAL | PARTIAL | N/A | PARTIAL | P0 | Needs signature-integrity checks |
| Digital signature verification | PARTIAL | GAP | GAP | PARTIAL | N/A | PARTIAL | P0 | Foundation present, verification incomplete |
| Offline create/sign/sync | PARTIAL | GAP | PARTIAL | PARTIAL | N/A | PARTIAL | P0 | Missing robust conflict/idempotency tests |
| Role-based data isolation | PARTIAL | PARTIAL | PARTIAL | PARTIAL | N/A | PARTIAL | P0 | Must prove no leakage in real flows |
| Home screen UX stability | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | N/A | P1 | Need stronger regression checks |
| Notifications + deep links | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | N/A | P1 | Need deterministic navigation tests |
| LGPD export/deletion operations | PARTIAL | GAP | GAP | PARTIAL | N/A | PARTIAL | P1 | Need explicit end-to-end validation |
| Accessibility/neurofriendly readiness | GAP | GAP | GAP | PARTIAL | GAP | N/A | P0 | Not at pro level yet |
| CI quality gate reliability | PARTIAL | N/A | N/A | N/A | N/A | N/A | P0 | Workflow exists; policy enforcement needed |

## Strict Ordered Testing Backlog (No Estimates)
1. P0-TEST-01: End-to-end request lifecycle on device and test report capture.
2. P0-TEST-02: Offline sign/sync with reconnect and duplicate prevention checks.
3. P0-TEST-03: PDF + signature validation checks with reproducible artifact evidence.
4. P0-TEST-04: Accessibility baseline pass on critical screens (screen reader + contrast + focus).
5. P0-TEST-05: Role isolation checks across technician/clinic/company paths.
6. P1-TEST-06: Deep-link notification deterministic tests.
7. P1-TEST-07: LGPD export/deletion verification script + test evidence.
8. P1-TEST-08: UI regression smoke checks for main role dashboards.

## Evidence Rules
- Every completed test item must include:
  - command executed
  - environment/device
  - expected result
  - actual result
  - path to generated report/log
- If evidence is missing, status remains PARTIAL.

