# AG.ARCH-DOC-AUDIT-0.NORMALIZE-1 — Live SSOT Normalization / Audit Correction / Documentation Closeout

## Status

CLOSED / PASS

```text
TYPE   = DOCS-ONLY (live-SSOT normalization + roadmap reconciliation + audit correction)
REPO   = D:\codebank\AndCode\AndroidAgent-V2
DATE   = 2026-10-28
HEAD   = edfc603eadb0067bd010c04ca26b4bc28de2e685 (start; main)
EVIDENCE = docs/evidence/ag_arch_doc_audit_0_normalize_1/ (00-09)
```

本轮原则：**让文档追上已经发生的真实开发**，而不是追上 AndroidTerminal
正在开发中的未来状态。CODE TRUTH = LIVE SSOT TRUTH = ROADMAP CURRENT STATUS。

---

## 1. Baseline

```text
START_BRANCH   = main
START_HEAD     = edfc603eadb0067bd010c04ca26b4bc28de2e685
               (git rev-parse 实测；上一轮 audit 主文档中 ...de285
                为手写 SHA typo — C0)
WORKTREE_BEFORE =
  2 tracked upload 文件被外部删除（非本轮操作，未 staged/未还原/未隐藏）
  + 上一轮审计/收尾 untracked 文档
```

## 2. Controller Corrections（本轮正式记录）

```text
C0  Audit HEAD SHA transcription typo
    REPORTED = ...de285 / VERIFIED = ...de2e685
    处理 = 修正 audit 主文档 obvious typo + §19 Errata 记录
D1  AG Core 生产依赖真相（AGENTS.md 旧文本）        -> 已归一化
D2  Agent Runtime 依赖真相（JSON 澄清）             -> 已归一化
D3  POST-I freeze = PRE-UI 冻结（非失效）           -> 保持原状，语义澄清
D4  AGENTS.md AT 快照陈旧（V3/c9cee21/DRAFT）       -> 已归一化（取自
    canonical handoff 文件，未复制完整 AT 状态）
D5  Master Roadmap live-state drift（Controller 新增，
    原 audit 漏检；STATE-003/006、HOST-010 = DISCOVERED 与实现真相冲突）
    SEVERITY = MEDIUM（调度风险，非架构代码违规）   -> 已归一化
```

## 3. Files Changed

```text
MODIFIED (tracked):
  AGENTS.md                                   live SSOT 归一化（4 处编辑）
  docs/roadmap/AndroidAgent_Master_Roadmap.md 状态对账（registry/coverage/
                                              completed baseline/queue/trail）
ADDED (untracked, 本轮 + 上一轮产物):
  docs/audit/AG.ARCH-DOC-AUDIT-0.md           +§19 Controller Review Addendum
  docs/evidence/ag_arch_doc_audit_0/          原 audit 17 份 evidence
  docs/evidence/ag_arch_doc_audit_0_normalize_1/  本 evidence 00-09
  docs/implementation/AG.ARCH-DOC-AUDIT-0.NORMALIZE-1.md (本文件)
  docs/implementation/AG.UI-TRANSCRIPT-CARDS-1.CLOSEOUT-MERGE-0.md
  upload/ (11 份审计 + 关键架构导出副本)
REPORTED (not part of this Step):
  upload/AG.UI-TRANSCRIPT-CARDS-1.md / .evidence.txt = 外部删除（tracked D）
```

## 4. AGENTS.md Normalization（D1/D2/D4 + UI 登记）

```text
1. D1/D2: "no production dependency beyond the Kotlin standard library"
   替换为真实状态：
   - AG Core 保持 pure JVM / platform-independent；唯一当前生产依赖 =
     kotlinx-coroutines-core（授权 Run-Control/durability closeout，
     最小 NonCancellable 需求）；禁入清单不变（Android/Room/AT/app/
     concrete Tool/Provider/HTTP-wire）。
   - 新增 Runtime 段落：pure JVM / host-neutral / Android-free / AT-free /
     Room-free；当前依赖 = coroutines + serialization-json（TopicSession
     MessageSerializer，storage-neutral）。明确区分：
     "Persistence serialization JSON != provider wire JSON"。
2. D4: "Current integration snapshot" 块 = canonical handoff 文件的 V4 值
   （AT_V4_CONSUMER_HANDOFF / 7216d0e / FROZEN / VENDORED_AAR debug /
   :integration-at 单一消费点 / ONE_SHOT model-visible / V0+V3 SUPERSEDED /
   NEXT = POST_V4_RELEASE_VALIDATION）+ 一句克制的 upstream 说明
   （AT 上游在建能力 ≠ AG current baseline，除非正式 handoff/consumer
   freeze/sync Step）。不复制完整 AT 状态。
3. D4: "Repository identity" 块同步 V4/7216d0e + 指针引用；
   NEXT = CONTROLLER_DECISION。
4. UI 登记（task §19，what-is-true-now 简洁条目）：
   AG.UI-SHELL-1 = CLOSED；AG.UI-TRANSCRIPT-CARDS-1 = CLOSED
   （cards + per-block actions + TopicSession browser；C1-C9/S1-S10 PASS；
   410/0；DB schema 未动；Core 未重开；Retry = Retry-as-New-Turn；
   Rewind/Redo/Fork/Checkpoint = NOT IMPLEMENTED；commit edfc603）。
   AGENTS.md 未被改成 step 日志。
```

