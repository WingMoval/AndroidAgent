# AG.AT-V042-CONSUMER-SYNC-0

TYPE = CONSUMER ARTIFACT SYNC / DEPENDENCY MIGRATION / PROVENANCE VERIFICATION / STATIC INTEGRATION VALIDATION
DATE = 2026-11-04
STATUS = CANDIDATE / VALIDATION_PENDING

## 1. State Transition

| Field | Before | After |
|-------|--------|-------|
| PHYSICAL_AG_AT_CONSUMPTION | v0.4.0 | **v0.4.2 CANDIDATE** |
| FORMAL_VALIDATED_AG_AT_BASELINE | v0.4.0 | v0.4.0 (unchanged) |
| V042_DEVICE_VALIDATION | N/A | **PENDING** |
| V042_IS_FINAL_AG_V1_BASELINE | N/A | **NO** |

## 2. Source Identity

```text
AT_SOURCE_REPO = D:\codebank\AndCode\AndroidTerminal-V4-LOCAL-TCP-042
AT_SOURCE_TAG = v0.4.2
AT_SOURCE_COMMIT = 925e7ac223d19eea7ddb89bf05e55df25ca7a7f2
AT_BUILD_METHOD = DETACHED_WORKTREE
AT_BUILD_WORKTREE = D:\codebank\AndCode\_worktrees\AndroidTerminal-v0.4.2-consumer-sync
AT_SOURCE_MODIFIED = NO
```

## 3. Candidate Bundle

```text
BUNDLE_PATH = third_party/at-v042-consumer-candidate/
BUNDLE_STATUS = CANDIDATE / VALIDATION_PENDING
ARTIFACT_COUNT = 8 (6 AAR + 2 JAR)
MAVEN_VERSION = 4.0.0 (preserved from upstream)
VARIANT = debug
SHA256_MANIFEST = third_party/at-v042-consumer-candidate/SHA256SUMS.txt
```

## 4. Old Bundle Preservation

```text
V040_BUNDLE_PATH = third_party/at-v4-consumer-freeze-v1/
V040_BUNDLE_PRESERVED = YES
V040_BUNDLE_MODIFIED = NO
```

## 5. Artifact Differential

| Artifact | v0.4.0 SHA256[:16] | v0.4.2 SHA256[:16] | Status |
|----------|--------------------|--------------------|----|
| at-engine-api | b6b28fb1a88eb313 | 5b7034467db84c2e | BUILD-DIFFERENT (same 19 classes) |
| at-engine-spi | 04bf7569f66a15d4 | 96cdec5b95d34df2 | BUILD-DIFFERENT |
| at-engine | bb46d47d5e04159e | bb46d47d5e04159e | IDENTICAL |
| at-backend-local | 684f282423b498dd | 6728101df6599ed8 | BUILD-DIFFERENT |
| at-backend-adb | 5e60f66eb61c5126 | 15895a5b6fe4b699 | BUILD-DIFFERENT |
| adb-engine | 2a3edde073a88405 | 714170fae82b1e2e | **IMPLEMENTATION-DIFFERENT** |
| at-cli | e301389d0110d63f | ef3266a30f5f154f | BUILD-DIFFERENT |
| at-composition-android | d3d13476a1b94f7d | c195b7666e203799 | BUILD-DIFFERENT |

Native lib `libadbd_engine.so` (arm64-v8a):
- v0.4.0: `ce36dd55bcb38afb...`
- v0.4.2: `804e547fa57a5972...`
- V042_NATIVE_DIFF_CONFIRMED = YES

Public API: at-engine-api 19 classes, CLASS SET IDENTICAL.

## 6. Gradle Wiring

```text
CHANGED_FILE = settings.gradle.kts
CHANGE = Added third_party/at-v042-consumer-candidate/m2 as FIRST maven repo
         (before the v0.4.0 repo), so Gradle resolves candidate bytes
         for the same 4.0.0 GAV.
OLD_REPO_REMOVED = NO (retained for rollback)
CACHE_AVOIDANCE = candidate repo listed first + --refresh-dependencies
```

## 7. Resolution Proof

Evidence A (dependencyInsight):
```text
:app:dependencyInsight --dependency com.adb.engine:adb-engine
  com.adb.engine:adb-engine:4.0.0 resolved via at-composition-android
  -> at-backend-adb -> adb-engine (correct transitive chain)
```

Evidence B (byte fingerprint):
```text
CANDIDATE_AAR_NATIVE_SHA256 = 804e547fa57a5972f263df9052d3d55fefffec744cdb41e6fda79dee0b0b67d7
AG_APK_NATIVE_SHA256 = 804e547fa57a5972f263df9052d3d55fefffec744cdb41e6fda79dee0b0b67d7
APK_CONTAINS_V042_NATIVE = YES
OLD_V040_NATIVE_SHA256 = ce36dd55bcb38afb889f86e52499726b27fb4c3c78affe95e0935c4016172918
APK != OLD = CONFIRMED
```

## 8. Compile / Test / Assemble

```text
INTEGRATION_AT_COMPILE = PASS
TOOL_ANDROID_SHELL_COMPILE = PASS
APP_COMPILE = PASS
TEST_TOTAL = 233
TEST_PASS = 233
TEST_FAIL = 0
TEST_SKIPPED = 0
ASSEMBLE_DEBUG = PASS
APK_PATH = app/build/outputs/apk/debug/app-debug.apk
APK_SIZE = 6306722 bytes
APK_SHA256 = 51f6e5a3d3b3317a...
```

## 9. Source Compatibility

```text
AG_PRODUCTION_SOURCE_CHANGED = NO
INTEGRATION_AT_SOURCE_CHANGED = NO
TOOL_ANDROID_SHELL_SOURCE_CHANGED = NO
AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGED = NO
DB_CHANGED = NO
```

No AG source modification was needed. The v0.4.2 candidate is source-compatible
with the current AG codebase.

## 10. Device Validation

```text
DEVICE_TEST_RUN = NO
G_A_STATUS = NOT_RUN
G_B1_STATUS = NOT_RUN
G_B2_STATUS = NOT_RUN
G_C1_STATUS = NOT_RUN
G_C2_STATUS = NOT_RUN
G_D_STATUS = NOT_RUN
G_E_STATUS = NOT_RUN
G_F_STATUS = NOT_RUN
G_G_STATUS = NOT_RUN
```

All device gates belong to AG.AT-V042-COMPAT-VALIDATION-1.

## 11. Status

```text
V042_SYNC = COMPLETE / CANDIDATE
V042_DEVICE_VALIDATION = PENDING
V042_FORMAL_BASELINE = NOT_YET
NEXT = AG.AT-V042-COMPAT-VALIDATION-1
```
