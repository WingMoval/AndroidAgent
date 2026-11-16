# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Note: earlier 0.1.x development was internal and was never publicly released,
so it is not listed here as a public release.

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
