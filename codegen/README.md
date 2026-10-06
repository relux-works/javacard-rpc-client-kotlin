# Kotlin backend

`Plugin.Generate(schema, options)` implements the released
`github.com/relux-works/javacard-rpc/pluginapi.Plugin` contract. It renders three
ordered `pluginapi.File` values without writing files or mutating the schema:

1. `settings.gradle.kts`
2. `build.gradle.kts`
3. `src/main/kotlin/<namespace>/<Applet>Client.kt`

`options.Namespace` selects the package and generated Gradle group. Empty
namespaces, nil schemas, malformed AIDs and unsupported renderer fields refuse
with no partial files. Inputs must already satisfy the facade's IDL validation.
Stream workspace, memory and simulator choices are facade/server concerns and
do not change Kotlin output. This preserves the v0.4.5 contract; the backend
does not add validation for those options or alter the stream wipe behavior.

The renderer/templates and required naming/ordering helpers were extracted from
core commit `ef6e04bfae8dab0f8e1ac40c9bb10713dbf09250`. The renderer bodies,
including the historical Counter status ordering, are preserved. There is no
dependency on facade codegen, parser or templates. Root Gradle layout, group,
artifact and `io.jcrpc.client` package remain unchanged. The root `v0.3.0` tag
pins Go module `github.com/relux-works/javacard-rpc-client-kotlin v0.3.0` and
runtime `io.jcrpc:javacard-rpc-client-kotlin:0.3.0` together. The backend depends
on `github.com/relux-works/javacard-rpc/pluginapi v0.1.0`.

Generated manifests retain their historical `0.2.0` runtime dependency to keep
the accepted v0.4.5 output byte identical. Use the [pinned runtime composite
build](../README.md#use-the-pinned-release) to select the matching source
runtime. See [v0.3.0 release notes](../RELEASE-NOTES-0.3.0.md).

## Validation

Run `go test ./...`, `go build ./...`, `go vet ./...` and `./gradlew build` from
the repository root. `TestPluginReleasedParity` compares actual `Plugin.Generate`
output with independently generated v0.4.5 fixtures across 216 combinations.
`TestParityCheckerRejectsBoundedByteChange` detects byte, inventory and order
changes. `TestPluginRefusalsReturnNoFiles` pairs renderer rejection cases with
a valid generation and checks preservation of caller state.

`TestGeneratedKotlinStreamClientHarness` generates and compiles Kotlin against
the root runtime, then executes the retained named lifecycle regressions and
boundary checks. Reports are left under `.temp/kotlin-contract/`. Its frozen
Java endpoint models host execution; physical-card behavior and facade CLI
validation are outside this package's test claims.

Run `go run ./codegen/cmd/check-mutants -out .temp/backend-mutants` for bounded
source controls. Every admitted mutation retains its gate and weakens one
specific rejection. A control is killed only by a named test failure with exit
1, never by a compile/setup failure. `-only` selects comma-separated IDs. The
descriptor byte-length control preserves the original searched token and runs
the JVM behavioral test. Receipts declare surviving controls rather than
counting them as coverage.
