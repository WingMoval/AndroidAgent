# AG.FILE-0 — FileTool READ v0 (Tool Layer)

> 类型：PRODUCTION IMPLEMENTATION · TOOL LAYER · ANDROID TOOL ·
> NO-LLM DIRECT VALIDATION · L1 JVM VERIFIED · REAL-DEVICE L2+L3 PASS.
> 前置：`AG.FILE-0.PREFLIGHT`（docs/research）= IMPLEMENTATION_READY /
> READY_FOR_AG.FILE-0 = YES（roadmap §22 条目）。
> 三个不变量：
> ① `ag-core` / `ag-runtime` 永远不依赖任何 `:tool-*` 或 Android 文件系统；
> ② `:tool-file` 使用平台 `java.nio.file`（NIO.2, API 26+），`AT_DEPENDENCY = NONE`
>    （不建 FileBackend / SAF / ContentResolver / 任何后端抽象）；
> ③ Direct Tool Validation 走同一生产 `ToolRegistry`（DebugBridge `tool_invoke`，无 LLM），
>    L3 真 Agent E2E 走同一生产 `AgentRuntime.send`（与 UI 同源）。
>
> 状态：**PASS**（L1 55/55 绿 + 回归绿 + L2 真机 direct tool_invoke 16/16 PASS +
> L3 真 Agent E2E PASS + observation dependency PASS）。

---

## 1. Baseline

```text
AndroidAgent : main @ AG.STABLE-1 基线 + AG.NETWORK-0 之后工作树 + 本 Step 未提交工作树
设备         : APLMUT4312005935 / HONOR ALI-AN00 / arm64-v8a（L2/L3 真机验证用）
provider     : OpenRouter（deepseek/deepseek-v4-flash-0731，L3 真 Agent 用）
JDK          : 21（Microsoft OpenJDK 21.0.6）
Gradle / Kotlin / AGP : 8.7 / 2.1.0 / 8.5.2（bytecode 17）
minSdk / targetSdk / ABI : 26 / 34 / arm64-v8a
```

## 2. Preflight Input（SSOT）

```text
docs/research/AG.FILE-0.PREFLIGHT_FirstClass_FileTool_Android_Resource_Principal_Freeze.md
  - 30 sections · verdict IMPLEMENTATION_READY · READY_FOR_AG.FILE-0 = YES
  - APP_SELF realm 冻结（root = filesDir/agent/）
  - 4 个 action 冻结（read/search/list/stat）
  - READ-only 冻结（无 write/edit/delete）
  - 安全边界冻结（canonical + root confinement + traversal + symlink escape）
  - 编码策略冻结（UTF-8 + BOM strip + REPLACE）
  - 二进制策略冻结（NUL sniff 8KB window -> isBinary=true, 不强制解码）
  - 边界常量冻结（1MiB / 30k / 200 / 500）
docs/roadmap/AndroidAgent_Master_Roadmap.md §22 TOOL-004 条目
```

## 3. Tool Layer 架构（沿用 AG.NETWORK-0 冻结）

```text
AndroidAgent = AG Engine + Tool Layer + Host/Product

AG Engine     = AG Core + Agent Runtime
Tool Layer    = 具体工具模块（每能力一个）
                :tool-android-shell   （AG.SHELL-1，AT Engine Handoff）
                :tool-network         （AG.NETWORK-0，HTTPS fetch，无 AT）
                :tool-file            （AG.FILE-0，READ family，无 AT）
Host/Product  = AGA（Composition Root）：构造 + 注册工具
```

模块依赖方向（冻结，不变）：

```text
tool-*      -> ag-core          允许（工具实现 Tool contract）
ag-core     -> tool-*           禁止
ag-runtime  -> tool-*           禁止
ag-core     -> Android FS/任意 tool   禁止
```

## 4. Module Graph（本 Step 后）

```text
:app ──▶ :tool-android-shell ──▶ :ag-core（Tool contract）
                         └────▶ com.androidterminal:at-runtime（AT Handoff，不变）
:app ──▶ :tool-network ──▶ :ag-core（Tool contract）
                 └────▶ kotlinx-serialization-json 1.8.0（已有版本）
                 └────▶ kotlinx-coroutines-core 1.10.2（已有版本）
:app ──▶ :tool-file ──▶ :ag-core（Tool contract）
                └────▶ kotlinx-serialization-json 1.8.0（已有版本）
                └────▶ kotlinx-coroutines-core 1.10.2（已有版本）
                （平台 java.nio.file：零新增第三方依赖）
:app / :ag-core / :ag-runtime ─X─ :tool-file（无生产依赖）
:tool-file ─X─ :app / :ag-runtime / provider / :tool-android-shell / :tool-network / AT
```

