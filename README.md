# AndroidAgent-V2

## Status

```text
STATUS = V2 BASELINE MIGRATION / ACTIVE DEVELOPMENT TREE
SOURCE = AG.V1-KNOWN-GOOD-FREEZE-V1 (frozen V1 known-good baseline)
BASELINE = VERIFIED_EQUIVALENT_TO_V1_FREEZE
```

This tree was established by `AG.V2-1.BASELINE-MIGRATION` as a direct
`git clone` of the frozen V1 repository at the freeze tag, then reset to the
freeze commit. Production content is byte-equivalent to the V1 freeze; only
V2 identity / migration / research-archival docs differ (see
`docs/migration/AG.V2-1.BASELINE-MIGRATION.md`).

## Source of record

```text
V1_SOURCE_REPO   = D:\codebank\AndCode\AndroidAgent
V1_FREEZE_COMMIT = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71
V1_FREEZE_TREE   = 92692cafe8d50673303ba97687eef414288b9425
V1_FREEZE_TAG    = AG.V1-KNOWN-GOOD-FREEZE-V1 (present in this history as the
                   source boundary)
```

## Architecture

```text
INHERITED FROM CONFIRMED V1 / UNCHANGED

AndroidAgent = AG Engine + Tool Layer + Host/Product
AG Engine    = AG Core + Agent Runtime
Tool Layer   = concrete tool modules (one per capability)
```

No module restructure, no package rename, no new abstraction.

## Current AT integration

```text
CURRENT AT = same V1 paired bundle (third_party/at-engine-handoff-v0 @ 0.1.0)
NEW AT     = NOT YET INTEGRATED (deferred to AG.V2-2.AT-INTEGRATION)
CAPABILITY_HANDOFF = AT_TO_AG_HANDOFF_V1
AT_CONTRACT_STATUS = DRAFT
AG_CONSUMPTION     = ONE-SHOT ONLY
```

Canonical pointer: `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md`.

## Pre-migration research

The pre-migration research workspace (AG.V2-0 architecture research,
Controller Strategy B decision, evidence, proposed changes) is preserved
verbatim as historical evidence under:

```text
docs/v2-research/pre-migration-workspace/
```

It is point-in-time evidence (including the original Strategy C
recommendation and the later Strategy B decision) and must not be rewritten.

## Next

```text
NEXT = AG.V2-2.AT-INTEGRATION (NOT yet authorized; awaiting Controller Review)
```

V1 (`D:\codebank\AndCode\AndroidAgent`) remains FROZEN_KNOWN_GOOD:
reference / rollback / evidence source; no new feature development there.
