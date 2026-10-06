package io.jcrpc.streamdemo.client

import io.jcrpc.client.APDUCommand
import io.jcrpc.client.APDUResponse
import io.jcrpc.client.APDUTransport
import io.jcrpc.streamdemo.server.StreamDemoBoundedStreamRuntime
import io.jcrpc.streamdemo.server.StreamDemoStreamEndpoint
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class LostResponse : RuntimeException()

private class RuntimeBackedTransport(
    private val loseWriteChunkResponse: Boolean = false,
    private val loseCloseWriteResponse: Boolean = false,
    private val loseReadChunkResponse: Boolean = false,
    private val loseCloseReadResponse: Boolean = false,
    private val corruptDescriptorDigest: Boolean = false,
    private val cancelAfterINS: Int? = null,
    private val suspendAfterINS: Int? = null,
    private val throwOnInvalidate: Boolean = false,
    private val ordinaryResponseData: ByteArray = byteArrayOf(0x07),
    private val ordinaryStatusWord: UShort = 0x9000u.toUShort(),
    private val fixedResponseData: ByteArray = byteArrayOf(0x01) + ByteArray(10) { it.toByte() } + byteArrayOf(0, 0, 0, 2),
    private val alterResponse: ((Int, ByteArray) -> ByteArray)? = null,
) : APDUTransport {
    private val workspace = ByteArray(2048)
    private val digestScratch = ByteArray(32)
    private val scalars = ShortArray(StreamDemoBoundedStreamRuntime.SCALAR_COUNT.toInt())
    private val handlerSlot = arrayOfNulls<Any>(StreamDemoBoundedStreamRuntime.HANDLER_SLOT_COUNT.toInt())
    private var writeChunkResponseLost = false
    private var closeWriteResponseLost = false
    private var readChunkResponseLost = false
    private var closeReadResponseLost = false
    private var cancellationDelivered = false
    private var suspensionDelivered = false
    private var pendingContinuation: Continuation<APDUResponse>? = null
    private var pendingResponse: APDUResponse? = null

    var handlerExecutions: Int = 0
        private set
    var writeChunkCalls: Int = 0
        private set
    var pendingInfoCalls: Int = 0
        private set
    var closeReadCalls: Int = 0
        private set
    var readChunkCalls: Int = 0
        private set
    var abortCalls: Int = 0
        private set
    var invalidateCalls: Int = 0
        private set

    private val sha256 = StreamDemoStreamEndpoint.Sha256 { input, inputOffset, inputLength, output, outputOffset ->
            val digest = MessageDigest.getInstance("SHA-256").digest(
                input.copyOfRange(inputOffset.toInt(), inputOffset.toInt() + inputLength.toInt()),
            )
            digest.copyInto(output, destinationOffset = outputOffset.toInt())
        }

    private val handler = StreamDemoStreamEndpoint.Handler { _, input, inputOffset, inputLength, output, outputOffset, _ ->
            handlerExecutions++
            var left = 0
            var right = inputLength.toInt() - 1
            while (left <= right) {
                val leftValue = input[inputOffset.toInt() + left]
                output[outputOffset.toInt() + left] = input[inputOffset.toInt() + right]
                output[outputOffset.toInt() + right] = leftValue
                left++
                right--
            }
            inputLength
        }

    private val runtime = StreamDemoBoundedStreamRuntime(
        workspace,
        digestScratch,
        scalars,
        handlerSlot,
        sha256,
    )

    override suspend fun transmit(command: APDUCommand): APDUResponse {
        val cla = command.cla
        val ins = command.ins
        val p1 = command.p1
        val p2 = command.p2
        val data = command.data
        assertEquals(0xB0u.toUByte(), cla)
        if (ins.toInt() == 0x01) {
            return response(ordinaryStatusWord, ordinaryResponseData)
        }
        if (ins.toInt() == 0x02) {
            return response(0x9000u.toUShort(), fixedResponseData)
        }
        val operation = ins.toInt() - 0x20
        require(operation in 0..5) { "unexpected INS 0x%02X".format(ins.toInt()) }
        if (ins.toInt() == 0x20) writeChunkCalls++
        if (ins.toInt() == 0x22) pendingInfoCalls++
        if (ins.toInt() == 0x23) readChunkCalls++
        if (ins.toInt() == 0x24) closeReadCalls++
        if (ins.toInt() == 0x25) abortCalls++

        val request = data ?: byteArrayOf()
        val response = ByteArray(255)
        val responseLength = try {
            runtime.dispatch(
                1.toByte(),
                operation.toByte(),
                true,
                1792.toShort(),
                192.toShort(),
                true,
                1792.toShort(),
                192.toShort(),
                (-1).toShort(),
                handler,
                p1.toByte(),
                p2.toByte(),
                request,
                0,
                request.size.toShort(),
                response,
                0,
                response.size.toShort(),
            ).toInt() and 0xFFFF
        } catch (failure: StreamDemoStreamEndpoint.StreamStatusWordException) {
            return response(failure.statusWord.toUShort(), byteArrayOf())
        }

        if (ins.toInt() == cancelAfterINS && !cancellationDelivered) {
            cancellationDelivered = true
            throw CancellationException("cancelled after INS")
        }

        if (ins.toInt() == 0x20 && p1.toInt() == 0 && loseWriteChunkResponse && !writeChunkResponseLost) {
            writeChunkResponseLost = true
            throw LostResponse()
        }
        if (ins.toInt() == 0x21 && loseCloseWriteResponse && !closeWriteResponseLost) {
            closeWriteResponseLost = true
            throw LostResponse()
        }
        if (ins.toInt() == 0x23 && p1.toInt() == 0 && loseReadChunkResponse && !readChunkResponseLost) {
            readChunkResponseLost = true
            throw LostResponse()
        }
        if (ins.toInt() == 0x24 && loseCloseReadResponse && !closeReadResponseLost) {
            closeReadResponseLost = true
            throw LostResponse()
        }

        val result = response.copyOf(responseLength)
        if (corruptDescriptorDigest && (ins.toInt() == 0x21 || ins.toInt() == 0x22) && result.size == 35) {
            result[3] = (result[3].toInt() xor 0x01).toByte()
        }
        val transportResult = response(0x9000u.toUShort(), alterResponse?.invoke(ins.toInt(), result) ?: result)
        if (ins.toInt() == suspendAfterINS && !suspensionDelivered) {
            suspensionDelivered = true
            return suspendCoroutine { continuation ->
                pendingContinuation = continuation
                pendingResponse = transportResult
            }
        }
        return transportResult
    }

    override fun invalidateSession() {
        invalidateCalls++
        if (throwOnInvalidate) throw IllegalStateException("invalidation failed")
        runtime.abort(StreamDemoStreamEndpoint.ABORT_HOST_FAILURE)
    }

    fun workspaceIsCleared(): Boolean = workspace.all { it == 0.toByte() }

    fun resumeSuspendedResponse() {
        val continuation = requireNotNull(pendingContinuation)
        val response = requireNotNull(pendingResponse)
        pendingContinuation = null
        pendingResponse = null
        continuation.resume(response)
    }

    private fun response(sw: UShort, data: ByteArray): APDUResponse = APDUResponse(
        data + byteArrayOf(
            ((sw.toInt() ushr 8) and 0xFF).toByte(),
            (sw.toInt() and 0xFF).toByte(),
        ),
    )
}

