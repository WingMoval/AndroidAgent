# AT_TO_AG_HANDOFF_CURRENT

Canonical current-snapshot pointer for the AT <-> AG integration.
Created: AG.V1-PRE-FREEZE-NORMALIZATION (2026-10-18).

This file is the ONE authoritative pointer for "which AT snapshot does AG
currently consume". Other documents (AGENTS.md, Roadmap) reference this file
instead of re-stating the whole AT state.

---

## 1. Current snapshot (frozen facts)

```text
CURRENT_CAPABILITY_HANDOFF   = AT_TO_AG_HANDOFF_V1
CURRENT_BINARY_HANDOFF       = third_party/at-engine-handoff-v0 @ 0.1.0
                               (paired AAR bundle: at-runtime.aar + at-core.aar,
                                hash-verified via SHA256SUMS + manifest)
AT_CONTRACT_STATUS           = DRAFT
AT_CONTRACT_FREEZE_AUTHORIZED = NO
AT_PHASE                     = PRE_EMULATOR
NEXT_AT_PHASE                = AT.EMULATOR-0
AG_CONSUMPTION               = ONE-SHOT ONLY (AtRuntime.execute)
RAW_TERMINAL                 = EXTERNALLY_AVAILABLE / NOT YET CONSUMED BY AG
SCREEN_AWARE                 = BLOCKED_BY_AT.EMULATOR-0
HUMAN_AGENT_SHARED_SESSION   = DEFERRED
NEXT_FORMAL_SYNC             = POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW
                               (expected: AT_TO_AG_HANDOFF_V2)
```

## 2. Capability vs binary handoff (formal distinction)

```text
CAPABILITY_HANDOFF = AT_TO_AG_HANDOFF_V1
```
is a CAPABILITY SNAPSHOT VERSION (controller notification): it states which
AT capabilities are available and verified for AG to plan against.

```text
CURRENT_BINARY_HANDOFF = at-engine-handoff-v0 @ 0.1.0
```
is the physical BINARY BUNDLE actually consumed by `:tool-android-shell`
(via repo-local maven `third_party/at-engine-handoff-v0/m2`).

The two are deliberately decoupled. Handoff V1 does NOT rename the bundle
folder to `handoff-v1`; a folder rename happens only when the binary bundle
itself is version-bumped (bundle atomicity rule: regenerate the whole pair
on any AT update).

## 3. Binary provenance (from bundle manifest)

```text
repo              = D:/codebank/AndCode/AndroidTerminal-V2
branch            = main
sourceCommit      = 726a441acd5d5a7e24c5015adce57e543d1897f7
sourceDirty       = true (uncommitted AT.RUNTIME-2A/2B/2D + AT.ENGINE-AG0 sources)
sourceTreeSha256  = ff0a6432b9600f831b12456734a798fdc3802a08cee488bda4cb154be7f9ce3b
fingerprintFiles  = 162
inRepoValidation  = AT.ENGINE-AG0 VERIFIED (H1-H4 real-device PASS)
```

Provenance proves BINARY IDENTITY only. It does NOT prove a final AT API:
`AT_CONTRACT_STATUS = DRAFT` and no final API freeze is authorized.

## 4. Missing local freeze refs (recorded, not "verified")

The following refs named in the Handoff V1 controller notification are NOT
present in the current AT tree (verified by `git cat-file` / `git tag -l`):

```text
700ca3b                          = NOT FOUND LOCALLY
7ccb1bf3ae332b66a425f47586c60d9a30ba70ef = NOT FOUND LOCALLY
tag AT.PRE-EMULATOR-FREEZE-V1    = NOT FOUND LOCALLY
```

Recorded as:

```text
USER/CONTROLLER HANDOFF NOTIFICATION = ACCEPTED CAPABILITY INPUT
LOCAL FREEZE REF VERIFICATION        = NOT AVAILABLE IN CURRENT AT TREE
```

The AT repo has moved past the pre-emulator freeze (history shows
AT.V2-3B -> 4A -> 4B -> AT.RUNTIME-1). The consumed binary's authoritative
identity is the bundle manifest provenance + hashes above, not the missing
freeze refs.

## 5. What AG may assume (Handoff V1)

```text
one-shot execution            = exists (CONSUMED)
persistent terminal           = exists (NOT consumed by AG yet)
backend-neutral execution     = exists
raw byte output               = exists
byte input / write            = exists
Ctrl-C                        = exists
resize                        = exists
lifecycle / close events      = exist
Local backend                 = exists
ADB backend                   = exists
multi-session                 = exists
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
AG_RAW_TERMINAL_CAPABILITY = ELIGIBLE_FOR_PLANNING
AG_RAW_TERMINAL_IMPLEMENTED = NO
AG_SCREEN_AWARE_TERMINAL   = BLOCKED_BY_AT.EMULATOR-0
```

## 8. Synchronization cadence

AG does not track every AT commit. Formal snapshots only:

```text
current   = AT_TO_AG_HANDOFF_V1
next      = AT_TO_AG_HANDOFF_V2 (expected after AT.EMULATOR-0 + CONTRACT-1 review)
```

A new AT binary integration is scheduled for `AG.V2-2.AT-INTEGRATION` on the
V2 tree; the V1 freeze and the V2 baseline migration both use the CURRENT
bundle (v0 @ 0.1.0).
