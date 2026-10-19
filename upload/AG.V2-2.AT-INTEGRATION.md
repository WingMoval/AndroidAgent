# AG.V2-2: Consume AT.AG-CONSUMER-FREEZE-V1 — Android Execution Path Closure

## Status

```text
AG.V2-2.AT-INTEGRATION = PASS / CLOSED
```

## 1. Integration Purpose

Replace the legacy AT v0 bundle and the previous composite-build AT V3
consumption with a **self-contained vendored AAR bundle** from
`AT.AG-CONSUMER-FREEZE-V1` (source baseline `c9cee21`). The AG build no
longer requires the sibling AT source tree.

`android_shell(command)` one-shot is the only model-visible AT capability.

## 2. AT Provenance

```text
AT_ROOT                = D:\codebank\AndCode\AndroidTerminal-V3
AT_SOURCE_BASELINE     = c9cee2126b3ebc54a62be27d1f7bdea8ded22a7f
AT_FREEZE_PUBLICATION  = a6f23d7112ca40c873b63cba4aef5686e0dd4405
AT_FREEZE_TAG          = AT.AG-CONSUMER-FREEZE-V1
AT_HANDOFF             = AT_TO_AG_CONSUMER_HANDOFF_V1
AT_CONTRACT_STATUS     = DRAFT
FINAL_API_FREEZE       = NO
```

The freeze publication commit `a6f23d7` is a **docs-only** commit that
publishes the handoff snapshot. The actual source baseline is `c9cee21`
(AT.CONSUMER-BOOTSTRAP-0 + AT.CLI-CONSUMER-0).

## 3. Physical Consumption

```text
PHYSICAL_CONSUMPTION = VENDORED_AAR (repo-local Maven)
FINAL_BUILD_REQUIRES_AT_SOURCE_TREE = NO
```

AARs built from exact source baseline `c9cee21` (detached worktree) and
stored in `third_party/at-consumer-freeze-v1/`. Repo-local Maven metadata
in `third_party/at-consumer-freeze-v1/m2/` resolves them.

```text
CONSUMED_AT_MODULES = :at-engine, :at-backend-adb, :adb-engine
AT_CLI_USED         = NO (not needed for one-shot ADB)
CONSUMER_DEPENDS_ON_AT_APP = NO
```

## 4. Artifact Provenance

```text
at-engine.aar         23,778 B    SHA256 BD2E0A9A...
at-backend-adb.aar     8,627 B    SHA256 795CFE85...
adb-engine.aar       1,161,599 B  SHA256 EC7234AE...
  native: lib/arm64-v8a/libadbd_engine.so (2,876,224 B)
```

Full hashes in `third_party/at-consumer-freeze-v1/MANIFEST.md`.

## 5. AG-Side AT Execution Adapter

`integration-at` / `AtExecutionAdapter` — the unified AT consumption point.

- Holds `ATEngine` composition (ADB one-shot + persistent backends)
- Exposes `AdbConsumerBootstrap` for first-use pairing
- Exposes one-shot execution, persistent session, raw I/O, resize, Ctrl-C
- AG app-private identity directory: `filesDir/at-consumer/identity`

## 6. Identity Policy

```text
IDENTITY_DIR                    = filesDir/at-consumer/identity
IDENTITY_OWNER                  = PER_CONSUMER_APP_PRIVATE
SELF_GENERATED_IDENTITY         = YES
ANDROIDTERMINAL_KEY_COPIED      = NO
OLD_IDENTITY (files/at-identity) = INACTIVE (V3 app key, no longer referenced)
```

AG's identity is self-generated via `AdbConsumerBootstrap.pair()`. The key
SHA256 differs from both the V3 app key and the old copied key.

## 7. Bootstrap Policy

```text
ADB_CONSUMER_BOOTSTRAP_USED     = YES
PAIRING_IMPLEMENTATION_OWNER    = AdbConsumerBootstrap (in :adb-engine)
PAIRING_IMPLEMENTATION_COUNT    = 1
ADB_RUNTIME_OWNER               = AdbRuntime (UNIQUE)
ADB_RUNTIME_COUNT               = 1
RECOVERY_OWNER_COUNT            = 1
```

Bootstrap state mapping:
- No identity → `PAIRING_REQUIRED`
- Identity exists + connect failure → `CONNECT_FAILED` (NOT auto PAIRING_REQUIRED)
- WD off → `USER_ACTION_REQUIRED_WD`
- Connected → `READY`

## 8. Minimal Pairing UX

`:app` has a minimal pairing UI: 6-digit code input + Pair button. The code
is read from the input, passed to `AdbConsumerBootstrap.pairDiscovered()`,
then cleared. Not persisted, not logged, not stored.

## 9. Device Gates

```text
Gate A: Bootstrap initial state     = PAIRING_REQUIRED (PASS)
Gate B: Pairing discovery           = PASS (0 endpoints, screen not open)
Gate C: Real pairing                = PASS (user 6-digit code, self-generated key)
Gate D: Post-pair READY             = PASS
Gate E: android_shell id            = PASS (uid=2000(shell))
Gate F: stdout                      = PASS (AG_V2_AT_CONSUMER_OK)
Gate G: stdout/stderr/nonzero       = PASS (OUT/ERR/7)
Gate H: Sequential reuse (10x)      = PASS (10/10, same runtime)
Gate I: Process restart identity    = PASS (READY, no re-pairing)
```

## 10. Legacy AT v0

```text
LEGACY_HANDOFF       = at-engine-handoff-v0 @ 0.1.0
LEGACY_HANDOFF_ACTIVE = NO (removed from settings.gradle.kts Maven repo)
LEGACY_NATIVE_ACTIVE  = NO (libandroidterminaladb.so not in APK)
```

The legacy v0 bundle files remain in the repo (Git history) but are NOT in
the active dependency graph. No fallback-to-legacy.

## 11. Canonical Tests

```text
CANONICAL_TEST_TOTAL   = 726
CANONICAL_TEST_FAILURES = 0
CANONICAL_TEST_SKIPPED  = 6 (tool-file symlink, Linux-gated)
```

## 12. Build

```text
ASSEMBLE_DEBUG = PASS
FULL_BUILD     = PASS
SELF_CONTAINED_BUILD_GATE = PASS (clean build without sibling AT source)
```

## 13. APK Native Audit

```text
FINAL_NATIVE_LIB_SET:
  lib/arm64-v8a/libadbd_engine.so (2,876,224 B)  owner: :adb-engine

LEGACY_NATIVE = ABSENT
HYBRID_RUNTIME = NO
```

## 14. AG Core / Runtime Boundary

```text
AG_CORE_AT_DEPENDENCY    = NONE
AG_RUNTIME_AT_DEPENDENCY = NONE
```

AT imports only in:
- `:integration-at` (16 imports — unified consumption point)
- `:tool-android-shell` (5 imports — via adapter api dep)
- `:app` debug (1 import — DebugBridge session validation, debug-only)

## 15. Model Tool Surface

```text
MODEL_VISIBLE_AT_CAPABILITY = ONE_SHOT_ONLY
ANDROID_SHELL_CONTRACT_CHANGED = NO
PERSISTENT_AGENT_TERMINAL_ADDED = NO
EMULATOR_ADDED = NO
SCREEN_STATE_ADDED = NO
```

## 16. Final Verdict

```text
VERDICT = PASS
AG.V2-2.AT-INTEGRATION = CLOSED
```
