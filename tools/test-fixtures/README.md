# OpenAI-Compatible Test Fixture (AG.HOST-STOP-1)

Test infrastructure for the real-device Stop acceptance (HOST-005). It is a
minimal OpenAI-compatible `/v1` endpoint the on-device Debug APK points its
**production** OpenAI-compatible provider at, so a human can watch real
streaming on a phone and tap Stop — with **no real LLM and no API cost**.

It is **not** a ModelProvider, a product provider, a tool, a new AG module, or
a Roadmap capability. Standard library only (no pip, no Docker).

## Launch

```bash
python tools/test-fixtures/openai_compatible_fixture.py --host 127.0.0.1 --port 18080
```

Defaults: `--delay-ms 300`, `--chunks 600` (≈ 180 s of streaming, long enough to
tap Stop). It binds to `127.0.0.1` (loopback) by default; `--host 0.0.0.0` is
allowed but prints a warning. The log prints `FIXTURE_READY` when listening.

## ADB reverse

```bash
adb reverse tcp:18080 tcp:18080
adb reverse --list
```

The device then reaches the loopback fixture at `http://127.0.0.1:18080/v1`
without any LAN exposure.

## Configure the Debug APK

The APK auto-loads `filesDir/provider.local.json`. For the EMG node (file name
`provider.local.json`) use:

```json
{
  "provider": {
    "type": "openai-compatible",
    "baseUrl": "http://127.0.0.1:18080/v1",
    "apiKey": "fixture-key",
    "model": "fixture-stream",
    "requestTimeoutSeconds": 300
  }
}
```

Write it via `run-as` (the only verified path to the app-private `files/`
directory; a plain `adb shell "echo ... > /data/data/..."` redirect does **not**
work — the shell user cannot write another app's private storage). Save the JSON
above on the PC as `provider.local.fixture.json`, then:

```bash
# 1. Push the file to a world-readable staging location.
adb push provider.local.fixture.json /data/local/tmp/provider.local.json

# 2. Copy it into the app's private files/ as the app UID (debug builds only).
adb shell run-as com.androidagent.app cp /data/local/tmp/provider.local.json files/provider.local.json

# 3. Remove the staging copy.
adb shell rm /data/local/tmp/provider.local.json

# 4. Verify (baseUrl / model are non-secret and may be shown).
adb shell run-as com.androidagent.app cat files/provider.local.json
```

Before overwriting, **back up any existing provider config** so the user's real
configuration is never lost:

```bash
adb shell run-as com.androidagent.app cat files/provider.local.json        > provider.local.backup.json
adb shell run-as com.androidagent.app cat files/provider.openrouter.json   > provider.openrouter.backup.json
```

Restore it the same way (`adb push` + `run-as cp`) when the acceptance is done.

Then **force-stop and relaunch** the app so the config auto-loads:

```bash
adb shell am force-stop com.androidagent.app
adb shell am start -n com.androidagent.app/.ChatActivity
```

(Debug cleartext for `http://127.0.0.1` is enabled in `src/debug` only; the
release APK is unaffected.)

## Manual Stop test (D1)

1. Send `测试 Stop 功能`. The Assistant streams `fixture token 0001 ...`.
2. Tap **Stop**. Observe `Stopping...` then `Stopped`, streaming stops, the Stop
   button disables, Send re-enables. The fixture log prints
   `CLIENT_DISCONNECTED`.

## Second run (D2)

Send `第二轮测试`. A new request reaches the fixture, a new stream begins, the UI
returns to RUNNING and Stop re-enables (the server stays up across disconnects).

## Shutdown

`Ctrl+C` (or kill the process). The server stops cleanly; `FIXTURE_STOPPED`.