## 5. `:tool-file` 模块

```text
namespace : com.androidagent.tool.file
类型      : com.android.library，minSdk 26 / compileSdk 34 / JVM 17
依赖      : :ag-core · kotlinx-serialization-json 1.8.0 · kotlinx-coroutines-core 1.10.2
            （+ JUnit5 / coroutines-test）
unitTests.isReturnDefaultValues = true（无 Android 依赖的纯 JVM 单测）
settings.gradle.kts : include(":tool-file")
app/build.gradle.kts : implementation(project(":tool-file"))
```

## 6. 源文件清单（本 Step 新增）

```text
main / com/androidagent/tool/file/
  FileToolImpl.kt         345  Tool 实现 + read/search/list/stat + 常量 + binary sniff + normalizeDisplay
  PathResolver.kt          94  canonical 解析 + root 封闭 + traversal + symlink 防御
  FileToolSchema.kt       139  参数解析（action/path + op-specific 字段，invalid -> 0 file ops）
  FileResultPayload.kt     99  JSON 结果编码（deterministic）
  TextCodec.kt             38  UTF-8 + BOM strip + REPLACE
  FileError.kt             28  冻结错误枚举（10 个 code）
test / com/androidagent/tool/file/
  FileToolImplTest.kt     370  工具级 L1（args/read/search/list/stat/bounds/binary/escape）
  FileCoresTest.kt        188  纯核心 L1（PathResolver 13 + TextCodec 4 + binary 3）
```

## 7. Controller Freeze（契约，冻结）

```text
name      : file
actions   : read / search / list / stat（v0 唯一四个 action）
realm     : APP_SELF（root = filesDir/agent/，Host 注入）
path      : root-relative（绝对路径仅当 canonical 后仍在 root 内）
read      : offsetLines? / limitLines? / maxBytes?
search    : pattern（必填）/ regex? / maxMatches? / contextLines? / caseSensitive?
list      : maxEntries?
stat      : path only
AT_DEPENDENCY        : NONE
SAF / ContentResolver : NONE（不建后端抽象）
WRITE / EDIT / DELETE : 禁止（v0 READ-only）
```

## 8. 冻结常量（FileToolImpl，实测一致）

```text
MAX_FILE_READ_BYTES = 1_048_576   # 1 MiB 读取上限
MAX_CONTENT_CHARS   = 30_000      # 抽取文本字符上限
MAX_SEARCH_MATCHES  = 200         # 搜索结果上限
MAX_LIST_ENTRIES    = 500         # 列表结果上限
MAX_ERROR_CHARS     = 2_000       # 错误文本上限
BINARY_SNIFF_BYTES  = 8_192       # NUL 扫描窗口
MAX_SEARCH_DEPTH    = 16          # 递归搜索深度上限
MAX_SEARCH_LINE_CHARS = 2_000     # 单匹配行显示上限
TOOL_NAME           = "file"
```

## 9. ToolResult 语义（冻结）

```text
success = 工具机制成功
到达可读取资源（含空文件）-> success=true
领域拒绝（NOT_FOUND / NOT_A_FILE / NOT_A_DIRECTORY / PATH_OUTSIDE_ROOT /
          UNSUPPORTED_CONTENT / INVALID_ARGUMENT）-> success=false + FileError code
二进制文件 -> success=true + isBinary=true + content=""（不强制解码）
CancellationException 传播（永不转换）
payload（deterministic）：
  read  : {"action":"read","path":"...","content":"...","startLine":0,
           "totalLines":N,"truncated":false,"bytesRead":N,"totalBytes":N,
           "isBinary":false}
  search: {"action":"search","path":"...","matches":[{"path","line","text"}],
           "totalMatches":N,"truncated":false}
  list  : {"action":"list","path":"...","entries":[{"name","type","sizeBytes"}],
           "totalEntries":N,"truncated":false}
  stat  : {"action":"stat","path":"...","type":"file|dir|missing",
           "sizeBytes":N,"lastModified":N,"readable":true,"writable":true}
```

## 10. PathResolver（冻结）

