# QUICK REFERENCE - ANDA
Last updated: 2026-04-02

## Mandatory Reading Order
1. `PROJECT_MASTER_STATUS.md`
2. `NEXT_IMPLEMENTATION_PRIORITY.md`
3. `TEST_GAP_MATRIX.md`
4. `DOCUMENTATION_INDEX.md`

## Mandatory Execution Order
1. Run unit tests.
2. Run connected android tests.
3. Build debug artifact.
4. Execute top P0 backlog items only.

## Validation Commands
```bash
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
./gradlew :app:assembleDebug --no-daemon --console=plain
```

## Current Priority
- Follow `NEXT_IMPLEMENTATION_PRIORITY.md` in strict order.
- Track PASS/PARTIAL/GAP movement in `TEST_GAP_MATRIX.md`.
- Update `PROJECT_MASTER_STATUS.md` at end of every session.

## Handoff Rule
Before ending a session, update:
- what was changed
- what was verified
- what is blocked
- exact next action for the next assistant

## Legacy Note
This file intentionally stays short.
Detailed historical context remains in older docs, but current truth is only the master status/backlog/matrix set.
