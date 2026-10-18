# AG.NETWORK-0 — NetworkTool Fetch v0 (Tool Layer)

> 类型：PRODUCTION IMPLEMENTATION · TOOL LAYER FORMALIZATION · ANDROID TOOL ·
> NO-LLM DIRECT VALIDATION · L1 JVM VERIFIED · REAL-DEVICE L2+L3 PASS.
> 前置：`AG.NETWORK-0.PREFLIGHT`（docs/research）= COMPLETE / READY_FOR_AG.NETWORK-0 = YES.
> 三个不变量：
> ① `ag-core` / `ag-runtime` 永远不依赖任何 `:tool-*` 或 Android HTTP；
> ② `:tool-network` 使用平台 `HttpsURLConnection`，`AT_DEPENDENCY = NONE`（不建 NetworkBackend /
>    ShellNetworkBridge / AT adapter）；
> ③ Direct Tool Validation 走同一生产 `ToolRegistry`（DebugBridge `tool_invoke`，无 LLM），
>    不建第二份 fetch 实现、不建 model-visible 工具。
>
> 状态：**PASS**（L1 78/78 绿 + 回归绿 + L2 真机直接 fetch PASS + L3 真 Agent E2E PASS +
> observation dependency PASS + android_shell 回归 PASS）。

---

## 1. Baseline

```text
AndroidAgent : main @ 868eacf（AG.OSERVABILITY-0 close 之后）+ 本 Step 未提交工作树
设备         : APLMUT4312005935 / HONOR ALI-AN00 / arm64-v8a（L2/L3 真机验证用）
provider     : OpenRouter（deepseek/deepseek-v4-flash-0731，L3 真 Agent 用）
JDK          : 21（C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot）
Gradle / Kotlin / AGP : 8.7 / 2.1.0 / 8.5.2（bytecode 17）
minSdk / targetSdk / ABI : 26 / 34 / arm64-v8a
```

## 2. Preflight Input（SSOT）

```text
docs/research/AG.NETWORK-0.PREFLIGHT_NetworkTool_Android_Implementation_Freeze.md
  - 25 sections · verdict COMPLETE · READY_FOR_AG.NETWORK-0 = YES
  - 唯一 SOFT blocker = §9（pinning 机制）→ 本 Step 以 Direct Tool Validation 解决
docs/research/AG.RESEARCH-3_Network_Capability_Deep_Dive.md（能力深潜）
docs/research/AG.RESEARCH-2_Mature_Agent_Full_Capability_Inventory.md（9 层模型）
```

## 3. Tool Layer 架构决定（本 Step 冻结）

```text
AndroidAgent = AG Engine + Tool Layer + Host/Product

AG Engine     = AG Core + Agent Runtime
Tool Layer    = 具体工具模块（每能力一个）
                :tool-android-shell   （AG.SHELL-1，AT Engine Handoff）
                :tool-network         （AG.NETWORK-0，HTTPS fetch，无 AT）
Host/Product  = AGA（Composition Root）：构造 + 注册工具
```

模块依赖方向（冻结）：

```text
tool-*      -> ag-core          允许（工具实现 Tool contract）
ag-core     -> tool-*           禁止
ag-runtime  -> tool-*           禁止
ag-core     -> Android HTTP/任意 tool   禁止
```

禁止项（冻结）：无 `:tool-layer` 中心模块；无 ToolManager / ToolRuntime /
ToolBackend / ToolFactory 框架；无 NetworkBackend / AppNetworkBackend /
AtNetworkBackend / ShellNetworkBridge / AT adapter。工具 = 实现冻结 `Tool`
contract 的具体类；Host 把具体实例注册进 `ToolRegistry`。新增能力 = 新增
`:tool-*` 模块（或既有工具模块内新类），Host 注册。AG Core / Agent Runtime
为加工具而改动 = 0。

## 4. Module Graph（本 Step 后）

