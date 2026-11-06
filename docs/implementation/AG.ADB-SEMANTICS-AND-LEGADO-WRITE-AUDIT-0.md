# AG.ADB-SEMANTICS-AND-LEGADO-WRITE-AUDIT-0

**Date:** 2026-11-07
**Type:** Audit / Investigation only (no capability, no production change)
**Status:** CLOSED
**VERDICT:** PASS_WITH_FINDINGS

---

## Purpose

A real AndroidAgent work session (configuring Legado book sources) produced two
unverified claims that this audit re-examines with source + real-device evidence:

1. **How the AT embedded `adb` CLI interprets the arguments after `adb shell`** —
   quoting, redirection, pipes, and why `adb shell cat <path>` works but
   `adb shell "cat <path>"` does not.
2. **Whether Legado's external `bookSource.json` is a writable runtime SSOT**,
   or just a backup/export mirror, and what the real save path is.

No production code, AT code, or Legado data was modified.

---

## Q1 — Does `adb shell cat <path>` work?

**PASS.** Gate C1: exit 0, full 344-byte `bookGroup.json` content returned
through the embedded adb CLI.

## Q2 — Why does `adb shell "cat <path>"` fail?

**Root cause = two-pass shell parsing (D3 remote-shell quoting via the argv model).**

Chain (source-confirmed, `AdbCliArgs.kt` AT.CLI-0.1 + `LocalCliBootstrap` +
`AtExecutionAdapter.executeLocal` + `adb_engine_jni.cpp`):

```
LLM "adb shell cat /path"
  → outer /system/bin/sh parses ONCE → adb() fn argv = ["shell","cat","/path"]
  → adb() forwards "$@" to app_process → AtCliMain
  → serializeToRemoteCommand(["cat","/path"]) = 'cat' '/path'   (each elem single-quoted)
  → bridge → AdbBackend.executeShell → JNI "shell,v2,raw:'cat' '/path'"
  → adbd remote /system/bin/sh parses → runs: cat /path   ✓
```

For the quoted form, the outer sh collapses `"cat /path"` into **one** argv
element, so AtCliMain emits **one** single-quoted token `'"cat /path"'`, and the
remote sh tries to exec an executable literally named `cat /path` → **exit 127**
(command not found). Gate C2 reproduced this exactly; the stderr names the whole
`cat /path` as the not-found target (the argv-mis-serialization signature, not a
permission or missing-binary error). C1 (same file, unquoted) succeeds — proving
the failure is quoting, not the filesystem.

`adb shell sh -c 'cat /path'` (Gate C3) and `adb shell dd if=... bs=1 count=512`
(Gate C4) both PASS — the correct forms for shell features and the form the real
task used successfully.

## Q3 — Can the ADB shell write to Legado's external dir?

**YES — full CREATE/WRITE/READ/DELETE.** Gate E (probe file only, real Legado
files untouched):

| Op | Result |
|----|--------|
| CREATE (`touch`) | PASS (exit 0, `-rw-rw---- shell ext_data_rw`) |
| WRITE (`cp` of staged token) | PASS |
| READBACK | PASS (exact token `AG_LEGADO_WRITE_PROBE_7F3A91`) |
| DELETE (`rm`) | PASS (confirmed gone) |

The previous LLM conclusion **"Android/data is not writable" is DISPROVEN.**

## Q4 — Is editing `bookSource.json` a valid save path?

**No.** Runtime book sources live in a **private Room DB** (`@Entity
"book_sources"`, under `/data/data/.../databases/`, shell-invisible).
`bookSource.json` (44 MB) is the **export / local-backup** JSON array whose
schema matches the `BookSource` Room entity 1:1; Legado consumes it only on
explicit import.

`BOOKSOURCE_JSON_ROLE = EXTERNAL_BACKUP_EXPORT_OR_MIRROR`
`DIRECT_EDIT_BOOKSOURCE_JSON_RECOMMENDED = NO`

The **supported** save surfaces are:
- **ContentProvider** `io.legado.app.release.readerProvider`
  (URIs `bookSource/insert`, `bookSources/query`, …) — gated by the
  **signature** permission `io.legado.READ_WRITE`.
- **In-app Web API** `http://127.0.0.1:<port>/saveBookSource` — requires the
  user to enable "Web 服务"; not currently running.

Both are unreachable by the ADB shell identity (uid 2000 holds no signature
permission; Web service is off). So on this device the agent cannot save a book
source through a public surface, and raw-editing the JSON is not a valid path.

`DIRECT_LEGADO_SAVE_SURFACE = YES` (exists) but **not reachable** by the agent.

---

## Side audit — ToolResult success semantics

`TOOLRESULT_SUCCESS_MEANING = tool invocation MECHANISM success`
(`COMPLETED → success=true` regardless of exitCode; real result in the output
payload). Gate C2 observed `success=true` + `exitCode=127` — the exact shape
that misled the original session. `MODEL_AMBIGUITY_RISK = YES` (contract is
correct and documented, but `success=true`+nonzero exit is an LLM trap).

---

## Findings

| # | Finding | Attribution | Action |
|---|---------|-------------|--------|
| 1 | `adb shell "<multi-word>"` collapses to one remote word (exit 127) | **TOOL GUIDANCE GAP** (secondary: LLM misuse). **AT defect = NO** — behavior is correct, documented, unit-tested (AT.CLI-0.1). | Minimal ToolSpec guidance note (NOT a code change): warn that `adb shell "cmd args"` is wrong; use unquoted argv or `adb shell sh -c 'cmd'` for redirection/pipes. **Controller decision.** |
| 2 | "Android/data not writable" + "bookSource.json = SSOT" | **LLM PREMATURE CONCLUSION** — both inferred from quoting-artifact failures + file name. Writability DISPROVEN (Gate E); SSOT claim unsupported (Gate F/G). | No code change. |

`PRODUCTION_DEFECT_FOUND = NO`

---

## Scope

```
AG_PRODUCTION_CHANGED = NO
AT_CHANGED = NO
LEGADO_REAL_DATA_CHANGED = NO
NO_LEFTOVER_PROBE = YES
ROOT/UNINSTALL/CLEAR-DATA = NONE
NO_NEW_CAPABILITY / NO_TOOLSPEC / NO_ROOM / NO_LEGADO_TOOL = NO
```

---

## Evidence

`docs/evidence/ag_adb_semantics_and_legado_write_audit_0/` (19 files:
preflight, execution chain, CLI parser, C1-C4 read tests, E1-E5 write probe,
Legado package surface, storage model, bookSource role, save surface,
ToolResult semantics, scope audit, final verdict).

---

## Final

```
VERDICT = PASS_WITH_FINDINGS
Q1..Q4 = all answered (evidence or explicit classification)
CURRENT_CAPABILITY_RELEASE_BASELINE = READY (unchanged)
NEXT = CONTROLLER_REVIEW (minimal adb-shell quoting tool-guidance note)
STOP = YES
```
