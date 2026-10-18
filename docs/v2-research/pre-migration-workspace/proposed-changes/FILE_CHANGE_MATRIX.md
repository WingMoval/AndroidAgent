# FILE CHANGE MATRIX (PROPOSAL)

AG.V2-0.ARCHITECTURE-RESEARCH — proposed V2 actions per V1 file/area.
Phases: V2_BASELINE | V2_REFACTOR | LATER_CAPABILITY | DOC_ONLY | NO_CHANGE

This is a PROPOSAL. Nothing is executed until Controller approval of the
migration matrix (gate M2).

| V1 File/Area | Problem | Proposed V2 action | Phase |
|---|---|---|---|
| ag-core/** | None. Pure Kotlin leaf; zero Android/AT/HTTP deps; imports only java.util.concurrent.* | COPY_EQUIVALENT (byte-identical sources; same tests 41) | V2_BASELINE |
| ag-runtime/** | None. Depends only on :ag-core + coroutines; owns single-active-Run, interruptActiveRun, structured cancellation, RunOutcome.Interrupted | COPY_EQUIVALENT (same tests 26) | V2_BASELINE |
| provider-openai-compatible/** | None. :ag-core-only dep; wire/parser/config isolated from transport | COPY_EQUIVALENT (same tests 59) | V2_BASELINE |
| provider-openai-compatible-jdk/** | None. JVM transport variant; no AT | COPY_EQUIVALENT | V2_BASELINE |
| tool-android-shell/** | None structurally. Correctly the ONLY AT consumer; uses only com.androidterminal.runtime.* stable surface (initialize/connectAuto/readinessState/disconnect/execute); one-shot only today | COPY_EQUIVALENT (same tests 16); persistent-terminal extension = separate LATER_CAPABILITY Step | V2_BASELINE |
| tool-network/** | None. Valid non-AT tool (HttpsURLConnection direct) | COPY_EQUIVALENT (same tests 78) | V2_BASELINE |
| tool-file/** | None. Valid non-AT tool (APP_SELF READ family) | COPY_EQUIVALENT (same tests 55) | V2_BASELINE |
| app/ (AGA host) | None structurally. Composition root constructs+registers concrete tools; Host holds no AT types; Stop/DebugBridge/Provider controls are presentation+composition, not execution ownership | COPY_EQUIVALENT (same tests 109) | V2_BASELINE |
| app/src/main/.../debug/DebugBridge.kt | (fixed in V1 e30d250) bridge runs now drive setBusy via onRunStateChanged | COPY (fix already in V1 HEAD) | V2_BASELINE |
| third_party/at-engine-handoff-v0/** | None. Bundle v0 (0.1.0) matches Handoff V1 capability snapshot; provenance = AT@726a441 + sourceTreeSha256; hash-verified; m2 wrapper is metadata only | COPY_EQUIVALENT (keep folder name; do NOT rename to handoff-v1 — see SSOT_RESIDUE R8) | V2_BASELINE |
| settings.gradle.kts / build.gradle.kts (root) | None | COPY_EQUIVALENT (module graph unchanged) | V2_BASELINE |
| tools/test-fixtures/**, tools/ag_debug.py, tools/ag_file_l2/3.py | None. Test infrastructure | COPY_EQUIVALENT | V2_BASELINE |
| docs/** (research/implementation/roadmap) | SSOT residue R1/R2/R3/R7 (coverage-matrix status, duplicate NEXT, hash line, AT-001/002 notes) | DOC_ONLY corrections in V1 tree (separate small Step); V2 copies docs verbatim at baseline | DOC_ONLY |
| AGENTS.md | None material (point-in-time test counts are historical by design) | DOC_ONLY (optional count note) | DOC_ONLY |
| upload/ | Export copies only | DO_NOT_MIGRATE (regenerate from V2 docs at release) | NO_CHANGE |
| Persistent raw terminal AG tool | Not a V1 file; new capability | LATER_CAPABILITY (requires FUTURE_TERMINAL_PREFLIGHT_QUESTIONS answers + TOOL-006 bounding first) | LATER_CAPABILITY |
| Screen-aware terminal tools | Not available in AT | DEFER (BLOCKED_BY_AT.EMULATOR-0) | LATER_CAPABILITY |
| Human-Agent shared session | DEFERRED | DEFER | LATER_CAPABILITY |

SUMMARY: NO V2_REFACTOR rows. Every production area is COPY_EQUIVALENT or
DOC_ONLY. The proposed V2 tree is structurally identical to V1.
