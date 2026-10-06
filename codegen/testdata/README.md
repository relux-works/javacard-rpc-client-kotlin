# Compatibility fixtures

`parity-*.json` contains validated API schemas and ordered package files for the
counter example, counter fixture, bounded-stream fixture and immutable B6
bsim-auth IDL. Each of two namespaces has one golden package; workspace,
stream-memory and simulator options are byte invariant for Kotlin.
`input-*.identity.json` records original input names and SHA-256 identities.

Golden bytes were generated independently by `jcrpc-gen` built from signed core
tag `v0.4.5`, commit `cfed4182356a4f4609c88f58924aac79c05ae5b6`. All 216
workspace/memory/simulator/namespace combinations were executed against that
binary and the extracted backend. File order was checked against the landed
donor `kotlin.Plugin.Generate` at `ef6e04b`.

The pinned B6 input is commit `2d23abdafa1e0f68c6003ab56274b2ac38378ef9`,
SHA-256 `1be1ed52ac9a85a62d5c5e371a9e22d38f834282681528476ca071e7bfc2cb66`.
Fixtures are data, so the published backend has no TOML/parser dependency.

`harness-schema.json`, `jvm/src/main/java/` and the seven original named tests in
`jvm/src/test/kotlin/` come from the landed donor. Additional Kotlin tests check
descriptor, upload, chunk/acknowledgement and status-word refusals. The generated
Kotlin source under test always comes from this repository's `Plugin.Generate`;
the real runtime is substituted from this repository's root Gradle build.
`JCSystem.java` is the donor's host-only allocation fixture, not an assertion
about physical Java Card lifecycle. No archived native receipts are reused.