## 5. Roadmap Normalization（D5）

```text
Registry:   STATE-003 DISCOVERED -> VERIFIED（真实持久化 note）
            STATE-006 DISCOVERED -> VERIFIED（真实 resume note；
                        明示 Fork/Share/Export 不在此 ID 内）
            HOST-010  DISCOVERED -> VERIFIED（AG.UI-TRANSCRIPT-CARDS-1，
                        S1-S10 真机 PASS）
            HOST-011  保持 DISCOVERED（复合能力，不得因 Resume 完成而关闭）；
                        note 更新为 "Resume path exists...; Fork/Share/
                        Export remain pending — NOT closed"
Coverage:   3 行 mirror 同步（STATE-003/006、HOST-010）
Baseline:   Completed Baseline += STATE-003, STATE-006, HOST-010
            （仅 3 个有证据的 ID，不批量加入）
Dep graph:  边不变；仅 status consequence（005/014 的 re-entry 条件
            "STATE-003 persistence done" 已满足，二者本身状态不动）
Queue:      CURRENT = AG.ARCH-DOC-AUDIT-0.NORMALIZE-1
            NEXT = CONTROLLER_DECISION; NEXT_OPEN_AUTHORIZED = NO
            （旧 V3 时代队列文本标记为 "Historical queue (superseded)"，
            保留 provenance）
            LATER 中 "STATE-003/006" 条目移除（已 VERIFIED）
Trail:      新增 NORMALIZE-1 小节（old -> new -> reason，7 行）
            历史 Trail 旧值全部保留
Stable ID:  无发明 / 无复用 / 无改名
Retry/Rewind: CORE-007 = RESEARCH_REQUIRED、STATE-001 = RESEARCH_REQUIRED、
            STATE-002 = DISCOVERED（NOT IMPLEMENTED）、Redo/Fork 不变 —
            不因 persistence 完成而提升
```

## 6. AT Current-Pointer Rule

```text
AG 当前正式消费的 AT 仍由 docs/integration/AT_TO_AG_HANDOFF_CURRENT.md
决定（本轮只读，未修改）：AT_V4_CONSUMER_HANDOFF / 7216d0e /
AT.V4-CONSUMER-FREEZE-V1 / FROZEN consumer boundary / vendored AAR。
AT 上游在建能力：NOT COMPLETE / NOT HANDED OFF / NOT AG-CONSUMED /
NOT AG-FROZEN -> 一律不写入 AG current。仅保留一句克制说明。
AT_SYNC_PERFORMED = NO · AT_UNFINISHED_UPSTREAM_IMPORTED = NO
```

## 7. Historical-Doc Preservation

```text
docs/architecture/AndroidAgent_总体架构.md = NO CHANGE
  （ag-0 时代 FROZEN 快照；"Core only stdlib" / "Runtime no JSON" 在当时
   为真，后被授权 Step 演进覆盖；仓库无 supersession-note 规范 ->
   按 fallback 不修改；supersede 事实记录在 audit §19 / drift matrix /
   本文件）
冻结架构文档（Capability_Map_v1.0 / POST-I freeze / TopicSession freeze /
V1 freeze / baseline freezes）= 全部 NO CHANGE
POST-I freeze 保持 PRE-UI 身份；UI 世代仅扩展 Host/Product，
未重开 Core/Runtime 架构
```

## 8. No-Freeze Declaration

```text
NORMALIZATION_COMPLETE = YES
NEW_ARCHITECTURE_FREEZE = NO
NEW_PRODUCT_FREEZE      = NO
NEW_AT_FREEZE           = NO
POST_I_FREEZE_REMAINS_VALID_FOR_NON_UI_ARCHITECTURE = YES
（未创建 POST-UI / FINAL / V2-PRODUCT 任何 freeze）
```

## 9. Git Closeout

```text
git diff --check       = PASS（无 whitespace 错误）
NON_DOC_PRODUCTION_DIFF = NONE
ROADMAP consistency scan = PASS（live 位置全部一致；旧值仅存于
                               历史 Update Trail，按设计保留）
COMMIT = 见 Final Report
TAG = NO · PUSH = NO
```