```text
:app ──▶ :tool-android-shell ──▶ :ag-core（Tool contract）
                        └────▶ com.androidterminal:at-runtime（AT Handoff，不变）
:app ──▶ :tool-network ──▶ :ag-core（Tool contract）
                └────▶ kotlinx-serialization-json 1.8.0（已有版本）
                └────▶ kotlinx-coroutines-core 1.10.2（已有版本）
                （平台 HttpsURLConnection：零新增第三方依赖）
:app / :ag-core / :ag-runtime ─X─ :tool-network（无生产依赖）
:tool-network ─X─ :app / :ag-runtime / provider / :tool-android-shell / AT
```

## 5. `:tool-network` 模块

```text
namespace : com.androidagent.tool.network
类型      : com.android.library，minSdk 26 / compileSdk 34 / JVM 17
依赖      : :ag-core · kotlinx-serialization-json 1.8.0 · kotlinx-coroutines-core 1.10.2
            （+ JUnit5 / coroutines-test）
unitTests.isReturnDefaultValues = true（无 Android 依赖的纯 JVM 单测）
settings.gradle.kts : include(":tool-network")
app/build.gradle.kts : implementation(project(":tool-network"))
```

## 6. 源文件清单（本 Step 新增）

```text
main / com/androidagent/tool/network/
  NetworkToolImpl.kt      169  Tool 实现 + 常量 + doFetch 流程
  AppHttpFetcher.kt       221  真实传输（重定向循环 + 逐跳再校验 + 字节封顶流式读 + gzip/deflate + 超时 + finally disconnect）
  PublicIpGuard.kt        140  SSRF 核心（IPv4/IPv6 分类，mapped-unwrap，全公网规则）
  UrlValidator.kt         120  scheme/host 归一/IDN/creds/port/IP-literal（纯语法，无 DNS）
  NetworkResultMapper.kt   84  content-type 策略 + 文本上限
  HtmlToText.kt            86  最小 HTML→文本抽取器
  Fetcher.kt               74  tool-local 传输 seam + FetchOutcome + TransportError
  NetworkResultPayload.kt  47  JSON 编码
  NetworkToolSchema.kt     56  参数解析（action/url 仅此二项）
  NetworkError.kt          27  冻结错误枚举
test / com/androidagent/tool/network/
  NetworkToolImplTest.kt  602  工具级 L1（args/SSRF/DNS/redirect/HTTP status/content-type/bounds/cancellation）
  NetworkCoresTest.kt     203  纯核心 L1（PublicIpGuard / UrlValidator / HtmlToText）
```

## 7. Controller Freeze（契约，冻结）

```text
name      : network
action    : fetch（KEEP；v0 唯一 action）
args      : {"action":"fetch","url":"https://..."}（仅此二字段）
scheme    : HTTPS-only（http/file/ftp → UNSUPPORTED_SCHEME）
network   : PUBLIC_NETWORK_ONLY（完整 IPv4+IPv6 SSRF guard）
method    : GET-only
AT_DEPENDENCY        : NONE
SHELL_NETWORK_DEPENDENCY : NONE
禁止（v0）：search / download / request / POST / auth / cookie / streaming /
            WebSocket / browser / JS / PDF / image / spill-to-file（无 FileTool）
```

## 8. 冻结常量（NetworkToolImpl，实测一致）

```text
MAX_RESPONSE_BYTES = 524_288   # 512 KiB 解压后上限
MAX_CONTENT_CHARS  = 30_000    # 抽取文本字符上限
MAX_ERROR_CHARS    = 2_000
CONNECT_TIMEOUT_MS = 15_000
READ_TIMEOUT_MS    = 30_000
MAX_REDIRECT_HOPS  = 5
TOOL_NAME          = "network"
```

## 9. ToolResult 语义（冻结）

