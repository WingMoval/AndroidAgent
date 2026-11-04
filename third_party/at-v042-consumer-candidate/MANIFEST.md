# AT v0.4.2 Consumer Candidate Bundle — MANIFEST

## Status

| Field | Value |
|-------|-------|
| BUNDLE_STATUS | **POST_V042_FIX_CANDIDATE** |
| DEVICE_VALIDATION | **PASS** (AG.AT-V042-ADB-BACKEND-ROUTING-FIX-0) |
| FREEZE | **NO** |
| V042_IS_FINAL_AG_V1_BASELINE | **NO** |

## Provenance

| Field | Value |
|-------|-------|
| Source repo | `D:\codebank\AndCode\AndroidTerminal-V4-LOCAL-TCP-042` |
| Source tag | `v0.4.2` |
| Source commit | `925e7ac223d19eea7ddb89bf05e55df25ca7a7f2` |
| Build method | Detached worktree at `D:\codebank\AndCode\_worktrees\AndroidTerminal-v0.4.2-consumer-sync` |
| Build date | 2026-11-04 |
| Build variant | **debug** |
| Maven version | **4.0.0** (preserved from upstream AT publication coordinate; NOT manually bumped) |
| Previous validated bundle | `third_party/at-v4-consumer-freeze-v1` (v0.4.0, commit 7216d0e) |

## Build commands

```text
gradlew :at-engine-api:assemble :at-engine-spi:assemble \
  :at-engine:assembleDebug :at-backend-local:assembleDebug \
  :at-backend-adb:assembleDebug :adb-engine:assembleDebug \
  :at-cli:assembleDebug :at-composition-android:assembleDebug
```

## Maven coordinates (repo-local)

All artifacts resolved from `third_party/at-v042-consumer-candidate/m2`.

| Group | Artifact | Version | Packaging | AT module | Visibility |
|-------|----------|---------|-----------|-----------|------------|
| `com.at.engine.api` | `at-engine-api` | 4.0.0 | jar | `:at-engine-api` | **public contract** |
| `com.at.composition.android` | `at-composition-android` | 4.0.0 | aar | `:at-composition-android` | **consumer entry** |
| `com.at.cli` | `at-cli` | 4.0.0 | aar | `:at-cli` | optional frontend |
| `com.at.engine` | `at-engine` | 4.0.0 | aar | `:at-engine` | internal (transitive) |
| `com.at.engine.spi` | `at-engine-spi` | 4.0.0 | jar | `:at-engine-spi` | internal (transitive) |
| `com.at.backend.local` | `at-backend-local` | 4.0.0 | aar | `:at-backend-local` | internal (transitive) |
| `com.at.backend.adb` | `at-backend-adb` | 4.0.0 | aar | `:at-backend-adb` | internal (transitive) |
| `com.adb.engine` | `adb-engine` | 4.0.0 | aar | `:adb-engine` | internal (transitive) |

## SHA256

See `SHA256SUMS.txt` for all artifact + POM hashes.

## Differential vs v0.4.0

| Artifact | v0.4.0 | v0.4.2 candidate | Status |
|----------|--------|-----------------|--------|
| at-engine-api | b6b28fb1a88eb313 | 5b7034467db84c2e | BUILD-DIFFERENT (same classes, compiler metadata) |
| at-engine-spi | 04bf7569f66a15d4 | 96cdec5b95d34df2 | BUILD-DIFFERENT (same classes) |
| at-engine | bb46d47d5e04159e | bb46d47d5e04159e | IDENTICAL |
| at-backend-local | 684f282423b498dd | 6728101df6599ed8 | BUILD-DIFFERENT |
| at-backend-adb | 5e60f66eb61c5126 | 15895a5b6fe4b699 | BUILD-DIFFERENT (pre-fix) |
| adb-engine | 2a3edde073a88405 | 714170fae82b1e2e | **IMPLEMENTATION-DIFFERENT** (native lib changed) |
| at-cli | e301389d0110d63f | ef3266a30f5f154f | BUILD-DIFFERENT |
| at-composition-android | d3d13476a1b94f7d | c195b7666e203799 | BUILD-DIFFERENT (additive facade) |

Native lib:
- `libadbd_engine.so` (arm64-v8a): ce36dd55... -> 804e547f... (**DIFFERENT**)

Public API:
- at-engine-api: 19 classes, **CLASS SET IDENTICAL**

## POST_V042_FIX_CANDIDATE (2026-11-05)

AG.AT-V042-ADB-BACKEND-ROUTING-FIX-0: ADB backend transport routing fix.

| Field | Value |
|-------|-------|
| PATCH | ADB backend transport routing fix |
| SOURCE_COMMIT | e24cf4c (AT feature/local-tcp-v0.4.2) |
| BASE_VERSION | 0.4.2 |
| STATUS | candidate |
| CHANGED_ARTIFACT | at-backend-adb-4.0.0.aar |
| OLD_SHA | 15895a5b6fe4b699 |
| NEW_SHA | 58b26810e4e452ad |
| FIX | AdbOneShotBackend.execute(): AdbEngine.executeShell() → AdbRuntime.executeShell() |

This is a POST-v0.4.2 fix candidate. The original v0.4.2 candidate evidence
is preserved above. This patch fixes the ADB one-shot transport routing
bypass so that AG Local ADB one-shot commands use executeShellLocalhost
instead of the Wireless-only JNI path.
