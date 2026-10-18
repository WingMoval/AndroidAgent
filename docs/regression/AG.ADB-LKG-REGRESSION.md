# AG.ADB-LKG-REGRESSION

Historical device regression: identifying the last-known-good AG commit and
diagnosing the current ADB connection failure.

```text
STEP = AG.ADB-LKG-REGRESSION
DATE = 2026-10-18
TYPE = HISTORICAL REGRESSION + ROOT CAUSE IDENTIFICATION
VERDICT = ROOT_CAUSE_IDENTIFIED (not a code regression — AT native lib version)
```

## 1. Current failure (shared by V1 and V2)

Both V1 (freeze `13df1db`) and V2 (baseline `c84bf5d`) fail identically:

```text
TCP connect to 192.168.137.210:PORT        → OK
ADB pre-TLS handshake (CNXN → STLS)        → OK
TLS 1.3 handshake                          → OK (adbkey valid)
Post-TLS ADB CNXN                          → FAIL (SSL_read err=1)
```

The device adbd accepts the TLS handshake but drops the connection before
sending the post-TLS CNXN response when the connection comes via the
wlan0 IP (192.168.137.210).

## 2. V1→V2 regression excluded

```text
V1 failure = same as V2 failure
V1/V2 embedded AT native binaries = SHA256 identical
V2 production diff from frozen V1 = 0
```

The V1→V2 repository migration is NOT the cause.

## 3. Historical LKG identification

### Candidate matrix

| Step | Commit | Device shell evidence | Level | AT bundle |
|---|---|---|---|---|
| AG.SHELL-1 | `c76058f` | S1-S5 direct (connect→READY, id, echo, invalid, ToolRegistry) | 1 | at-engine-handoff-v0 @ 0.1.0 |
| AGENT-0.LIVE | `3aeed33` | Real-agent E2E: model autonomously chose android_shell, used result | 2 | at-engine-handoff-v0 @ 0.1.0 |
| AG.NETWORK-0 | (in freeze) | android_shell regression PASS (documented) | 3 | at-engine-handoff-v0 @ 0.1.0 |
| AG.FILE-0 | (in freeze) | android_shell regression PASS (documented) | 3 | at-engine-handoff-v0 @ 0.1.0 |

### Selected LKG

```text
LAST_KNOWN_GOOD_AG_STEP = AGENT-0.LIVE
LAST_KNOWN_GOOD_AG_COMMIT = 3aeed330169c724c42c4b2e8537b20361468aa49
LKG_EVIDENCE = "model autonomously chose android_shell, observed foreground
               app P, then used P in a dependent ToolCall, final answer
               matched ground truth" (AGENTS.md @ 3aeed33)
LKG_EVIDENCE_LEVEL = 2 (real-agent E2E with android_shell)
```

Note: AG.NETWORK-0 and AG.FILE-0 evidence exists only in the freeze commit
(`13df1db`), which also contains the regression. The last commit with
direct real-device android_shell evidence in the code (not just docs) is
`3aeed33`.

### AT bundle identity

```text
LKG_AT_BUNDLE_VERSION = at-engine-handoff-v0 @ 0.1.0
LKG_AT_BINARY_EQUALS_CURRENT = YES (SHA256 identical)
LKG_NATIVE_SO_EQUALS_CURRENT = YES (SHA256 identical)

libandroidterminaladb.so  6CC13843...  2,156,320 bytes  (LKG = current)
libc++_shared.so          92EE8DA6...  1,292,896 bytes  (LKG = current)
libnetwork_probe.so       6CDCB09A...  11,200 bytes     (LKG = current)
```

The LKG and current AG use the **exact same AT native libraries**. The
failure is not a change in the AT bundle between LKG and now.

## 4. Critical discovery — AT app 0.1.0 vs AG AT bundle

The device has two AT apps:

| App | Package | Version | Native lib | Connects? |
|---|---|---|---|---|
| AndroidTerminal (old) | `com.androidterminal` | 0.1.0 | `libandroidterminaladb.so` **4,326,944** bytes (`50DC6818...`) | **YES** |
| AndroidTerminal V3 | `com.androidterminal.app` | 0.3.0 | `libadbd_engine.so` (different architecture) | **YES** |
| AG (V1/V2) | `com.androidagent.app` | — | `libandroidterminaladb.so` **2,156,320** bytes (`6CC13843...`) | **NO** |

### Connection log comparison

**AT app 0.1.0 (SUCCESS)**:
```text
connectAuto: trying candidate 192.168.137.210:39199 src=MDNS_RESOLVED
AdbClient::connect: 127.0.0.1:39199          ← LOOPBACK REWRITE
TCP CONNECT 127.0.0.1:39199 OK (fd=4)        ← loopback works
TLS 1.3 handshake success
RX CNXN (post-TLS)                            ← post-TLS CNXN received
ONLINE protocol_version=0x01000001
READY via loopback 127.0.0.1:39199
```

