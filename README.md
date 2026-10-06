# javacard-rpc-client-kotlin

Kotlin/JVM runtime and Go code generation backend for `javacard-rpc` clients.

It mirrors the role of `javacard-rpc-client-swift` on the Swift side:

- `APDUCommand` and `APDUResponse`
- `APDUTransport`, including synchronous selected-session invalidation for
  failed or cancelled generated stream operations
- `TCPTransport` for the local jCardSim bridge
- `DataPacker` helpers for APDU payload assembly

This package is intended for development and integration testing. Production
transports can implement `APDUTransport` over BLE, NFC, or any other channel.

## Coordinates

- Group: `io.jcrpc`
- Artifact: `javacard-rpc-client-kotlin`
- Runtime version: `0.3.0`
- Runtime package: `io.jcrpc.client`
- Canonical repository tag: `v0.3.0`
- Go backend: `github.com/relux-works/javacard-rpc-client-kotlin/codegen`
- Go module version: `github.com/relux-works/javacard-rpc-client-kotlin v0.3.0`
- Plugin API dependency: `github.com/relux-works/javacard-rpc/pluginapi v0.1.0`

The root tag selects both the Go backend and the Gradle runtime from the same
repository commit. See [the v0.3.0 release notes](RELEASE-NOTES-0.3.0.md) for
compatibility and platform limits.

## Use the pinned release

Add the backend to a Go composition module:

```bash
go get github.com/relux-works/javacard-rpc-client-kotlin/codegen@v0.3.0
```

Import `github.com/relux-works/javacard-rpc-client-kotlin/codegen` and compose
`codegen.Plugin{}` with the released `pluginapi.Plugin` interface.

For a generated JVM client, check out the matching runtime next to the client:

```bash
git clone --branch v0.3.0 https://github.com/relux-works/javacard-rpc-client-kotlin.git
cd javacard-rpc-client-kotlin
./gradlew jar
```

Add this to the generated client's `settings.gradle.kts`:

```kotlin
includeBuild("../javacard-rpc-client-kotlin") {
    dependencySubstitution {
        substitute(module("io.jcrpc:javacard-rpc-client-kotlin")).using(project(":"))
    }
}
```

Gradle substitutes the source runtime for the same group/artifact dependency.
The preserved generator emits a `0.2.0` runtime dependency for v0.4.5 output
parity; the composite build above selects the runtime at `v0.3.0` explicitly.
These instructions use a repository checkout and do not require a Maven Central
publication.

## Compatibility

The runtime targets Kotlin/JVM with Kotlin plugin `2.1.10` and JDK 17. The
repository uses Gradle wrapper `9.2.1`; the backend requires Go 1.24 or newer.
Host verification covers macOS arm64 with Go `1.25.5` and Homebrew JDK
`17.0.18`. Android, Kotlin/Native, Kotlin/JS and physical-card transports have
no native test lane in this repository. `TCPTransport` is a JVM bridge
transport; BLE/NFC integrations supply their own `APDUTransport`.

## Build

```bash
./gradlew build
go test ./...
go build ./...
go vet ./...
```

## Code generation backend

The root Go module is `github.com/relux-works/javacard-rpc-client-kotlin`.
Its `codegen` package implements `pluginapi.Plugin` through `codegen.Plugin{}`.
The only external Go dependency is the released
`github.com/relux-works/javacard-rpc/pluginapi v0.1.0`; the backend has no parser,
facade, or other renderer dependency. It consumes a validated schema and returns
an ordered in-memory package: settings, build manifest, then Kotlin source.
Parsing, option validation, output validation and filesystem writes belong to
the composing facade. See [the package contract](codegen/README.md).

Gradle and the runtime stay at the repository root. A repository release tag
pins both the Go backend and Kotlin runtime to the same commit; the Maven
coordinates and `io.jcrpc.client` package remain unchanged.

## Tools

| Tool | Command / purpose | Outputs |
| --- | --- | --- |
| Gradle wrapper, JDK 17 | `./gradlew build` builds and tests the runtime | `build/`, including test reports |
| Go 1.24+ | `go build ./...` builds the backend and validation tool; `go vet ./...` checks Go code | Go build cache |
| Go tests | `go test ./...` checks dependency boundary, v0.4.5 package parity, refusals, and generated-client behavior | JVM projects/logs/reports under `.temp/kotlin-contract/`; Go results on stdout |
| Narrowing controls | `go run ./codegen/cmd/check-mutants -out .temp/backend-mutants` checks weakened gates in disposable copies; `-only ID,ID` selects a bounded subset | Selected source copies, actual exit codes, logs and `receipts.json` under the output directory |

The generated-client test invokes the root Gradle wrapper and substitutes this
repository's real runtime into a generated package. It retains the seven named
donor stream regressions and adds four boundary/rejection cases. Frozen Java
endpoint fixtures support host testing; no card or device is touched.

<!-- relux-ecosystem:start -->

## About Relux Works

This project is part of the open-source ecosystem of
[Relux Works](https://relux.works), an AI-native software development studio.
We build fixed-price MVPs, rescue vibe-coded apps, run local AI inference, and
train teams to work with coding agents. Much of the infrastructure behind that
work is open source.

- Full catalog: [relux.works/en/open-source](https://relux.works/en/open-source/)
- Agentic enablement: [agent harnesses & team training](https://relux.works/en/agentic-enablement/)
- Hire us the agent-native way: point your assistant at `https://api.relux.works/mcp`
- Contact: ivan@relux.works

<!-- relux-ecosystem:end -->
