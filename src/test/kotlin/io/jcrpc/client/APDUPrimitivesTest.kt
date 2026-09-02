package io.jcrpc.client

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class APDUPrimitivesTest {
    private class RecordingTransport : APDUTransport {
        var invalidated = false

        override suspend fun transmit(command: APDUCommand): APDUResponse =
            APDUResponse(byteArrayOf(0x90.toByte(), 0x00))

        override fun invalidateSession() {
            invalidated = true
        }
    }

    @Test
    fun `transport exposes synchronous session invalidation`() {
        val transport = RecordingTransport()

        assertFalse(transport.invalidated)
        transport.invalidateSession()
        assertTrue(transport.invalidated)
    }

    @Test
    fun `command serializes short apdu with lc`() {
        val command = APDUCommand(
            cla = 0xB0u,
            ins = 0x01u,
            p1 = 0x05u,
            p2 = 0x00u,
            data = byteArrayOf(0x10, 0x20),
        )

        assertContentEquals(byteArrayOf(0xB0.toByte(), 0x01, 0x05, 0x00, 0x02, 0x10, 0x20), command.bytes)
    }

    @Test
    fun `response exposes payload and status word`() {
        val response = APDUResponse(byteArrayOf(0x12, 0x34, 0x90.toByte(), 0x00))

        assertEquals(0x9000u.toUShort(), response.sw)
        assertEquals(0x12u, response.readU8(0))
        assertEquals(0x1234u.toUShort(), response.readU16(0))
        assertContentEquals(byteArrayOf(0x12, 0x34), response.data)
    }

    @Test
    fun `response throws on error status word`() {
        val response = APDUResponse(byteArrayOf(0x69, 0x85.toByte()))

        val error = assertFailsWith<APDUStatusWordException> {
            response.checkSW()
        }

        assertEquals(0x6985u.toUShort(), error.sw)
    }

    @Test
    fun `data packer writes fixed width primitives`() {
        val packer = DataPacker()
        packer.packU8(0xABu)
        packer.packU16(0x1234u)
        packer.packU32(0xDEADBEEFu)
        packer.packBool(true)
        packer.packFixedBytes(byteArrayOf(0x55, 0x66), 2)

        assertContentEquals(
            byteArrayOf(
                0xAB.toByte(),
                0x12,
                0x34,
                0xDE.toByte(),
                0xAD.toByte(),
                0xBE.toByte(),
                0xEF.toByte(),
                0x01,
                0x55,
                0x66,
            ),
            packer.data,
        )
    }
}
