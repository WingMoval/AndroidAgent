# AG.V2-BASELINE-V1 — V2 baseline manifest

```text
TAG = AG.V2-BASELINE-V1
SEMANTICS = KNOWN_GOOD_EQUIVALENT_TO_V1_FREEZE
            (NOT a final API freeze; NOT a new architecture)
DATE = 2026-10-18
```

This is the V2 baseline identity record. The migration SSOT is
`docs/migration/AG.V2-1.BASELINE-MIGRATION.md`.

## Source

```text
SOURCE_V1_FREEZE   = D:\codebank\AndCode\AndroidAgent
V1_FREEZE_COMMIT   = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71
V1_FREEZE_TREE     = 92692cafe8d50673303ba97687eef414288b9425
V1_FREEZE_TAG      = AG.V1-KNOWN-GOOD-FREEZE-V1
MIGRATION_METHOD   = LOCAL_GIT_CLONE_FROM_FROZEN_V1 (--no-local),
                     then git checkout -B main AG.V1-KNOWN-GOOD-FREEZE-V1
V2_REMOTE_TO_V1    = NONE (origin removed after clone)
```

## V2 identity

```text
V2_BASELINE_COMMIT = <recorded at commit time>
V2_BASELINE_PARENT = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71
V2_BASELINE_TREE   = <recorded at commit time>
V2_BASELINE_TAG    = AG.V2-BASELINE-V1 (annotated)
```

## Production equivalence

```text
PRODUCTION_DIFF_FROM_V1_FREEZE = 0
  (only docs differ: README.md, AGENTS.md,
   docs/roadmap/AndroidAgent_Master_Roadmap.md,
   docs/migration/AG.V2-1.BASELINE-MIGRATION.md,
   docs/migration/AG.V2-BASELINE-V1.md, docs/v2-research/**)
MODULE_COUNT = 8 (ag-core, ag-runtime, provider-openai-compatible,
                 provider-openai-compatible-jdk, tool-android-shell,
                 tool-network, tool-file, app)
ARCHITECTURE = INHERITED FROM CONFIRMED V1 / UNCHANGED
```

## Test matrix (re-run on V2)

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

Identical to the V1 freeze matrix.

## Build fingerprint

```text
ASSEMBLE_DEBUG = PASS
FULL_BUILD     = FAIL_KNOWN_BASELINE (1 error: AT AAR manifest
                 CoarseFineLocation; 14 warnings — identical to V1)
NEW_BUILD_REGRESSION = NO
```

## AT bundle

```text
AT_BINARY_CHANGED = NO
AT_BINARY_PROVENANCE_MATCH = YES
  (third_party/at-engine-handoff-v0 byte-identical to V1 freeze;
   SHA256SUMS @ EC1EC20D6E3BC32BC4D04DBEBFDA70EBD3966C4C53F7B6727F63575065186B05;
   at-engine-handoff-v0 @ 0.1.0; provenance AT@726a441 unchanged)
CAPABILITY_HANDOFF = AT_TO_AG_HANDOFF_V1
CURRENT_ANDROID_SHELL_CAPABILITY = ONE_SHOT_ONLY
AT_CONTRACT_STATUS = DRAFT
NEW_AT_INTEGRATED = NO
```

## Device

```text
DEVICE_EQUIVALENCE_SMOKE = PRIOR_FREEZE_EVIDENCE_INHERITED
  (production diff = 0; V1 freeze device evidence carries over)
```

## Next

```text
NEXT = AG.V2-2.AT-INTEGRATION (new AT binary / handoff integration;
       tool-android-shell compatibility; AT provenance refresh;
       one-shot regression. NO persistent-terminal tool in that step.)
NEXT_OPEN_AUTHORIZED = NO (awaiting Controller Review of V2 baseline evidence)
```

## V1 status

```text
V1 = FROZEN_KNOWN_GOOD (reference / rollback / evidence only;
     tag AG.V1-KNOWN-GOOD-FREEZE-V1 unchanged; no new feature development)
```
