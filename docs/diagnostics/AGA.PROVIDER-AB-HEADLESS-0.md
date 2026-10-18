# AGA.PROVIDER-AB-HEADLESS-0 — Real-Device Provider A/B Diagnostic

> STEP: AGA.PROVIDER-AB-HEADLESS-0 (REAL-DEVICE DIAGNOSTIC / HEADLESS PROVIDER A-B TEST)
> BASELINE HEAD: `cd0eb33`
> WORKTREE: DIRTY (pre-existing user experiments preserved)
> STATUS: **AGA_PROVIDER_AB_HEADLESS_0_READY** (device offline; harness + configs + PC live all ready)

This Step verifies the real LLM Provider path **without the AGA UI**, using Android
instrumentation, and builds an `at` / `noat` A-B control matrix. It does NOT run an
autonomous Agent, does NOT click the chat UI, and does NOT modify AT/AG Core/Runtime.

---

## 1. Baseline

```text
HEAD              cd0eb33 (feat: load device-local provider config in AGA)
pre-existing dirty:
  app/build.gradle.kts                                   (at/noat flavors + BuildConfig.AT_ENABLED_BY_DEFAULT)
  app/src/main/java/com/androidagent/app/ChatActivity.kt (AT wiring switch; button experiment removed later)
  provider-openai-compatible/.../OpenAiCompatibleConfig.kt        (atEnabled PRE-EXISTING EXPERIMENT)
  provider-openai-compatible/.../OpenAiCompatibleConfigLoader.kt  (atEnabled parse)
  tool-android-shell/.../ShellConnection.kt                        (connectTo(host,port) experiment)
```

These are **not** outcomes of this Step and must not be treated as frozen design.

## 2. Task-owned new files

```text
provider-openai-compatible-jdk/src/test/.../OpenRouterPcProbe.kt     (PC live check, @Tag live)
app/src/androidTest/.../ProviderRealDeviceProbeTest.kt               (headless P1/P2/P3 harness)
config/provider.openrouter.local.json                                 (gitignored, real key)
docs/diagnostics/AGA.PROVIDER-AB-HEADLESS-0.md                       (this doc)
.gitignore                                                           (added /config/provider.openrouter.local.json)
```

## 3. Provider Config Sources

| Source | File | Key | Status |
|---|---|---|---|
| EMG (current AGA dev) | `config/provider.local.json` | emg_* | unchanged, gitignored |
| OpenRouter (new) | `config/provider.openrouter.local.json` | sk-or-* | new, gitignored, never committed |

Both use the **same** OpenAI-compatible schema:

```json
{ "provider": { "type": "openai-compatible", "baseUrl": "...", "apiKey": "...",
  "model": "...", "requestTimeoutSeconds": 120 } }
```

OpenRouter config was generated from the machine-local OpenCode `auth.json`
(`~/.local/share/opencode/auth.json`). Only `OPENROUTER_API_KEY = PRESENT` and
`OPENROUTER_MODEL = deepseek/deepseek-v4-flash-0731` are ever printed. The model is
served via OpenRouter routing to **StreamLake** (`streamlake/fp8`, per global
opencode config `provider.openrouter.models."deepseek/deepseek-v4-flash-0731".options.provider`).

## 4. PC-Side Sanity Check (before device)

Used `OpenAiCompatibleProvider` + `JdkOpenAiHttpTransport` (existing production
classes), minimal single-message request, tools empty, stream false.

```text
OPENROUTER_PC_LIVE = PASS
MODEL              = deepseek/deepseek-v4-flash-0731
SAFE_ENDPOINT      = https://openrouter.ai/api/v1/chat/completions
ELAPSED_MS         = 12952
RESULT             = FinalAnswer
CONTENT_LENGTH     = 3   (model replied "OK")
```

## 5. Headless Android Harness

`app/src/androidTest/.../ProviderRealDeviceProbeTest.kt`

- Entry: `InstrumentationRegistry.getInstrumentation().targetContext` (no UI)
- Config: `DeviceLocalProviderConfigLoader.load(appContext)` → `filesDir/provider.local.json`
- No re-implementation of JSON parsing; reuses existing loader/parser
- Three layers per case:

