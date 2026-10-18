# AG.V2-CONTROLLER-DECISION

Recorded: AG.V1-PRE-FREEZE-NORMALIZATION (2026-10-18).

## 1. Strategy selection

```text
STRATEGY_C_TECHNICAL_FINDING = VALID
STRATEGY_B_CONTROLLER_CHOICE = ACTIVE
```

`AG.V2-0.ARCHITECTURE-RESEARCH` recommended **Strategy C** (no physical V2
fork; normalize in place) on the technical finding that no structural defect
exists:

```text
ARCHITECTURE_V1_STRUCTURALLY_VALID = YES   (CONFIRMED by code audit)
PRODUCTION_REFACTOR_REQUIRED       = NO
```

The Controller / user subsequently selected **Strategy B** for engineering
reasons that are NOT a refutation of the above facts:

```text
MIGRATION ISOLATION
KNOWN-GOOD FREEZE
ROLLBACK SAFETY
NEW-AT INTEGRATION ISOLATION
```

This decision does NOT invalidate the technical finding. The V1 architecture
is not broken; the strategy is about process risk management, not defect
repair.

## 2. Frozen consequences

```text
AndroidAgent (V1)   -> freeze as Known-Good baseline (AG.V1-FREEZE)
AndroidAgent-V2     -> future active development tree
```

Frozen migration sequence:

```text
AG.V1-PRE-FREEZE-NORMALIZATION   (this Step's predecessor context)
        ↓
AG.V1-FREEZE                     (final freeze commit + tag + manifest)
        ↓
AG.V2-1.BASELINE-MIGRATION       (V1 -> V2 EQUIVALENCE only; no new AT, no features)
        ↓
V2 EQUIVALENCE GATE
        ↓
AG.V2-2.AT-INTEGRATION           (new AT binary / Handoff V2 integration, V2 only)
        ↓
NEW AT INTEGRATION GATE
        ↓
AG.OUTPUT-BOUND.PREFLIGHT        (TOOL-006; semantics unchanged, execution order only)
        ↓
future capabilities on V2
```

## 3. Boundaries

- V1 after freeze: KNOWN-GOOD REFERENCE / ROLLBACK SOURCE / EVIDENCE SOURCE.
  No new feature development (only explicitly-authorized critical freeze-line
  bugfixes).
- V2 baseline migration target is EQUIVALENCE (same module graph, same
  architecture, same current AT binary, same behavior, same test baseline),
  not improvement.
- New AT must NOT enter during the V2 baseline copy (isolated to
  AG.V2-2.AT-INTEGRATION).
- TOOL-006: status / priority / capability identity UNCHANGED; only execution
  order changes (TOOL_006_TARGET = V2).
- Persistent raw terminal: NOT implemented in the baseline; capability intake
  pending (no Stable ID assigned).
- This file and the V2 workspace remain RESEARCH / PRE-MIGRATION. No
  production migration is authorized until AG.V1-FREEZE completes and the
  migration gates (M1-M7) pass Controller Review.
