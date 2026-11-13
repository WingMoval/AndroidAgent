# AG.SELECTIVE-RESET-VALIDATION-0 — Implementation Report

## Summary

Validated that a selective process/runtime reset (force-stop → cold restart) preserves all durable user state (Sessions, messages, Provider config, API credentials) while completely resetting volatile runtime state.

## Experiment

```
OLD PROCESS (PID 5252)
  - AgentRuntime
  - In-memory Session
  - In-memory Provider
  - Transcript projection
    ↓ force-stop
NEW PROCESS (PID 8042)
  - Fresh AgentRuntime
  - Restored Session (from Room)
  - Restored Provider (from filesDir)
  - Restored transcript (from Room)
    ↓ verify
DURABLE DATA = SAME
```

## Key Results

| Gate | Result |
|------|--------|
| Process terminated | PASS (PID 5252 → 8042) |
| Session DB preserved | PASS (file intact) |
| Session ID preserved | PASS (same UUID) |
| Sentinel message preserved | PASS (model recalled it) |
| No duplication on restore | PASS (block count unchanged) |
| Provider config preserved | PASS (SHA-256 unchanged) |
| API key preserved | PASS (model call succeeded) |
| Pre-reset model call | PASS |
| Post-reset model call | PASS |
| Context recall | PASS (exact sentinel token) |
| Passive restore | PASS (0 model/tool calls) |
| No active run after restore | PASS |

## What Was Reset

- Process (force-stop)
- AgentRuntime instance
- In-memory Session materialization
- In-memory Provider instance
- In-memory transcript projection
- DebugBridge connections
- Activity state

## What Was Preserved

- Room database (topic_sessions.db)
- Provider config files (provider.local.json, provider.openrouter.json)
- API credentials (in provider config files)
- Agent workspace (filesDir/agent/)
- AT identity (filesDir/at-consumer/)
- SAF grants

## Security Non-Goal

API key is stored in plaintext in `filesDir/provider.<node>.json`. This step validates **preservation** (key survives cold restart), NOT **at-rest security**. Encryption/redesign is out of scope.

## No Production Code Changed

This was a pure validation task. No production code, AG Core, AG Runtime, AT, or Tool modules were modified.
