# AT_TO_AG_HANDOFF_CURRENT

Canonical current-snapshot pointer for the AT <-> AG integration.
Created: AG.V1-PRE-FREEZE-NORMALIZATION (2026-10-18).
Updated: AG.V2-2B.AT-ENGINE-FULL-INTEGRATION (2026-10-18).

This file is the ONE authoritative pointer for "which AT snapshot does AG
currently consume". Other documents (AGENTS.md, Roadmap) reference this file
instead of re-stating the whole AT state.

---

## 1. Current snapshot (frozen facts)

```text
AT_HANDOFF                  = AT.PRE-EMULATOR-FREEZE-V1
AT_EXECUTION_SOURCE         = 700ca3b
AT_FREEZE_PUBLICATION       = 7ccb1bf3ae332b66a425f47586c60d9a30ba70ef
AT_CONTRACT_STATUS          = DRAFT
AT_CONTRACT_FREEZE_AUTHORIZED = NO
AT_PHASE                    = PRE_EMULATOR
NEXT_AT_PHASE               = AT.EMULATOR-0

AG_CONSUMPTION_MODE         = GRADLE_COMPOSITE_BUILD
                              (includeBuild + dependencySubstitution;
                               NOT AAR binary handoff)
BINARY_AAR_HANDOFF          = NOT REQUIRED / NOT ESTABLISHED
                              (formal Maven/AAR packaging DEFERRED to
                               independent packaging track)

AG_AT_ENGINE_INTEGRATION    = FULL_CURRENT_EXECUTION_CAPABILITY
AG_MODEL_VISIBLE_AT_CAPABILITY = ONE_SHOT_ONLY

AT_MODULES_CONSUMED         = :at-engine, :at-backend-adb,
                              :at-backend-local, :adb-engine

LEGACY_AT_V0                = SUPERSEDED / INACTIVE
                              (legacy AAR no longer in dependency graph;
                               legacy native lib absent from APK)

SCREEN_AWARE                = NOT_IMPLEMENTED (BLOCKED_BY_AT.EMULATOR-0)
HUMAN_AGENT_SHARED_SESSION  = DEFERRED
NEXT_FORMAL_SYNC            = POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW
                              (expected: AT_TO_AG_HANDOFF_V2)
```

## 2. Capability vs binary handoff (formal distinction)

```text
CAPABILITY_HANDOFF = AT_TO_AG_HANDOFF_V1
```
is a CAPABILITY SNAPSHOT VERSION (controller notification): it states which
AT capabilities are available and verified for AG to plan against.

```text
AG_CONSUMPTION_MODE = GRADLE_COMPOSITE_BUILD
```
is the physical consumption mechanism: AG's `settings.gradle.kts` uses
`includeBuild("../AndroidTerminal-V3")` with `dependencySubstitution` to
resolve V3 modules as Gradle project dependencies. This is COMPOSITE BUILD
SOURCE CONSUMPTION, not a binary AAR handoff.

The V3 repository is a sibling directory. No V3 source is copied, vendored,
or modified. The V3 repo remains CLEAN (0 writes, HEAD = freeze tag).

## 3. Module provenance (composite build)

```text
repo              = D:/codebank/AndCode/AndroidTerminal-V3
branch            = master
sourceCommit      = 7ccb1bf3ae332b66a425f47586c60d9a30ba70ef
                  (= AT.PRE-EMULATOR-FREEZE-V1)
sourceDirty       = false (clean working tree)
modules           = :at-engine, :at-backend-adb, :at-backend-local,
                    :adb-engine (4 modules consumed by AG)
notConsumed       = :app, :test-host, :adb-only-test-host,
                    :at-shellless-test-host
```

## 4. Freeze ref verification

The following refs ARE present in the V3 tree (verified by `git rev-parse`):

```text
700ca3b                          = AT_EXECUTION_BASELINE (in V3 history)
7ccb1bf3ae332b66a425f47586c60d9a30ba70ef = AT.PRE-EMULATOR-FREEZE-V1 (HEAD)
tag AT.PRE-EMULATOR-FREEZE-V1    = PRESENT (= 7ccb1bf3...)
```

## 5. What AG may assume (Handoff V1, Freeze V1)

```text
one-shot execution            = CONSUMED (Local + ADB)
persistent terminal           = CONSUMED (Local + ADB)
backend-neutral execution     = CONSUMED (AtExecutionAdapter)
raw byte output               = CONSUMED
byte input / write            = CONSUMED
Ctrl-C                        = CONSUMED
resize                        = CONSUMED
lifecycle / close events      = CONSUMED
Local backend                 = CONSUMED
ADB backend                   = CONSUMED
multi-session                 = CONSUMED
natural exit                  = CONSUMED (EOF detection)
explicit close                = CONSUMED
ADB pairing/discovery/TLS/transport/recovery = owned by AT (AG does not duplicate)
```

## 6. What AG must NOT assume

```text
final AT public API frozen          = NO (DRAFT)
final TerminalSession API frozen    = NO
final callback / Flow model frozen  = NO
final AG<->AT adapter API frozen    = NO
Terminal Emulator available         = NO (NOT_STARTED)
Screen State available              = NO (NOT_STARTED)
Screen Snapshot available           = NO (NOT_STARTED)
cursor observation API              = NO (NOT_STARTED)
TUI semantic observation            = NO (NOT_STARTED)
RAW_ESCAPE_BYTES == SCREEN_STATE    = NO (frozen: they are NOT equal)
```

## 7. AG readiness (frozen)

```text
AG_RAW_TERMINAL_CAPABILITY = FULL_CURRENT_EXECUTION_CAPABILITY_INTEGRATED
AG_RAW_TERMINAL_IMPLEMENTED = YES (internal; NOT model-visible)
AG_PERSISTENT_TERMINAL_TOOL = NOT_IMPLEMENTED
AG_SCREEN_AWARE_TERMINAL   = NOT_IMPLEMENTED (BLOCKED_BY_AT.EMULATOR-0)
```

## 8. Synchronization cadence

AG does not track every AT commit. Formal snapshots only:

```text
current   = AT.PRE-EMULATOR-FREEZE-V1 (consumed via composite build)
next      = AT_TO_AG_HANDOFF_V2 (expected after AT.EMULATOR-0 + CONTRACT-1 review)
```

## 9. Historical bundle (superseded)

The legacy v0 AAR bundle (`third_party/at-engine-handoff-v0 @ 0.1.0`) is
SUPERSEDED. It is no longer in the AG dependency graph. The legacy native
lib (`libandroidterminaladb.so`) is absent from the final APK.

The v0 bundle remains in the repo for historical reference only. It must
not be re-activated.