class StreamClientHarness {
    // Exact scalar payloads decode; short and one-byte-long payloads refuse.
    @Test
    fun generatedClientRequiresTheExactFixedResponseLength() {
        assertEquals(0x07u.toUByte(), runSuspend { StreamDemoClient(RuntimeBackedTransport()).getVersion() })

        assertFailsWith<StreamDemoClientException.InvalidResponse> {
            runSuspend {
                StreamDemoClient(RuntimeBackedTransport(ordinaryResponseData = byteArrayOf())).getVersion()
            }
        }
        assertFailsWith<StreamDemoClientException.InvalidResponse> {
            runSuspend {
                StreamDemoClient(RuntimeBackedTransport(ordinaryResponseData = byteArrayOf(0x07, 0x08))).getVersion()
            }
        }
    }

    // Fixed multi-field responses decode only at their exact 15-byte size.
    @Test
    fun generatedClientRequiresTheExactMultiFieldResponseLength() {
        val exact = runSuspend { StreamDemoClient(RuntimeBackedTransport()).getFixedInfo() }
        assertEquals(0x01u.toUByte(), exact.schema)
        assertContentEquals(ByteArray(10) { it.toByte() }, exact.identity)
        assertEquals(2u, exact.generation)

        assertFailsWith<StreamDemoClientException.InvalidResponse> {
            runSuspend {
                StreamDemoClient(RuntimeBackedTransport(fixedResponseData = ByteArray(14))).getFixedInfo()
            }
        }
        assertFailsWith<StreamDemoClientException.InvalidResponse> {
            runSuspend {
                StreamDemoClient(RuntimeBackedTransport(fixedResponseData = ByteArray(16))).getFixedInfo()
            }
        }
    }

