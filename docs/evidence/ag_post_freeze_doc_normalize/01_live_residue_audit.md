# 01 — Live Residue Audit (AG.POST-I-PRE-UI-DOC-NORMALIZE-1)

Scan: docs/**/*.md,*.txt + AGENTS.md for live-residue patterns
(FREEZE CANDIDATE / POST-I closure audit / Device verification PENDING / 86 uncommitted /
M2 not yet synced / DOCUMENTED WORKTREE CHECKPOINT / FREEZE_COMMIT|TAG NOT CREATED /
worktree shows modified / NEXT = POST-I).

| Doc | Location | Old state | Current fact | Modified? | Reason |
|---|---|---|---|---|---|
| docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md | §11 Known Debt (L200,202) | M2/M4 listed as active debt | M2/M4 CLOSED by Pre-UI Git Freeze | YES (R1) | live freeze doc; M2/M4 no longer open |
| docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md | KNOWN DEBT (L69,71) | M2/M4 listed as active debt | M2/M4 CLOSED | YES (R2) | live handoff; also added Pre-UI Freeze Identity + full 557-test regression |
| docs/implementation/...-0_FINAL.md | §11 Recommended Next (L368,371) | TOPIC_SESSION_PERSISTENCE = CLOSED / FREEZE CANDIDATE; NEXT = POST-I closure audit | FROZEN; PRE_UI_CORE_GIT_FREEZE = COMPLETE; NEXT = UI + RELEASE PREPARATION | YES (R3) | live current-state section, superseded by §20/§21/§22 |
| docs/implementation/...-0_FINAL.md | §21 report MINOR_DEBT (L2001-2005) | M2 "not yet synced" / M4 "separate authorized task" (point-in-time) | M2/M4 subsequently CLOSED | YES (supersede note only) | point-in-time report kept; added NOTE that M2/M4 closed in §22 round (no historical rewrite) |
| docs/implementation/...-0_FINAL.md | §22 notes (L2169) | "Worktree shows this file as modified after the tag" | post-tag normalization is a separate docs-only commit; freeze tag immutable | YES (R4) | live note contradicted final WORKTREE_CLEAN=YES fact |
| docs/implementation/...-0_FINAL.md | top STATUS (L3) | had POST-I closure PASS + FROZEN + NEXT, but no Pre-UI Git Freeze | add PRE-UI CORE GIT FREEZE = COMPLETE + FREEZE_NAME | YES (top status sync) | live status must reflect the completed freeze |

HISTORICAL_RESIDUE_PRESERVED = YES — the following hits are point-in-time evidence and were
NOT modified:
- docs/evidence/ag_post_i_closure/06_ssot_residue_audit.md, 08_final_closure_matrix.md,
  ALL_EVIDENCE_CONSOLIDATED.md (closure-round evidence; "86 uncommitted", "FREEZE CANDIDATE"
  describe that round's state)
- docs/evidence/ag_pre_ui_core_freeze/06_freeze_candidate_matrix.md, ALL_EVIDENCE_CONSOLIDATED.md
  (pre-UI freeze-round evidence)
- docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md §19 (Phase I,
  DEVICE-I=DEFERRED), §21 report body, §22 report body (point-in-time final reports)
- docs/implementation/AG.TOPIC-SESSION-...-0_FINAL_保存1.md, -0_保存1.md (saved variants)
- docs/implementation/AG.V1-PRE-FREEZE-NORMALIZATION.md, v2-research copy (V1 freeze, unrelated)

Only LIVE current-state sections were modified. No mechanical global replace.
