# Gradle Physical Dependency Graph (compileClasspath / debugCompileClasspath, real `gradlew dependencies` output)

Evidence captured: `gradlew :<module>:dependencies` per module (JVM modules: compileClasspath;
Android library/app modules: debugCompileClasspath). Project edges only (external libs omitted).

```
                        +----------------------+
                        |      :ag-core        |   (no project deps at all)
                        +----------+-----------+
                                 ^
        +----------------+-------+----------------+
        |                |                         |
+-------+--------+  +----+-----------+  +---------+--------+  +---------------------------+
| :ag-runtime    |  | :provider-openai-|  | :tool-android-  |  | :tool-network            |
|                |  | compatible       |  | shell           |  |                           |
+-------+--------+  +---------+--------+  +---------+-------+  +---------+-----------------+
        ^                ^                       ^                         ^
        |                |                       |                         |
        |        +-------+---------+             |                         |
        |        | :provider-openai-             |                         |
        |        | compatible-jdk                |                         |
        |        +-----------------+             |                         |
        |                            ^           |                         |
        |                            |           |   +---------------------+---------+
        |                            |           |   | :integration-at     |         |
        |                            |           +---| (AT V4 AARs, ONLY   |         |
        |                            |               |  module on AT)      |         |
        |                            |               +---------------------+         |
        |                            |                                               |
        +----------------------------+------------------------------------------------+
                                     |
                              +------v-------+
                              |     :app     |  (Host: also direct :provider-openai-compatible,
                              +--------------+   :tool-*, :integration-at; Room/AndroidX)
```

Verified edges (actual `project :X` lines in the dependency reports):

| From | To | Kind | Status |
|---|---|---|---|
| ag-runtime | ag-core | implementation | ALLOWED (Runtime -> Core) |
| provider-openai-compatible | ag-core | implementation | ALLOWED (Provider -> Core contract) |
| provider-openai-compatible-jdk | provider-openai-compatible | implementation | ALLOWED (transport -> protocol) |
| tool-android-shell | integration-at | **api** | ALLOWED (concrete tool -> AT adapter; AT types visible to tool only) |
| tool-android-shell | ag-core | implementation | ALLOWED (Tool -> Core contract) |
| tool-network | ag-core | implementation | ALLOWED (Tool -> Core contract) |
| tool-file | ag-core | implementation | ALLOWED (Tool -> Core contract) |
| integration-at | (AT V4 AARs: com.at.engine.api:at-engine-api:4.0.0, com.at.cli:at-cli:4.0.0) | maven | ALLOWED (only AT consumption point) |
| app | ag-core, ag-runtime, provider-openai-compatible, tool-android-shell, tool-network, tool-file, integration-at | implementation | ALLOWED (Host composes everything) |

Forbidden edges checked (all ABSENT from the real graphs):

| Forbidden edge | Found? |
|---|---|
| ag-core -> ag-runtime | NO |
| ag-core -> app | NO |
| ag-core -> any tool-* | NO |
| ag-core -> any provider-* | NO |
| ag-core -> Android/Room/AT | NO (pure JVM, no Android plugin, zero android/androidx on compileClasspath) |
| ag-runtime -> app | NO |
| ag-runtime -> Room/Android | NO (pure JVM) |
| ag-runtime -> any tool-* | NO |
| ag-runtime -> any provider-* | NO |
| provider-* -> app | NO |
| provider-* -> tool-* | NO |
| tool-network -> integration-at (AT) | NO (counter-example holds: fetch tool has zero AT) |
| tool-file -> integration-at (AT) | NO |
| integration-at -> ag-core / ag-runtime | NO (adapter is AT-side only; does not import AG domain) |

Direction verdict: matches the frozen concept
`Core <- Runtime <- Host` and `Core <- concrete tools/providers <- Host composition`.
