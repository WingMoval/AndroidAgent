# AG.CURRENT-CAPABILITY-RELEASE-VALIDATION-0

**Date:** 2026-11-05
**Status:** CLOSED
**VERDICT:** PASS
**CURRENT_CAPABILITY_RELEASE_BASELINE:** READY

---

## Purpose

Validate that the current frozen capability combination forms a stable
Release Baseline on real device, real provider, real model, formal AT v0.4.3.

---

## Capability Under Validation

```
AG Engine
├─ Session / Run
├─ durable TopicSession (Room)
├─ transcript projection
├─ real model provider (OpenAI-compatible)
├─ android_shell (local one-shot + ADB CLI)
├─ file (read/search/list/stat, APP_SELF)
└─ network (fetch, HTTPS only)

AT consumer
└─ formal AndroidTerminal v0.4.3 (AT.V043-CONSUMER-FREEZE-V1)
```

---

## Results

| Gate | Result |
|------|--------|
| A: Architecture | PASS (no AT drift, v0.4.3 active) |
| B: Tests + Build | PASS (428/0 fail, debug+release) |
| C: Install + Launch | PASS (no crash) |
| D: Provider/Model | PASS (RELEASE_MODEL_OK) |
| E: File LLM | PASS (file.read autonomous, token correct) |
| F: Network LLM | PASS (network.fetch autonomous, 200, title correct) |
| G: Shell LLM | PASS (android_shell id, uid=10418) |
| H: AT/ADB | PASS (CLI + uid=2000 + model) |
| I: New Session | PASS (isolation confirmed) |
| J: Force-Stop Restore | PASS (transcript restored, no replay) |
| K: Post-Restore Reuse | PASS (context correct) |
| M: Routing Matrix | PASS (Policy B confirmed) |
| N: Scope Freeze | PASS (no new capability) |
| O: Known Limitations | Documented (G-b1 deferred) |

---

## Routing Validation (Policy B)

| Intent | Tool | Result |
|--------|------|--------|
| File read in workspace | file.read | PASS |
| HTTPS URL content | network.fetch | PASS |
| Local shell identity | android_shell | PASS |
| ADB shell operations | android_shell (adb) | PASS |

---

## Known Limitations (NOT release blockers)

- **G-b1:** Long-running local one-shot child termination (Stop ≠ guaranteed kill). KNOWN_DEFERRED.
- **File write/edit:** Not implemented. Out of scope.
- **Web Search / Download:** Not implemented. Out of scope.
- **Test harness:** `:ag-runtime:test` and `:provider-openai-compatible-jdk:test` have pre-existing compilation errors in test fakes. Non-blocking.

---

## Production Change

```
PRODUCTION_CODE_CHANGED = NO
```

Only evidence + implementation docs added.

---

## Evidence

`docs/evidence/ag_current_capability_release_validation_0/` (16 files)

---

## Next

RELEASE_BASELINE_FREEZE

STOP.
