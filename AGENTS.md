# AndroidAgent — development rules

## Repository roles

```text
AndroidAgent         = development source of truth
AndroidAgent-Public  = release / export downstream only
```

Flow is one-directional:

```text
development directory  ->  explicit Release Step  ->  *-Public
```

Forbidden:

- developing directly in `-Public`
- fixing a bug in `-Public` and copying it back into development
- treating both directories as source of truth

Do not create `-Public` for symmetry. Create it at a real release node.

## Architecture rules

```text
AG Core  -X->  AT Core
AG Core  -X->  Android framework
AG Core  -X->  ADB / JNI
AG Core  -X->  com.androidterminal.*
```

AG Core reaches Android execution only through the Tool system.
`Android Tools` is the only layer allowed to depend on both AG Core and AT Core.

`AT Core` lives in the AndroidTerminal project. AndroidAgent must not import,
vendor, copy, or re-implement it. If AG Core appears to need it, the design is
wrong: introduce a tool instead.

## Package boundary

```text
com.androidagent.core      frozen
```

Forbidden: `com.androidterminal.*`, `com.androidagent.app.*`.
AG Core is product-independent; AGA, AGC, and any future frontend must be able
to reuse it unchanged.

## Development discipline

```text
No opportunistic refactor
No future-feature scaffolding without real need
Logical boundary before unnecessary abstraction
Do not implement empty architecture layers merely for symmetry
```

Adding a module, abstraction, or dependency requires a real consumer today,
not a hypothetical one. One execution backend does not justify a backend
abstraction layer.

## Verification

Any change must keep these green:

```bash
./gradlew :ag-core:test
./gradlew build
```

New logic in AG Core requires a JVM unit test. AG Core is testable precisely
because it is Android-free; do not erode that.

## Git

```text
No commit / push unless the task explicitly allows it.
```

Branch on demand. Do not pre-build a multi-branch Git flow for a single-line
project.

## Out of scope for AG.BASELINE-0

Do not begin Agent implementation (AgentCore, AgentLoop, ModelProvider,
ToolRegistry, Skills, permissions), Android tooling, AGA, or AGC until the
corresponding step is explicitly opened.