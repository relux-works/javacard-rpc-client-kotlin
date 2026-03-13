package io.jcrpc.client

data class APDUCommand(
    val cla: UByte,
    val ins: UByte,
    val p1: UByte = 0u,
    val p2: UByte = 0u,
    val data: ByteArray? = null,
    val le: UByte? = null,
) {
    val bytes: ByteArray
        get() {
            val payload = data?.takeIf { it.isNotEmpty() }
            val size = 4 + (payload?.size?.plus(1) ?: 0) + if (le != null) 1 else 0
            val out = ByteArray(size)
            out[0] = cla.toByte()
            out[1] = ins.toByte()
            out[2] = p1.toByte()
            out[3] = p2.toByte()

            var offset = 4
            if (payload != null) {
                require(payload.size <= 0xFF) { "APDU short data length must be <= 255 bytes" }
                out[offset] = payload.size.toByte()
                offset += 1
                payload.copyInto(out, offset)
                offset += payload.size
            }
            if (le != null) {
                out[offset] = le.toByte()
            }
            return out
        }
}
