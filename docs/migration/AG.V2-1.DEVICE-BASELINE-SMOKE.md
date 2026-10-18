# AG.V2-1.DEVICE-BASELINE-SMOKE

Real-device baseline / pairing identity continuity smoke for
AndroidAgent-V2 + existing AT bundle (at-engine-handoff-v0 @ 0.1.0).

```text
STEP = AG.V2-1.DEVICE-BASELINE-SMOKE
DATE = 2026-10-18
TYPE = REAL-DEVICE BASELINE VERIFICATION
VERDICT = BLOCKED
FAILURE_CLASSIFICATION = B (AT cannot reconnect — post-TLS ADB handshake failure)
```

## 1. V2 Git identity

```text
PRE_SMOKE_HEAD = a3e7e61e42cb216936c4fff2a21a477aacc01331
V2_BASELINE_TAG_TARGET = c84bf5ded15c6c6c617a1ac72ac80707226e3b90
PRODUCTION_DIFF_FROM_BASELINE = 0
AT_BINARY_DIFF_FROM_BASELINE = 0
```

## 2. APK identity

```text
APK_PATH = app/build/outputs/apk/debug/app-debug.apk (V2)
APK_SHA256 = 159941BE5C5D115CDFA16099FDEDC94C6C1AF33587BC1810872D2CF4F2C2FB06
APPLICATION_ID = com.androidagent.app
ASSEMBLE_DEBUG = PASS
```

## 3. Device test preconditions

```text
DEVICE_CONNECTED = YES (Honor ALI-AN00, USB)
PRE_EXISTING_INSTALL = YES (V1 app already installed with app data)
UNINSTALL_PERFORMED = NO
APP_DATA_CLEARED = NO
```

## 4. Update-install procedure

```text
INSTALL_MODE = UPDATE_INSTALL (adb install -r)
UPDATE_INSTALL = PASS
APP_DATA_PRESERVED_BY_TEST_PROCEDURE = YES
```

App data preserved: `files/at-identity/` (adbkey, adbkey.pub,
adb_cert.pem — dated 2026-10-16) and `files/provider.local.json`
intact after update install.

## 5. AT bundle identity

```text
AT_BINARY_CHANGED = NO
AT_BINARY_DIFF_FROM_BASELINE = 0 (git diff --exit-code = 0)
V1_VS_V2_NATIVE_LIBS = IDENTICAL (SHA256 match):
  libandroidterminaladb.so  6CC13843...  2,156,320 bytes
  libc++_shared.so          92EE8DA6...  1,292,896 bytes
  libnetwork_probe.so       6CDCB09A...  11,200 bytes
```

## 6. Pairing action status

```text
PAIRING_ACTION_PERFORMED_DURING_TEST = NO
PAIRING_PROMPT_OCCURRED = NO
```

No pairing code was requested or used. The existing identity files
(adbkey / adb_cert.pem) were intact and used by the AT engine.

## 7. AT connection result — ROOT CAUSE

The AT engine (`AtRuntime.connectAuto`) successfully:
1. Discovered mDNS candidates (`src=MDNS_RESOLVED`)
2. Established TCP connection to the candidate
3. Completed the ADB protocol pre-TLS handshake (CNXN → STLS)
4. **Completed TLS 1.3 handshake** (cert+key from at-identity,
   `SSL_do_handshake returned rc=1`, `TLS 1.3 handshake success`)

But then **FAILED at post-TLS**:

```text
[C07] TLS_OK
[C08] POST_TLS_HOST_CNXN_SKIPPED (waiting for device CNXN)
E ReadFully: SSL_read failed after 0/24: err=1
E [C09] RX_CNXN_POST_TLS read failed: failed to read 24-byte header
E AdbClient::connect: AdbConnection failed: post-TLS read failed
E at_runtime::connect: failed 192.168.137.210:39613
W AtRuntime: connectAuto: all candidates failed
```

The device adbd **accepts the TLS handshake** (proving the adbkey is
valid) but **drops the connection before sending the post-TLS CNXN
response**. All discovered mDNS candidates fail identically.

### Device environment state

