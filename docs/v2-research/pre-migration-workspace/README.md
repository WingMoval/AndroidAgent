# AndroidAgent-V2

## Current status

```text
ARCHITECTURE RESEARCH ONLY
```

This directory is the research workspace for
`AG.V2-0.ARCHITECTURE-RESEARCH` (V2 architecture / migration boundary freeze).

## Source of record

```text
V1_SOURCE = D:\codebank\AndCode\AndroidAgent
```

V1 is the CURRENT KNOWN-GOOD production baseline. It is READ-ONLY for this
Step and was NOT modified.

```text
AT_SOURCE = D:\codebank\AndCode\AndroidTerminal-V2
```

AndroidTerminal is READ-ONLY for this Step and was NOT modified.

## What this is NOT

```text
NOT a production replacement
NOT a release
NOT a feature branch
NOT a Gradle project
```

Controller has selected future Strategy B (V1 freeze as Known-Good baseline;
this tree as the future active development tree). Production migration is NOT
yet authorized until AG.V1-FREEZE completes and the migration gates pass
Controller Review. See `docs/architecture/AG.V2-CONTROLLER-DECISION.md`.

No production modules, no Gradle files, no copied production code exist here.
`COPY_PRODUCTION_CODE_TO_V2 = NO` (migration scope is an OUTPUT of this
research, not a premise).

## Production migration policy

Any production migration into this tree requires a
Controller-approved V2 migration Step. None has been authorized by this
research document.

## Layout

```text
README.md
docs/
├─ research/
│  └─ AG.V2-0.ARCHITECTURE-RESEARCH.md   (research SSOT)
├─ architecture/
│  └─ README.md
└─ migration/
   └─ README.md
evidence/
├─ AG_V1_BASELINE.txt
├─ AG_CURRENT_MODULE_GRAPH.txt
├─ AG_AT_DEPENDENCY_AUDIT.txt
├─ AT_HANDOFF_V1_EVIDENCE.txt
├─ STALE_ASSUMPTIONS.txt
└─ SSOT_RESIDUE.txt
proposed-changes/
└─ FILE_CHANGE_MATRIX.md
```