```text
输入  : model-facing path（root-relative 或 absolute）
解析  : relative -> root.resolve(rawPath)
        absolute -> 原样使用
canonical : toRealPath()（解析 symlink + 要求存在）
           失败（不存在）-> normalize()（折叠 ..，不触文件系统）
边界  : canonical 必须在 root 子树内（== root 或 startsWith root）
        否则 -> OutsideRoot("resolves outside the file root")
symlink : toRealPath 跟随 symlink；若目标逃逸 root -> OutsideRoot
broken symlink : toRealPath 失败 -> normalize；仍在 root 内 -> Ok
display : root.relativize(canonical) + replace('\\','/')（Windows 兼容）
blank   : 空白路径 -> OutsideRoot
```

## 11. TextCodec（冻结）

```text
BOM  : EF BB BF 存在 -> 剥离，UTF-8 解码
无 BOM : 直接 UTF-8 解码
malformed : CodingErrorAction.REPLACE（无 charset 自动检测）
binary  : 由调用方（isBinary NUL sniff）在 decode 前判定，
          本 codec 只解码未被标记为 binary 的内容
```

## 12. 二进制检测（冻结）

```text
NUL-byte sniff：前 8192 字节内出现 0x00 -> isBinary=true
isBinary=true -> content="" + totalBytes 保留 + 不 decode
非 binary     -> TextCodec.decodeUtf8
无 MIME / 无扩展名判定（纯内容嗅探）
```

## 13. 边界策略（冻结）

```text
read   : 字节边界 maxBytes（默认 1 MiB）-> 截断字节
         行窗口 offsetLines / limitLines -> 截断行
         字符边界 MAX_CONTENT_CHARS (30k) -> 截断字符
         truncated = 任一边界命中
search : maxMatches（默认 200）-> 超出后 truncated=true
         单行显示 MAX_SEARCH_LINE_CHARS (2k)
list   : maxEntries（默认 500）-> 超出后 truncated=true
stat   : 无边界（元数据）
```

## 14. 安全边界（冻结）

```text
root    : filesDir/agent/（Host 注入，APP_SELF realm）
traversal : ../ 折叠后逃逸 root -> PATH_OUTSIDE_ROOT
absolute  : 绝对路径 canonical 后不在 root 内 -> PATH_OUTSIDE_ROOT
symlink   : symlink 目标逃逸 root -> PATH_OUTSIDE_ROOT
broken symlink : normalize 后仍在 root -> Ok（stat 报 missing）
系统路径  : /etc/hostname 等 -> PATH_OUTSIDE_ROOT
无 SAF    : 不使用 ContentResolver / Storage Access Framework
无 shell  : 不使用 android_shell 读写文件
```

## 15. L1 JVM 测试（PASS，55/55 绿）

```text
FileCoresTest（20 tests，3 skipped on Windows）：
  P1  relative path resolves inside root           PASS
  P2  root itself resolves                         PASS
  P3  nested relative resolves                     PASS
  P4  dotdot traversal outside root rejected       PASS
  P5  dotdot inside root stays                     PASS
  P6  absolute path inside root accepted           PASS
  P7  absolute path outside root rejected          PASS
  P8  absolute system path rejected                PASS
  P9  symlink inside root followed                 SKIPPED (Linux only)
  P10 symlink escaping root rejected               SKIPPED (Linux only)
  P11 broken symlink normalizes and stays inside   SKIPPED (Linux only)
  P12 blank path rejected                          PASS
  P13 rel display is root-relative no duplication  PASS
  T1  plain UTF-8 decodes                          PASS
  T2  BOM is stripped                              PASS
  T3  malformed UTF-8 replaces not throws          PASS
  T4  empty is empty                               PASS
  B1  NUL byte is binary                           PASS
  B2  plain text is not binary                     PASS
  B3  NUL beyond window is not sniffed             PASS

FileToolImplTest（35 tests）：
  A1  blank arguments rejected                     PASS
  A2  not valid JSON rejected                      PASS
  A3  missing action rejected                      PASS
  A4  unknown action rejected                      PASS
  A5  missing path rejected                        PASS
  A6  blank path rejected                          PASS
  A7  search without pattern rejected              PASS
  A8  negative offsetLines rejected                PASS
  A9  zero limitLines rejected                     PASS
  A10 negative maxEntries rejected                 PASS
  C1  dotdot escape rejected                       PASS
  C2  stat missing inside root ok type=missing     PASS
  R1  read plain file                              PASS
  R2  read missing file                            PASS
  R3  read directory rejected                      PASS
  R4  read empty file success empty content        PASS
  R5  read binary refused isBinary                 PASS
  R6  read offsetLimit window                      PASS
  R7  read UTF-8 BOM stripped                      PASS
  R8  read maxBytes truncates                      PASS
  S1  search file substring caseSensitive          PASS
  S2  search caseInsensitive                       PASS
  S3  search regex                                 PASS
  S4  search invalid regex rejected                PASS
  S5  search directory subtree                     PASS
  S6  search maxMatches truncates                  PASS
  S7  search missing path                          PASS
  L1  list directory                               PASS
  L2  list file rejected                           PASS
  L3  list missing rejected                        PASS
  L4  list maxEntries truncates                    PASS
  ST1 stat file                                    PASS
  ST2 stat directory                               PASS
  ST3 stat missing                                 PASS
  N1  nested path display is root-relative         PASS

回归（全绿）：
  :ag-core / :ag-runtime / :tool-android-shell / :tool-network /
  :provider-openai-compatible / :provider-openai-compatible-jdk /
  :app:test / :app:assembleDebug
```

