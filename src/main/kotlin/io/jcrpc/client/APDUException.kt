package io.jcrpc.client

open class APDUException(message: String, cause: Throwable? = null) : Exception(message, cause)

class APDUStatusWordException(
    val sw1: UByte,
    val sw2: UByte,
) : APDUException("APDU error: SW=%02X%02X".format(sw1.toInt(), sw2.toInt())) {
    val sw: UShort = (((sw1.toUInt() shl 8) or sw2.toUInt()) and 0xFFFFu).toUShort()
}

class APDUConnectionException(message: String, cause: Throwable? = null) : APDUException(message, cause)

class APDUTimeoutException(message: String = "APDU transport timeout", cause: Throwable? = null) :
    APDUException(message, cause)

class APDUInvalidResponseException(message: String = "Invalid APDU response", cause: Throwable? = null) :
    APDUException(message, cause)
