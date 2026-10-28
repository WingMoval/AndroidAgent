# AG.UI-TRANSCRIPT-CARDS-1.CLOSEOUT-MERGE-0 — Final Closeout / Atomic Commit / Integration

## Status

CLOSED / PASS

日期: 2026-10-28
仓库: D:\codebank\AndCode\AndroidAgent-V2
设备: HONOR ALI-AN00 (APLMUT4312005935)

---

## 1. Scope

本轮不是新功能开发。目标：

```
AG.UI-TRANSCRIPT-CARDS-1 PASS
    → final diff audit
    → SSOT closeout
    → atomic commit
    → integrate into canonical V2 branch
    → post-merge regression
    → clean baseline
```

---

## 2. Git Baseline

```
START_BRANCH = main
START_HEAD   = b7dbc0491eb1bcb0fb07f0daf7f8a4919a9e67bd
REMOTE       = (none)
CANONICAL_TARGET_BRANCH = main (current branch IS canonical)
MERGE_REQUIRED = NO
```

Git log (pre-commit):

```
b7dbc04 (HEAD -> main) docs: normalize post-freeze live SSOT
f640ed0 (tag: AG.POST-I-CORE-FREEZE-V1) freeze: establish post-I pre-UI AG core baseline
a6eea61 AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: close complete AT V3 consumer subsystem
```

---

## 3. Dirty Worktree Analysis

`git status --short` 显示 101 个文件变更，分为两组：

### EXPECTED — AG.UI-TRANSCRIPT-CARDS-1

| 文件 | 变更 |
|------|------|
| `ag-runtime/.../AgentRuntime.kt` | +switchSession (additive) |
| `ag-runtime/.../persistence/TopicSessionStore.kt` | +listSessions (additive) |
| `app/build.gradle.kts` | +recyclerview 1.3.2 |
| `app/.../ChatActivity.kt` | RecyclerView wiring, retrySend, editAndResend, showSessionSwitcher |
| `app/.../SessionHolder.kt` | +topicSessionStoreRef accessor |
| `app/.../persistence/Daos.kt` | +listAll query |
| `app/.../persistence/RoomTopicSessionStore.kt` | +listSessions impl |
| `app/.../TranscriptCardAdapter.kt` | NEW |
| `app/.../item_transcript_card.xml` | NEW |
| `app/.../activity_chat.xml` | RecyclerView replaces TextView |
| `app/test/.../HostStopStateTest.kt` | NoopStore +listSessions |
| `app/test/.../StoreMessageDurabilityTest.kt` | ScriptedStore +listSessions |
| `docs/implementation/AG.UI-TRANSCRIPT-CARDS-1.md` | NEW |
| `docs/evidence/ag_ui_transcript_cards_1/` (13 files) | NEW |
| `upload/AG.UI-TRANSCRIPT-CARDS-1.md` | NEW |
| `upload/AG.UI-TRANSCRIPT-CARDS-1.evidence.txt` | NEW |

### EXPECTED — AG.UI-SHELL-1 (co-committed, shared files)

| 文件 | 变更 |
|------|------|
| `app/.../HostActions.kt` | NEW (send/stop/newSession/listSessions/switchSession/providerSwitch) |
| `app/.../ModelCatalog.kt` | NEW |
| `app/.../ModelDiscovery.kt` | NEW |
| `app/.../ProviderProfile.kt` | NEW |
| `app/.../SafResource.kt` | NEW |
| `app/.../DeviceLocalProviderConfig.kt` | +ProviderNode enum |
| `app/.../debug/DebugBridge.kt` | +automation commands |
| `app/.../debug/DebugProtocol.kt` | +stop/new_session/provider_switch/snapshot |
| `app/.../strings.xml`, `themes.xml`, `colors.xml`, `dimens.xml` | UI resources |
| `app/.../dialog_provider_settings.xml` | NEW |
| `app/.../drawable/ws_*.xml` (7 files) | NEW |
| `app/test/.../HostActionsTest.kt` | NEW |
| `app/test/.../WorkspaceShellStateTest.kt` | NEW |
| `app/test/.../DeviceLocalProviderConfigTest.kt` | updated |
| `app/test/.../DebugProtocolTest.kt` | updated |
| `docs/implementation/AG.UI-SHELL-1.md` | NEW |
| `docs/evidence/ag_ui_shell_1/` (22 files) | NEW |
| `docs/evidence/ag_ui_baseline_audit_1/` (17 files) | NEW |
| `docs/audit/AG.UI-BASELINE-AUDIT-1.md` | NEW |
| `docs/design/AndroidAgent_V2_VNext_UI_设计基线.md` | NEW |
| `tools/ui_automation/` (2 files) | NEW |
| `upload/AG.POST-I-*.md` (4 files) | DELETED (superseded) |