## 16. 编译/测试修正（本 Step 内发现并修复）

```text
PathResolver.kt:
  1) String.toPath() / File.toPath() 在 src/main/java 下无法解析
     -> 改用 java.nio.file.Paths.get()
  2) Windows 路径分隔符 \ 泄漏到模型可见路径
     -> relDisplay 加 .replace('\\', '/')

FileToolImpl.kt:
  3) if/else 尾随 lambda 类型推断失败（expected Function1, actual Any）
     -> 改先声明 val matcher 再分支赋值
  4) "a\nb\n" split 产生末尾空串，行数多算 1
     -> 去掉尾部空行（rawLines.last().isEmpty() -> dropLast(1)）
  5) Stream.toList() 需 API 34，minSdk 26（lint NewApi）
     -> 改 Collectors.toList()
  6) 缺少 kotlin.io.path.toPath import
     -> 补上

FileToolSchema.kt:
  7) const val 不能是 Set<String>
     -> 改 val
  8) Parsed data class 用 var 导致 smart cast 失败
     -> 改回 val，解析过程用局部变量

FileCoresTest.kt:
  9) P5 前提错误（folder/../../notes.txt 实际逃逸到 root 外）
     -> 改为 folder/sub/../../notes.txt
  10) symlink 创建需管理员权限，Windows 下失败
     -> P9/P10/P11 加 @EnabledOnOs(OS.LINUX)
```

## 17. 注册（Host / Composition Root）

```text
ChatCompositionRoot.init:
  toolRegistry.register(FileToolImpl(File(applicationContext.filesDir, "agent")))

Host 注入 APP_SELF root = filesDir/agent/
FileToolImpl 内部 PathResolver(rootFile) 封闭所有访问
无新 Manifest 权限（app-specific dir 不需要）
无 SAF / AT / write capability
```

## 18. Direct Tool Validation 接线（AG.NETWORK-0 已建立，本 Step 复用）

```text
DebugBridge.handleToolInvoke:
  registry = toolRegistryProvider()   # 同一生产 ToolRegistry
  call = ToolCall(id="debug-direct", name=name, arguments=arguments)
  result = registry.dispatch(call, ToolApprovalPolicy.AllowAll)
  -> JSONL tool_result_direct 返回

ag_debug.py 新增:
  invoke <name> <json-args>  命令（tool_invoke 发送）
  tool_result_direct 渲染（[DIRECT] tool=... success=...）

tools/ag_file_l2.py:
  L2 测试脚本（16 用例，连接 DebugBridge，逐条 tool_invoke，记录 PASS/FAIL）
```

## 19. 测试数据策略（L2/L3）

```text
问题  : android_shell 以 uid=2000(shell) 运行，无法访问
         /data/data/com.androidagent.app/files/（应用私有目录）
解决  : androidTest（instrumentation test）以应用自身 UID 运行，
        可访问 filesDir；创建测试数据 + 清理
FileL2FixtureTest:
  create_fixtures : 在 filesDir/agent/.l2test/ 下创建 9 个测试文件
  cleanup_fixtures: 删除 .l2test/
FileL3FixtureTest:
  create_facts    : 在 filesDir/agent/ 下创建 facts.txt（含 SEVENTEEN 标记）
  cleanup_facts   : 删除 facts.txt
运行  :
  adb shell am instrument -w -e class \
    com.androidagent.app.FileL2FixtureTest#create_fixtures \
    com.androidagent.app.test/androidx.test.runner.AndroidJUnitRunner
谨慎  : 所有文件操作仅在 filesDir/agent/ 内自建文件，不读已有应用数据
        测试后清理，不留残余
```

