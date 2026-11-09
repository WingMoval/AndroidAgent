# AndroidAgent Current Capability Release Baseline V1

**Date:** 2026-11-09
**Type:** Release Baseline Freeze
**Status:** FROZEN
**Tag:** `ag-current-capability-release-baseline-v1`

---

## 1. Freeze Identity

```text
CAPABILITY_VALIDATION_COMMIT = 84e30b4
  (AG.CURRENT-CAPABILITY-RELEASE-VALIDATION-0 + CLOSEOUT-0)

ADB_SEMANTICS_AUDIT_EVIDENCE_COMMIT = 208df7b
  (AG.ADB-SEMANTICS-AND-LEGADO-WRITE-AUDIT-0, docs/evidence only)

ADB_GUIDANCE_PRODUCTION_COMMIT = 57461b8
  (AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1, ToolSpec guidance fix)

ADB_GUIDANCE_CLOSEOUT_EVIDENCE_COMMIT = 72d30e1
  (AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1.CLOSEOUT, docs/evidence only)

FREEZE_SOURCE_HEAD = 72d30e1
FREEZE_COMMIT = (this commit; see git tag ag-current-capability-release-baseline-v1)
FREEZE_TAG = ag-current-capability-release-baseline-v1
TAG_TARGET = FREEZE_COMMIT
```

Commit roles:
- `84e30b4` = capability validation baseline (tests + session + ToolResult restore)
- `208df7b` = audit evidence (no production change)
- `57461b8` = **the only production change** in this baseline chain (ToolSpec description)
- `72d30e1` = closeout evidence (no production change)

---

## 2. Frozen Architecture

```text
TopicSession (durable, Room DB = SSOT)
  └── Run (one user request, max 1 active per session)
        └── Agent Loop (max 8 tool steps per run)
              └── Tool Dispatch (Policy B: Realm + Intent)
                    ├── file (APP_SELF realm)
                    ├── network.fetch (public HTTPS GET)
                    └── android_shell (local shell / adb shell)
```

- **Room DB** = durable SSOT for sessions, messages, tool results
- **Core Session** = live materialization of TopicSession
- **TranscriptModel** = presentation projection (not SSOT)
- **AgentRuntime** = lifecycle owner (start, interrupt, stop)
- **ToolResult** = mechanism-level result (success = transport completed)

---

## 3. Frozen Capabilities

### 3.1 Agent / Session / Run

| Capability | Status |
|-----------|--------|
| TopicSession durable conversation | FROZEN / VALIDATED |
| Run = one user request | FROZEN / VALIDATED |
| Per-session: max 1 active Run | FROZEN / VALIDATED |
| Multi-session independent domains | FROZEN / VALIDATED |
| Room DB durable SSOT | FROZEN / VALIDATED |
| Historical session switching | FROZEN / VALIDATED |
| Switch-back transcript correctness | FROZEN / VALIDATED |
| Force-stop restore (no tool replay) | FROZEN / VALIDATED |
| ToolResult durable restore | FROZEN / VALIDATED |

### 3.2 Interrupt / Stop

```text
Host Stop → AgentRuntime.interruptActiveRun() → child Job cancel → Interrupted terminal state
```

```text
Interrupted != Failure
```

Known limitation: G-b1 (see Section 7).

### 3.3 android_shell

```text
MODEL_VISIBLE = YES
DEFAULT_MODE = Local one-shot
IDENTITY = AndroidAgent app execution identity
ADB_IDENTITY = uid 2000 Android shell principal
```

Embedded adb (frozen semantics):

```text
limited embedded CLI, NOT full desktop platform-tools adb

simple:      adb shell cat /path/file
wrong:       adb shell "cat /path/file"   (ONE remote word, exit 127)
shell syntax: adb shell sh -c 'ls | grep | wc -l'
adb devices:  unsupported
```

ToolResult (frozen semantics):

```text
success = true  →  execution mechanism completed (NOT command success)
exitCode == 0   →  command succeeded
exitCode != 0   →  command failed (read stdout/stderr for details)
```

Model guidance (frozen at `57461b8`):
- C1: embedded adb limitation
- C2: argv vs sh -c construction
- C3: exitCode-based judgment

Status: **FROZEN / ADB_GUIDANCE_GAP = CLOSED**

### 3.4 network.fetch

```text
MODEL_VISIBLE = YES
ACTION = fetch
CAPABILITY = HTTPS GET, public-network SSRF protection, redirect revalidation,
             bounded response, HTML→text extraction
NOT = Search, Browser, POST, JS, anti-bot, download manager
```

Status: **FROZEN**

### 3.5 file

```text
REALM = APP_SELF
ACTIONS = read, search, list, stat
SECURITY = canonical confinement, traversal rejection, outside-root rejection,
           symlink escape rejection, UTF-8, binary sniff, bounds
```

Validation status:
- `file.read` = FROZEN / autonomous-LLM validated
- `file.search` = IMPLEMENTED (no autonomous-LLM validation recorded)
- `file.list` = IMPLEMENTED (no autonomous-LLM validation recorded)
- `file.stat` = IMPLEMENTED (no autonomous-LLM validation recorded)

