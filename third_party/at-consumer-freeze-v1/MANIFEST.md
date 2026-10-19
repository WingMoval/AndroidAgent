AT Consumer Freeze V1 — AG Vendored Bundle Manifest

HANDOFF                = AT_TO_AG_CONSUMER_HANDOFF_V1
FREEZE_TAG             = AT.AG-CONSUMER-FREEZE-V1
SOURCE_BASELINE        = c9cee2126b3ebc54a62be27d1f7bdea8ded22a7f
FREEZE_PUBLICATION     = a6f23d7112ca40c873b63cba4aef5686e0dd4405
AT_CONTRACT_STATUS     = DRAFT
ABI                    = arm64-v8a
BUILD_VARIANT          = release

ARTIFACTS:

at-engine.aar
  size    = 23778 B
  sha256  = BD2E0A9A580C4442CB137CCEFAB7DF8EEEC23043DDA96B1F4ABC469318E28521
  module  = :at-engine
  native  = (none)

at-backend-adb.aar
  size    = 8627 B
  sha256  = 795CFE8522220FF1EC570B40E7C328B9A95A51748E9D9723D5CF02E500DC52A4
  module  = :at-backend-adb
  native  = (none)

adb-engine.aar
  size    = 1161599 B
  sha256  = EC7234AE2EDB047F831C96166A365814CE0460C343D8CE799A073A895CD66912
  module  = :adb-engine
  native  = lib/arm64-v8a/libadbd_engine.so (2876224 B)

PROVENANCE:
  Built from AT V3 source at exact commit c9cee21 (detached worktree).
  Gradle: :at-engine:assembleRelease :at-backend-adb:assembleRelease :adb-engine:assembleRelease
  No AT source modifications. No :app dependency. No :at-cli included.

NOT A RELEASE:
  This is a vendored consumer snapshot for AG V2 integration.
  It is NOT an AT official Maven release.
  It is NOT a final ABI guarantee.
  AT_CONTRACT_STATUS remains DRAFT.
