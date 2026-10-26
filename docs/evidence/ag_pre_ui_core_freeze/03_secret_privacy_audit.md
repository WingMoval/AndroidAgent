# 03 — Secret / Privacy Audit (AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1)

Scope: all freeze-eligible candidate text files (untracked + modified tracked, excluding binary AAR/POM).
SCANNED_FILE_COUNT = 114

## Method
Regex scan for credential patterns (api_key/apikey/token/secret/password/authorization/bearer/sk-/
OPENAI_API_KEY/OPENROUTER_API_KEY/x-api-key/private-key markers) + privacy patterns (device serial,
model, private IPs 10./192.168./172.16-31.), then manual adjudication of every hit.

## SECRET pattern hits — ALL adjudicated (0 real credentials)

| File:line | Match | Adjudication |
|---|---|---|
| docs/evidence/ag_topic_session_device_i/07_logcat.txt:194,212,217 | `token=android.os.BinderProxy@...` | FALSE POSITIVE — Android window/binder token, not a credential |
| docs/implementation/AG.FRESH-INSTALL-LLM-PAIRING-E2E-0.md:52,216 | `PROVIDER_SECRET_LEAKED = NO` | DOCUMENTATION TEXT — asserts no leak |
| docs/implementation/AG.TOPIC-SESSION-...-0_FINAL.md:1789,1851,1886,1889 | "non-secret apiKey", "task-allowed", "non-secret diagnostic" | DOCUMENTATION TEXT |
| provider.local.fixture.json:5 | `"apiKey": "fixture-key"` | SAFE TEST FIXTURE — non-secret loopback value |
| provider-openai-compatible-jdk/.../LiveApiTest.kt:67 | `API_KEY = PRESENT/ABSENT` | SAFE DIAGNOSTIC — never prints the key |
| provider-openai-compatible-jdk/.../ProviderProtocolTest.kt:429,433,440-451 | `sk-super-secret-value-9999`, `sk-leak-me` | SYNTHETIC TEST VALUE — asserts the key is NEVER echoed (assertFalse on leak) |
| tools/test-fixtures/echo_fixture.py:16,18,19,103 | "Never logs the Authorization header / API key", "NON-SECRET" | DOCUMENTATION TEXT — fixture explicitly avoids secrets |
| upload/AG.TOPIC-SESSION-...-0_FINAL.md:1789,1851,1886,1889 | same as SSOT | DOCUMENTATION TEXT (snapshot copy) |

REAL_SECRET_FOUND = 0
REAL_CREDENTIAL_FOUND = NO

## DEVICE / PRIVACY pattern hits — adjudicated

- Device serial `APLMUT4312005935`, model `ALI-AN00`, build fingerprint, private IPs `192.168.137.x`
  appear in: pre-existing historical implementation docs (AT-V4-MODEL-CAPABILITY-UPDATE-0,
  AT-V4-PTY-PERSISTENCE-TEST-0, DEBUG-LLM-ADB-DIAGNOSIS-0, FRESH-INSTALL-LLM-PAIRING-E2E-0),
  the DEVICE-I evidence dir, and the SSOT §20 / freeze doc.
- These are REAL internal-device verification identifiers, NOT credentials.
- `127.0.0.1` (loopback) in fixtures/tests = local-only, non-identifying.
- "serial/serialization/serially" hits (MessageSerializer, serial tool calls) = DOMAIN NOISE, false positive.

DEVICE_IDENTIFIER_POLICY = PRIVATE_REPO_RETAINED
Rationale: repo has NO remote (local-only); identifiers are internal real-device evidence, not
credentials; redacting would rewrite point-in-time historical docs/evidence (unnecessary churn).
If a public repo / export is later created, run a separate public-repo privacy normalization
(redact exact serial to a stable label).

## provider.local.fixture.json — decision

PORTABLE = YES (baseUrl http://127.0.0.1:18080/v1, apiKey "fixture-key", model "fixture-stream").
No personal endpoint, no token, no absolute path, no secret.
PROVIDER_LOCAL_FIXTURE_DECISION = COMMIT (repo-portable test fixture)

## Build input

third_party/at-v4-consumer-freeze-v1/ = vendored AT V4 m2 (AAR/POM/MANIFEST), 1.67 MB.
Verified: NO cache/temp/lock/resolver-status/junk inside (clean m2 layout only).
AT_V4_VENDORED_BUILD_INPUT = COMMIT
CLEAN_CLONE_BUILD_INPUT_COMPLETE = YES

## Verdict

SECRET_SCAN = PASS
PRIVACY_AUDIT = PASS
REAL_CREDENTIAL_FOUND = NO
MACHINE_LOCAL_FILES_STAGED = 0
BUILD_ARTIFACTS_STAGED = 0