```text
success = 工具机制成功（NOT HTTP 2xx）
到达 HTTP 响应（200/404/500）-> success=true + 真实 httpStatus
DESTINATION_BLOCKED / 超时 / DNS / TLS -> success=false
CancellationException 传播（永不转换）
payload（deterministic）：
  {"action":"fetch","requestedUrl":"...","finalUrl":"...","httpStatus":200,
   "contentType":"text/html","content":"...","truncated":false,
   "bytesRead":N,"redirectCount":0[, "note":"..."]}
失败：
  {"action":"fetch","error":"DESTINATION_BLOCKED","message":"..."}
```

## 10. Transport（冻结）

```text
平台 HttpsURLConnection（与 provider transport 同类；零新增第三方依赖）
INTERNET 权限：app/AndroidManifest.xml 已存在（line 6），不改；不加 NSC
重定向：跟随 <= MAX_REDIRECT_HOPS 跳，每跳重新走 PublicIpGuard（防 SSRF 重定向逃逸）
禁止：https -> http 降级
流式读：边读边计字节，超 MAX_RESPONSE_BYTES 即截断（不整包加载）
压缩：gzip / deflate 自动解压
超时：connect 15s / read 30s
资源：finally disconnect()
```

## 11. SSRF（冻结，PUBLIC_NETWORK_ONLY）

```text
IPv4 全阻断（边界显式判定，PublicIpGuard.isPublicV4）：
  0.0.0.0/8 · 127.0.0.0/8 · 10.0.0.0/8 · 172.16.0.0/12 · 192.168.0.0/16
  169.254.0.0/16（含云 metadata 169.254.169.254）· 100.64.0.0/10（CGNAT）
  192.0.0.0/24 · 192.0.2.0/24 · 198.51.100.0/24 · 203.0.113.0/24（TEST-NET）
  198.18.0.0/15（benchmarking）· 224.0.0.0/4（multicast）· 240.0.0.0/4（reserved/broadcast）
IPv6 全阻断（PublicIpGuard.isPublicV6 + mapped-unwrap）：
  :: / ::1 / fe80::/10 / fc00::/7(ULA) / ff00::/8 / 2001:db8::/32(doc) / 64:ff9b::/96(NAT64)
  以及 ::ffff:a.b.c.d mapped -> 按内嵌 IPv4 判定
规则：解析出所有 IP 全部检查；公网+私网混合 -> DENY；每跳重校验
实现修正：isPublicV4 由位移常量改为显式边界区间判定（避免 ushr 常量算错）；
          CGNAT 100.64.0.0/10 用 100.64.0.0..100.127.255.255 边界判定
```

## 12. DNS / 重定向 / Rebinding

```text
DNS_REBINDING_DEFENSE = PARTIAL
  - 控制器决定：优先 HttpsURLConnection，不手搓 raw-SSLSocket HTTP 客户端
  - 记录 KNOWN_RESIDUAL_RISK：解析->连接 之间 DNS 可变（TOCTOU）
  - 不 trust-all / 不 hostname-bypass（TLS 校验保持平台默认）
重定向：每跳重跑 PublicIpGuard（防 http->内网 或 公网->内网 逃逸）
```

## 13. TLS（冻结）

```text
平台默认信任链（无自定义 SSLSocketFactory / 无 trust-all / 无 hostname 旁路）
```

## 14. Content-Type / Charset / 压缩（冻结）

```text
text/* + application/json + application/xml（+少量白名单）-> 文本抽取
  - text/html -> HtmlToText（去 script/style/head/注释/doctype，实体解码，空白归一，留 title）
  - 其它文本 -> 原样
image/* / application/octet-stream / 未知二进制 -> 到达即 success=true，content=""，
  note="binary content omitted"（不返回 body 字节）
charset：响应头 charset > 类型默认 > UTF-8 兜底
压缩：gzip / deflate
```

## 15. 审批（Approval）

```text
走冻结 Tool 审批通道（本 Step 不新增审批策略）
Direct Tool Validation 用 AllowAll（仅 L2 调试路径，非 model-visible）
```

## 16. Runtime / 取消（冻结）

```text
Tool.execute 为 suspend；fetch 在 withContext(Dispatchers.IO)
CancellationException 原样传播（不转 ToolResult）
```

