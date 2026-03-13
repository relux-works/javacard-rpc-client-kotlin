package io.jcrpc.client

import java.io.ByteArrayOutputStream

class DataPacker {
    private val out = ByteArrayOutputStream()

    val data: ByteArray
        get() = out.toByteArray()

    fun packU8(value: UByte) {
        out.write(value.toInt())
    }

    fun packU16(value: UShort) {
        out.write((value.toInt() ushr 8) and 0xFF)
        out.write(value.toInt() and 0xFF)
    }

    fun packU32(value: UInt) {
        out.write(((value.toLong() ushr 24) and 0xFF).toInt())
        out.write(((value.toLong() ushr 16) and 0xFF).toInt())
        out.write(((value.toLong() ushr 8) and 0xFF).toInt())
        out.write((value.toLong() and 0xFF).toInt())
    }

    fun packBool(value: Boolean) {
        out.write(if (value) 0x01 else 0x00)
    }

    fun packBytes(value: ByteArray) {
        out.write(value)
    }

    fun packFixedBytes(value: ByteArray, length: Int) {
        require(value.size == length) { "Expected $length bytes, got ${value.size}" }
        out.write(value)
    }
}
