# AG.ANDROID-SHELL-GUIDANCE-FIX-0

**Date:** 2026-11-05
**Status:** CLOSED
**VERDICT:** PASS

---

## Root Cause

`docs/research/AG.TOOL-GUIDANCE-ROUTING-AUDIT-0.md` identified:

The AT V4 Model Capability Update (commit f640ed0) rewrote `android_shell`
ToolSpec WHEN clause to include **"(files, local state)"**, creating direct
overlap with the `file` tool's domain. Before this change, the model
correctly selected `file` for workspace file reading (AG.FILE-0 L3 PASS).
After the change, the model selected `android_shell` (ls + cat).

**PRIMARY_CAUSE = ANDROID_SHELL_GUIDANCE_CONFLICT**

---

## Minimal Semantic Change

**File:** `AndroidShellToolImpl.kt` (WHEN clause only)

**Before:**
```
WHEN: Use the local shell for operations that belong to AndroidAgent's own
execution environment (files, local state). Use the `adb` CLI inside this
shell when the task requires ADB-specific operations, ADB shell privileges,
pairing, or device-side shell access.
```

**After:**
```
WHEN: Use the local shell for command execution, process operations,
environment inspection, build/test commands, or behavior that requires
shell semantics. Use the `adb` CLI inside this shell when the task
requires ADB-specific operations, ADB shell privileges, pairing, or
device-side shell access.
```

**What changed:** Removed "(files, local state)" as a primary use case.
Replaced with accurate shell-domain use cases (command execution, process
operations, environment inspection, build/test, shell semantics).

**What was preserved:** CONTEXT line, ADB semantics, principal distinction,
INPUT/OUTPUT contract, tool name, argument schema.

---

## Same-Model Retest

| Gate | Result |
|------|--------|
| R1 File autonomous selection | **PASS** (file.read, not shell) |
| R2 Observation dependency | **PASS** (token only in fixture) |
| R3 Next-turn recall | **PASS** (0 toolcalls, correct answer) |
| Shell positive case | **PASS** (autonomous `id` for identity) |

**GUIDANCE_FIX_CAUSAL_CLOSURE = STRONG_PASS**

Single variable changed (shell WHEN wording), outcome flipped (shell→file).
Same model, same provider, same prompt, same tool set, same instructions.

---

## Scope Preservation

```
AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGED = NO
FILE_TOOL_CHANGED = NO
NETWORK_TOOL_CHANGED = NO
AGENT_INSTRUCTIONS_CHANGED = NO
TOOL_CONTRACT_CHANGED = NO
PROVIDER_CHANGED = NO
AT_CHANGED = NO
DB_CHANGED = NO
```

Only: `AndroidShellToolImpl.spec.description` + 3 new contract tests.

---

## Tests

376 total / 373 pass / 0 fail / 3 skip / BUILD PASS

New tests: U20 (no file claim), U21 (no "files, local state"), U22 (command execution present).

---

## Current Tool Baseline

```
file.read       = FROZEN (LLM-validated: autonomous selection + observation + recall)
network.fetch   = FROZEN (LLM-validated: autonomous selection + observation + recall)
android_shell   = FROZEN (LLM-validated: autonomous selection for identity/process)
```

**CURRENT_EXISTING_TOOL_BASELINE = FROZEN**

---

## Evidence

`docs/evidence/ag_android_shell_guidance_fix_0/` (14 files)

---

## Next

CURRENT_CAPABILITY_RELEASE_VALIDATION

STOP.
