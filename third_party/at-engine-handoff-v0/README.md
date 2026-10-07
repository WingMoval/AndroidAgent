# AT Engine Handoff Bundle — v0

`AT.ARTIFACT-HANDOFF-v0` — the **indivisible delivery unit** for the AT Engine.

> **AT Engine = AT Runtime + AT Core** (logical). The physical v0 distribution
> unit is this **paired AAR bundle**: two AARs that must always be used together,
> plus the metadata, hashes, and verifier that prove the pair is in lockstep.

---

## 1. What is in this bundle

```
at-engine-handoff-v0/
  at-engine-handoff.json     # provenance + artifact hashes + public contract + validation status
  SHA256SUMS                 # sha256 of both AARs + the 3 native libs (machine-checkable)
  README.md                  # this file
  artifacts/
    at-runtime.aar           # the AG-facing STABLE public contract (no core, no .so)
    at-core.aar              # frozen implementation + JNI + arm64-v8a native libs
    native/                  # the 3 .so extracted from at-core.aar (for standalone hash check)
      libandroidterminaladb.so
      libc++_shared.so
      libnetwork_probe.so
  verify/
    verify-at-engine-handoff.ps1   # bundle integrity verifier (PASS / FAIL)
```

## 2. The atomicity rule (read this first)

**The two AARs are a pair. Never replace or swap only one.**

- Use `at-runtime.aar` and `at-core.aar` **together, always**.
- On **any** AT update, **regenerate the ENTIRE bundle** from a clean build so the
  pair, the hashes in `SHA256SUMS`, and `at-engine-handoff.json` stay in lockstep.
- The verifier (`verify/verify-at-engine-handoff.ps1`) enforces this: a mismatch on
  **either** AAR or native lib is a hard **FAIL**.

## 3. Verify the bundle (one command)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File verify\verify-at-engine-handoff.ps1
```

Exit code `0` = **PASS** (complete, in-lockstep, uncorrupted). Exit code `1` = **FAIL**.

The verifier checks: required files present → both AARs are valid non-empty ZIPs →
`at-runtime.aar` carries only the `com.androidterminal.runtime.*` contract (no Core
classes, no `.so`) → `at-core.aar` carries the Core/bridge classes + all 3 arm64-v8a
native libs → on-disk SHA256 of both AARs + the 3 native libs match `SHA256SUMS` →
the hashes recorded in `at-engine-handoff.json` equal those in `SHA256SUMS`.

## 4. Consume the bundle from an external Android app

Copy `artifacts/at-runtime.aar` and `artifacts/at-core.aar` into your project and
add them as **flat file dependencies** (no AT source, no AT Gradle module, no
composite build):

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/at-runtime.aar"))   // the stable public contract
    implementation(files("libs/at-core.aar"))       // implementation + JNI + native
}
```

Keep native packaging identical to the AT consumers so
`System.loadLibrary("androidterminaladb")` resolves the same way:

```kotlin
android {
    packaging { jniLibs { useLegacyPackaging = true } }
    defaultConfig { ndk { abiFilters += "arm64-v8a" } }
}
```

### The ONLY public API you may use

Import **only** `com.androidterminal.runtime.*`. Entry point is the Kotlin object
singleton `com.androidterminal.runtime.AtRuntime`:

```kotlin
import com.androidterminal.runtime.AtRuntime
import com.androidterminal.runtime.AtExecutionResult
import com.androidterminal.runtime.AtRuntimeDiagnostic

// 1. platform bootstrap (call once; wires discovery + readiness backends)
AtRuntime.initialize(applicationContext)

// 2. auto-connect: readiness -> discovery -> endpoint select -> connect -> READY
//    (you supply ONLY the keyDir; you never know the port / mDNS / fallback)
val diag: AtRuntimeDiagnostic = AtRuntime.connectAuto(keyDir)   // diag.isReady

// 3. one non-interactive shell command
val result: AtExecutionResult = AtRuntime.execute("echo hello")
// result.outcome / result.stdout / result.stderr / result.exitCode / result.hasError

// 4. teardown (idempotent)
AtRuntime.disconnect()
```

**You must NOT** import or reference: `TerminalRuntime`, `AdbManager`,
`AtWirelessConnector`, `RecoveryCoordinator`, `EndpointStore`, `DiscoveryBackend`,
`NsdManager`, `_adb-tls-connect`, any hardcoded Wireless-ADB port, or any
recovery / test-only Runtime API. All of that is owned by the AT Engine.

> All `AtRuntime` methods **block** — call them off the main thread.

## 5. Prerequisites & assumptions (v0)

- **Wireless Debugging already enabled** on the device (the Engine detects it; it
  never auto-enables it). If it is off, `connectAuto` returns
  `USER_ACTION_REQUIRED` (`WIRELESS_DEBUGGING_DISABLED`).
- **Identity already trusted** (the device already trusts the caller's adbkey).
- `keyDir` is the **caller's own** private directory (its `filesDir`). No private
  key is committed; a test-only authorized adbkey fixture is injected at validation
  time and removed afterwards.
- **ABI**: `arm64-v8a` only (the only native target built into `at-core.aar`).

## 6. Provenance & validation

- Source: `AndroidTerminal-V2` @ `main`, commit `726a441…` (**dirty** — uncommitted
  2A/2B/2D/AG0). Because HEAD alone does not pin the source, `at-engine-handoff.json`
  records a `sourceTreeSha256` content fingerprint over the relevant tracked sources.
  **AT Core production diff is empty (0 changes to the frozen Core).**
- In-repo handoff consumer (`AT.ENGINE-AG0`): **VERIFIED** (H1–H4 real-device PASS,
  Golden Chain intact).
- External standalone consumer: **build + static API audit + APK native/dex audit =
  PASS** (copied-AAR-only, zero `project()`). Real-device runtime smoke is the
  remaining acceptance gate (see `at-engine-handoff.json.validationStatus`).
- `BUILD SUCCESSFUL` and a well-formed APK are **build/package verification**, not
  runtime PASS. Device acceptance is recorded separately in the implementation doc.