## 17. Direct Tool Validation 架构（L2 机制，已接线）

```text
新增 DebugBridge 消息（无 LLM）：
  PC -> {"type":"tool_invoke","name":"network",
         "arguments":"{\"action\":\"fetch\",\"url\":\"https://example.com\"}"}
DebugBridge.handleToolInvoke -> 同一生产 toolRegistry.dispatch(call, AllowAll)
                                -> ToolResult -> 回传 JSONL
  Device -> {"type":"tool_result","name":"network",
             "success":true,"output":"{...}"}
约束：复用同一生产 NetworkToolImpl + ToolRegistry；不建第二份 fetch；
      不注册 model-visible 工具；AT 不参与。
改动文件：
  DebugProtocol.kt  + ClientMessage.ToolInvoke + "tool_invoke" 解析 + toolResult(...) 编码
  DebugBridge.kt    + toolRegistryProvider 参数 + ToolInvoke 处理 + handleToolInvoke
  ChatActivity.kt   ensureDebugBridge 传 toolRegistryProvider = { root?.toolRegistryView }
  ChatCompositionRoot.kt + val toolRegistryView: ToolRegistry get() = toolRegistry
```

## 18. L1 单元证据（JVM，无 Android / 无网络 / 无 LLM）

```text
:tool-network:testDebugUnitTest  -> BUILD SUCCESSFUL
  NetworkToolImplTest  56 tests · 0 failures
  NetworkCoresTest      22 tests · 0 failures
  合计 78 tests · 0 failures
覆盖：
  A args：action 缺/非 fetch/url 缺/url 非字符串/blank
  B scheme：http/file/ftp 拒绝；https 通过
  C IPv4 SSRF：0/8 127/8 10/8 172.16/12 192.168/16 169.254/16 100.64/10
              192.0.0/24 TEST-NET benchmarking multicast reserved
  D IPv6 SSRF：:: ::1 fe80 fc00 fd00 ff02 2001:db8 NAT64 + mapped-unwrap
  E DNS：全公网继续 / 公网+私网混合 DENY / 全私网 DENY / 解析失败
  F redirect：超 MAX_REDIRECT_HOPS / 重定向到内网 DENY
  G HTTP status：200/404/500 到达即 success=true + 真实 httpStatus
  H content-type：html 抽取 / json 原样 / image 到达但无 body
  I bounds：bytes > MAX_RESPONSE_BYTES 截断 / text > MAX_CONTENT_CHARS 截断
  J cancellation：CancellationException 传播
  PublicIpGuard：V4/V6 全区间 + allPublic 混合/全公网/空
  UrlValidator：https/大写/trailing-dot/端口/http/file/creds/IPv4-literal/IPv6-literal/blank
  HtmlToText：title+body 抽取/script+style 去除/实体解码/空白归一/注释+doctype 去除
```

## 19. 回归证据（本 Step 后全绿）

```text
:ag-core:test             PASS（不变）
:tool-android-shell:test  PASS（不变）
:app:assembleDebug        BUILD SUCCESSFUL（含 :tool-network + NetworkToolImpl 注册）
```

## 20. 注册证据（AGA Composition Root）

```text
ChatCompositionRoot.init:
  toolRegistry.register(AndroidShellToolImpl())   # Tool #1（AG.SHELL-1）
  toolRegistry.register(NetworkToolImpl())         # Tool #2（AG.NETWORK-0）
ChatCompositionRoot:  val toolRegistryView: ToolRegistry get() = toolRegistry
ChatActivity.ensureDebugBridge:  toolRegistryProvider = { root?.toolRegistryView }
```

## 21. 改动文件汇总（本 Step）

