# SSOT Residue Audit (live vs historical)

## Fixed this round (LIVE section)

- `AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md` §11 "Recommended Next"
  - BEFORE: "**Device verification** (`DEV-I1..I4`, NEXT) ... Device verification = PENDING"
  - AFTER: "TopicSession persistence subsystem = CLOSED / FREEZE CANDIDATE ... DEVICE-I =
    VERIFIED (§20) ... NEXT: POST-I ARCHITECTURE / IMPLEMENTATION CLOSURE AUDIT -> if PASS:
    UI + RELEASE PREPARATION"
  - Top STATUS line and §6 table were already updated in the DEVICE-I round to
    `DEVICE-I VERIFIED` / `COMPLETED (§20)`.

## Historical residue — deliberately NOT modified (point-in-time evidence)

All remaining PENDING/DEFERRED hits are inside historical phase reports, each superseded
by a later section of the same SSOT. Per task rule ("do not rewrite history; only live
current-state sections"), these stay as-is:

| Line | Text | Context | Superseded by |
|---|---|---|---|
| 262, 658 | "replace production Noop" | Phase F scope wording (the Noop WAS replaced in Phase F) | §12 Phase F report |
| 481, 630 | "Phase F pending, as frozen" | earlier-phase scope text | §12 |
| 782, 784 | "pending cancellation" | test-name descriptions (CANCEL_F3/F4) | n/a (test semantics) |
| 1147, 1155 | FAILURE_CLOSEOUT_STABLE_BOUNDARY = PENDING / quarantine deferred | Phase G report, resolved by NORMALIZE-1 | §14 NORMALIZE-1 |
| 1415 | DEVICE_G_VERIFICATION = DEFERRED (JVM/Robolectric only this round) | Phase G report | §20 DEVICE-I PASS |
| 1553-1554, 1582 | Phase I PENDING / PHASE_I_STATUS = NEXT (PENDING) | Phase G report's next-work table (point-in-time) | §19 Phase I report |
| 1712-1716 | DEVICE_I1..I4 = DEFERRED, DEVICE_I_VERIFICATION = DEFERRED | §19 Phase I report (point-in-time, written before device round) | §20 DEVICE-I PASS (all four = PASS, REAL_DEVICE_VERIFIED) |

## Architecture docs (docs/architecture/*.md)

Stale-claim scan (`Device verification PENDING`, `pending Phase F/G`) = 0 hits. The two
architecture freeze docs (AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0, AG.TOPIC-SESSION-DURABILITY-SEAM-
PREFLIGHT-0) are read-only inputs and contain no live-residue device wording.

## AGENTS.md

No device-pending / Phase-pending residue. AGENTS.md current-state section describes the
pre-persistence capability line (RUN-CONTROL, HOST-STOP, observability, tools) as frozen
facts — none of it is contradicted by Phase A-I or DEVICE-I (all those capabilities remain
VERIFIED). Note: AGENTS.md is the repo-level "WHAT IS TRUE NOW" doc; adding the TopicSession
persistence line there is a Controller/roadmap-level documentation decision and is OUT OF
SCOPE for this round (allowed-file list). Recorded as MINOR DEBT (doc sync candidate for the
next normalization round).

## Verdict

LIVE_SSOT_RESIDUE_CLEANED = YES (within allowed-file scope: FINAL SSOT live sections are
consistent; historical sections intentionally untouched)
