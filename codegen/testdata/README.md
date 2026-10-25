# Compatibility fixtures

The corpus contains the independent counter, bounded-stream, counter-example
and synthetic SampleStore inputs. SampleStore is authored as record/page storage,
not a consumer API. Its TOML SHA-256 is
`46729b4e03cc71d6997de286c2361271a1c281fbc7839556c79de031a2b63847`.

Its baseline outputs were independently produced by signed facade v0.4.5
(commit `cfed4182356a4f4609c88f58924aac79c05ae5b6`), executable SHA-256
`adb7c8b8402413391eda07083dbe1512333e44fe4b00d10c2abd324b72bfedf6`.
The Java matrix adds all54 workspace/lifecycle/simulator/namespace combinations.
Kotlin packages retain both namespaces. Swift records the intended stream
refusal. The other three inputs retain their existing baseline provenance.

Current source-projection receipts freeze a reviewed API delta only; they are
not independent behavior, allocation or physical-device evidence. Package order,
bytes/hashes, complete matrix coverage and input nonmutation remain tested.

`harness-schema.json`, `jvm/src/main/java/` and the seven original named tests in
`jvm/src/test/kotlin/` come from the landed donor. Additional Kotlin tests check
descriptor, upload, chunk/acknowledgement and status-word refusals. The generated
Kotlin source under test always comes from this repository's `Plugin.Generate`;
the real runtime is substituted from this repository's root Gradle build.
`JCSystem.java` is the donor's host-only allocation fixture, not an assertion
about physical Java Card lifecycle. No archived native receipts are reused.