```text
新增  tool-network/（整个模块：build.gradle.kts + 10 main + 2 test）
改    settings.gradle.kts        + include(":tool-network")
改    app/build.gradle.kts       + implementation(project(":tool-network"))
改    app/.../ChatCompositionRoot.kt  + import + register(NetworkToolImpl()) + toolRegistryView
改    app/.../ChatActivity.kt    + toolRegistryProvider 接线
改    app/.../debug/DebugBridge.kt   + toolRegistryProvider + ToolInvoke + handleToolInvoke
改    app/.../debug/DebugProtocol.kt + ClientMessage.ToolInvoke + 解析 + toolResult 编码
改    AGENTS.md                  + Tool Layer 架构事实 + :tool-network 条目 + AG.NETWORK-0 状态
```

## 22. L2 真机直接 fetch（PASS，无 LLM）

```text
设备  : APLMUT4312005935 / HONOR ALI-AN00 / arm64-v8a
通道  : adb forward tcp:45678 tcp:45678 + DebugBridge tool_invoke（同一生产 ToolRegistry）
判据  : 全部命中

用例 1  https://example.com
  success=true  httpStatus=200  finalUrl=https://example.com:443/
  contentType=text/html; charset=utf-8  bytesRead=577  redirectCount=0  truncated=false
  content（HTML 已抽取为纯文本）：
    "Example Domain\nThis domain is for use in documentation examples without needing
     permission. This is not a service; avoid relying on it for testing and monitoring
     purposes."
  => 平台 HttpsURLConnection + SSRF 放行 + HtmlToText 全链路真机通过

用例 2  https://127.0.0.1/            -> success=false  DESTINATION_BLOCKED（SSRF 拦环回）
用例 3  http://169.254.169.254/...     -> success=false  UNSUPPORTED_SCHEME（拒绝 http）
用例 4  https://example.com/definitely-404-page
                                             -> success=true  httpStatus=404（机制成功语义）
用例 5  https://this-domain-does-not-exist-xyz123.invalid/
                                             -> success=false  DNS_FAILURE
结论  : L2 = PASS（transport / SSRF / scheme 策略 / 机制成功语义 / DNS 失败 全部真机验证，
         零 LLM token，AT 未参与，无第二 fetch 实现）
```

## 23. L3 真 Agent E2E（PASS，OpenRouter / deepseek-v4-flash-0731）

```text
通道  : 同一生产 AgentRuntime.send（DebugBridge send 消息，与 UI 同源）
prompt: "读取 https://example.com 的网页内容，然后用一句话总结这个网站是做什么的。"
执行流（实测）：
  turn1  THINKING: "...fetch the content of https://example.com... Let me fetch it."
         [TOOL] network  >  {"action":"fetch","url":"https://example.com"}   ← 模型自主选择
         [RESULT] success=true  httpStatus=200  content="Example Domain / This domain
                 is for use in documentation examples..."                     ← 进 history
  turn2  THINKING: "Let me summarize in one sentence in Chinese."
         [ANSWER] "已读取 https://example.com 的内容。一句话总结：example.com 是一个专门
                  用于文档和示例的占位域名，供开发者测试使用，本身不提供任何实际服务。"
  [RUN] Finished (Answer(...))
判定  :
  - 模型自主调用 network（非用户指定）          = PASS
  - network ToolResult 进入 history             = PASS
  - FinalAnswer 依赖抓取内容（复述 documentation/占位/无实际服务）
                                                = OBSERVATION_DEPENDENCY = PASS
  - model_turns=2  network_calls=1  shell_calls=0  run_failed=None
```

### L3 回归：android_shell 仍可选择（PASS）

```text
prompt: "告诉我当前处于前台的 Android 应用是什么（用工具查实际状态，不要猜）。"
执行流（实测）：
  [TOOL] android_shell  >  {"command":"dumpsys activity activities | grep -E
         \"mResumedActivity|topResumedActivity|ResumedActivity\" | head -5"}
  [RESULT] success=true  exitCode=0
         stdout="topResumedActivity=ActivityRecord{... com.androidagent.app/.ChatActivity ...}"
  [ANSWER] "根据设备实际状态，当前处于前台的应用是 com.androidagent.app，具体界面为 ChatActivity。"
判定  : 加 network 后 android_shell 未被挤掉，仍被模型自主选择并真实执行（AT Engine 路径正常）
```