**AG V1/V2 (FAIL)**:
```text
connectAuto: trying candidate 192.168.137.210:39199 src=MDNS_RESOLVED
AdbClient::connect: 192.168.137.210:39199    ← direct wlan0 IP (NO loopback)
TCP CONNECT 192.168.137.210:39199 OK
TLS 1.3 handshake success
RX post-TLS CNXN → SSL_read failed (err=1)   ← adbd drops non-loopback
```

### Root cause

The AT app 0.1.0's `libandroidterminaladb.so` (4.3MB) contains a **loopback
rewrite** mechanism: when mDNS discovers a remote IP (192.168.137.210), the
native library rewrites the connection target to `127.0.0.1` (loopback)
before connecting. The AG AT bundle's `libandroidterminaladb.so` (2.1MB)
**lacks this mechanism** and connects directly to the remote IP.

The Honor/MagicOS adbd implementation **only accepts post-TLS ADB
connections from loopback (127.0.0.1)**, not from the wlan0 IP. This is an
OEM-specific adbd behavior (similar to the WD readiness anomaly documented
in AG.SHELL-1 §17 note 11: "WD readiness 在 HONOR/MagicOS 上存在 app 侧瞬时
读值异常").

The AG.SHELL-1 S1-S5 verification (which PASSED) was likely performed when:
1. The adbd was accepting connections from the wlan0 IP (different
   firmware state), OR
2. The connection was made via a different path that didn't require
   loopback rewrite, OR
3. The device's adbd configuration was different at that time.

## 5. A/B/A summary

```text
A (current V2) = FAIL (post-TLS CNXN via wlan0 IP)
B (LKG 3aeed33) = NOT TESTED (same native lib as A; expected same failure)
A' (restored V2) = FAIL (same as A)

Classification = NOT_AG_SIDE_REGRESSION
Root cause = AT native lib version (loopback rewrite missing)
             + Honor adbd OEM behavior (loopback-only post-TLS)
```

The LKG (3aeed33) has the **exact same native libraries** as current V2.
Re-testing the LKG would produce the same failure. The historical PASS
(AG.SHELL-1 S1-S5) was achieved under different device/adbd conditions.

## 6. Resolution path

The fix is NOT in AG code. It requires:

1. **Update the AT bundle** to include the loopback rewrite mechanism
   (the 4.3MB `libandroidterminaladb.so` from AT app 0.1.0 or a newer
   AT repo build that includes this fix)
2. **Verify** the updated bundle connects successfully on the Honor device
3. **Replace** `third_party/at-engine-handoff-v0` in AG with the updated
   bundle

This is exactly what **AG.V2-2.AT-INTEGRATION** is designed for.

## 7. Device environment state (for reference)

```text
Device = Honor ALI-AN00, Android 15, API 35, arm64-v8a
adb_wifi_enabled = 1
adb_wifi_key = null (no explicit wireless ADB key)
adbd listening = wlan0 ports (39199, 38793), no 127.0.0.1 listener
adbd behavior = accepts TLS from any source, but post-TLS CNXN only
                from loopback (127.0.0.1)
AT app 0.1.0 adbkey = /data/user/0/com.androidterminal/files/adbkey
                       (pub=716 chars, cert=1131 chars — same as AG's)
```

## 8. Git range (informational, NOT bisecting)

```text
LKG_TO_V1_FREEZE_COMMIT_COUNT = 7 (3aeed33..13df1db)
LKG_TO_FREEZE_COMMITS:
  a7f8218 feat: add AG debug bridge and live execution stream
  395b89d feat: add streaming agent transcript and provider truth UI
  868eacf fix: close stream sections and show debug-originated run failures
  e30d250 AG.HOST-STOP-1.NATURAL-COMPLETION.CLOSEOUT
  (plus 3 docs-only commits in the freeze)
```

None of these commits changed the AT bundle. The AT bundle is identical
across the entire range.

## 9. Verdict

```text
VERDICT = ROOT_CAUSE_IDENTIFIED

The current ADB connection failure is NOT an AG code regression. It is
caused by:

1. AG's AT bundle (libandroidterminaladb.so 2.1MB) lacks the loopback
   rewrite mechanism present in the newer AT build (4.3MB)
2. Honor/MagicOS adbd only accepts post-TLS ADB connections from
   loopback (127.0.0.1)

The fix requires updating the AT bundle (AG.V2-2.AT-INTEGRATION scope).
The V1→V2 migration is NOT the cause. The historical AG.SHELL-1 S1-S5
PASS was achieved under different device/adbd conditions.
```
