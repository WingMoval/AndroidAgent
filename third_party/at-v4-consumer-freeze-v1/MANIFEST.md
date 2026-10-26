# AT V4 Consumer Freeze Bundle — MANIFEST

## Provenance

| Field | Value |
|-------|-------|
| Source repo | `D:\codebank\AndCode\AndroidTerminal-V4` |
| Freeze tag | `AT.V4-CONSUMER-FREEZE-V1` |
| Freeze commit | `7216d0e8131e92e8914ce7c8443e1668b1d53aca` |
| Final docs commit | `7f3a7dface6e504970714631ba4fb2224052f5ef` |
| Bundle dir (AT) | `dist/AT.V4-CONSUMER-FREEZE-V1/` |
| Variant | **debug** (AG currently uses release V3 AARs) |
| POM | **not included in AT bundle** (generated here) |
| SHA256 | see `dist/AT.V4-CONSUMER-FREEZE-V1/SHA256SUMS.txt` in AT repo |

## Maven coordinates (repo-local)

All artifacts are resolved from `third_party/at-v4-consumer-freeze-v1/m2`
(wired in `settings.gradle.kts` dependencyResolutionManagement).

| Group | Artifact | Version | Packaging | AT module | Visibility |
|-------|----------|---------|-----------|-----------|------------|
| `com.at.engine.api` | `at-engine-api` | 4.0.0 | jar | `:at-engine-api` | **public contract** |
| `com.at.composition.android` | `at-composition-android` | 4.0.0 | aar | `:at-composition-android` | **consumer entry** |
| `com.at.cli` | `at-cli` | 4.0.0 | aar | `:at-cli` | optional frontend |
| `com.at.engine` | `at-engine` | 4.0.0 | aar | `:at-engine` | internal (transitive) |
| `com.at.engine.spi` | `at-engine-spi` | 4.0.0 | jar | `:at-engine-spi` | internal (transitive) |
| `com.at.backend.local` | `at-backend-local` | 4.0.0 | aar | `:at-backend-local` | internal (transitive) |
| `com.at.backend.adb` | `at-backend-adb` | 4.0.0 | aar | `:at-backend-adb` | internal (transitive) |
| `com.adb.engine` | `adb-engine` | 4.0.0 | aar | `:adb-engine` | internal (transitive) |

## AG consumption boundary

```text
:integration-at  →  api("com.at.engine.api:at-engine-api:4.0.0")     [public contract]
:integration-at  →  api("com.at.composition.android:at-composition-android:4.0.0")  [consumer entry]
:integration-at  →  api("com.at.cli:at-cli:4.0.0")                   [optional frontend]
:app             →  implementation("com.at.composition.android:at-composition-android:4.0.0")  [Host calls ATAndroid.create]
```

AG adapter (`:integration-at`) imports ONLY:
- `com.at.engine.*` (public contract types)
- `com.at.composition.android.*` (assembly facade: ATAndroidComposition, ATAndroidAdbControl, ATAdb* state types)
- `com.at.cli.*` (optional CLI bridge)

AG adapter does NOT import:
- `com.adb.engine.*` (AdbRuntime / AdbConsumerBootstrap / AdbPairingDiscovery)
- `com.at.backend.*` (AdbOneShotBackend / LocalOneShotBackend / etc.)

## V3 → V4 mapping

| V3 (superseded) | V4 (current) |
|-----------------|-------------|
| `com.at.engine:at-engine:1.0.0` (AAR, concrete `ATEngine` class) | `com.at.engine.api:at-engine-api:4.0.0` (JAR, `ATEngine` interface) |
| `com.at.backend.local:at-backend-local:1.0.0` | `com.at.composition.android:at-composition-android:4.0.0` (facade) |
| `com.at.backend.adb:at-backend-adb:1.0.0` | (transitive via composition) |
| `com.adb.engine:adb-engine:1.0.0` | (transitive via composition; AG no longer imports) |
| `com.at.cli:at-cli:1.0.0` | `com.at.cli:at-cli:4.0.0` |
| `ATPersistentBackend.Launch` | `ATSessionLaunch` |
| `AdbRuntimeState` | `ATAdbReadinessState` |
| `AdbBootstrapState` | `ATAdbBootstrapState` |
| `AdbPairingResultV2` | `ATAdbPairingResult` |
| `ATEngine(oneShot=..., persistent=...)` (concrete class) | `ATAndroid.create(context, config)` → `ATAndroidComposition` |

## Superseded

`third_party/at-consumer-freeze-v1/` (V3, baseline c9cee21, release) is
retained for rollback reference only. Its m2 repo entry in
`settings.gradle.kts` is commented out.
