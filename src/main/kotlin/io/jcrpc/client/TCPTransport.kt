package io.jcrpc.client

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TCPTransport(
    private val host: String = "127.0.0.1",
    private val port: Int = 9025,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 5_000,
) : APDUTransport {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    fun connect() {
        if (socket != null) {
            return
        }

        try {
            val created = Socket()
            created.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            created.soTimeout = readTimeoutMillis
            socket = created
            input = created.getInputStream()
            output = created.getOutputStream()
        } catch (e: Exception) {
            disconnect()
            throw APDUConnectionException("Connection failed: ${e.message ?: "unknown error"}", e)
        }
    }

    fun disconnect() {
        try {
            input?.close()
        } catch (_: Exception) {
        }
        try {
            output?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        input = null
        output = null
        socket = null
    }

    override suspend fun transmit(command: APDUCommand): APDUResponse {
        val inStream = input ?: throw APDUConnectionException("Not connected")
        val outStream = output ?: throw APDUConnectionException("Not connected")

        try {
            val apdu = command.bytes
            val payloadLength = 1 + apdu.size
            require(payloadLength <= 0xFFFF) { "Bridge frame is too large" }

            val frame = ByteArray(2 + payloadLength)
            frame[0] = ((payloadLength ushr 8) and 0xFF).toByte()
            frame[1] = (payloadLength and 0xFF).toByte()
            frame[2] = 0x01
            apdu.copyInto(frame, destinationOffset = 3)

            outStream.write(frame)
            outStream.flush()

            val respLenBuf = recvExact(inStream, 2)
            val respLen = ((respLenBuf[0].toUByte().toInt() shl 8) or respLenBuf[1].toUByte().toInt())
            require(respLen > 0) { "Bridge returned empty response frame" }

            val respBuf = recvExact(inStream, respLen)
            when (respBuf[0].toUByte().toInt()) {
                0x81 -> {
                    val rapdu = respBuf.copyOfRange(1, respBuf.size)
                    return APDUResponse(rapdu)
                }

                0xE0 -> {
                    val message = if (respBuf.size > 4) {
                        respBuf.copyOfRange(4, respBuf.size).decodeToString()
                    } else {
                        "unknown bridge error"
                    }
                    throw APDUConnectionException("Bridge error: $message")
                }

                else -> throw APDUInvalidResponseException("Unsupported bridge frame type: 0x%02X".format(respBuf[0].toUByte().toInt()))
            }
        } catch (e: APDUException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw APDUTimeoutException(cause = e)
        } catch (e: IllegalArgumentException) {
            throw APDUInvalidResponseException(e.message ?: "Invalid bridge response", e)
        } catch (e: EOFException) {
            throw APDUInvalidResponseException("Unexpected end of stream", e)
        } catch (e: Exception) {
            throw APDUConnectionException("Transport failure: ${e.message ?: "unknown error"}", e)
        }
    }

    private fun recvExact(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(out, offset, count - offset)
            if (read < 0) {
                throw EOFException("Stream closed")
            }
            offset += read
        }
        return out
    }
}
