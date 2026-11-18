# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Note: earlier 0.1.x development was internal and was never publicly released,
so it is not listed here as a public release.

## [0.2.1] - 2026-11-18

### Added

- Provider-aware configured model selection: model identity is now
  `(profileId, modelId)`, so the same model ID can exist on multiple
  providers.
- Add Model now first shows a **Select Provider** step (even when only one
  provider profile is configured) before discovering models from that
  provider's `/models` endpoint.
- Optional per-model **aliases** with rename support from the model picker
  (long-press → Rename).
- Remove configured models from the picker (long-press → Remove); the
  currently active model is protected.

### Changed

- Main model picker shows **configured models only**; the provider's
  discovered `/models` catalogue is reachable only through Add Model.
- Model status moved from the message composer row to a lightweight row
  **directly above** the composer (no border, no card, no background).
- Home display format: `ProviderDisplayName · ModelDisplay` (e.g.
  `OpenRouter · deepseek-v4-flash-0731`).
- Default model display now uses the model ID **suffix** after the last `/`
  (e.g. `deepseek/deepseek-v4-flash-0731` → `deepseek-v4-flash-0731`)
  instead of the provider prefix.
- Version bump: versionName `0.2.0` → `0.2.1`, versionCode `2` → `3`.

### Fixed

- Add Model click not responding, caused by recycled clickable rows in the
  model picker adapter.
- Discovered provider catalogue no longer appears as automatically
  switchable models in the main picker.

## [0.2.0] - 2026-11-16

First public release of AndroidAgent (AG).

### Added

- **Adaptive Agent loop** (AG Core): model-driven think → tool-call →
  observe → continue loop with a per-Run step limit
  (`RunOptions.DEFAULT_MAX_STEPS = 8`).
- **Tools**:
  - `android_shell` — shell execution on a target Android device over ADB
    via the vendored AT Engine (one-shot semantics).
  - `network` — HTTPS GET fetch, public network only, with a full SSRF guard.
  - `file` — read-only access to the agent's own directory
    (`filesDir/agent/`): read / search / list / stat.
- **TopicSession persistence**: durable sessions backed by Room/SQLite;
  cold-restart restore, session list, session switching.
- **Session management**: rename and delete sessions (messages cascade-cleaned).
- **Stop / Interrupt**: structured `RunOutcome.Interrupted` semantics; the
  partial transcript is preserved on stop.
- **ADB management surface**: status, Reconnect, Pair / Re-pair, and open
  Wireless Debugging settings, inside the app.
- **Provider support**: OpenAI-compatible chat-completions providers
  (streaming + tool calls), e.g. OpenRouter; auto-load of
  `filesDir/provider.local.json` with manual UI fallback.
- **Streaming transcript UI**: Thinking → Thought reasoning blocks,
  streamed assistant content, and per-tool action/result/failure cards with
  copy / retry-as-new-turn / edit-and-resend.
- **Release infrastructure**: independent AndroidAgent release signing
  identity; env-var-driven release signing configuration (fails explicitly
  when signing material is absent — no debug-signing fallback).
- **Publication**: Apache-2.0 `LICENSE`, root `NOTICE` with
  AndroidTerminal / BoringSSL attribution, and
  `docs/legal/boringssl/HISTORICAL_BORINGSSL_LICENSE.md`.

### Changed

- Version bump: versionName `0.1.0` → `0.2.0`, versionCode `1` → `2`.
- AT consumer baseline: vendored AT v0.4.4 (GAV 4.0.0) release bundle
  (`third_party/at-v044-consumer-freeze-v1/`).
