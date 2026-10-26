FINAL MATRIX — DEVICE-VERIFY-1  =>  PASS
===========================================================================
VERDICT: PASS — REAL_DEVICE_USER_VISIBLE_RESUME_VERIFIED

All four device cases PASS on a real HONOR ALI-AN00 (Android 15 / API 35,
serial APLMUT4312005935). No production or test source was modified; only
test infrastructure (fixture + UI/debug/DB helpers) and evidence were added.

  DEV-I1  Kill process + visible resume      PASS  (PID 7130 -> 13877; same session; transcript exact; 0 dup)
  DEV-I2  Restore then continue              PASS  (msg_count=6 proves restored history supplied to next model call)
  DEV-I3  New session + old preserved        PASS  (A != B; B empty; A row + 6 messages intact)
  DEV-I4  Force-stop relaunch (x2)           PASS  (both rounds restore B; idempotent; 0 dup; no cross-session mix)

PASS GATE:
  REAL_PROCESS_RESTART_PROVEN      = YES (PID change each kill/force-stop)
  LATEST_SESSION_RESTORED          = YES
  VISIBLE_TRANSCRIPT_RESTORED      = YES
  VISIBLE_TRANSCRIPT_DUPLICATES    = 0
  RESTORED_CONTEXT_CONTINUES       = YES (msg_count evidence)
  NEW_RUN_AFTER_RESTORE            = PASS
  NEWSESSION_SWITCH                = PASS
  OLD_SESSION_PRESERVED            = YES
  FORCE_STOP_RELAUNCH              = PASS
  REPROJECT_DURABLE_WRITE          = NO
  RESTORE_MODEL_CALLS              = 0
  RESTORE_TOOL_EXECUTIONS          = 0
  FATAL_EXCEPTION_COUNT            = 0
  ANR_COUNT                        = 0
  ROOM_SCHEMA_CHANGED              = NO
  DATABASE_VERSION                 = 1 (unchanged)
  PRODUCTION_CODE_CHANGED_THIS_ROUND = NO
  AG_CORE/AG_RUNTIME/APP/PROVIDER  = PASS (52/88/171/26, 0 failures, pre- and post-device)
  ASSEMBLE_DEBUG                   = PASS

PRODUCT CLAIM (scope strictly limited):
  FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED
  = latest TopicSession -> Android process restart / force-stop -> visible
    transcript restore -> continue conversation.
  NOT: multi-session browser, session management, cloud sync, cross-device resume.

TRANSPORT / OEM NOTES (non-defect):
  - `adb reverse tcp:45678` failed with "Address already in use" on this host
    (no visible PC listener). `adb forward tcp:45678 tcp:45678` used instead;
    identical effect. `adb reverse tcp:18080` worked normally.
  - `am kill` is a no-op on this HONOR ROM; `kill -9` is "Operation not
    permitted" (no root). `am force-stop` (the DEV-I4 mechanism) used for the
    DEV-I1 kill — terminates the process without clearing durable data
    (task-allowed equivalent). PID change confirms a real restart.
  - Mid-session the device once dropped off USB (earlier attempt); after a
    stable reconnect all four cases completed. That earlier drop is recorded in
    the prior BLOCKED note and is superseded by this PASS.

RECOMMENDED_NEXT = freeze/checkpoint the TopicSession Persistence subsystem +
  post-Phase-I architecture + implementation closure audit; then decide the
  real next need (session management / tool expansion / model-provider
  lifecycle / Agent-loop capability / Android execution / product UX) — not
  further persistence expansion.