## 24. Observation Dependency 定义（实测 PASS）

```text
PASS = final answer 明确引用/复述了 network ToolResult 中的抓取内容
       （非通用回答），且抓取内容在 history 中先于 final answer
本 Step：final answer 复述 "用于文档和示例的占位域名 / 不提供实际服务"，
        与抓取文本 "for use in documentation examples / not a service" 一一对应
        => OBSERVATION_DEPENDENCY = PASS
```

## 25. 禁止范围（本 Step 不做）

```text
network search / download / request(POST/auth/cookie/stream)
FileTool / spill-to-file
Tool-Layer 框架扩展（ToolManager / backends / factory）
AT 改动 / Core / Runtime / Provider 改动
下一步（AGENT-0.CONTRAST 等）
```

## 26. 未来 AT（说明，非本 Step）

```text
若未来需要 AT 侧网络能力，按 AGENTS.md：引入新 tool 或既有 tool 内新类，
Host 注册；绝不把网络能力塞进 ag-core / ag-runtime。本 Step 的
:tool-network 与 AT 完全解耦，不受 AT 主线演进影响。
```

## 27. Drift（实现 vs Preflight）

```text
无架构 drift：模块方向 / 契约 / 语义 / 常量 / SSRF / transport / Direct Validation
均按 preflight 冻结。
两处实现级修正（非冻结变更）：
  1) PublicIpGuard.isPublicV4 位移常量 -> 显式边界区间（修 CGNAT/benchmarking 判定）
  2) UrlValidator.isIpLiteral 去 DNS（纯语法，避免验证期真实解析副作用）
```

## 28. 测试卫生

```text
所有 @Test 为块体 Unit 方法（无 local-function @Test）
测试计数来自 JUnit 实际执行（78 = 56 + 22），非源码方法数
:tool-network 纯 JVM 单测（isReturnDefaultValues=true，无 Robolectric）
```

## 29. 未提交工作树说明

```text
本 Step 未 commit（遵循 AGENTS.md：无显式许可不 commit）。
工作树含：tool-network/（新）、settings/app/Chat*/Debug* 修改、AGENTS.md 修改。
另有历史遗留未跟踪文件（ProviderRealDeviceProbeTest.kt / OpenRouterPcProbe.kt /
docs/diagnostics/）非本 Step 产物，保持原状。
```

## 30. Verdict

```text
AG.NETWORK-0 = PASS（全层验证完成）

L1  JVM 单测        78/78 绿（args/SSRF v4+v6/DNS/redirect/HTTP status/
                        content-type/bounds/cancellation + PublicIpGuard/
                        UrlValidator/HtmlToText 核心）
回归                ag-core / tool-android-shell / app assembleDebug 全绿
L2  真机直接 fetch   PASS（无 LLM）
                        example.com 200 + HTML 抽取 / 127.0.0.1 DESTINATION_BLOCKED /
                        http 元数据 UNSUPPORTED_SCHEME / 404 success=true / DNS_FAILURE
L3  真 Agent E2E     PASS（OpenRouter deepseek-v4-flash-0731）
                        模型自主调 network -> ToolResult 进 history ->
                        FinalAnswer 依赖抓取内容；OBSERVATION_DEPENDENCY = PASS
L3  回归            android_shell 仍被模型自主选择并真实执行（dumpsys 前台应用）
架构                Tool Layer 落地（AG Engine + Tool Layer + Host）；
                        :tool-network 注册进 AGA；Direct Tool Validation 接线
边界                无 Core / Runtime / Provider / AT 改动；无新第三方依赖；
                        AT_DEPENDENCY=NONE（L2/L3 均确认 AT 未参与）

本 Step 到此 STOP。不做：Search / Download / Request(POST/auth/cookie/stream) /
FileTool / Tool-Layer 框架扩展 / AT 改动 / 下一 Step。
```
