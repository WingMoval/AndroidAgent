# AG.AT-FULL-CONSUMER-INTEGRATION-0 — Restore Full AT V3 Consumer Capability

> INPUT = AG.AT-CONSUMER-RECONCILIATION-0.1 @ 50211c6
> BEFORE = 5/8
> TARGET = 8/8

---

## 1. Source Identities

```text
AG_START_HEAD = 50211c608e68db22fc46488cc29bb0ecd501ffe5
AT_HEAD = f55973db6d5839b87bd770884b22d4de2f68e65a
AT_SOURCE_BASELINE = c9cee2126b3ebc54a62be27d1f7bdea8ded22a7f
AT_FREEZE_TAG = AT.AG-CONSUMER-FREEZE-V1
AT_MODIFIED = NO
```

## 2. Artifact Provenance

All 5 AARs built from exact AT source baseline `c9cee21` (detached worktree):

| Artifact | Size | SHA256 | Module | Native |
|----------|------|--------|--------|--------|
| at-engine.aar | 23,778 B | BD2E0A9A...E28521 | :at-engine | none |
| at-backend-local.aar | 117,576 B | E0CED869...4492D6C | :at-backend-local | libat_local_backend.so |
| at-backend-adb.aar | 8,627 B | 795CFE85...500DC52A4 | :at-backend-adb | none |
| adb-engine.aar | 1,161,599 B | EC7234AE...3A895CD66912 | :adb-engine | libadbd_engine.so |
| at-cli.aar | 168,777 B | 5CC456AE...0E7F24D | :at-cli | libatcli_env.so |

```text
REQUIRED_RAW_AAR_SET_COMPLETE = YES
FORMAL_AT_ARTIFACT_BUNDLE_CREATED = NO (AG-side vendored consumption only)
AAR_TRANSITIVITY_METADATA = ABSENT at raw AAR level (AG POMs provide local resolution)
```

## 3. Dependency Graph

```text
:integration-at
  ├── com.at.engine:at-engine:1.0.0           (api)
  ├── com.at.backend.local:at-backend-local:1.0.0 (api)
  ├── com.at.backend.adb:at-backend-adb:1.0.0 (api)
  ├── com.adb.engine:adb-engine:1.0.0         (api)
  └── com.at.cli:at-cli:1.0.0                 (api)
```

## 4. Composition Graph

```text
ChatCompositionRoot (app)
    │
    ▼
ShellConnection → AtExecutionAdapter (integration-at)
    │
    ├── localEngine: ATEngine
    │     ├── oneShot = LocalOneShotBackend()
    │     └── persistent = LocalPersistentBackend()
    │
    ├── adbEngine: ATEngine
    │     ├── oneShot = AdbOneShotBackend(placeholder)
    │     └── persistent = AdbPersistentBackend(placeholder)
    │
    ├── bootstrap: AdbConsumerBootstrap(identityDir)
    │
    ├── cliExecution: ATCliExecution (routes to adbEngine + AdbRuntime)
    │
    ├── cliBridge: AdbCliBridgeServer (same-UID UDS)
    │
    └── cliBootstrap: LocalCliBootstrap("com.at.cli.AtCliMain")
```

```text
CONSUMER_COMPOSITION_ROOT = 1 (AtExecutionAdapter)
LOCAL_ENGINE_ROLE = PRESENT
ADB_ENGINE_ROLE = PRESENT
ADBRUNTIME_LIFECYCLE_OWNER_COUNT = 1
RECOVERY_OWNER_COUNT = 1
TRANSPORT_OBSERVER_OWNER_COUNT = 1
SECOND_OWNERSHIP_ISLAND = NO
```

## 5. 8-Clause Matrix

| Clause | Before | After |
|--------|--------|-------|
| 1 Local one-shot | FAIL | **PASS** |
| 2 Local persistent | FAIL | **PASS** |
| 3 ADB one-shot | PASS | PASS |
| 4 ADB persistent | PASS | PASS |
| 5 AT CLI embedded | FAIL | **PASS** |
| 6 Bootstrap | PASS | PASS |
| 7 Identity | PASS | PASS |
| 8 AdbRuntime lifecycle | PASS | PASS |

## 6. CLI Embedding Recipe

Mirrors `:at-cli-consumer-host` reference:

1. `ATCliExecution` implemented inside `AtExecutionAdapter` — routes to the SAME
   `adbEngine` + `AdbRuntime.ensureReady` + `AdbConsumerBootstrap`.
2. `AdbCliBridgeServer` started in `ChatCompositionRoot` init (idempotent, same-UID UDS).
3. `LocalCliBootstrap("com.at.cli.AtCliMain")` provides `writeScript()` + `sourceLine()`.
4. Identity: same `filesDir/at-consumer/identity` as direct ADB path.
5. No second AdbRuntime / recovery owner / identity.

## 7. Build Gates

```text
ASSEMBLE_DEBUG = PASS
FULL_BUILD = PASS (476 tasks)
SELF_CONTAINED_BUILD = PASS (no includeBuild, no AT source path)
CANONICAL_TEST_TOTAL = 666
CANONICAL_TEST_FAILURES = 0
CANONICAL_TEST_SKIPPED = 6
```

## 8. APK Audit

```text
APK_PATH = app/build/outputs/apk/debug/app-debug.apk
APK_SIZE = 5,246,481 B
APK_SHA256 = 8A6443A93C24799AF0336D7B88D7B631C139DFF8EEFE4BC763BEA3B610380AF0
BUILD_SOURCE = current HEAD (post-implementation)

NATIVE_LIBS:
  lib/adbd_engine.so (2,876,224 B)
  libat_local_backend.so (328,488 B)
  libatcli_env.so (378,512 B)

DEX CLASSES:
  com/at/engine = YES
  com/at/backend/local = YES
  com/at/backend/adb = YES
  com/adb/engine = YES
  com/at/cli = YES
  com/at/cli/AtCliMain = YES (classes.dex)
```

## 9. Regression Protection

- ADB one-shot: `executeAdb()` unchanged, `android_shell` tool unchanged.
- ADB persistent: `openAdbSession()` + session helpers unchanged.
- Bootstrap: `bootstrapState()`/`discoverPairing()`/`pairDiscovered()`/`pair()` unchanged.
- Identity: same `filesDir/at-consumer/identity`, self-generated.
- AdbRuntime: single owner, no second recovery.
- Model surface: `android_shell` tool spec + behavior UNCHANGED.

## 10. Final Verdict

```text
FULL_CURRENT_AT_V3_CONSUMER_INTEGRATION = PASS / 8 OF 8
```

Device conformance gates (C1-C11) to be executed post-commit on device.