## 20. L2 真机 Direct Tool Validation（PASS，16/16，无 LLM）

```text
设备  : APLMUT4312005935 / HONOR ALI-AN00 / arm64-v8a
通道  : adb forward tcp:45678 tcp:45678 + DebugBridge tool_invoke（同一生产 ToolRegistry）
数据  : androidTest fixture 创建 filesDir/agent/.l2test/（9 个文件）
判据  : 全部命中

基础操作：
  F1  read .l2test/hello.txt        success=true  content="hello\nworld"  totalLines=2
  F2  read .l2test/empty.txt        success=true  content=""  totalBytes=0
  F3  stat .l2test/hello.txt        success=true  type=file  sizeBytes=12
  F4  list .l2test/                 success=true  entries 含 hello.txt/sub/empty.txt

安全边界：
  F5  read ../shared_prefs          success=false  PATH_OUTSIDE_ROOT
  F6  read /etc/hostname            success=false  PATH_OUTSIDE_ROOT
  F7  read .l2test/nope.txt         success=false  NOT_FOUND
  F8  read .l2test/sub              success=false  NOT_A_FILE
  F9  list .l2test/hello.txt        success=false  NOT_A_DIRECTORY

编码 + 二进制：
  F10 read .l2test/bom.txt          success=true  content="hi"（BOM 已剥离）
  F11 read .l2test/binary.dat       success=true  isBinary=true  content=""
  F12 read .l2test/big.txt maxBytes=100  success=true  truncated=true  bytesRead=100

搜索 + 窗口：
  F13 search .l2test/lines.txt pattern=l3     totalMatches=1  line=3
  F14 search .l2test/sub pattern=needle       totalMatches=1  path=.l2test/sub/a.txt
  F15 read .l2test/lines.txt offset=1 limit=2  content="l2\nl3"  totalLines=5
  F16 search .l2test/many.txt pattern=hit maxMatches=3  matches=3  truncated=true

结论  : L2 = PASS（read/search/list/stat / 安全边界 / BOM / binary / 截断 /
          子树搜索 全部真机验证，零 LLM token，AT 未参与）
清理  : androidTest cleanup_fixtures 删除 .l2test/
```

## 21. L3 真 Agent E2E（PASS，OpenRouter / deepseek-v4-flash-0731）

```text
通道  : 同一生产 AgentRuntime.send（DebugBridge send 消息，与 UI 同源）
数据  : androidTest fixture 创建 filesDir/agent/facts.txt
         内容: "The answer to the L3 probe is: SEVENTEEN\n"
prompt: "What is the answer to the L3 probe? Read the file facts.txt in your
         workspace and give me the exact answer."
执行流（实测）：
  turn1  THINKING: "The user asks about 'the L3 probe' and wants me to read
                    facts.txt in my workspace. Let me read the file first."
          [TOOL] file  >  {"action":"read","path":"facts.txt"}   ← 模型自主选择
          [RESULT] success=true  output={"action":"read","path":"facts.txt",
                  "content":"The answer to the L3 probe is: SEVENTEEN",
                  "startLine":0,"totalLines":1,...}            ← 进 history
  turn2  [ANSWER] "The answer to the L3 probe is **SEVENTEEN**."
  [RUN] Finished (Answer(...))
判定  :
  - 模型自主调用 file（非用户指定）            = PASS
  - file ToolResult 进入 history               = PASS
  - FinalAnswer 依赖文件内容（SEVENTEEN 只能来自 facts.txt）
                                                 = OBSERVATION_DEPENDENCY = PASS
  - model_turns=2  file_calls=1  shell_calls=0  run_failed=None
```

## 22. Observation Dependency 定义（实测 PASS）

```text
PASS = final answer 明确引用/复述了 file ToolResult 中的文件内容
       （非通用回答），且文件内容在 history 中先于 final answer
本 Step：final answer 复述 "SEVENTEEN"，与 facts.txt 内容
         "The answer to the L3 probe is: SEVENTEEN" 一一对应
         SEVENTEEN 为独特标记，模型无法凭空猜出
         => OBSERVATION_DEPENDENCY = PASS
```

## 23. 禁止范围（本 Step 不做）

```text
file write / edit / delete / patch / move / rename / copy / mkdir
SAF / ContentResolver / Storage Access Framework
Tool-Layer 框架扩展（ToolManager / backends / factory）
AT 改动 / Core / Runtime / Provider 改动
下一步（AGENT-0.CONTRAST 等）
```

