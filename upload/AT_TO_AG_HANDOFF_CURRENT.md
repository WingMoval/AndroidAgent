# AT_TO_AG_HANDOFF_CURRENT

Canonical current-snapshot pointer for the AT <-> AG integration.
Created: AG.V1-PRE-FREEZE-NORMALIZATION (2026-10-18).
Updated: AG.V2-2.AT-INTEGRATION (2026-10-20).

---

## 1. Current snapshot

```text
AT_HANDOFF             = AT_TO_AG_CONSUMER_HANDOFF_V1
SOURCE_BASELINE        = c9cee2126b3ebc54a62be27d1f7bdea8ded22a7f
FREEZE_TAG             = AT.AG-CONSUMER-FREEZE-V1
FREEZE_PUBLICATION     = a6f23d7112ca40c873b63cba4aef5686e0dd4405
AT_CONTRACT_STATUS     = DRAFT
FINAL_API_FREEZE       = NO

AG_CONSUMPTION_MODE    = VENDORED_AAR (repo-local Maven)
FINAL_BUILD_SELF_CONTAINED = YES (no sibling AT source tree required)

AT_MODULES_CONSUMED    = :at-engine, :at-backend-adb, :adb-engine
AT_CLI_USED            = NO

AG_AT_ENGINE_INTEGRATION     = ONE_SHOT ADB (model-visible)
AG_MODEL_VISIBLE_AT_CAPABILITY = ONE_SHOT_ONLY

LEGACY_AT_V0           = SUPERSEDED / INACTIVE
SCREEN_AWARE           = NOT_IMPLEMENTED
HUMAN_AGENT_SHARED_SESSION = DEFERRED
NEXT_FORMAL_SYNC       = POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW
```

## 2. Historical handoff relationship

```text
AT_TO_AG_HANDOFF_V1          = HISTORICAL PUBLISHED SNAPSHOT (base 700ca3b, UNCHANGED)
AT_TO_AG_CONSUMER_HANDOFF_V1 = SUPPLEMENTAL CONSUMER CAPABILITY SNAPSHOT (base c9cee21)
RELATION                     = EXTENDS / DOES NOT REWRITE
AT_TO_AG_HANDOFF_V2          = NOT YET CREATED
```

## 3. Physical consumption

Vendored AARs in `third_party/at-consumer-freeze-v1/` with repo-local Maven
metadata in `third_party/at-consumer-freeze-v1/m2/`. AARs built from exact
source baseline `c9cee21` (detached worktree). No sibling AT source tree
required for build.

## 4. Identity

```text
IDENTITY_OWNER            = PER_CONSUMER_APP_PRIVATE
SELF_GENERATED_IDENTITY   = YES
ANDROIDTERMINAL_KEY_COPIED = NO
PAIRING_OWNER             = AdbConsumerBootstrap (AT-owned)
ADB_RUNTIME_OWNER         = AdbRuntime (UNIQUE)
```

## 5. Synchronization cadence

```text
current = AT.AG-CONSUMER-FREEZE-V1 (consumed via vendored AAR)
next    = AT_TO_AG_HANDOFF_V2 (expected after AT.EMULATOR-0 + CONTRACT-1 review)
```

## 6. Legacy bundle (superseded)

The v0 AAR bundle (`third_party/at-engine-handoff-v0 @ 0.1.0`) is SUPERSEDED.
No longer in the AG dependency graph. Legacy native lib absent from APK.
Files remain in repo for Git history reference.