### 重叠文件 (无法安全拆分)

`ChatActivity.kt`, `SessionHolder.kt`, `activity_chat.xml`, `build.gradle.kts`,
`Daos.kt`, `RoomTopicSessionStore.kt` 同时包含两个 Step 的修改。

**结论**: 两个 Step 一起 co-commit，不拆分。

---

## 4. Unexpected Diff Gate

```
ag-core/        = NO CHANGE
provider-*/     = NO CHANGE
tool-*/         = NO CHANGE
integration-at/ = NO CHANGE
AT handoff      = NO CHANGE
DB schema       = NO CHANGE
Room migration  = NO CHANGE
```

```
AG_CORE_CHANGE = NO
AG_RUNTIME_CHANGE = ADDITIVE_SESSION_SWITCH_ONLY
TOPIC_SESSION_STORE_CHANGE = ADDITIVE_LIST_ONLY
DB_SCHEMA_CHANGE = NO
TOOL_CHANGE = NO
PROVIDER_CHANGE = NO
AT_CHANGE = NO
```

---

## 5. Retry Final Audit

源码确认路径:

```
TranscriptCardAdapter.onRetry
    → ChatActivity.retrySend(text)
        → HostActions.send(text)
            → AgentRuntime.send(text)
```

- `findOriginUserText(position)`: 向前扫描找 nearest User block
- UserCard: 直接 `onRetry(block.text)`
- Assistant/Failure/Interrupted: `findOriginUserText` → `onRetry(origin)`
- Active Run: `isBusy()` → Retry button `View.GONE`

确认:
- NO history delete
- NO history mutation
- NO rewind
- NO card-only fake deletion
- NO second active Run

```
RETRY_SEMANTICS = RETRY_AS_NEW_TURN
RETRY_SEMANTICS_CHANGED = NO
DISPLAY_ONLY_REDO_ADDED = NO
REWIND_IMPLEMENTED = NO
REDO_IMPLEMENTED = NO
```

### Retry UI Terminology

按钮: ↻ icon (`ic_menu_rotate`)
contentDescription: "Retry"
无中文"重做"文字。
NO CHANGE REQUIRED。

---

## 6. Session Browser Final Audit

```
TopicSessionStore.listSessions()  = additive query only
AgentRuntime.switchSession()      = additive switch seam
DB_SCHEMA_CHANGE = NO
MIGRATION = NO
```

UI 路径确认:

```
UI tap
  → HostActions.switchSession
  → AgentRuntime.switchSession
  → restoreExistingSession (load + repair + hydrate)
  → projectCurrentHistory
  → Transcript Cards update
```

非: `UI → DAO → UI transcript`。SSOT 保持。

---

## 7. Secret Scan

```
git diff:        0 matches (sk-or-v1-*, sk-*, Bearer *)
untracked .kt:   0 matches
untracked .py:   0 matches
untracked .json: 0 matches
SECRET_SCAN = PASS
```

---

## 8. Regression

### Pre-commit

```
.\gradlew :app:test        → 410 tests, 0 failures
.\gradlew :app:assembleDebug → BUILD SUCCESSFUL
```

### Post-commit

```
.\gradlew :app:test        → BUILD SUCCESSFUL (UP-TO-DATE)
.\gradlew :app:assembleDebug → BUILD SUCCESSFUL
TESTS = 410 / 0
```

---

## 9. Atomic Commit

```
COMMIT = YES
SHA    = edfc603eadb0067bd010c04ca26b4bc28de2e685
BRANCH = main
FILES  = 101 changed, 13870 insertions(+), 3459 deletions(-)
```

Commit message:

```
AG.UI-TRANSCRIPT-CARDS-1 + AG.UI-SHELL-1: transcript cards, session browser,
provider settings, shell UI

Track A (Transcript Cards):
- RecyclerView replaces single TextView (TranscriptCardAdapter)
- 6 card types: User/Thought/Assistant/ToolExecution/Failure/Interrupted
- Per-block Copy, Retry (as New Turn), Edit & Resend, collapse, native selection

Track B (Session Browser):
- TopicSessionStore.listSessions() (additive)
- AgentRuntime.switchSession() (additive)
- Sidebar session switcher dialog with current highlight

AG.UI-SHELL-1 (co-committed, shared files):
- Provider profile system (ProviderProfile, ModelDiscovery, ModelCatalog)
- Debug bridge automation (stop/new_session/provider_switch/snapshot)
- Sidebar layout, composer, workspace shell
- SAF picker, Add API dialog

C1-C9 PASS, S1-S10 PASS, TESTS 410/0, ASSEMBLE PASS
DB_SCHEMA_CHANGE=NO, AG_CORE_CHANGE=NO, AT_CHANGE=NO
```