## 10. Final Report

```text
AG.ARCH-DOC-AUDIT-0.NORMALIZE-1 FINAL REPORT

VERDICT =
PASS

BRANCH =
main

START_HEAD =
edfc603eadb0067bd010c04ca26b4bc28de2e685

FINAL_HEAD =
(见 commit 后 git rev-parse；docs-only atomic commit)

WORKTREE_BEFORE =
DIRTY (2 tracked upload files deleted externally + prior untracked docs)

WORKTREE_AFTER =
CLEAN (Step files committed) — 外部删除的 2 个 upload 文件不在本轮
staged 范围内，处理见下

MODE =
DOCS_ONLY

PRODUCTION_CODE_CHANGED =
NO

TEST_CODE_CHANGED =
NO

GRADLE_CHANGED =
NO

DB_CHANGED =
NO

ARCHITECTURE_FREEZE_CREATED =
NO

AT_SYNC_PERFORMED =
NO

AT_UNFINISHED_UPSTREAM_IMPORTED =
NO

CURRENT_AT_POINTER =
docs/integration/AT_TO_AG_HANDOFF_CURRENT.md (UNMODIFIED, canonical)

CURRENT_AT_FREEZE =
AT.V4-CONSUMER-FREEZE-V1 (source baseline 7216d0e; FROZEN consumer
boundary; vendored AAR debug; NEXT sync = POST_V4_RELEASE_VALIDATION)

AUDIT_SHA_TYPO =
CORRECTED (typo fixed in audit doc + Errata recorded in §19)

D1_CORE_DEPENDENCY_DRIFT =
RESOLVED_IN_LIVE_SSOT

D2_RUNTIME_DEPENDENCY_DRIFT =
RESOLVED_IN_LIVE_SSOT

D3_PRE_UI_SCOPE =
EXPECTED (POST-I remains PRE-UI by identity; no change made)

D4_AT_SNAPSHOT_DRIFT =
RESOLVED

D5_ROADMAP_STATUS_DRIFT =
RESOLVED

STATE_003_BEFORE =
DISCOVERED ("Session in-memory only; storage model NOT frozen")

STATE_003_AFTER =
VERIFIED (Room/SQLite durable TopicSession, DB v1, chain verified,
foundation FROZEN)

STATE_006_BEFORE =
DISCOVERED

STATE_006_AFTER =
VERIFIED (restore + restart/process-death resume + hydration +
re-projection; FULL_USER_VISIBLE_RESUME real-device verified)

HOST_010_BEFORE =
DISCOVERED ("not started")

HOST_010_AFTER =
VERIFIED (AG.UI-TRANSCRIPT-CARDS-1: list + last_used + highlight +
switch; S1-S10 real-device PASS)

HOST_011_STATUS =
DISCOVERED (unchanged; note updated: Resume path exists;
Fork/Share/Export remain pending — NOT closed)

RETRY_STATUS =
IMPLEMENTED = Retry-as-New-Turn (unchanged)

REWIND_STATUS =
NOT IMPLEMENTED (STATE-002 DISCOVERED; CORE-007 RESEARCH_REQUIRED;
unchanged — no promotion)

AGENTS_UPDATED =
YES

ROADMAP_UPDATED =
YES

ROADMAP_COVERAGE_SYNC =
PASS

ROADMAP_COMPLETED_BASELINE_SYNC =
PASS (STATE-003, STATE-006, HOST-010 added)

ROADMAP_QUEUE =
CURRENT = AG.ARCH-DOC-AUDIT-0.NORMALIZE-1; NEXT = CONTROLLER_DECISION;
NEXT_OPEN_AUTHORIZED = NO; stale V3-era queue marked historical

NEXT_OPEN_AUTHORIZED =
NO

HISTORICAL_ARCH_DOC_REWRITTEN =
NO

FROZEN_ARCHITECTURE_DOC_CHANGED =
NO

POST_I_FREEZE_STATUS =
UNCHANGED / VALID (PRE-UI, authoritative for non-UI architecture)

GIT_DIFF_CHECK =
PASS

NON_DOC_DIFF =
NONE (the 2 pre-existing upload deletions are external, reported,
not staged)

COMMIT =
YES

COMMIT_SHA =
(see git rev-parse HEAD post-commit)

COMMIT_MESSAGE =
docs: normalize post-UI architecture and roadmap SSOT

TAG =
NO

PUSH =
NO

FINAL_WORKTREE =
CLEAN (Step scope) — external upload deletions reported separately

NEXT =
CONTROLLER_DECISION

STOP =
YES
```
