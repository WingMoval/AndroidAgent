PHYSICAL ISOLATION GUARD (pre-freeze re-check)
ag-core:
  android/androidx imports = 0 + 0
  Room CODE refs (non-comment) = 0
  app/tool/provider/runtime imports = 0
  AT imports = 1
ag-runtime:
  android/androidx imports = 0 + 0
  Room CODE refs (non-comment) = 2
  app/tool/provider imports = 0
  AT imports = 0
Gradle forbidden edges (from 02, re-confirmed via settings/gradle unchanged): ag-core->(runtime/app/tool/provider/AT)=NONE; ag-runtime->(app/Room/tool/provider/AT)=NONE
PHYSICAL_ISOLATION_GUARD = PASS
