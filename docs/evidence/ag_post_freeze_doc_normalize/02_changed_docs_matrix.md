# 02 — Changed Docs Matrix (AG.POST-I-PRE-UI-DOC-NORMALIZE-1)

Source docs modified this round (LIVE normalization):
1. docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md          (R1: §11 M2/M4 -> Closed)
2. docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md                (R2: KNOWN DEBT M2/M4 -> Closed;
                                                             + Pre-UI Freeze Identity block;
                                                             + full 557-test regression)
3. docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-
   IMPLEMENTATION-0_FINAL.md                                 (R3: §11 -> FROZEN/NEXT=UI;
                                                             R4: §22 note fix;
                                                             §21 MINOR_DEBT supersede note;
                                                             top STATUS + Pre-UI Git Freeze)

Upload/ rolling snapshots synced (content-identical to source):
4. upload/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
5. upload/AG_POST_I_CLOSURE_HANDOFF.md
6. upload/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md

Carried from prior round (post-freeze docs-only, now included in this commit):
7. docs/evidence/ag_pre_ui_core_freeze/ALL_EVIDENCE_CONSOLIDATED.md   (new merged evidence)
8. upload/AG_POST_I_CORE_FREEZE_EVIDENCE_CONSOLIDATED.md              (new snapshot)
9. upload/AG_POST_I_CLOSURE_EVIDENCE_CONSOLIDATED.md                  (deleted: superseded by #8)

New evidence this round:
10. docs/evidence/ag_post_freeze_doc_normalize/00_baseline.txt
11. docs/evidence/ag_post_freeze_doc_normalize/01_live_residue_audit.md
12. docs/evidence/ag_post_freeze_doc_normalize/02_changed_docs_matrix.md
13. docs/evidence/ag_post_freeze_doc_normalize/03_final_verification.txt

NOT modified (frozen/historical, per task): upload/AG_POST_I_CLOSURE_EVIDENCE_CONSOLIDATED.md
(closure evidence — historical, left untouched); all evidence dirs except the new one.

GUARD: no *.kt / *.java / *.gradle / *.gradle.kts / AndroidManifest.xml / res/** / third_party/**
changed. DOCS_ONLY = YES.