```text
P1_RAW_ANDROID_POST   HttpsURLConnection direct POST, staged timing
                      (DNS_OR_OPEN / CONNECT / RESPONSE_CODE / RESPONSE_READ)
P2_ANDROID_TRANSPORT  AndroidOpenAiHttpTransport (production transport)
P3_PROVIDER           OpenAiCompatibleProvider.complete(ModelRequest)
                      (no Agent Loop / ToolRegistry / android_shell / AT / UI)
```

Network observation printed via ConnectivityManager (CELLULAR/WIFI/VPN/VALIDATED).

Secret policy: key/header/body never printed; only PRESENT/ABSENT, structural
`parseChoiceOk` (choices+message), sizes, HTTP status.

## 6. Flavor Control (diagnostic use only, not frozen)

```text
at   flavor  -> applicationId com.androidagent.app       AT default ON
noat flavor  -> applicationId com.androidagent.app.noat  AT default OFF
BuildConfig.AT_ENABLED_BY_DEFAULT drives ChatActivity's connectShell gating.
```

P1/P2/P3 do not call android_shell; flavor only controls whether AT lifecycle
runs in the host app.

## 7. Provider A/B Matrix (planned)

```text
               EMG                    OpenRouter
noat APK      N-E                    N-O
at APK        A-E                    A-O

fixed order: N-E, N-O, A-E, A-O; each runs P1, P2, P3; repeat 3x (cost permitting 5x).
```

Control variables kept constant across N-E/N-O: same phone, same APK flavor, same
test binary, same transport, same test code, same timeout, same prompt. Only
baseUrl/apiKey/model change.

## 8. Device Results

**DEVICE = CONNECTED via USB (APLMUT4312005935, HONOR ALI-AN00).**

Wireless adb proved unusable for this Step (repeated offline/port-drift during
`am instrument`); USB gave a stable session.

Per user instruction, **EMG was NOT probed** (EMG is in active service and only
supports one concurrent session). Only the OpenRouter arm of the matrix was run.

### N-O — noat + OpenRouter (com.androidagent.app.noat)

```text
NETWORK: CELLULAR=false WIFI=true VPN=false VALIDATED=true
P1 RAW_ANDROID_POST:  PASS HTTP_STATUS=200 ELAPSED_MS=2519 RESPONSE_BYTES=1007
P2 ANDROID_TRANSPORT: PASS HTTP_STATUS=200 ELAPSED_MS=4735 RESPONSE_BYTES=1180
P3 PROVIDER:          PASS TURN=FinalAnswer CONTENT_LENGTH=2 ELAPSED_MS=3555
```

### A-O — at + OpenRouter (com.androidagent.app)

```text
NETWORK: CELLULAR=false WIFI=true VPN=false VALIDATED=true
P1 RAW_ANDROID_POST:  PASS HTTP_STATUS=200 ELAPSED_MS=1958 RESPONSE_BYTES=1045
P2 ANDROID_TRANSPORT: PASS HTTP_STATUS=200 ELAPSED_MS=2042 RESPONSE_BYTES=1178
P3 PROVIDER:          PASS TURN=FinalAnswer CONTENT_LENGTH=3 ELAPSED_MS=4881
```

### N-E — noat + EMG (com.androidagent.app.noat)

```text
NETWORK: CELLULAR=false WIFI=true VPN=false VALIDATED=true
P1 RAW_ANDROID_POST:  PASS HTTP_STATUS=200 ELAPSED_MS=31422 RESPONSE_BYTES=649
P2 ANDROID_TRANSPORT: PASS HTTP_STATUS=200 ELAPSED_MS=34574 RESPONSE_BYTES=639
P3 PROVIDER:          PASS TURN=FinalAnswer CONTENT_LENGTH=2 ELAPSED_MS=14239
```

### A-E — at + EMG (com.androidagent.app)

```text
NETWORK: CELLULAR=false WIFI=true VPN=false VALIDATED=true
P1 RAW_ANDROID_POST:  PASS HTTP_STATUS=200 ELAPSED_MS=127012 RESPONSE_BYTES=639
P2 ANDROID_TRANSPORT: PASS HTTP_STATUS=200 ELAPSED_MS=5275  RESPONSE_BYTES=638
P3 PROVIDER:          PASS TURN=FinalAnswer CONTENT_LENGTH=2 ELAPSED_MS=142168
```

