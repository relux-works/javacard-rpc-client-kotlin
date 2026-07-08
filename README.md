# javacard-rpc-client-kotlin

Thin Kotlin/JVM runtime for `javacard-rpc` generated clients.

It mirrors the role of `javacard-rpc-client-swift` on the Swift side:

- `APDUCommand` and `APDUResponse`
- `APDUTransport`
- `TCPTransport` for the local jCardSim bridge
- `DataPacker` helpers for APDU payload assembly

This package is intended for development and integration testing. Production
transports can implement `APDUTransport` over BLE, NFC, or any other channel.

## Coordinates

- Group: `io.jcrpc`
- Artifact: `javacard-rpc-client-kotlin`

## Build

```bash
./gradlew build
```

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
