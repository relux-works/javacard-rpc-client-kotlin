# javacard-rpc-client-kotlin v0.3.0

This release adds the independently consumable Go code generation backend to
the Kotlin/JVM runtime repository. Both components share the root `v0.3.0` tag.

| Component | Exact pin |
| --- | --- |
| Go backend module | `github.com/relux-works/javacard-rpc-client-kotlin v0.3.0` |
| Backend import | `github.com/relux-works/javacard-rpc-client-kotlin/codegen` |
| Plugin API | `github.com/relux-works/javacard-rpc/pluginapi v0.1.0` |
| Kotlin runtime | `io.jcrpc:javacard-rpc-client-kotlin:0.3.0` from root tag `v0.3.0` |
| Runtime package | `io.jcrpc.client` |
| Output compatibility baseline | Core `v0.4.5`, commit `cfed4182356a4f4609c88f58924aac79c05ae5b6` |

## Changes

- `codegen.Plugin{}` implements the released `pluginapi.Plugin` contract. It
  consumes a validated schema and returns ordered settings, build manifest and
  Kotlin source files in memory.
- The backend's only external Go dependency is the released plugin API. Parsing,
  IDL/option validation, output validation and writing files remain the
  composing facade's responsibility.
- Extracted renderer and templates preserve core v0.4.5 output bytes, file
  ordering, eight renderer refusals and caller-state preservation. Compatibility
  checks cover 216 combinations over four input fixtures.
- The Kotlin runtime's public API and implementation are unchanged from
  `0.2.0`; its release version becomes `0.3.0`. The root Gradle layout, group,
  artifact and Kotlin package names are retained.

## Consumption and compatibility

Use `go get github.com/relux-works/javacard-rpc-client-kotlin/codegen@v0.3.0` for
Go composition. For the matching runtime, use the tagged checkout and Gradle
composite build described in the [README](README.md#use-the-pinned-release).
Generated build manifests retain the `0.2.0` runtime dependency required for
byte parity; the composite build explicitly substitutes the `v0.3.0` source
runtime. Repository consumption does not depend on Maven Central publication.

The backend requires Go 1.24+. The runtime targets Kotlin/JVM, Kotlin plugin
`2.1.10` and JDK 17, using Gradle wrapper `9.2.1`. Host evidence covers macOS
arm64, Go `1.25.5` and Homebrew JDK `17.0.18`: five runtime tests and eleven
generated-client JVM tests, including stream failure, cancellation, concurrency
and malformed-response regressions. Frozen Java endpoint fixtures provide host
test behavior.

Android, Kotlin/Native, Kotlin/JS and physical-card transports are outside the
verified native lanes. The repository provides a JVM TCP bridge transport;
BLE/NFC integrations implement `APDUTransport`. Facade validation, server
workspace policy and physical-card persistence are outside the backend's
contract. Stream workspace/memory/simulator options preserve their existing
effect on Kotlin output; this release does not change stream wipe behavior.