```text
adb_wifi_enabled = 1 (switch is ON)
adb_wifi_key = null (NO wireless ADB key generated)
/data/misc/adb/ = does not exist (no ADB key directory)
adbd listening = ports 38793, 39613 (wlan0, uid=2000, no 127.0.0.1)
```

### V1 cross-check

V1 APK (SHA256 BDB86496..., 5,357,419 bytes) was update-installed and
tested. **V1 also fails identically** — same post-TLS ADB handshake
failure. This confirms the issue is NOT introduced by the V2 migration.

### Old AT app comparison

The old AT app (`com.androidterminal.app`) CAN connect. Log analysis
reveals it uses a **fundamentally different architecture**:

```text
Old AT app:
  AdbRuntime: transport observer started (read-only projection)
  AdbCliBridge: nativeStart OK listen_fd=120
  → loads libadbd_engine.so (LOCAL adbd engine, Unix socket)

AG app (V1 and V2):
  AtRuntime: connectAuto → mDNS → TCP → TLS → post-TLS FAIL
  → loads libandroidterminaladb.so (REMOTE ADB client)
```

The old AT app runs its own **local adbd engine** (`libadbd_engine.so`)
and does not connect to the system adbd. The AG AT engine is a **remote
ADB client** that connects to the system adbd over TCP. These are
different connection architectures.

## 8. Shell smoke

```text
ANDROID_SHELL_SMOKE_1 = NOT_REACHED (AT not connected)
ANDROID_SHELL_SMOKE_2 = NOT_REACHED
AT_RUNTIME_REUSABLE = NOT_TESTED
AGENT_RUN = NOT_RUN
```

Direct `tool_invoke` via DebugBridge confirmed the tool path works:

```text
{"type":"tool_result_direct","tool":"android_shell","success":false,
 "output":"{\"outcome\":\"TRANSPORT_ERROR\",\"exitCode\":-1,...}",
 "error":"not connected (call connectWireless first)"}
```

The tool correctly reports the AT not-connected state. The tool path
(DebugBridge → ToolRegistry → AndroidShellToolImpl → AtRuntime) is
functional; the failure is in the AT engine's connection to the device
adbd.

## 9. Failure classification

```text
CLASSIFICATION = B (AT cannot reconnect)

Root cause: The device adbd accepts the TLS 1.3 handshake from the AG
AT engine (proving the adbkey is valid) but drops the connection before
sending the post-TLS CNXN response. This is a device-adbd / AT-engine
compatibility issue in the current device environment state.

NOT caused by:
  - V2 migration (V1 fails identically)
  - AT binary change (V1 and V2 native libs are SHA256-identical)
  - adbkey invalidity (TLS handshake succeeds)
  - App data loss (identity files intact)
  - Uninstall (update install only)

Likely related to:
  - adb_wifi_key = null despite adb_wifi_enabled = 1
  - Device adbd post-TLS behavior (possible firmware/Honor-specific
    ADB implementation difference)
```

## 10. Final verdict

```text
VERDICT = BLOCKED
V2_DEVICE_BASELINE = NOT_VERIFIED
PAIRING_IDENTITY_CONTINUITY = INCONCLUSIVE (cannot determine — AT
  connection fails before the identity is exercised at the ADB protocol
  level; TLS proves the key is valid but the connection is dropped
  before the ADB session is established)
```

The V2 code is correct: production diff = 0, AT binary unchanged,
tool path functional, app data preserved. The blocker is a
device-environment / AT-engine compatibility issue that also affects
V1 and is unrelated to the V1→V2 migration.

## 11. Next

```text
NEXT = AG.V2-2.AT-INTEGRATION (NOT authorized — awaiting Controller
       decision on how to proceed given the device-environment blocker)
NEXT_OPEN_AUTHORIZED = NO
```

The device-environment issue should be resolved before AG.V2-2 can
provide a meaningful before/after comparison. Options for the
Controller:

1. Investigate the device adbd post-TLS behavior (Honor-specific?)
2. Try a different device
3. Check if the AT engine (newer AT bundle in AG.V2-2) handles the
   post-TLS handshake differently
4. Verify the wireless ADB setup on the device (adb_wifi_key = null)
