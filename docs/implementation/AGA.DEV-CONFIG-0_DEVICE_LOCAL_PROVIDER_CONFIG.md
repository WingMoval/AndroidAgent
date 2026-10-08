# AGA.DEV-CONFIG-0 — Device-Local Provider Config

> Step: **AGA.DEV-CONFIG-0** (HOST CONFIGURATION / DEVELOPMENT BOOTSTRAP)
> Baseline: main @ `74a653f` (AGENT-0.PREFLIGHT PASS)
> Verdict: **AGA_DEV_CONFIG_0_PASS**

This Step made AGA read its own private `filesDir/provider.local.json` at startup,
parse it with the **existing** `OpenAiCompatibleConfigLoader`, and auto-configure the
Provider. When the file is absent or unusable, the existing manual UI flow is
preserved. **No device work, no ADB, no AGENT-0.LIVE.**

---

## 1. Motivation / Old behaviour

Before this Step the AGA host had **no** way to load a provider configuration
without the user typing Base URL / Model / API Key / Timeout and tapping Apply.
The repo already carried `config/provider.local.json` (gitignored, real key) and the
`OpenAiCompatibleConfigLoader`, but those served the JVM `liveTest` path only. The
Android APK could not use them because the loader was `java.nio.file.Path`-based.

## 2. New behaviour

```text
AGA.onCreate
   ↓
DeviceLocalProviderConfigLoader.load(applicationContext)
   = File(context.filesDir, "provider.local.json")
   ↓
Case A  exists + valid   -> parse (reused) -> OpenAiCompatibleConfig
                            -> ChatCompositionRoot(cfg, ctx) -> Provider READY
                            -> status "Provider config: DEVICE_LOCAL | ... | API key: PRESENT/ABSENT"
                            -> API key input field stays BLANK
Case B  missing          -> manual flow ("enter key, Apply")
Case C  exists + invalid -> NO CRASH, NO raw JSON, NO key
                            -> status "Device-local config invalid (DEVICE_LOCAL_CONFIG_INVALID). ..."
                            -> manual flow remains available
```

After a device-local (or manual) Provider is ready, the AT Engine lifecycle
(`connectShell`) is wired identically for both paths.

## 3. File location

```text
fixed name    : provider.local.json
fixed location: File(context.filesDir, "provider.local.json")
```

No hardcoded `/data/...`, no package-specific absolute path, no external storage,
no repo/PC path. `context.filesDir` only.

## 4. Schema reuse

The device file uses the **exact** schema and field semantics as the repo
`config/provider.local.json`:

```json
{ "provider": { "type": "openai-compatible", "baseUrl": "...",
  "apiKey": "...", "model": "...", "requestTimeoutSeconds": 60 } }
```

So `repo/config/provider.local.json` can be copied straight into
`device filesDir/provider.local.json` with **no conversion**.

## 5. Parser reuse

`OpenAiCompatibleConfigLoader` was refactored minimally:

```text
parse(rawJson: String, label: String = "provider config"): OpenAiCompatibleConfig   (NEW, pure)
load(path: Path): OpenAiCompatibleConfig   (unchanged semantics; now delegates to parse)
```

* The `Path` overload still only checks existence and reads the file; every
  parse/validation decision is in `parse`.
* **Validation semantics are UNCHANGED** — all 22 pre-existing `ProviderConfigTest`
  cases still pass, plus 7 new `parse` cases (C15–C21) proving `parse == load` and
  secret-safety.
* `DeviceLocalProviderConfigLoader` (Host) does normal Android `File.readText()` and
  calls `OpenAiCompatibleConfigLoader.parse`. It re-implements **no** JSON parsing,
  validation, `REPLACE_ME` handling, field mapping, or timeout semantics.
* No ConfigFramework / CredentialManager / SettingsRepository / DI was introduced.

## 6. Startup precedence + manual override

```text
device-local config (valid)  -> current active Provider
manual Apply                 -> replaces the active Provider for the current process
manual Apply does NOT write back provider.local.json
```

The device-local file is **READ-ONLY** from the AGA's perspective. The app never
creates, opens for writing, or modifies it (proven by T10: the loader source opens
no output stream / writer and never calls `createNewFile`).

## 7. Credential / secret policy

```text
API key logged           NO
config raw JSON printed  NO (Invalid maps to a stable category, never the raw text)
Authorization printed    NO
key in BuildConfig/res/  NO
  assets                 NO (T11: no provider.local.json in assets/res/raw/APK)
real key committed       NO (config/provider.local.json stays gitignored)
```

Allowed in the UI: `API key = PRESENT/ABSENT`, model id, safe endpoint identity
(scheme + host + port + path), `config source = DEVICE_LOCAL / MANUAL`.
`OpenAiCompatibleConfig.toString()` is already redacted (`apiKey=<redacted>`).

## 8. Composition integration