Status: **FROZEN** (read validated; others implemented)

---

## 4. Frozen Tool Routing (Policy B: Realm + Intent)

```text
APP_SELF bounded structured file read  → file
known HTTPS URL                        → network.fetch
process / command / pipeline / system  → android_shell
ADB-specific / uid=2000 / device path  → android_shell
dedicated tool incapable               → android_shell fallback
```

Explicitly NOT adopted: strict specialized-tool-first.

---

## 5. Validation Evidence

| Commit | Step | Key Results |
|--------|------|-------------|
| 84e30b4 | AG.CURRENT-CAPABILITY-RELEASE-VALIDATION-0 + CLOSEOUT-0 | Full test baseline PASS, session switch-back PASS, ToolResult restore PASS, force-stop restore PASS |
| 208df7b | AG.ADB-SEMANTICS-AND-LEGADO-WRITE-AUDIT-0 | ADB semantics verified, Legado external dir writable, bookSource.json = backup, production defect = NONE |
| 57461b8 | AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1 | Guidance fix + 5 unit tests + F1/F2/F3 real-LLM PASS |
| 72d30e1 | AG.ANDROID-SHELL-ADB-GUIDANCE-FIX-1.CLOSEOUT | success=true + exitCode=1 interpretation proven |

Evidence directories:
- `docs/evidence/ag_current_capability_release_validation_0/`
- `docs/evidence/ag_current_capability_release_validation_closeout_0/`
- `docs/evidence/ag_adb_semantics_and_legado_write_audit_0/`
- `docs/evidence/ag_android_shell_adb_guidance_fix_1/`
- `docs/evidence/ag_android_shell_adb_guidance_fix_1_closeout/`

---

## 6. Real-Work Evidence

Task: Legado + m.92sr.com book-source investigation

```text
REAL_WORK_AUTONOMY = STRONG
TOOL_ORCHESTRATION = STRONG
ERROR_RECOVERY = STRONG
SESSION_CONTINUITY = PASS
REAL_TASK_E2E = INCOMPLETE (8-step limit reached before full answer)
```

Proven:
- Agent Loop works on unknown real tasks
- android_shell natural selection works
- network.fetch natural selection works
- Multi-turn continuation works
- ToolResult context usable across long tasks

Exposed (now closed):
- ADB model guidance gap → CLOSED @ 57461b8 + 72d30e1

---

## 7. Known Limitations

| ID | Description | Status |
|----|-------------|--------|
| G-b1 | Long-running Local one-shot child process may survive Stop | DEFERRED / NON-BLOCKING |
| G-b2 | No generic mutation approval gate; model may issue state-changing commands without product-level approval | DEFERRED / KNOWN PRODUCT LIMITATION |
| G-b3 | No general Web Search / Browser / JS / POST / anti-bot capability | CAPABILITY BOUNDARY (not a defect) |
| G-b4 | MAX_TOOL_STEPS_PER_RUN = 8; complex tasks may need user continuation | KNOWN DESIGN LIMIT (not a bug) |

**Baseline positioning:** developer/internal capability baseline.
NOT a claim of safety for unrestricted end-user autonomy.

---

## 8. AT Consumer Identity

```text
AT_CONSUMER_VERSION = v0.4.3
AG ↔ AT production execution = CLOSED / PASS
```

AT v0.4.4 consumer update = **POST-FREEZE SEPARATE TASK**
(`AG.AT-V044-CONSUMER-UPDATE-0`)

---

## 9. Explicit Non-Claims

This baseline does **NOT** claim:

- All possible Android operations supported
- Full desktop adb compatibility
- General-purpose browser capability
- General Web search
- Arbitrary filesystem access
- Generic app-data mutation safety
- Generic mutation approval
- Long-running process termination completeness
- Unbounded autonomous run
- All FileTool actions autonomously LLM-validated
- Legado integration completed
- m.92sr.com book source investigation completed

---

## 10. Post-Freeze Change Policy

Any change affecting:

```text
AG Core contracts
Agent Runtime lifecycle
ToolResult semantics
Tool routing
android_shell behavior
ToolSpec model semantics
Session persistence
Stop/Interrupt semantics
AT consumer dependency
```

MUST:
1. Create a new task
2. Produce evidence
3. Not silently mutate the frozen baseline

Historical evidence files are **immutable**.
Live SSOT docs should be normalized.

---

## 11. Final Freeze Verdict

```text
CURRENT_CAPABILITY_RELEASE_BASELINE = FROZEN
ADB_GUIDANCE_GAP = CLOSED
EXITCODE_RESULT_GUIDANCE = CLOSED
RELEASE_BLOCKER = NONE

FREEZE_TAG = ag-current-capability-release-baseline-v1
TAG_TARGET = FREEZE_COMMIT

NEXT = AG.AT-V044-CONSUMER-UPDATE-0
STOP = YES
```
