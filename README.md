# javacard-rpc-client-kotlin

Thin Kotlin/JVM runtime for `javacard-rpc` generated clients.

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
- Version: `0.2.0`

## Build

```bash
./gradlew build
```
