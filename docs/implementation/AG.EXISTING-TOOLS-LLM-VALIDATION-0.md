# AG.EXISTING-TOOLS-LLM-VALIDATION-0

**Date:** 2026-11-05
**Status:** CLOSED
**VERDICT:** FAIL (partial) — Network PASS, File FAIL

---

## Purpose

Validate that the two existing tools (`file.read`, `network.fetch`) can be
autonomously identified, called, and utilized by a real LLM, with ToolResults
entering the TopicSession and being recalled in the next turn.

---

## Provider / Model

```
PROVIDER = OpenRouter
MODEL = deepseek/deepseek-v4-flash-0731
```

---

## Results Summary

| Gate | Result | Notes |
|------|--------|-------|
| F1 File Tool Selection | **FAIL** | Model chose `android_shell` (ls+cat), not `file` tool |
| F2 File Observation Dependency | PASS | Token only in fixture file, not in any prompt |
| F3 File Next-Turn Recall | NOT_TESTED | Blocked by F1 FAIL |
| N1 Network Tool Selection | **PASS** | Model autonomously selected `network` tool (action=fetch) |
| N2 Network Observation Dependency | **PASS** | Title only in ToolResult, not in prompt |
| N3 Network Next-Turn Recall | **PASS** | 0 new toolcalls, correct title recalled |

---

## Network Fetch Closure (PASS)

Complete chain validated:

```
User prompt (URL only)
→ LLM sees ToolSpec
→ LLM autonomously selects network tool
→ NetworkToolImpl.fetch executes
→ ToolResult (HTTP 200, full HTML content)
→ LLM reads observation
→ Final answer contains correct title
→ Next turn: LLM recalls title without re-executing
```

Bilibili returned 200 with full content (mobile redirect to m.bilibili.com).
No SITE_SPECIFIC_LIMITATION, no fallback needed.

---

## File Read Failure (FAIL)

The model (deepseek-v4-flash-0731) selected `android_shell` twice:
1. `ls -la` (list directory)
2. `cat files/agent/llm_file_probe.txt` (read file)

It did NOT select the `file` tool despite it being available in the ToolSpec.

**Assessment:** This is a model tool-selection behavior, not a production bug.
The file tool exists and works (verified in prior production rounds). The
model simply prefers the shell for file operations.

**Impact:** The file.read tool cannot be declared "LLM-validated" until
either:
- A different model that reliably selects the file tool is tested, OR
- The file tool ToolSpec is improved (future step, NOT this round)

---

## Observation Dependency

Both tests satisfied the core principle:

- **File:** Token `FILE_3EA6B6461ABB` appeared ONLY in the fixture file.
  Not in user prompt, system prompt, tool descriptions, or filename.
- **Network:** Page title appeared ONLY in the fetched HTML content.
  Not in user prompt (URL only provided).

---

## Session Scope

- Same TopicSession only (Turn 1 → Turn 2)
- No cross-session, process restore, or Activity recreate testing
- No new session features

---

## Production Code

```
PRODUCTION_CODE_CHANGED = NO
AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGED = NO
AT_CHANGED = NO
TOOLSPEC_MODIFIED = NO
```

Only changes: handoff doc normalization + evidence/implementation docs.

---

## Tests

```
TEST_TOTAL = 220
TEST_PASS = 220
TEST_FAIL = 0
BUILD = PASS
```

---

## Known Limitations

- File tool selection is model-dependent; current model (deepseek-v4-flash)
  does not reliably choose file over shell
- No contrast matrix tested (2 positive cases only)
- Bilibili test uses a single URL; no multi-site validation
- DebugBridge event stream shows `name=?` for tool_call events (parsing
  limitation in debug protocol, not a production issue)

---

## Evidence

`docs/evidence/ag_existing_tools_llm_validation_0/` (14 files)

---

## Next

CONTROLLER_REVIEW

Decision needed:
1. Accept file.read as "tool works but model doesn't select it" (model limitation)
2. Schedule a future step to improve file tool ToolSpec visibility
3. Test with a different model that may select file tool more reliably

STOP.
