# javacard-rpc-client-kotlin v0.3.1

This patch publishes the accepted generic storage fixtures with matching root
Go and Kotlin/JVM runtime versions. The root tag is `v0.3.1`; the runtime
coordinate is `io.jcrpc:javacard-rpc-client-kotlin:0.3.1`, and the backend module
is `github.com/relux-works/javacard-rpc-client-kotlin v0.3.1`.

The plugin API dependency remains `pluginapi v0.1.0`. Renderer output, runtime
behavior and public APIs are unchanged. Generated build manifests retain the
historical `0.2.0` dependency required by the v0.4.5 byte-parity contract;
the documented composite build selects the tagged runtime explicitly.

The existing Go and Kotlin/JVM suites remain the release checks. Their scope
includes generated-client refusals, failure, cancellation and recovery;
it does not establish Android device or physical-card behavior. See the
[README](README.md) for exact build commands and supported platforms.
