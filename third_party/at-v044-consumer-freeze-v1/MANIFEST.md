# AT v0.4.4 Consumer Freeze Bundle

Created: 2026-11-10 (AG.AT-V044-CONSUMER-UPDATE-0)

## Identity

```text
AT_VERSION          = v0.4.4
AT_SOURCE_COMMIT    = e2f2d7b (tag v0.4.4)
AT_SOURCE_REPO      = D:\codebank\AndCode\AndroidTerminal-V4
BUILD_VARIANT       = release
GAV_VERSION         = 4.0.0 (stable API, unchanged from v0.4.3)
```

## Artifacts

| Module | GAV | Type |
|--------|-----|------|
| at-engine-api | com.at.engine.api:at-engine-api:4.0.0 | JAR |
| at-engine-spi | com.at.engine.spi:at-engine-spi:4.0.0 | JAR |
| at-engine | com.at.engine:at-engine:4.0.0 | AAR |
| at-backend-local | com.at.backend.local:at-backend-local:4.0.0 | AAR |
| at-backend-adb | com.at.backend.adb:at-backend-adb:4.0.0 | AAR |
| at-cli | com.at.cli:at-cli:4.0.0 | AAR |
| at-composition-android | com.at.composition.android:at-composition-android:4.0.0 | AAR |
| adb-engine | com.adb.engine:adb-engine:4.0.0 | AAR |

## API Delta (v0.4.3 -> v0.4.4)

```text
PUBLIC_API_CHANGE = NONE
at-engine-api     = UNCHANGED (byte-identical API)
at-cli            = UNCHANGED (byte-identical API)
at-composition-android = KDoc comments only (ATAndroid.kt documentation)
```

The v0.4.4 release adds:
- Consumer Entry architecture freeze (documentation)
- AppSessionRegistry / SharedSessionHandle (host-level, :app only)
- Release freeze + signed APK

None of these affect the AG consumer boundary.

## Supersedes

- `at-v043-consumer-freeze-v1` (v0.4.3, now HISTORICAL)
