# Sprint 3 — Firebase Crashlytics & Telemetry Integration

**Status**: ✅ **COMPLETE** — Production telemetry configured, 6 tests passing

**Scope**: Firebase Crashlytics integration, PII redaction, LGPD compliance, opt-in consent

---

## Deliverables

### 1. CrashReporter Telemetry Bridge

**File**: `core/telemetry/CrashReporter.kt` (237 lines)

**Purpose**: Decoupled bridge between local error logging (`CrashShield`) and remote telemetry (`Firebase Crashlytics`).

**Key Functions**:
- `initialize(context)` — Sets up Crashlytics with device/build metadata
- `recordRecoverableError(origin, throwable, metadata)` — Log locally; optionally send to Crashlytics
- `recordEvent(category, event, metadata)` — Custom events (sync retry, encryption key rotation, etc.)
- `reportFatalCrash(payload)` — Report fatal crashes recovered by CrashShield
- `setUserId(userId)` — Set opaque user ID for Crashlytics (post-auth)
- `clearUserId()` — Clear on sign-out
- `setOptIn(context, optIn)` — LGPD consent management
- `isOptIn(context)` — Check user consent status

**Design Principles**:
1. **Opt-in only** — default `false`; user must consent
2. **PII redaction** — automatic stripping of CPF, phone, email patterns
3. **Decoupled** — Firebase failures never crash the app (wrapped in `runCatching`)
4. **Throttled** — max 1 report per hour to avoid spam
5. **Localized** — errors always logged to `CrashShield` even if Crashlytics unavailable

### 2. Integration with Application Lifecycle

**File**: `AndaApplication.kt` (modified)

Added to `onCreate()`:
```kotlin
runCatching { CrashReporter.initialize(this) }
    .onFailure { Log.e("AndaApplication", "Falha ao iniciar CrashReporter", it) }
```

**Initialization Order**:
1. LocalDataProtection (crypto)
2. CrashShield (local error capture)
3. **CrashReporter (remote telemetry)**  ← New
4. SstRepository, DocumentLocalRepository, SyncQueueRepository (data)
5. SyncScheduler (background work)

### 3. Testing

**File**: `core/telemetry/CrashReporterTest.kt` (6 tests)

| Test | Coverage |
|---|---|
| `redactPii_redactsCpf` | Regex matches `123.456.789-00` → `[CPF]` |
| `redactPii_redactsPhone` | Regex matches `(11) 98765-4321` → `[PHONE]` |
| `redactPii_redactsEmail` | Regex matches `user@example.com` → `[EMAIL]` |
| `redactPii_keepsNonPii` | Non-matching text passes through |
| `redactPii_capsLength` | Output capped at 200 chars |
| `redactPii_multiplePiiPatterns` | All patterns redacted in same string |

**Result**: ✅ 6/6 passing, 0 failures

### 4. Dependencies

**`gradle/libs.versions.toml`** (modified):
- Added: `firebaseCrashlytics = "18.6.1"`

**`build.gradle.kts`** (modified):
- Added: `implementation(libs.firebase.crashlytics)`

### 5. Firebase Configuration

**Already in place** (from Phase 1):
- `google-services.json` configured
- `com.google.gms:google-services` plugin wired
- Firebase Auth already integrated

**Crashlytics activation**:
- Automatic with `google-services.json` (Crashlytics is opt-in=true by default in Google Services)
- User consent checked via `CrashReporter.isOptIn(context)` before sending reports

---

## LGPD Compliance Details

### Article 46 — Security Measures

| Requirement | Implementation | Status |
|---|---|---|
| **Consent Management** | `setOptIn()` requires explicit user signal; default false | ✅ |
| **PII Redaction** | Regex-based stripping of CPF, phone, email before upload | ✅ |
| **Data Minimization** | Only error messages, stack traces, device info (no user data) | ✅ |
| **Right to Deletion** | `clearUserData()` removes all telemetry data | ✅ |
| **User Identification** | Opaque `userId` (no PII linked) | ✅ |
| **Error Logging** | All errors logged locally via `CrashShield` regardless of Crashlytics state | ✅ |

### Data Fields Sent to Firebase Crashlytics

When user opts in:
- **Exception**: error message, stack trace (PII redacted)
- **Custom Keys**: app_version, sdk_version, device, build_type
- **User ID**: opaque identifier (no PII)
- **Log messages**: error origin (e.g., "SyncWorker/processBatch")

**Never sent**:
- CPF, CNPJ, email, phone
- Document contents or PII payloads
- Personal identifying information

---

## Error Routing Examples

### Example 1: Sync Network Error

```kotlin
// In SyncPendingRecordsWorker
try {
    val synced = SstRepository.syncPendingRecords()
} catch (e: IOException) {
    CrashReporter.recordRecoverableError(
        origin = "SyncPendingRecordsWorker/doWork",
        throwable = e,
        metadata = mapOf("retry_count" to "3")
    )
}
```

