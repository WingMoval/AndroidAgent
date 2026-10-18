# AG.V2-1.BASELINE-MIGRATION

Frozen V1 -> AndroidAgent-V2 direct baseline clone / equivalence migration.

```text
STEP = AG.V2-1.BASELINE-MIGRATION
DATE = 2026-10-18
TYPE = DIRECT FROZEN-BASELINE CLONE + EQUIVALENCE VERIFICATION
ARCHITECTURE REDESIGN = NO
PRODUCTION REFACTOR = NO
NEW FEATURE = NO
NEW AT INTEGRATION = NO
NEW STABLE CAPABILITY ID = NO
```

This document is the SSOT for the V2 baseline migration. The companion
baseline manifest is `docs/migration/AG.V2-BASELINE-V1.md`.

---

## 1. Provenance

```text
V1_SOURCE_REPO   = D:\codebank\AndCode\AndroidAgent
V1_FREEZE_COMMIT = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71
V1_FREEZE_TREE   = 92692cafe8d50673303ba97687eef414288b9425
V1_FREEZE_TAG    = AG.V1-KNOWN-GOOD-FREEZE-V1
```

Source verification (Gate E1) re-ran in V1 before any copy:

```text
git rev-parse AG.V1-KNOWN-GOOD-FREEZE-V1^{commit} = 13df1dbd... (match)
git rev-parse AG.V1-KNOWN-GOOD-FREEZE-V1^{tree}   = 92692caf... (match)
git status --short = only the 2 excluded local backups (untracked)
V1_SOURCE_MODIFIED = NO
```

## 2. Migration method

```text
MIGRATION_METHOD = LOCAL_GIT_CLONE_FROM_FROZEN_V1
                  (git clone --no-local D:\codebank\AndCode\AndroidAgent
                   D:\codebank\AndCode\AndroidAgent-V2,
                   then git checkout -B main AG.V1-KNOWN-GOOD-FREEZE-V1)
```

Rationale: preserves exact Git ancestry (the freeze commit + tag are native
V2 history), avoids the V1 working-tree untracked scratch, and proves
provenance without manual file copying. `--no-local` was used so V1/V2 do not
share hardlinked object ownership.

Post-clone the accidental `origin -> D:\codebank\AndCode\AndroidAgent`
relationship was REMOVED:

```text
V2_REMOTE_TO_V1 = NONE (no remote configured this Step)
```

The V1 freeze tag `AG.V1-KNOWN-GOOD-FREEZE-V1` remains in V2 history as the
source boundary (not deleted, not moved).

## 3. Clone identity (Gate E2)

```text
V2_INITIAL_HEAD  = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71 (= V1 freeze commit)
V2_INITIAL_TREE  = 92692cafe8d50673303ba97687eef414288b9425 (= V1 freeze tree)
WORKTREE_CLEAN   = YES (before any V2 doc change)
PRODUCTION_TREE_EQUIVALENCE = EXACT
```

## 4. Research preservation (Gate E3)

The pre-migration research workspace (14 files: AG.V2-0 research, Controller
Strategy B decision, evidence, proposed changes, README) was:

1. backed up to `D:\codebank\AndCode\_AndroidAgent-V2-PreMigration-Backup`
   (manifest: `_pre_migration_manifest.txt`; 14 files; HASH_MISMATCH = 0);
2. the original V2 directory moved into the backup as
   `original-research-workspace`;
3. restored verbatim into the isolated namespace
   `docs/v2-research/pre-migration-workspace/` (relative structure preserved);
4. re-verified: RESTORE_COUNT = 14, RESTORE_HASH_MISMATCH = 0.

```text
RESEARCH_BACKUP_FILE_COUNT   = 14
RESEARCH_RESTORE_FILE_COUNT  = 14
RESEARCH_RESTORE_HASH_MATCH  = YES
BACKUP_SAFE_TO_DELETE_LATER  = YES (but NOT deleted this Step; Controller decides)
```

The research content is preserved as PRE-MIGRATION RESEARCH EVIDENCE,
including the original Strategy C recommendation and the later Strategy B
decision (point-in-time; not rewritten).

## 5. Architecture (Gate E4)

```text
ARCHITECTURE_V1 = INHERITED / CONFIRMED / UNCHANGED
AG_FRAMEWORK    = AG_ENGINE + TOOL_LAYER + HOST_PRODUCT
MODULE_COUNT    = 8
MODULE_GRAPH_CHANGED = NO
AG_CORE_AT_DEPENDENCY    = NONE
AG_RUNTIME_AT_DEPENDENCY = NONE
CURRENT_AT_CONSUMERS     = tool-android-shell
```

No new module created (no tool-terminal / execution-adapter / tool-layer /
at-adapter). No package rename. No interface change.

## 6. Production equivalence (Gate E5) — the key gate

```text
git diff --name-only AG.V1-KNOWN-GOOD-FREEZE-V1   (after V2 doc changes)
```

Every diff belongs to the docs allowlist:

```text
README.md
AGENTS.md
docs/roadmap/AndroidAgent_Master_Roadmap.md
docs/migration/AG.V2-1.BASELINE-MIGRATION.md
docs/migration/AG.V2-BASELINE-V1.md
docs/v2-research/**
```

```text
PRODUCTION_DIFF_FROM_V1_FREEZE = 0
```

