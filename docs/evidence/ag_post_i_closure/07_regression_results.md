FULL REGRESSION (POST-I CLOSURE AUDIT) -- rerun-tasks, JUnit XML actuals
Run: .\gradlew.bat :ag-core:test :ag-runtime:test :provider-openai-compatible-jdk:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks
BUILD = BUILD SUCCESSFUL (2m51s); assembleDebug = PASS

ag-core : tests=52 failures=0 errors=0
ag-runtime : tests=88 failures=0 errors=0
app : tests=171 failures=0 errors=0
provider-jdk : tests=26 failures=0 errors=0

I11 FLAKY OBSERVED THIS RUN = NO (suite green under full matrix)
