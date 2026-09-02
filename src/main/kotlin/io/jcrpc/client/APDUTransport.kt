package io.jcrpc.client

interface APDUTransport {
    suspend fun transmit(command: APDUCommand): APDUResponse

    /**
     * Invalidates the currently selected card session after a generated RPC
     * operation can no longer prove its stream state.
     *
     * Implementations must synchronously close the logical channel or
     * connection. The caller must establish a new session and select the
     * applet before sending another command.
     */
    fun invalidateSession()
}