`ChatActivity.onCreate` calls `tryDeviceLocalConfig()`. On `Loaded` it builds
`ChatCompositionRoot(cfg, applicationContext)` exactly as the manual path does, so
**AGENT_INSTRUCTIONS_V0 injection, the AgentTraceCollector wiring, android_shell
registration, and ShellConnection behaviour are unchanged** — they all live in
`ChatCompositionRoot`, which is constructed identically from either config source.

## 9. Tests (real JUnit execution)

```text
provider-openai-compatible:test   39 PASS  (incl. new C15-C21 parse tests)
app:testDebugUnitTest             24 PASS  (11 AGENT-0.PREFLIGHT + 13 AGA.DEV-CONFIG-0)
```

New `DeviceLocalProviderConfigTest` (13):
```text
T1 missing -> Missing
T2 valid   -> Loaded(expected baseUrl/model/apiKey/timeout)
T3 same schema: repo load(Path) == device load(File) for identical JSON
T4 malformed JSON -> Invalid, no crash, no secret leak
T5 REPLACE_ME / T5b unsupported type / T5c missing provider -> Invalid
T6 sentinel never in result/reason/config.toString
T7 Loaded config is a complete, ready-to-use OpenAiCompatibleConfig (no manual key)
T8 Missing + Invalid both keep the manual flow available
T10 app never writes provider.local.json (static: no output stream / writer / create)
T11 provider.local.json not in assets/res/raw/APK; repo local stays gitignored
T12 no AT imports, no ADB / process-exec in :app main
```

## 10. Regression

```text
:ag-core:test                       41 PASS
:ag-runtime:test                     8 PASS
:provider-openai-compatible:test    39 PASS
:provider-openai-compatible-jdk:test 24 PASS
:tool-android-shell:test             16 PASS
:app:testDebugUnitTest               24 PASS
:app:assembleDebug                  PASS (APK 5,017,005 B)
```

`:app:lintDebug` still fails on the **pre-existing** AT-AAR manifest issue
(`ACCESS_FINE_LOCATION` without `COARSE`). This Step did not touch any manifest or
AAR, so it is recorded as PRE-EXISTING and is not a failure of this Step.

## 11. Static boundary / secret audit

```text
AG Core production changes       0
Agent Runtime production changes 0
AT Engine changes                0
:app AT imports                  0
:app ADB / process-exec          0
provider.local.json in APK       NO
real private-key material        0
```

## 12. Changed files

```text
modified
  provider-openai-compatible/.../OpenAiCompatibleConfigLoader.kt   (extract parse(String))
  provider-openai-compatible/.../ProviderConfigTest.kt             (C15-C21)
  app/src/main/.../ChatActivity.kt                                 (startup auto-config + safe status)
added
  app/src/main/.../DeviceLocalProviderConfig.kt
  app/src/test/.../DeviceLocalProviderConfigTest.kt
  docs/implementation/AGA.DEV-CONFIG-0_DEVICE_LOCAL_PROVIDER_CONFIG.md (this)
```

## 13. Known limitations

```text
- Device test NOT run (no device in this Step); DeviceLocalProviderConfigLoader
  load(Context) is the only path not exercised on a real Context (the identical
  load(File) core is fully tested on the JVM).
- No persistent manual-config save: a manual Apply is process-lifetime only and
  never writes the device-local file (by design).
- A single process-wide config: switching config rebuilds the ChatCompositionRoot.
- The device-local file is a development convenience, not a secure credential store.
```

## 14. Final Verdict

```text
AGA_DEV_CONFIG_0_PASS

DEVICE_LOCAL_CONFIG_PATH       = filesDir/provider.local.json
CONFIG_SCHEMA                  = SAME_AS_REPO_LOCAL
CONFIG_PARSER                  = REUSED
AUTO_LOAD_VALID_CONFIG         = VERIFIED
MISSING_FILE_FALLBACK          = VERIFIED
INVALID_FILE_FALLBACK          = VERIFIED
MANUAL_OVERRIDE                = VERIFIED
APP_WRITES_LOCAL_CONFIG        = NO
API_KEY_LOGGED                 = NO
CONFIG_PACKAGED_IN_APK         = NO
ADB_CODE_IN_APK                = NO

AG_CORE                        = UNCHANGED
AG_RUNTIME                     = UNCHANGED
AT_ENGINE                      = UNCHANGED

DEVICE_TEST                    = NOT_RUN
READY_FOR_DEVICE_PROVISIONING  = YES
```

STOP HERE. Do not connect the phone, do not run ADB, do not start AGENT-0.LIVE.
Next (after Controller Review + user connects the phone):

```text
repo/config/provider.local.json
  -> ADB/run-as provisioning
  -> AGA filesDir/provider.local.json
  -> launch AGA
  -> verify DEVICE_LOCAL config loaded
  -> AGENT-0.LIVE
```
