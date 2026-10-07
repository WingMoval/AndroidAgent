<#
  AT Engine Handoff Bundle verifier — v0
  ======================================
  Validates that a bundle directory is a complete, uncorrupted, in-lockstep
  "versioned paired AAR bundle". A bundle is VALID only when ALL of the
  following hold (the pair is atomic — a mismatch on ANY artifact is FAIL):

    1. Required files exist:
         at-engine-handoff.json
         SHA256SUMS
         artifacts/at-runtime.aar
         artifacts/at-core.aar
    2. Both AARs are non-empty ZIP archives (AARs are zip containers).
    3. at-runtime.aar contains ONLY com/androidterminal/runtime/* classes
       (the stable contract) and NO Core classes and NO native .so.
    4. at-core.aar contains the Core/bridge classes AND the arm64-v8a native
       libs (libandroidterminaladb.so / libc++_shared.so / libnetwork_probe.so).
    5. The on-disk SHA256 of BOTH AARs and the three extracted native .so files
       match SHA256SUMS (which must itself match at-engine-handoff.json).
    6. Cross-check: the sha256 recorded in at-engine-handoff.json equals the
       sha256 recorded in SHA256SUMS for each artifact (metadata in lockstep).

  Usage:
    powershell -NoProfile -ExecutionPolicy Bypass -File verify-at-engine-handoff.ps1 [-BundleDir <path>]
  Exit code 0 = PASS, 1 = FAIL.
#>
param(
  [string]$BundleDir
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Default bundle dir: the parent of this verify/ folder (the bundle root).
if (-not $BundleDir) {
  if ($PSScriptRoot) { $BundleDir = Split-Path -Parent $PSScriptRoot }
  else { $BundleDir = (Get-Location).Path }
}
$BundleDir = (Resolve-Path $BundleDir).Path

$failures = @()
$passes   = @()
function Pass($m) { $script:passes += $m; Write-Host ("  [PASS] " + $m) -ForegroundColor Green }
function Fail($m) { $script:failures += $m; Write-Host ("  [FAIL] " + $m) -ForegroundColor Red }

Write-Host ""
Write-Host ("Verifying AT Engine Handoff Bundle: " + (Resolve-Path $BundleDir).Path) -ForegroundColor Cyan
Write-Host ""

# ---- 1. Required files ----------------------------------------------------
$required = @(
  'at-engine-handoff.json',
  'SHA256SUMS',
  'artifacts\at-runtime.aar',
  'artifacts\at-core.aar'
)
foreach ($r in $required) {
  $p = Join-Path $BundleDir $r
  if (Test-Path -LiteralPath $p) { Pass ("exists: " + $r) } else { Fail ("missing: " + $r) }
}
if ($script:failures.Count -gt 0) { Write-Host "`nABORT: required files missing." -ForegroundColor Yellow; exit 1 }

$rtPath = Join-Path $BundleDir 'artifacts\at-runtime.aar'
$corePath = Join-Path $BundleDir 'artifacts\at-core.aar'

# ---- 2. Non-empty ZIP ------------------------------------------------------
function IsZip($path) {
  try {
    if ((Get-Item $path).Length -le 0) { return $false }
    $z = [System.IO.Compression.ZipFile]::OpenRead($path)
    $n = $z.Entries.Count
    $z.Dispose()
    return ($n -gt 0)
  } catch { return $false }
}
if (IsZip $rtPath)   { Pass "at-runtime.aar is a non-empty ZIP" } else { Fail "at-runtime.aar is NOT a valid non-empty ZIP" }
if (IsZip $corePath) { Pass "at-core.aar is a non-empty ZIP" }     else { Fail "at-core.aar is NOT a valid non-empty ZIP" }

# An AAR stores its bytecode inside a NESTED classes.jar. We open the AAR,
# extract that classes.jar, then open the inner jar to enumerate the .class
# entries. Native libs live at the AAR top level (jni/<abi>/*.so).
function Get-ClassEntries($aarPath) {
  $tmp = Join-Path $env:TEMP ("ag0_verify_cls_" + [guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Force -Path $tmp | Out-Null
  try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($aarPath, $tmp)
    $jar = Join-Path $tmp 'classes.jar'
    if (-not (Test-Path $jar)) { return @() }
    $zj = [System.IO.Compression.ZipFile]::OpenRead($jar)
    $names = @($zj.Entries | ForEach-Object { $_.FullName } | Where-Object { $_ -match '\.class$' })
    $zj.Dispose()
    return $names
  } finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
  }
}
function Get-TopEntries($aarPath) {
  $z = [System.IO.Compression.ZipFile]::OpenRead($aarPath)
  $names = @($z.Entries | ForEach-Object { $_.FullName })
  $z.Dispose()
  return $names
}

# ---- 3. at-runtime.aar purity ---------------------------------------------
$rtClasses   = Get-ClassEntries $rtPath
$rtTop       = Get-TopEntries $rtPath
$rtHasCoreClass = @($rtClasses | Where-Object { $_ -match 'com/androidterminal/(TerminalRuntime|AdbManager|ConnectionState|ShellOutcome|TerminalResult|AdbKeyStore)\.class$' }).Count
$rtHasNative    = @($rtTop     | Where-Object { $_ -match '\.so$' }).Count
$rtHasRuntime   = @($rtClasses | Where-Object { $_ -match '^com/androidterminal/runtime/.*\.class$' }).Count
if ($rtHasRuntime -gt 0) { Pass ("at-runtime.aar carries runtime contract classes (" + $rtHasRuntime + ")") } else { Fail "at-runtime.aar has NO com/androidterminal/runtime/*.class" }
if ($rtHasCoreClass -eq 0) { Pass "at-runtime.aar contains NO Core classes" } else { Fail ("at-runtime.aar CONTAINS Core classes (leak): " + $rtHasCoreClass) }
if ($rtHasNative -eq 0)    { Pass "at-runtime.aar contains NO native .so" }    else { Fail ("at-runtime.aar CONTAINS native .so (leak): " + $rtHasNative) }

# ---- 4. at-core.aar content ------------------------------------------------
$coreClasses = Get-ClassEntries $corePath
$coreTop     = Get-TopEntries $corePath
$coreHasBridge = @($coreClasses | Where-Object { $_ -match 'com/androidterminal/(TerminalRuntime|AdbManager)\.class$' }).Count
$coreSo = @($coreTop | Where-Object { $_ -match 'jni/arm64-v8a/.*\.so$' })
$needSo = @('libandroidterminaladb.so','libc++_shared.so','libnetwork_probe.so')
# $coreSo holds full paths; match by the trailing "jni/arm64-v8a/<name>" suffix.
$missingSo = @($needSo | Where-Object { $n = $_; @($coreSo | Where-Object { $_ -like ('jni/arm64-v8a/' + $n) }).Count -eq 0 })
if ($coreHasBridge -gt 0) { Pass "at-core.aar carries Core/bridge classes" } else { Fail "at-core.aar has NO Core/bridge classes" }
if ($missingSo.Count -eq 0) { Pass "at-core.aar carries all 3 arm64-v8a native libs" } else { Fail ("at-core.aar MISSING native libs: " + ($missingSo -join ', ')) }

# ---- 5. SHA256 of AARs + extracted native libs -----------------------------
$expected = @{}
Get-Content (Join-Path $BundleDir 'SHA256SUMS') | ForEach-Object {
  $line = $_.Trim()
  if ($line -and -not ($line.StartsWith('#'))) {
    $parts = $line -split '\s{2,}'
    if ($parts.Count -ge 2) { $expected[$parts[1].Replace('\','/').ToLower()] = $parts[0].ToLower() }
  }
}

function Sha256Lower($p) { (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLower() }

function CheckHash($relPosix, $actualHash, $label) {
  $exp = $expected[$relPosix.ToLower()]
  if (-not $exp) { Fail ("no expected sha256 recorded for " + $label); return }
  if ($actualHash -eq $exp) { Pass ("sha256 OK: " + $label) } else { Fail ("sha256 MISMATCH: " + $label + " (got " + $actualHash + ", want " + $exp + ")") }
}

CheckHash 'artifacts/at-runtime.aar' (Sha256Lower $rtPath)   'at-runtime.aar'
CheckHash 'artifacts/at-core.aar'    (Sha256Lower $corePath) 'at-core.aar'

# extract native libs from the on-disk at-core.aar and hash them
$tmp = Join-Path $env:TEMP ("ag0_verify_" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
  [System.IO.Compression.ZipFile]::ExtractToDirectory($corePath, $tmp)
  $soDir = Join-Path $tmp 'jni\arm64-v8a'
  foreach ($so in $needSo) {
    $f = Join-Path $soDir $so
    if (Test-Path $f) { CheckHash ("artifacts/native/" + $so) (Sha256Lower $f) ("native/" + $so) }
    else { Fail ("native lib not present in at-core.aar: " + $so) }
  }
} finally {
  if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}

# ---- 6. Metadata lockstep (json vs SHA256SUMS) -----------------------------
$json = Get-Content (Join-Path $BundleDir 'at-engine-handoff.json') -Raw | ConvertFrom-Json
function JsonHash($artName) {
  return [string]$json.artifacts.$artName.sha256
}
foreach ($pair in @(@('at-runtime.aar','artifacts/at-runtime.aar'), @('at-core.aar','artifacts/at-core.aar'))) {
  $jsonKey = $pair[0]; $sumsKey = $pair[1]
  $jh = JsonHash $jsonKey
  $sh = $expected[$sumsKey.ToLower()]
  if ($jh -and $jh.ToLower() -eq $sh) { Pass ("metadata lockstep: " + $jsonKey + " (json == SHA256SUMS)") }
  else { Fail ("metadata MISMATCH: " + $jsonKey + " (json=" + $jh + " sums=" + $sh + ")") }
}

# ---- Verdict ---------------------------------------------------------------
Write-Host ""
Write-Host ("PASS=" + $passes.Count + "  FAIL=" + $failures.Count) -ForegroundColor Cyan
if ($failures.Count -eq 0) {
  Write-Host "RESULT: PASS — bundle is complete, in-lockstep, and uncorrupted." -ForegroundColor Green
  exit 0
} else {
  Write-Host "RESULT: FAIL — bundle is NOT valid. See [FAIL] lines above." -ForegroundColor Red
  exit 1
}
