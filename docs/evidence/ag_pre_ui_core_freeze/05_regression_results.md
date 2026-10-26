FULL MODULE REGRESSION (pre-freeze, --rerun-tasks fresh run)
Tasks: :ag-core:test :ag-runtime:test :provider-openai-compatible:test :provider-openai-compatible-jdk:test
  :tool-android-shell:testDebugUnitTest :tool-network:testDebugUnitTest :tool-file:testDebugUnitTest
  :integration-at:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
Pre-staging regression  = BUILD SUCCESSFUL (2m58s); assembleDebug = PASS
FINAL pre-commit regression (staged candidate) = BUILD SUCCESSFUL (2m35s); assembleDebug = PASS

ag-core                          tests=52   failures=0 errors=0
ag-runtime                       tests=88   failures=0 errors=0
app                              tests=171  failures=0 errors=0
integration-at                   tests=8    failures=0 errors=0
provider-openai-compatible       tests=59   failures=0 errors=0
provider-openai-compatible-jdk   tests=26   failures=0 errors=0
tool-android-shell               tests=20   failures=0 errors=0
tool-file                        tests=55   failures=0 errors=0
tool-network                     tests=78   failures=0 errors=0

GRAND TOTAL tests=557 failures=0

I11 FLAKY OBSERVED THIS RUN = NO (full matrix green)
M3_STATUS = KNOWN_NON_BLOCKING_DEBT (not reproduced this run)
