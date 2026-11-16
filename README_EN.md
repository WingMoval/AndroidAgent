# AndroidAgent

**[中文](README.md) | [English](README_EN.md)**

> An autonomous Agent on Android: the model thinks (Intelligence), AndroidTerminal executes (Execution).

AndroidAgent (short: **AG**) is an AI agent application that runs on an Android
phone. It drives an adaptive tool loop through an OpenAI-compatible Provider:
the model autonomously decides which tools to call, observes the results, and
keeps reasoning until it produces a final answer.

## Project Status

```text
Status: v0.2.0 — first public release (LOCAL RELEASE CANDIDATE)
Stage:  0.x, actively under development
License: Apache-2.0
```

This is the first public release of AndroidAgent. Earlier 0.1.x development was
internal and was never publicly released.

## Core Capabilities

| Capability | Description |
|------------|-------------|
| **Adaptive Agent loop** | The model drives a "think → tool call → observe → continue" loop until a final answer |
| **android_shell tool** | Executes shell commands on a target Android device over ADB (one-shot execution semantics) |
| **file tool** | Reads files under the agent's own directory (`filesDir/agent/`): read / search / list / stat (read-only; no write/delete) |
| **network tool** | HTTPS GET fetch (public network only, full SSRF guard; no POST / auth / cookies) |
| **TopicSession persistence** | Durable sessions (Room/SQLite): cold-restart restore, session list, session switching |
| **Session rename / delete** | Users can rename and delete sessions (messages cascade-cleaned) |
| **Stop / Interrupt** | Stop a run at any time; interruption yields a structured `RunOutcome.Interrupted`; the partial transcript is preserved |
| **ADB pairing / readiness / management** | Built-in ADB management surface: current status, Reconnect, Pair / Re-pair, open Wireless Debugging settings |
| **Provider configuration** | OpenAI-compatible providers (e.g. OpenRouter); auto-loads `provider.local.json` at startup, falls back to the manual UI when missing/invalid |
| **Streaming transcript** | Model reasoning (Thinking → Thought), streamed content, and tool actions/results/failures visible in real time |

## Architecture Overview

```text
AndroidAgent = AG Engine + Tool Layer + Host

AG Engine      = :ag-core (pure JVM, no Android dependency)
                 + :ag-runtime (pure JVM, host-neutral)
Tool Layer     = concrete tool modules, one module per capability
                 :tool-android-shell   (shell execution via AT Engine)
                 :tool-network         (platform HttpsURLConnection, no AT)
                 :tool-file            (APP_SELF read-only files)
Host/Product   = :app (AGA): Composition Root, constructs and registers tools
                 + :integration-at (the single AT consumption point)
```

Dependency direction is frozen: tools depend only on `:ag-core`; Core and
Runtime never depend on any concrete tool, provider, Android, or AT.

### AG = Intelligence, AT = Execution

AndroidAgent owns the intelligence side only: the agent loop, sessions,
providers, and the tool contracts. The Android execution side (ADB
connection, pairing, shell execution) is provided by the separate
[AndroidTerminal (AT)](#androidterminal-execution-layer) project. AG consumes
AT's **prebuilt artifacts as vendored AARs (repo-local Maven)**; the build is
self-contained and does not require an AT source tree.

This repository does **NOT** include AT source code — only the prebuilt
AAR/JAR and native artifacts of AT v0.4.4 (GAV 4.0.0) under
`third_party/at-v044-consumer-freeze-v1/`.

## V1 → V2 Evolution

AndroidAgent's V1 and V2 are **not two unrelated projects** — they are one
continuous Git history:

- **V1**: the early AndroidAgent, where the agent and Android execution / ADB
  were more tightly coupled.
- **V2**: continuous evolution on the same Git history, forming the formal
  `AG = Intelligence` / `AT = Execution` separation and consuming Android
  execution through the formal AT consumer boundary (vendored AARs).

The full V1 → V2 history is published together with this repository.

## Build Requirements

The requirements below come from the actual current Gradle configuration:

| Item | Requirement |
|------|-------------|
| JDK | 21 (AGP 8.5.2 requires JDK 17+; verified on this machine with JDK 21) |
| Android SDK | compileSdk 34 / targetSdk 34 |
| minSdk | 26 (Android 8.0+) |
| Gradle | 8.7 (wrapper included) |
| AGP | 8.5.2 |
| Kotlin | 2.1.0 |
| ABI | arm64-v8a only (AT native artifact product constraint) |

> Note: Android 15 is NOT required. Any arm64 device with Android 8.0+ can
> install the app.

## Build Steps

```bash
# 1. Configure the Android SDK location (written to local.properties, which is gitignored)
echo "sdk.dir=C:\android" > local.properties

# 2. Run unit tests
gradlew :ag-core:test :ag-runtime:test :app:testDebugUnitTest

# 3. Build the debug APK
gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Building the Release APK (requires an independent signing identity)

The release build reads its signing configuration from **environment
variables**; if any variable is missing the build **fails explicitly** and
does NOT fall back to debug signing:

```powershell
$env:ANDROID_AGENT_KEYSTORE_FILE  = "<path>\AndroidAgent-release.jks"
$env:ANDROID_AGENT_KEYSTORE_PASSWORD = "<store-password>"
$env:ANDROID_AGENT_KEY_ALIAS      = "androidagent-release"
$env:ANDROID_AGENT_KEY_PASSWORD   = "<key-password>"

.\gradlew.bat :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## Running the App

1. Install the APK on an **arm64** Android device (Android 8.0+).
2. Open the app and configure the provider:
   - Auto-load: the app reads `filesDir/provider.local.json` at startup;
   - Manual: fill in baseUrl / apiKey / model in the UI and Apply.
3. (For android_shell) complete ADB pairing for the target device in the
   ADB management surface, or ensure the device is in a ready state.
4. Type a task and watch the agent's streaming transcript (Thinking / tool
   calls / results). Press Stop at any time.