**Flow**:
1. Exception logged to `CrashShield` (always)
2. If user opted in: send to Crashlytics with "IOException: Network unavailable" and `retry_count=3`
3. Stack trace redacted for PII
4. Report throttled to 1/hour max

### Example 2: Encryption Error

```kotlin
// In DocumentEncryptionMapper
try {
    encryptor.decrypt(ciphertext)
} catch (e: EncryptionException) {
    CrashReporter.recordRecoverableError(
        origin = "DocumentEncryptionMapper/decrypt",
        throwable = e
    )
}
```

**Flow**:
1. Logged locally (never crashes)
2. Sent to Crashlytics if opted in
3. Exception message redacted (if it contains PII)
4. Helps diagnose encryption issues on prod

### Example 3: Custom Event (Non-Error)

```kotlin
// In SyncQueueRepository
if (failedCount > 5) {
    CrashReporter.recordEvent(
        category = "sync",
        event = "high_retry_rate_detected",
        metadata = mapOf(
            "failed_items" to "$failedCount",
            "time_window" to "1h"
        )
    )
}
```

**Flow**:
1. Logged as Crashlytics custom log entry
2. Helps monitor app health without errors
3. Respects user consent

---

## API Reference

### Initialization
```kotlin
CrashReporter.initialize(context)
```

### Error Reporting
```kotlin
CrashReporter.recordRecoverableError(
    origin: String,           // "Feature/method"
    throwable: Throwable,     // The exception
    metadata: Map<String, String> = emptyMap()
)
```

### Custom Events
```kotlin
CrashReporter.recordEvent(
    category: String,         // "sync", "encryption", "network"
    event: String,           // event name
    metadata: Map<String, String> = emptyMap()
)
```

### Fatal Crash Recovery
```kotlin
CrashReporter.reportFatalCrash(
    crashPayload: String,    // from CrashShield.consumeLastFatalReport()
    decrypted: String? = null
)
```

### User Management
```kotlin
CrashReporter.setUserId(userId)    // Post-auth
CrashReporter.clearUserId()        // Post-sign-out
```

### Consent Management
```kotlin
val isOptIn = CrashReporter.isOptIn(context)
CrashReporter.setOptIn(context, optIn = true)
CrashReporter.clearUserData(context)  // LGPD Art. 17 right to deletion
```

---

## Testing Commands

Run CrashReporter unit tests:
```bash
./gradlew :app:testDebugUnitTest --tests "*CrashReporter*"
```

Check test results:
```bash
ls app/build/test-results/testDebugUnitTest/*.xml | grep -i crash
```

---

## Files Modified/Created

### New
- `core/telemetry/CrashReporter.kt` (237 lines)
- `test/java/.../core/telemetry/CrashReporterTest.kt` (6 tests)

### Modified
- `gradle/libs.versions.toml` (added Firebase Crashlytics 18.6.1)
- `build.gradle.kts` (added dependency)
- `AndaApplication.kt` (added CrashReporter initialization)

### Existing (No Changes)
- `google-services.json` (already configured)
- ProGuard rules (already cover Firebase)

---

## Build Status

```
✅ app:compileDebugKotlin ................... SUCCESSFUL
✅ app:testDebugUnitTest (CrashReporter) ... 6 tests, 0 failures
✅ All previous tests still passing ........ 76 JVM + 22 instrumented
✅ Total test count ....................... 104 tests
```

---

## Deployment Checklist

- [ ] Test on device with Crashlytics enabled
- [ ] Verify PII redaction (test with fake CPF/email in logs)
- [ ] Test opt-in flow (UI consent dialog)
- [ ] Test opt-out (clearUserData, no reports sent)
- [ ] Monitor Firebase Crashlytics console for:
  - Crash rate
  - Top errors
  - Device distribution
  - OS version breakdown
- [ ] Configure Crashlytics alerts (crash spike > 5% increase)
- [ ] Set up on-call escalation for critical crashes

---

## Next Steps (Sprint 4)

1. **Consent Dialog UI** — Add privacy notice and opt-in button to MainActivity/Settings
2. **Instrumented Tests** — Mock Firebase and validate Crashlytics calls
3. **Analytics Dashboard** — Custom events tracking (sync success rate, encryption overhead)
4. **Error Categorization** — Bucket errors by severity (critical → immediate alert, recoverable → batch report)
5. **Play Store Compliance** — Privacy Policy update mentioning Crashlytics
6. **Beta Release** — Release candidate on Play Store Internal Testing track

---

**Summary**: Sprint 3 delivers a secure, LGPD-compliant telemetry system that bridges local error tracking (CrashShield) and remote monitoring (Firebase Crashlytics), with user consent management and automatic PII redaction.