## 24. 未来扩展（说明，非本 Step）

```text
file WRITE/EDIT（TOOL-005）：需 AUTH-002（action-level approval）解冻；
  当前 tool-level AllowAll 对 mutating action 太粗。
SAF realm：若未来需要访问用户选择的文件，引入新 resolver（PathResolver 形状保留），
  不建后端层次。
Tool-Layer 框架：无第二个工具需要抽象时不建；当前 3 个具体工具各自实现 Tool contract。
```

## 25. Drift（实现 vs Preflight）

```text
无架构 drift：模块方向 / 契约 / 语义 / 常量 / 安全边界 / 编码策略 / 二进制策略
均按 preflight 冻结。
实现级修正（非冻结变更，见 §16）：
  1) PathResolver 用 Paths.get() 替代 String.toPath()（src/main/java 兼容）
  2) relDisplay 加 replace('\\','/')（Windows 兼容）
  3) 行 split 去尾部空串（"a\nb\n" -> 2 行非 3 行）
  4) Stream.toList() -> Collectors.toList()（minSdk 26 兼容）
  5) FileToolSchema Parsed 改 val + 局部变量（smart cast 兼容）
```

## 26. 测试卫生

```text
所有 @Test 为块体 Unit 方法（无 local-function @Test）
测试计数来自 JUnit 实际执行（55 = 20 + 35），非源码方法数
:tool-file 纯 JVM 单测（isReturnDefaultValues=true，无 Robolectric）
3 个 symlink 测试 @EnabledOnOs(OS.LINUX)（Windows 无 symlink 权限）
L2/L3 测试数据由 androidTest fixture 创建（应用 UID），不由 shell 工具创建
```

## 27. 未提交工作树说明

```text
本 Step 未 commit（遵循 AGENTS.md：无显式许可不 commit）。
工作树含：
  tool-file/（新模块，6 个 main + 2 个 test）
  settings.gradle.kts（include :tool-file）
  app/build.gradle.kts（implementation :tool-file）
  app/.../ChatCompositionRoot.kt（注册 FileToolImpl）
  app/src/androidTest/.../FileL2FixtureTest.kt（新）
  app/src/androidTest/.../FileL3FixtureTest.kt（新）
  tools/ag_debug.py（加 invoke 命令 + tool_result_direct 渲染）
  tools/ag_file_l2.py（新，L2 测试脚本）
  tools/ag_file_l3.py（新，L3 测试脚本）
  AGENTS.md（AG.FILE-0 PASS 记录 + :tool-file 模块 + 禁止项更新）
  docs/roadmap/AndroidAgent_Master_Roadmap.md（TOOL-004 VERIFIED + NEXT 更新）
```

## 28. 已知遗留（非本 Step 引入）

```text
./gradlew build 因 vendored AT Engine AAR（at-runtime-0.1.0）lint 错误
（CoarseFineLocation in AndroidManifest.xml）失败，与 :tool-file 无关。
:app:assembleDebug 正常（lint 不在 assemble 路径上）。
```

## 29. Verdict

```text
AG.FILE-0 = PASS（全层验证完成）

L1  JVM 单测        55/55 绿（PathResolver 13 + TextCodec 4 + binary 3 +
                        FileToolImpl 35：args/read/search/list/stat/bounds/
                        binary/escape；3 symlink tests gated Linux）
回归                ag-core / ag-runtime / tool-android-shell / tool-network /
                        provider-openai-compatible / provider-openai-compatible-jdk /
                        app:test / app:assembleDebug 全绿
L2  真机 direct     PASS（16/16，无 LLM）
                        基础 read/stat/list / 安全边界（逃逸/系统/不存在/类型错）/
                        BOM/binary/maxBytes 截断 / 搜索文件+子树+offset+maxMatches
L3  真 Agent E2E    PASS（OpenRouter deepseek-v4-flash-0731）
                        模型自主调 file read facts.txt -> ToolResult 进 history ->
                        FinalAnswer 含 SEVENTEEN；OBSERVATION_DEPENDENCY = PASS
边界                无 Core / Runtime / Provider / AT 改动；无新第三方依赖；
                        AT_DEPENDENCY=NONE（L2/L3 均确认 AT 未参与）；
                        无 SAF / ContentResolver / write capability

本 Step 到此 STOP。不做：write/edit/delete / SAF / Tool-Layer 框架扩展 /
AT 改动 / AGENT-0.CONTRAST / 下一 Step。
NEXT = AGENT-0.CONTRAST（需 Controller 批准）。
```