Protected production areas (zero diff vs the freeze tag): ag-core/**,
ag-runtime/**, provider-openai-compatible/**, provider-openai-compatible-jdk/**,
tool-android-shell/**, tool-network/**, tool-file/**, app/**,
third_party/at-engine-handoff-v0/**, settings.gradle*, build.gradle*, gradle/**,
gradlew*.

## 7. AT equivalence (Gate E6)

```text
git diff --exit-code AG.V1-KNOWN-GOOD-FREEZE-V1 -- third_party/at-engine-handoff-v0
  = exit 0 (no diff)
AT_BINARY_CHANGED        = NO
AT_BINARY_PROVENANCE_MATCH = YES
  (SHA256SUMS @ EC1EC20D6E3BC32BC4D04DBEBFDA70EBD3966C4C53F7B6727F63575065186B05;
   bundle manifest provenance unchanged: AT@726a441 + sourceTreeSha256)
CAPABILITY_HANDOFF       = AT_TO_AG_HANDOFF_V1
CURRENT_BINARY_HANDOFF   = at-engine-handoff-v0 @ 0.1.0
CURRENT_ANDROID_SHELL_CAPABILITY = ONE_SHOT_ONLY
RAW_TERMINAL             = ELIGIBLE_FOR_PLANNING / NOT_IMPLEMENTED
SCREEN_AWARE             = BLOCKED_BY_AT.EMULATOR-0
HUMAN_AGENT_SHARED_SESSION = DEFERRED
```

No AAR replaced/rebuilt, no directory rename, no Maven metadata edit, no
provenance update, no new AT consumed.

## 8. Test equivalence (Gate E7)

All 8 module test tasks re-run on V2 (fresh clone):

| Module | Tests | Failures | Skipped |
|---|---:|---:|---:|
| ag-core | 41 | 0 | 0 |
| ag-runtime | 26 | 0 | 0 |
| provider-openai-compatible | 59 | 0 | 0 |
| provider-openai-compatible-jdk | 26 | 0 | 0 |
| tool-android-shell | 16 | 0 | 0 |
| tool-network | 78 | 0 | 0 |
| tool-file | 55 | 0 | 3 (Linux-only symlink tests) |
| app | 109 | 0 | 0 |
| **TOTAL** | **410** | **0** | **3** |

Identical to the V1 freeze matrix. Note: the clone initially failed with
"SDK location not found" because `local.properties` is gitignored; it was
re-created with the same value as V1 (`sdk.dir=C:/android`) — an environment
file, not a tracked change.

## 9. Build equivalence (Gate E8)

```text
ASSEMBLE_DEBUG = PASS
FULL_BUILD     = FAIL_KNOWN_BASELINE
  (1 error: vendored AT AAR manifest CoarseFineLocation at
   at-runtime-0.1.0 AndroidManifest.xml:30; 14 warnings — byte-identical
   fingerprint to the V1 freeze)
NEW_BUILD_REGRESSION = NO
```

## 10. Device equivalence

```text
DEVICE_EQUIVALENCE_SMOKE = PRIOR_FREEZE_EVIDENCE_INHERITED
```

Production diff = 0, so the V1 freeze real-device evidence (D1-D4, D3 re-test,
stop smoke, AGENT-0.LIVE, AG.SHELL-1 S1-S5) carries over unchanged. No new
device runs required or performed (device not attached at migration time).

## 11. V2 identities (Gates E9/E10)

```text
V2_BASELINE_COMMIT    = c84bf5ded15c6c6c617a1ac72ac80707226e3b90
V2_BASELINE_PARENT    = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71 (= V1 freeze)
V2_BASELINE_TREE      = 5ec605a0343ad10b9f5311f350b644987eb75c54
V2_BASELINE_TAG       = AG.V2-BASELINE-V1 (annotated; IMMUTABLE — always points
                        at c84bf5d; never moved / deleted / recreated)
```

Ancestry:

```text
AG.V1-KNOWN-GOOD-FREEZE-V1   13df1db
                                 |
                                 v
            V2 verified baseline commit (docs/research archive only)
            c84bf5d
                                 |
                                 +-- AG.V2-BASELINE-V1 (immutable)
                                 |
                                 v
            post-baseline docs-only commit(s)
            2e5add1
                                 |
                                 v
            current V2 HEAD (may advance beyond the baseline tag)
```

Baseline vs current-HEAD semantics (frozen):

```text
AG.V2-BASELINE-V1   = immutable verified equivalence baseline (c84bf5d)
CURRENT HEAD        = may advance beyond the baseline through docs-only
                      closeout commits and later authorized V2 development Steps
BASELINE_INVALIDATED_BY_LATER_DOC_COMMITS = NO
```

## 12. V2 status after this Step

```text
AndroidAgent-V2 = ACTIVE DEVELOPMENT TREE
V2_BASELINE     = VERIFIED_EQUIVALENT_TO_V1_FREEZE
V1              = FROZEN_REFERENCE (reference / rollback / evidence)
NEXT            = AG.V2-2.AT-INTEGRATION
NEXT_OPEN_AUTHORIZED = NO (awaiting Controller Review)
```

AG.V2-2 scope (recorded only, NOT started): new AT binary / handoff bundle
integration, tool-android-shell compatibility, AT provenance refresh, one-shot
regression. A persistent-terminal tool is explicitly NOT part of AG.V2-2
(it requires capability intake -> preflight -> implementation, gated by
TOOL-006 + AT contract stabilization).

## 13. Controller Closeout Normalization

(added by AG.V2-1.CLOSEOUT-NORMALIZATION)

```text
AG.V2-1 migration result remains PASS.
c84bf5d is the immutable verified V2 baseline (tag AG.V2-BASELINE-V1).
Subsequent docs-only commit(s) (e.g. 2e5add1) do not redefine or invalidate
the V2 baseline. Current HEAD may advance beyond the baseline tag through
docs-only closeout commits and later authorized V2 development Steps.
```