    // Invalid descriptors refuse before READ_CHUNK; the unmodified descriptor succeeds.
    @Test
    fun generatedClientRejectsMalformedDescriptorsBeforeReading() {
        val request = ByteArray(300) { it.toByte() }
        assertContentEquals(request.reversedArray(), runSuspend {
            StreamDemoClient(RuntimeBackedTransport()).processPacket(request)
        })
        val mutations: List<(ByteArray) -> ByteArray> = listOf(
            { it.copyOf(34) },
            { it + byteArrayOf(0) },
            { it.copyOf().apply { this[0] = 0 } },
            { it.copyOf().apply { this[1] = 0; this[2] = 0 } },
            { it.copyOf().apply { this[0] = 10; this[1] = 7; this[2] = 1 } },
            { it.copyOf().apply { this[0] = 1 } },
        )
        for (mutate in mutations) {
            val transport = RuntimeBackedTransport(alterResponse = { ins, data ->
                if (ins == 0x21) mutate(data) else data
            })
            assertFailsWith<StreamDemoClientException.InvalidResponse> {
                runSuspend { StreamDemoClient(transport).processPacket(request) }
            }
            assertEquals(0, transport.readChunkCalls)
            assertEquals(1, transport.abortCalls)
            assertEquals(1, transport.invalidateCalls)
            assertTrue(transport.workspaceIsCleared())
        }
    }

    // Empty/oversize uploads refuse before WRITE_CHUNK; the exact maximum succeeds.
    @Test
    fun generatedClientRejectsUploadBoundsBeforeWriting() {
        val valid = ByteArray(1792) { it.toByte() }
        assertContentEquals(valid.reversedArray(), runSuspend {
            StreamDemoClient(RuntimeBackedTransport()).processPacket(valid)
        })
        for (size in listOf(0, 1793)) {
            val transport = RuntimeBackedTransport()
            assertFailsWith<StreamDemoClientException.InvalidResponse> {
                runSuspend { StreamDemoClient(transport).processPacket(ByteArray(size)) }
            }
            assertEquals(0, transport.writeChunkCalls)
            assertEquals(0, transport.handlerExecutions)
            assertTrue(transport.workspaceIsCleared())
        }
    }

    // Nonempty success acknowledgements and short/long chunks refuse with cleanup.
    @Test
    fun generatedClientRejectsMalformedChunksAndAcknowledgements() {
        for ((command, delta) in listOf(0x20 to 1, 0x23 to -1, 0x23 to 1, 0x24 to 1)) {
            val transport = RuntimeBackedTransport(alterResponse = { ins, data ->
                if (ins == command) data.copyOf(data.size + delta) else data
            })
            assertFailsWith<StreamDemoClientException.InvalidResponse> {
                runSuspend { StreamDemoClient(transport).processPacket(ByteArray(300) { it.toByte() }) }
            }
            assertEquals(1, transport.abortCalls)
            assertEquals(1, transport.invalidateCalls)
            if (command == 0x23) assertEquals(1, transport.readChunkCalls)
            assertTrue(transport.workspaceIsCleared())
        }
    }

    // Success returns payload bytes; an explicit error preserves its exact status.
    @Test
    fun generatedClientRejectsErrorStatusWord() {
        val transport = RuntimeBackedTransport(ordinaryStatusWord = 0x6985u.toUShort())
        val failure = assertFailsWith<StreamDemoClientException.StatusWord> {
            runSuspend { StreamDemoClient(transport).getVersion() }
        }
        assertEquals(0x6985u.toUShort(), failure.sw)
        assertEquals(0x07u.toUByte(), runSuspend { StreamDemoClient(RuntimeBackedTransport()).getVersion() })
    }

