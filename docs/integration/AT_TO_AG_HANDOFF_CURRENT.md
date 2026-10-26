# AT_TO_AG_HANDOFF_CURRENT

Canonical current-snapshot pointer for the AT <-> AG integration.
Created: AG.V1-PRE-FREEZE-NORMALIZATION (2026-10-18).
Updated: AG.V2-2.AT-INTEGRATION (2026-10-20).
Updated: AG.AT-V4-INTEGRATION-1 (2026-10-22).

---

## 1. Current snapshot

```text
AT_HANDOFF             = AT_V4_CONSUMER_HANDOFF
SOURCE_BASELINE        = 7216d0e8131e92e8914ce7c8443e1668b1d53aca (freeze commit)
FINAL_DOCS_COMMIT      = 7f3a7dface6e504970714631ba4fb2224052f5ef
FREEZE_TAG             = AT.V4-CONSUMER-FREEZE-V1
AT_CONTRACT_STATUS     = FROZEN (V4 consumer boundary)
FINAL_API_FREEZE       = YES (V4 public contract frozen)

AG_CONSUMPTION_MODE    = VENDORED_AAR (repo-local Maven)
FINAL_BUILD_SELF_CONTAINED = YES (no sibling AT source tree required)
VARIANT                = debug (AG currently uses release V3 AARs;
                         debug bundle validated, release NOT yet validated)

AT_PUBLIC_CONTRACT     = com.at.engine.api:at-engine-api:4.0.0
AT_COMPOSITION_ENTRY   = com.at.composition.android:at-composition-android:4.0.0
AT_CLI_USED            = YES (optional frontend; AdbCliBridgeServer UDS)

AT_MODULES_CONSUMED    = :at-engine-api (public), :at-composition-android (entry),
                         :at-cli (optional). Internal modules (:at-engine,
                         :at-engine-spi, :at-backend-*, :adb-engine) are
                         transitive only; AG does not import them.

AG_AT_ENGINE_INTEGRATION     = ONE_SHOT ADB (model-visible) + PERSISTENT (adapter-owned)
AG_MODEL_VISIBLE_AT_CAPABILITY = ONE_SHOT_ONLY (unchanged)

IDENTITY               = at-consumer/identity (app-private, self-generated)
PAIRING_OWNER          = ATAndroidAdbControl (AT-owned, via composition facade)
ADB_RUNTIME_OWNER      = AdbRuntime (UNIQUE, AT-internal; AG does not import)

LEGACY_AT_V0           = SUPERSEDED / INACTIVE
LEGACY_V3_BUNDLE       = SUPERSEDED / INACTIVE (m2 entry commented out)
SCREEN_AWARE           = NOT_IMPLEMENTED
HUMAN_AGENT_SHARED_SESSION = DEFERRED
NEXT_FORMAL_SYNC       = POST_V4_RELEASE_VALIDATION
```

## 2. Historical handoff relationship

```text
AT_TO_AG_HANDOFF_V1          = HISTORICAL PUBLISHED SNAPSHOT (base 700ca3b, UNCHANGED)
AT_TO_AG_CONSUMER_HANDOFF_V1 = SUPPLEMENTAL CONSUMER CAPABILITY SNAPSHOT (base c9cee21, SUPERSEDED)
AT_V4_CONSUMER_HANDOFF       = CURRENT (V4 freeze 7216d0e; replaces V3 consumer handoff)
RELATION                     = V4 SUPERSEDES V3; V0/V1 unchanged (historical)
```

## 3. Physical consumption

Vendored AARs/JARs in `third_party/at-v4-consumer-freeze-v1/` with repo-local
Maven metadata in `third_party/at-v4-consumer-freeze-v1/m2/`. Artifacts from
AT V4 freeze commit `7216d0e` (tag `AT.V4-CONSUMER-FREEZE-V1`). No sibling AT
source tree required for build.

**Superseded:** `third_party/at-consumer-freeze-v1/` (V3, baseline c9cee21,
release) retained for rollback reference only. Its m2 repo entry in
`settings.gradle.kts` is commented out.

## 4. Identity

```text
IDENTITY_OWNER            = PER_CONSUMER_APP_PRIVATE
SELF_GENERATED_IDENTITY   = YES
ANDROIDTERMINAL_KEY_COPIED = NO
IDENTITY_PROFILE          = at-consumer/identity (AG's OWN profile)
PAIRING_OWNER             = ATAndroidAdbControl (AT-owned, via composition facade)
ADB_RUNTIME_OWNER         = AdbRuntime (UNIQUE, AT-internal)
```

## 5. Synchronization cadence

```text
current = AT.V4-CONSUMER-FREEZE-V1 (consumed via vendored AAR, debug variant)
next    = POST_V4_RELEASE_VALIDATION (AT V4 release bundle + re-validation)
```

## 6. Legacy bundles (superseded)

| Bundle | Path | Status |
|--------|------|--------|
| v0 AAR (0.1.0) | `third_party/at-engine-handoff-v0/` | SUPERSEDED / INACTIVE (no longer in dependency graph) |
| v3 AAR (1.0.0) | `third_party/at-consumer-freeze-v1/` | SUPERSEDED / INACTIVE (m2 entry commented out; rollback reference only) |
| **v4 AAR (4.0.0)** | `third_party/at-v4-consumer-freeze-v1/` | **CURRENT** |

## 7. V4 consumer boundary (AG compliance)

```text
:integration-at  imports  com.at.engine.*           (public contract)  = ALLOWED
:integration-at  imports  com.at.composition.android.* (assembly facade) = ALLOWED
:integration-at  imports  com.at.cli.*              (optional frontend) = ALLOWED
:integration-at  imports  com.adb.engine.*          (AdbRuntime etc.)   = FORBIDDEN
:integration-at  imports  com.at.backend.*          (backend impl)      = FORBIDDEN
:ag-core         imports  com.at.* / com.adb.*      (any AT type)       = FORBIDDEN
:ag-runtime      imports  com.at.* / com.adb.*      (any AT type)       = FORBIDDEN
```

All boundary rules verified PASS (2026-10-22, AG.AT-V4-INTEGRATION-1).