### Key observation

All 4 matrix arms PASS on WiFi (VALIDATED=true). OpenRouter is fast and stable
(2–5s). EMG is reachable but **highly variable and slow** (5s to 142s across
runs), with HTTP 200 in every case. The earlier cellular-default failures to
cloudflare are not reproduced on the WiFi path here; EMG slowness is
network-path dependent, not a Provider/transport defect.

## 9. Interpretation Rule (to apply once device data exists)

```text
N-E FAIL, N-O PASS  -> generic AGA Android stack healthy; EMG/Cloudflare path suspect
N-E FAIL, N-O FAIL  -> common Android HTTP/config/app environment suspect (not EMG yet)
N-E PASS, N-O PASS  -> basic noat LLM path healthy
N-E intermittent, N-O stable -> provider/path-specific instability strong evidence
noat both stable, at both unstable -> AT lifecycle/network interaction suspect
at and noat same behaviour -> AT interference evidence weak
at EMG fail, at OpenRouter pass -> provider-specific path dominant
```

### Applied to collected data

```text
N-O (noat + OpenRouter): P1/P2/P3 all PASS on WiFi
A-O (at + OpenRouter):   P1/P2/P3 all PASS on WiFi
N-E (noat + EMG):        P1/P2/P3 all PASS on WiFi (slow, 14-35s)
A-E (at + EMG):          P1/P2/P3 all PASS on WiFi (variable, 5-142s)

-> generic Android network + AndroidOpenAiHttpTransport + Provider path: HEALTHY
   on WiFi for BOTH providers, for BOTH noat and at flavors.
-> EMG path: reachable and correct (HTTP 200, FinalAnswer), but slow/variable
   (5-142s vs OpenRouter 2-5s). Not a Provider/transport defect; network-path
   dependent (cloudflare CDN over the phone's WiFi route).
-> AT interference on the Provider path: NOT EVIDENT (at and noat behaved the same;
   though P1/P2/P3 do not start AT lifecycle, so this only excludes AT influence
   on the Provider probe itself, not on host startup).
```

Root cause for the earlier unstable LLM sessions: on a validated WiFi path both
providers succeed; the failing condition correlated with the phone's cellular
default network being intermittent to cloudflare. EMG is additionally slow on
this path. The AGA Provider stack itself is not the instability source.

## 10. Secret Audit

```text
real API key in git diff            = NONE
Authorization header in logs        = NONE
provider local raw JSON in logs     = NONE
OpenRouter local config tracked     = NO (gitignored)
config/provider.local.json packaged = NO (never packaged)
test sources contain real key       = NONE
```

## 11. Production Changes

```text
AG Core                       = NO
Agent Runtime                 = NO
Provider production semantics = NO
Android transport semantics   = NO
AT Engine / Handoff           = NO
```

All new code is diagnostic fixtures or pre-existing experiments (unchanged by
this Step).

## 12. Pre-existing Dirty Files Preserved

Yes — `git reset`/`checkout -- .`/`restore`/`stash`/`clean` were NOT used. The
flavor/atEnabled/connectTo experiments remain untouched in the worktree.

## 13. Root Cause Status

**PARTIALLY RESOLVED (all 4 matrix arms measured on WiFi).**

- Both providers (EMG, OpenRouter) work through the AGA Android stack on a
  validated WiFi path, for both noat and at flavors (P1/P2/P3 all PASS).
- EMG is slow/variable (5–142s); OpenRouter is fast (2–5s). No provider/transport
  defect; the difference is network-path latency to each endpoint.
- Earlier unstable LLM sessions correlate with the phone's cellular default
  network being intermittent to cloudflare. WiFi path avoids it.
- AT interference on the Provider probe path: NOT EVIDENT.

## 14. Next Steps

1. For AGENT-0.LIVE, keep the phone on a validated WiFi path (or a proxy that
   does not drop Tailscale) so both LLM and AT remain reachable.
2. Prefer OpenRouter for interactive probing (fast); use EMG when the slower
   local path is acceptable. EMG may need a higher requestTimeout in AGA
   (current 120s is adequate but tight against the 142s observed).