    // Lost idempotent replies recover without duplicating handler execution.
    @Test
    fun generatedClientAndRuntimeRecoverLostCloseResponsesWithoutRepeatingTheHandler() {
        val transport = RuntimeBackedTransport(
            loseWriteChunkResponse = true,
            loseCloseWriteResponse = true,
            loseReadChunkResponse = true,
            loseCloseReadResponse = true,
        )
        val client = StreamDemoClient(transport)
        val request = ByteArray(300) { index -> (index and 0xFF).toByte() }

        val response = runSuspend { client.processPacket(request) }

        assertContentEquals(request.reversedArray(), response)
        assertEquals(1, transport.handlerExecutions)
        assertEquals(3, transport.writeChunkCalls)
        assertEquals(1, transport.pendingInfoCalls)
        assertEquals(3, transport.readChunkCalls)
        assertEquals(2, transport.closeReadCalls)
        assertEquals(0, transport.abortCalls)
        assertEquals(0, transport.invalidateCalls)
        assertTrue(transport.workspaceIsCleared())
    }

    // A one-bit digest mismatch aborts and invalidates instead of returning bytes.
    @Test
    fun generatedClientAbortsGeneratedRuntimeWhenTheResultDigestDoesNotMatch() {
        val transport = RuntimeBackedTransport(corruptDescriptorDigest = true)
        val client = StreamDemoClient(transport)
        val request = ByteArray(300) { index -> (index and 0xFF).toByte() }

        assertFailsWith<StreamDemoClientException.InvalidResponse> {
            runSuspend { client.processPacket(request) }
        }

        assertEquals(1, transport.handlerExecutions)
        assertEquals(1, transport.abortCalls)
        assertEquals(1, transport.invalidateCalls)
        assertTrue(transport.workspaceIsCleared())
    }

    // Cancellation at the four exercised APDU boundaries invalidates the session.
    @Test
    fun generatedClientInvalidatesTheTransportAtEveryCancellationBoundary() {
        for (ins in listOf(0x20, 0x21, 0x23, 0x24)) {
            val transport = RuntimeBackedTransport(cancelAfterINS = ins)
            val client = StreamDemoClient(transport)
            val request = ByteArray(300) { index -> (index and 0xFF).toByte() }

            assertFailsWith<CancellationException>("INS 0x%02X".format(ins)) {
                runSuspend { client.processPacket(request) }
            }

            assertEquals(1, transport.invalidateCalls, "INS 0x%02X".format(ins))
            assertTrue(transport.workspaceIsCleared(), "INS 0x%02X".format(ins))
        }
    }

    // Concurrent use refuses without touching the suspended session, which recovers.
    @Test
    fun generatedClientRejectsASecondStreamOperationWithoutTouchingTheFirstSession() {
        val transport = RuntimeBackedTransport(suspendAfterINS = 0x20)
        val client = StreamDemoClient(transport)
        val request = ByteArray(300) { index -> (index and 0xFF).toByte() }
        var firstResult: Result<ByteArray>? = null
        suspend { client.processPacket(request) }.startCoroutine(object : Continuation<ByteArray> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<ByteArray>) { firstResult = result }
        })
        assertEquals(null, firstResult)

        assertFailsWith<StreamDemoClientException.StreamBusy> {
            runSuspend { client.processPacket(byteArrayOf(9)) }
        }
        assertEquals(0, transport.abortCalls)
        assertEquals(0, transport.invalidateCalls)

        transport.resumeSuspendedResponse()
        assertContentEquals(request.reversedArray(), requireNotNull(firstResult).getOrThrow())
        assertTrue(transport.workspaceIsCleared())
    }

    // Cleanup failure preserves the original protocol error or cancellation.
    @Test
    fun invalidationFailureDoesNotMaskTheAuthoritativeFailureOrCancellation() {
        val protocolTransport = RuntimeBackedTransport(
            corruptDescriptorDigest = true,
            throwOnInvalidate = true,
        )
        assertFailsWith<StreamDemoClientException.InvalidResponse> {
            runSuspend { StreamDemoClient(protocolTransport).processPacket(ByteArray(300) { it.toByte() }) }
        }
        assertEquals(1, protocolTransport.invalidateCalls)

        val cancelledTransport = RuntimeBackedTransport(
            cancelAfterINS = 0x20,
            throwOnInvalidate = true,
        )
        assertFailsWith<CancellationException> {
            runSuspend { StreamDemoClient(cancelledTransport).processPacket(ByteArray(300) { it.toByte() }) }
        }
        assertEquals(1, cancelledTransport.invalidateCalls)
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(resumeResult: Result<T>) {
            result = resumeResult
        }
    })
    return requireNotNull(result).getOrThrow()
}
