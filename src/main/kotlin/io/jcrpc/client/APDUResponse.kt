package io.jcrpc.client

class APDUResponse(
    val rawBytes: ByteArray,
) {
    init {
        require(rawBytes.size >= 2) { "APDU response must contain SW1 and SW2" }
    }

    val sw1: UByte
        get() = rawBytes[rawBytes.lastIndex - 1].toUByte()

    val sw2: UByte
        get() = rawBytes[rawBytes.lastIndex].toUByte()

    val sw: UShort
        get() = (((sw1.toUInt() shl 8) or sw2.toUInt()) and 0xFFFFu).toUShort()

    val data: ByteArray
        get() = rawBytes.copyOf(rawBytes.size - 2)

    fun checkSW() {
        if (sw != 0x9000u.toUShort()) {
            throw APDUStatusWordException(sw1, sw2)
        }
    }

    fun readU8(offset: Int = 0): UByte = data.byteAt(offset).toUByte()

    fun readU16(offset: Int = 0): UShort {
        val hi = data.byteAt(offset).toUByte().toUInt() shl 8
        val lo = data.byteAt(offset + 1).toUByte().toUInt()
        return (hi or lo).toUShort()
    }

    fun readU32(offset: Int = 0): UInt {
        val b0 = data.byteAt(offset).toUByte().toUInt() shl 24
        val b1 = data.byteAt(offset + 1).toUByte().toUInt() shl 16
        val b2 = data.byteAt(offset + 2).toUByte().toUInt() shl 8
        val b3 = data.byteAt(offset + 3).toUByte().toUInt()
        return b0 or b1 or b2 or b3
    }

    fun readBool(offset: Int = 0): Boolean = data.byteAt(offset) != 0.toByte()

    fun readBytes(offset: Int = 0, count: Int? = null): ByteArray {
        val endExclusive = count?.let { offset + it } ?: data.size
        require(offset >= 0 && endExclusive <= data.size && endExclusive >= offset) {
            "Invalid read range"
        }
        return data.copyOfRange(offset, endExclusive)
    }

    private fun ByteArray.byteAt(offset: Int): Byte {
        require(offset in indices) { "Offset $offset is out of bounds for ${size}-byte response payload" }
        return this[offset]
    }
}