---

## 10. Merge

```
MERGE_REQUIRED = NO
MERGE_STATUS   = ALREADY_ON_CANONICAL_BRANCH
```

当前分支 `main` 即 canonical V2 development branch。Commit 直接在 `main` 上。

---

## 11. Post-Commit Verification

```
git rev-parse HEAD                              → edfc603eadb0067bd010c04ca26b4bc28de2e685
git status --short                              → (empty) CLEAN
git merge-base --is-ancestor edfc603 HEAD       → exit 0
```

---

## 12. Tag / Push

```
TAG  = NO (no tag convention in V2 SSOT for UI steps)
PUSH = NO
```

---

## 13. Implementation Doc Status

`docs/implementation/AG.UI-TRANSCRIPT-CARDS-1.md`:

```
STATUS = CLOSED
VERDICT = PASS

C1-C9 = PASS (全部真机验证, OpenRouter deepseek-v4-flash-0731)
Session Browser E2E = PASS (S1-S10)
TESTS = 410 / 0
ASSEMBLE_DEBUG = PASS
DEVICE_VALIDATION = PASS

Retry semantics = Retry as New Turn
Rewind = NOT IMPLEMENTED
Redo = NOT IMPLEMENTED
Display-only fake rewind = NOT IMPLEMENTED
```

---

## 14. AGENTS.md Update

AGENTS.md 无 "Current UI State" 或 step registry 区域。
AG.UI-SHELL-1 / AG.UI-TRANSCRIPT-CARDS-1 无既有条目。
NO UPDATE NEEDED。

---

## 15. Final Report

```text
VERDICT = PASS

START_BRANCH = main
START_HEAD = b7dbc0491eb1bcb0fb07f0daf7f8a4919a9e67bd

CANONICAL_TARGET_BRANCH = main

STEP_STATUS_BEFORE = PASS_UNCOMMITTED
STEP_STATUS_AFTER = CLOSED / COMMITTED

DIFF_AUDIT = PASS
EXPECTED_FILES_ONLY = NO (co-committed with AG.UI-SHELL-1)
UNRELATED_DIRTY_FILES = (none beyond the two expected steps)

RETRY_UI_LABEL = ↻ (contentDescription="Retry")
RETRY_SEMANTICS = RETRY_AS_NEW_TURN
RETRY_SEMANTICS_CHANGED = NO
DISPLAY_ONLY_REDO_ADDED = NO
REWIND_IMPLEMENTED = NO
REDO_IMPLEMENTED = NO

AG_CORE_CHANGED = NO
AG_RUNTIME_CHANGE = ADDITIVE_SESSION_SWITCH_ONLY
TOPIC_SESSION_STORE_CHANGE = ADDITIVE_LIST_ONLY
DB_SCHEMA_CHANGE = NO
TOOL_CHANGED = NO
PROVIDER_CHANGED = NO
AT_CHANGED = NO

SSOT_UPDATED = NO (no UI step registry in AGENTS.md)
IMPLEMENTATION_DOC_STATUS = CLOSED

TESTS_BEFORE_COMMIT = PASS (410/0)
ASSEMBLE_BEFORE_COMMIT = PASS

COMMIT = YES
STEP_COMMIT_SHA = edfc603eadb0067bd010c04ca26b4bc28de2e685
STEP_COMMIT_MESSAGE = AG.UI-TRANSCRIPT-CARDS-1 + AG.UI-SHELL-1: transcript cards, session browser, provider settings, shell UI

MERGE_REQUIRED = NO
MERGE_STATUS = ALREADY_ON_CANONICAL_BRANCH
MERGE_COMMIT_SHA = N/A

POST_MERGE_TESTS = PASS
POST_MERGE_ASSEMBLE = PASS

STEP_COMMIT_IS_FINAL_HEAD_ANCESTOR = YES
FINAL_BRANCH = main
FINAL_HEAD = edfc603eadb0067bd010c04ca26b4bc28de2e685
FINAL_WORKTREE = CLEAN

TAG = NO
PUSH = NO

NEXT_CANDIDATE = AG.CONVERSATION-REWIND-0.PREFLIGHT

STOP = YES
```