## Provider Configuration

Any OpenAI-compatible chat-completions provider is supported (streaming +
tool calls). `config/provider.example.json` in the repository is a
**placeholder example** (it contains no real keys):

```json
{
  "provider": {
    "type": "openai-compatible",
    "baseUrl": "https://api.example.com/v1",
    "apiKey": "REPLACE_ME",
    "model": "example-model",
    "requestTimeoutSeconds": 60
  }
}
```

Put your real configuration in `config/provider.local.json` (gitignored), or
copy its contents to `filesDir/provider.local.json` on the device for
auto-loading. Replace the placeholder values with any OpenAI-compatible
endpoint (e.g. OpenRouter, or your own compatible endpoint).

**Security note:** never paste real API keys into public docs, the
repository, or issues.

## ADB Usage

- The `android_shell` tool executes shell commands on the target device via
  the AT Engine over ADB (one-shot semantics: each call establishes an
  independent execution context).
- The app has a built-in ADB management surface (status / Reconnect /
  Pair / Re-pair / open Wireless Debugging settings).
- The ADB identity (key) is **per-app private and self-generated**; keys from
  other apps are never copied.
- First use requires wireless-debugging pairing on the target device.

## Known Limitations

This is a 0.x actively-developed release. The following limits are **known
and intentionally preserved** boundaries:

1. **Local one-shot child processes may survive Stop (G-b1)**: Stop cancels
   the agent run (model / tool-call chain), but a one-shot shell child
   process already dispatched to AT is NOT guaranteed to be terminated.
   External side effects are not rolled back.
2. **Generic mutation approval is not complete**: tool calls currently run
   under a register-and-allow policy; the user-facing mutation approval
   mechanism is not yet implemented.
3. **No general browser / search / JS / POST capability**: the network tool
   only does HTTPS GET fetch (public network). There is no browser
   automation, search, JavaScript execution, or POST / auth / cookies. The
   file tool is read-only (no write / edit / delete).
4. **Per-Run 8-step limit**: `RunOptions.DEFAULT_MAX_STEPS = 8`; a single
   run's tool loop is capped at 8 steps, after which `MaxStepsReached` is
   produced.
5. **The project is still in 0.x**: APIs and architecture may evolve with
   incompatible changes; no Rewind / Redo / Fork / Checkpoint; no MCP /
   multi-agent / persistence (RAG).

## Release APK

The official signed release APK for v0.2.0 is produced by this release-prep
round and staged locally under `upload/release/v0.2.0/` (this directory is
NOT in Git; it is local ephemeral staging only). APK metadata and SHA256 are
recorded in `docs/releases/v0.2.0.md`.

## License

This project is licensed under the **Apache License 2.0** (see `LICENSE` in
the repository root). Attribution and license requirements for third-party
components (AndroidTerminal AARs, BoringSSL, libc++, etc.) are documented in
the root `NOTICE` and in
`docs/legal/boringssl/HISTORICAL_BORINGSSL_LICENSE.md`.

## AndroidTerminal Execution Layer

Android execution is provided by the separate **AndroidTerminal (AT)**
project:

- This repository consumes the prebuilt AAR/JAR and native artifacts of AT
  **v0.4.4** (GAV `4.0.0`), built from AT source commit `e2f2d7b` (tag
  `v0.4.4`);
- The consumer boundary is `com.at.engine.api:at-engine-api:4.0.0` (public
  contract), with `com.at.composition.android:at-composition-android:4.0.0`
  as the composition entry point;
- AT itself is Apache-2.0 licensed; its AAR statically links the
  pre-2025-02 BoringSSL (OpenSSL BSD + SSLeay BSD + ISC + MIT). The required
  attributions and license texts are distributed with this repository as
  required (see `NOTICE`).
