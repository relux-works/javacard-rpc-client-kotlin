package io.jcrpc.streamdemo.server;

/**
 * Generated single-owner, bounded, half-duplex stream state machine.
 *
 * The skeleton constructs exactly one instance and injects CLEAR_ON_DESELECT
 * arrays for everything mutable: the byte workspace, the digest scratch, the
 * short[] scalar state machine and the one-slot handler reference. This class
 * declares no mutable field of its own, so a WRITE chunk, a result or an abort
 * never touches persistent memory, and deselect or reset returns the session to
 * the all-zero empty state without a reset marker (security audit S-06).
 * Command processing reuses one exception object and never allocates an object
 * or array.
 */
public final class StreamDemoBoundedStreamRuntime implements StreamDemoStreamEndpoint {
    private static final short SW_WRONG_LENGTH = (short) 0x6700;
    private static final short SW_INVALID_DATA = (short) 0x6A80;
    private static final short SW_WRONG_STATE  = (short) 0x6985;
    private static final short SW_NO_MEMORY    = (short) 0x6A84;

    private static final byte STATE_EMPTY        = (byte) 0x00;
    private static final byte STATE_WRITING      = (byte) 0x01;
    private static final byte STATE_READ_PENDING = (byte) 0x02;
    private static final byte STATE_READ_CLOSED  = (byte) 0x03;
    private static final byte STATE_SHORT_CLOSED = (byte) 0x04;

    private static final short DIGEST_LENGTH = (short) 32;
    private static final short CLOSE_LENGTH = (short) 34;
    private static final short DESCRIPTOR_LENGTH = (short) 35;
    private static final short SHORT_RESPONSE_CAPACITY = (short) 255;

    // Layout of the CLEAR_ON_DESELECT short[] scalar array. The all-zero
    // array is the empty state: STATE_EMPTY is 0, booleans are 0/1 and every
    // length or index is only read after begin() has written it.
    private static final short IDX_STATE                      = (short) 0;
    private static final short IDX_ACTIVE_METHOD              = (short) 1;
    private static final short IDX_HAS_REQUEST_STREAM         = (short) 2;
    private static final short IDX_HAS_RESPONSE_STREAM        = (short) 3;
    private static final short IDX_REQUEST_MAX_LENGTH         = (short) 4;
    private static final short IDX_REQUEST_CHUNK_SIZE         = (short) 5;
    private static final short IDX_RESPONSE_MAX_LENGTH        = (short) 6;
    private static final short IDX_RESPONSE_CHUNK_SIZE        = (short) 7;
    private static final short IDX_EXACT_SHORT_RESPONSE_LENGTH = (short) 8;
    private static final short IDX_INPUT_LENGTH               = (short) 9;
    private static final short IDX_LAST_CHUNK_OFFSET          = (short) 10;
    private static final short IDX_LAST_CHUNK_LENGTH          = (short) 11;
    private static final short IDX_SHORT_RESULT_LENGTH        = (short) 12;
    private static final short IDX_RESULT_LENGTH              = (short) 13;
    private static final short IDX_PACKET_COUNT               = (short) 14;
    private static final short IDX_NEXT_PACKET_INDEX          = (short) 15;
    private static final short IDX_RESULT_PACKET_COUNT        = (short) 16;
    /** Required length of the injected scalar array. */
    public static final short SCALAR_COUNT = (short) 17;
    /** Required length of the injected handler reference array. */
    public static final short HANDLER_SLOT_COUNT = (short) 1;
    private static final short IDX_HANDLER = (short) 0;

    private final byte[] workspace;
    private final byte[] digestScratch;
    private final short[] scalars;
    private final Object[] handlerSlot;
    private final Sha256 sha256;
    private final StreamStatusWordException failure;

    public StreamDemoBoundedStreamRuntime(
            byte[] workspace,
            byte[] digestScratch,
            short[] scalars,
            Object[] handlerSlot,
            Sha256 sha256) {
        this.workspace = workspace;
        this.digestScratch = digestScratch;
        this.scalars = scalars;
        this.handlerSlot = handlerSlot;
        this.sha256 = sha256;
        this.failure = new StreamStatusWordException(SW_WRONG_STATE);

        if (workspace == null || workspace.length == 0 ||
                digestScratch == null || digestScratch.length < DIGEST_LENGTH ||
                scalars == null || scalars.length < SCALAR_COUNT ||
                handlerSlot == null || handlerSlot.length < HANDLER_SLOT_COUNT ||
                sha256 == null) {
            failure.setStatusWord(SW_NO_MEMORY);
            throw failure;
        }
        clearAll();
    }

    @Override
    public short dispatch(
            byte methodId,
            byte operation,
            boolean requestEnabled,
            short requestLimit,
            short requestChunk,
            boolean responseEnabled,
            short responseLimit,
            short responseChunk,
            short shortResponseLength,
            Handler handler,
            byte p1,
            byte p2,
            byte[] requestBuffer,
            short requestOffset,
            short requestLength,
            byte[] responseBuffer,
            short responseOffset,
            short responseCapacity) {
        try {
            validateRange(requestBuffer, requestOffset, requestLength);
            validateRange(responseBuffer, responseOffset, responseCapacity);

            if (operation == OP_ABORT) {
                requireEmptyCommand(p1, p2, requestLength);
                abort(ABORT_EXPLICIT);
                return (short) 0;
            }

            if (operation == OP_WRITE_OR_INVOKE) {
                if (scalars[IDX_STATE] == STATE_READ_CLOSED ||
                        scalars[IDX_STATE] == STATE_SHORT_CLOSED) {
                    clearAll();
                }
                if (scalars[IDX_STATE] == STATE_EMPTY) {
                    begin(methodId, requestEnabled, requestLimit, requestChunk,
                            responseEnabled, responseLimit, responseChunk,
                            shortResponseLength, handler);
                } else if (scalars[IDX_ACTIVE_METHOD] != methodId) {
                    rejectWithoutClearing(SW_WRONG_STATE);
                }
            } else if (scalars[IDX_STATE] == STATE_EMPTY ||
                    scalars[IDX_ACTIVE_METHOD] != methodId) {
                rejectWithoutClearing(SW_WRONG_STATE);
            }

            switch (operation) {
                case OP_WRITE_OR_INVOKE:
                    return writeOrInvoke(p1, p2, requestBuffer, requestOffset, requestLength,
                            responseBuffer, responseOffset, responseCapacity);
                case OP_CLOSE_WRITE:
                    return closeWrite(p1, p2, requestBuffer, requestOffset, requestLength,
                            responseBuffer, responseOffset, responseCapacity);
                case OP_GET_PENDING_READ_INFO:
                    requireEmptyCommand(p1, p2, requestLength);
                    return pendingInfo(responseBuffer, responseOffset, responseCapacity);
                case OP_READ_CHUNK:
                    requireEmptyData(requestLength);
                    return readChunk(p1, p2, responseBuffer, responseOffset, responseCapacity);
                case OP_CLOSE_READ:
                    return closeRead(p1, p2, requestBuffer, requestOffset, requestLength);
                default:
                    fail(SW_WRONG_STATE);
                    return (short) 0;
            }
        } catch (StreamStatusWordException e) {
            if (e != failure) {
                clearAll();
            }
            throw e;
        } catch (RuntimeException e) {
            clearAll();
            throw e;
        }
    }

    @Override
    public void abort(byte reason) {
        clearAll();
    }

    private boolean hasRequestStream() {
        return scalars[IDX_HAS_REQUEST_STREAM] != 0;
    }

    private boolean hasResponseStream() {
        return scalars[IDX_HAS_RESPONSE_STREAM] != 0;
    }

    private void begin(
            byte methodId,
            boolean requestEnabled,
            short requestLimit,
            short requestChunk,
            boolean responseEnabled,
            short responseLimit,
            short responseChunk,
            short shortResponseLength,
            Handler handler) {
        short required = requestEnabled ? requestLimit : (short) 0;
        if (responseEnabled && responseLimit > required) {
            required = responseLimit;
        }
        short shortCapacity = shortResponseLength >= 0 ?
                shortResponseLength : SHORT_RESPONSE_CAPACITY;
        if (!responseEnabled && required < shortCapacity) {
            required = shortCapacity;
        }
        if ((!requestEnabled && !responseEnabled) || handler == null ||
                (requestEnabled && (requestLimit <= 0 || requestChunk <= 0)) ||
                (responseEnabled && (responseLimit <= 0 || responseChunk <= 0)) ||
                (responseEnabled && shortResponseLength != (short) -1) ||
                (!responseEnabled &&
                        (shortResponseLength < (short) -1 ||
                                shortResponseLength > SHORT_RESPONSE_CAPACITY)) ||
                required > workspace.length) {
            fail(SW_NO_MEMORY);
        }

        scalars[IDX_ACTIVE_METHOD] = methodId;
        scalars[IDX_HAS_REQUEST_STREAM] = requestEnabled ? (short) 1 : (short) 0;
        scalars[IDX_HAS_RESPONSE_STREAM] = responseEnabled ? (short) 1 : (short) 0;
        scalars[IDX_REQUEST_MAX_LENGTH] = requestLimit;
        scalars[IDX_REQUEST_CHUNK_SIZE] = requestChunk;
        scalars[IDX_RESPONSE_MAX_LENGTH] = responseLimit;
        scalars[IDX_RESPONSE_CHUNK_SIZE] = responseChunk;
        scalars[IDX_EXACT_SHORT_RESPONSE_LENGTH] = shortResponseLength;
        handlerSlot[IDX_HANDLER] = handler;
    }

    private short writeOrInvoke(
            byte p1,
            byte p2,
            byte[] request,
            short requestOffset,
            short requestLength,
            byte[] response,
            short responseOffset,
            short responseCapacity) {
        if (!hasRequestStream()) {
            if (scalars[IDX_STATE] != STATE_EMPTY) {
                rejectWithoutClearing(SW_WRONG_STATE);
            }
            requireZeroParameters(p1, p2);
            preflightHandlerResponse(responseCapacity);
            return executeOnce(request, requestOffset, requestLength,
                    response, responseOffset, responseCapacity, false);
        }

        int index = p1 & 0xFF;
        int count = p2 & 0xFF;
        int length = requestLength & 0xFFFF;
        int chunkSize = scalars[IDX_REQUEST_CHUNK_SIZE] & 0xFFFF;
        int maxPackets = ((scalars[IDX_REQUEST_MAX_LENGTH] & 0xFFFF) + chunkSize - 1) / chunkSize;
        if (count == 0 || count > maxPackets || index >= count || length == 0 ||
                length > chunkSize || (index < count - 1 && length != chunkSize)) {
            fail(SW_WRONG_LENGTH);
        }

        if (scalars[IDX_STATE] == STATE_WRITING &&
                index == ((scalars[IDX_NEXT_PACKET_INDEX] & 0xFF) - 1)) {
            if (count != (scalars[IDX_PACKET_COUNT] & 0xFF) ||
                    length != (scalars[IDX_LAST_CHUNK_LENGTH] & 0xFFFF) ||
                    !equalsRange(workspace, scalars[IDX_LAST_CHUNK_OFFSET],
                            request, requestOffset, requestLength)) {
                fail(SW_INVALID_DATA);
            }
            return (short) 0;
        }

        if (index == 0 && scalars[IDX_STATE] == STATE_EMPTY) {
            scalars[IDX_STATE] = STATE_WRITING;
            scalars[IDX_PACKET_COUNT] = p2;
            scalars[IDX_NEXT_PACKET_INDEX] = (short) 0;
        }
        if (scalars[IDX_STATE] != STATE_WRITING ||
                count != (scalars[IDX_PACKET_COUNT] & 0xFF) ||
                index != (scalars[IDX_NEXT_PACKET_INDEX] & 0xFF)) {
            fail(SW_INVALID_DATA);
        }
        if ((scalars[IDX_INPUT_LENGTH] & 0xFFFF) + length >
                (scalars[IDX_REQUEST_MAX_LENGTH] & 0xFFFF)) {
            fail(SW_WRONG_LENGTH);
        }

        scalars[IDX_LAST_CHUNK_OFFSET] = scalars[IDX_INPUT_LENGTH];
        scalars[IDX_LAST_CHUNK_LENGTH] = requestLength;
        copy(request, requestOffset, workspace, scalars[IDX_INPUT_LENGTH], requestLength);
        scalars[IDX_INPUT_LENGTH] = (short) ((scalars[IDX_INPUT_LENGTH] & 0xFFFF) + length);
        scalars[IDX_NEXT_PACKET_INDEX] = (short) (index + 1);
        return (short) 0;
    }

    private short closeWrite(
            byte p1,
            byte p2,
            byte[] request,
            short requestOffset,
            short requestLength,
            byte[] response,
            short responseOffset,
            short responseCapacity) {
        requireZeroParameters(p1, p2);
        requireCloseData(requestLength);

        int declaredLength = readU16(request, requestOffset);
        short digestOffset = (short) (requestOffset + 2);
        if (scalars[IDX_STATE] == STATE_SHORT_CLOSED) {
            if (declaredLength != (scalars[IDX_INPUT_LENGTH] & 0xFFFF) ||
                    !equalsRange(digestScratch, (short) 0, request, digestOffset, DIGEST_LENGTH)) {
                fail(SW_INVALID_DATA);
            }
            ensureResponseCapacity(responseCapacity, scalars[IDX_SHORT_RESULT_LENGTH]);
            copy(workspace, (short) 0, response, responseOffset, scalars[IDX_SHORT_RESULT_LENGTH]);
            return scalars[IDX_SHORT_RESULT_LENGTH];
        }
        if (!hasRequestStream() || scalars[IDX_STATE] != STATE_WRITING ||
                (scalars[IDX_NEXT_PACKET_INDEX] & 0xFF) != (scalars[IDX_PACKET_COUNT] & 0xFF)) {
            fail(SW_WRONG_STATE);
        }
        if (declaredLength != (scalars[IDX_INPUT_LENGTH] & 0xFFFF)) {
            fail(SW_WRONG_LENGTH);
        }

        preflightHandlerResponse(responseCapacity);
        sha256.digest(workspace, (short) 0, scalars[IDX_INPUT_LENGTH], digestScratch, (short) 0);
        if (!equalsRange(digestScratch, (short) 0, request, digestOffset, DIGEST_LENGTH)) {
            fail(SW_INVALID_DATA);
        }
        return executeOnce(workspace, (short) 0, scalars[IDX_INPUT_LENGTH],
                response, responseOffset, responseCapacity, true);
    }

    private short shortOutputCapacity() {
        return scalars[IDX_EXACT_SHORT_RESPONSE_LENGTH] >= 0 ?
                scalars[IDX_EXACT_SHORT_RESPONSE_LENGTH] : SHORT_RESPONSE_CAPACITY;
    }

    private void preflightHandlerResponse(short responseCapacity) {
        ensureResponseCapacity(responseCapacity,
                hasResponseStream() ? DESCRIPTOR_LENGTH : shortOutputCapacity());
    }

    private short executeOnce(
            byte[] input,
            short inputOffset,
            short inputSize,
            byte[] response,
            short responseOffset,
            short responseCapacity,
            boolean preserveInputCloseReceipt) {
        short outputCapacity = hasResponseStream() ?
                scalars[IDX_RESPONSE_MAX_LENGTH] : shortOutputCapacity();
        short produced = ((Handler) handlerSlot[IDX_HANDLER]).execute(
                (byte) scalars[IDX_ACTIVE_METHOD], input, inputOffset, inputSize,
                workspace, (short) 0, outputCapacity);
        if (produced < 0 || produced > outputCapacity ||
                (hasResponseStream() && produced == 0) ||
                (!hasResponseStream() && scalars[IDX_EXACT_SHORT_RESPONSE_LENGTH] >= 0 &&
                        produced != scalars[IDX_EXACT_SHORT_RESPONSE_LENGTH])) {
            fail(SW_WRONG_LENGTH);
        }

        scalars[IDX_RESULT_LENGTH] = produced;
        if (hasResponseStream()) {
            sha256.digest(workspace, (short) 0, produced, digestScratch, (short) 0);
            int chunkSize = scalars[IDX_RESPONSE_CHUNK_SIZE] & 0xFFFF;
            int chunks = ((produced & 0xFFFF) + chunkSize - 1) / chunkSize;
            scalars[IDX_RESULT_PACKET_COUNT] = (short) chunks;
            scalars[IDX_STATE] = STATE_READ_PENDING;
            return writeDescriptor(response, responseOffset, responseCapacity);
        }

        copy(workspace, (short) 0, response, responseOffset, produced);
        scalars[IDX_SHORT_RESULT_LENGTH] = produced;
        if (preserveInputCloseReceipt) {
            scalars[IDX_STATE] = STATE_SHORT_CLOSED;
        } else {
            clearAll();
        }
        return produced;
    }

    private short pendingInfo(byte[] response, short responseOffset, short responseCapacity) {
        if (!hasResponseStream() || scalars[IDX_STATE] != STATE_READ_PENDING) {
            fail(SW_WRONG_STATE);
        }
        return writeDescriptor(response, responseOffset, responseCapacity);
    }

    private short readChunk(
            byte p1,
            byte p2,
            byte[] response,
            short responseOffset,
            short responseCapacity) {
        if (!hasResponseStream() || scalars[IDX_STATE] != STATE_READ_PENDING) {
            fail(SW_WRONG_STATE);
        }
        int index = p1 & 0xFF;
        int count = p2 & 0xFF;
        if (count != (scalars[IDX_RESULT_PACKET_COUNT] & 0xFF) || index >= count) {
            fail(SW_INVALID_DATA);
        }
        int chunkSize = scalars[IDX_RESPONSE_CHUNK_SIZE] & 0xFFFF;
        int offset = index * chunkSize;
        int remaining = (scalars[IDX_RESULT_LENGTH] & 0xFFFF) - offset;
        int length = remaining < chunkSize ? remaining : chunkSize;
        ensureResponseCapacity(responseCapacity, (short) length);
        copy(workspace, (short) offset, response, responseOffset, (short) length);
        return (short) length;
    }

    private short closeRead(
            byte p1,
            byte p2,
            byte[] request,
            short requestOffset,
            short requestLength) {
        requireZeroParameters(p1, p2);
        requireCloseData(requestLength);
        int declaredLength = readU16(request, requestOffset);
        short digestOffset = (short) (requestOffset + 2);

        if (scalars[IDX_STATE] != STATE_READ_PENDING && scalars[IDX_STATE] != STATE_READ_CLOSED) {
            fail(SW_WRONG_STATE);
        }
        if (declaredLength != (scalars[IDX_RESULT_LENGTH] & 0xFFFF) ||
                !equalsRange(digestScratch, (short) 0, request, digestOffset, DIGEST_LENGTH)) {
            fail(SW_INVALID_DATA);
        }
        if (scalars[IDX_STATE] == STATE_READ_PENDING) {
            wipe(workspace);
            scalars[IDX_STATE] = STATE_READ_CLOSED;
        }
        return (short) 0;
    }

    private short writeDescriptor(byte[] response, short offset, short capacity) {
        ensureResponseCapacity(capacity, DESCRIPTOR_LENGTH);
        response[offset] = (byte) scalars[IDX_RESULT_PACKET_COUNT];
        response[(short) (offset + 1)] = (byte) ((scalars[IDX_RESULT_LENGTH] >>> 8) & 0xFF);
        response[(short) (offset + 2)] = (byte) (scalars[IDX_RESULT_LENGTH] & 0xFF);
        copy(digestScratch, (short) 0, response, (short) (offset + 3), DIGEST_LENGTH);
        return DESCRIPTOR_LENGTH;
    }

    private void requireCloseData(short length) {
        if (length != CLOSE_LENGTH) {
            fail(SW_WRONG_LENGTH);
        }
    }

    private void requireEmptyCommand(byte p1, byte p2, short length) {
        requireZeroParameters(p1, p2);
        requireEmptyData(length);
    }

    private void requireZeroParameters(byte p1, byte p2) {
        if (p1 != 0 || p2 != 0) {
            fail(SW_INVALID_DATA);
        }
    }

    private void requireEmptyData(short length) {
        if (length != 0) {
            fail(SW_WRONG_LENGTH);
        }
    }

    private void ensureResponseCapacity(short capacity, short required) {
        if (required < 0 || capacity < required) {
            fail(SW_WRONG_LENGTH);
        }
    }

    private int readU16(byte[] input, short offset) {
        return ((input[offset] & 0xFF) << 8) |
                (input[(short) (offset + 1)] & 0xFF);
    }

    private void validateRange(byte[] buffer, short offset, short length) {
        if (buffer == null || offset < 0 || length < 0 ||
                (offset & 0xFFFF) + (length & 0xFFFF) > buffer.length) {
            fail(SW_WRONG_LENGTH);
        }
    }

    private boolean equalsRange(
            byte[] left,
            short leftOffset,
            byte[] right,
            short rightOffset,
            short length) {
        for (short i = 0; i < length; i++) {
            if (left[(short) (leftOffset + i)] != right[(short) (rightOffset + i)]) {
                return false;
            }
        }
        return true;
    }

    private void copy(
            byte[] source,
            short sourceOffset,
            byte[] target,
            short targetOffset,
            short length) {
        for (short i = 0; i < length; i++) {
            target[(short) (targetOffset + i)] = source[(short) (sourceOffset + i)];
        }
    }

    private void wipe(byte[] value) {
        if (value == null) {
            return;
        }
        for (short i = 0; i < value.length; i++) {
            value[i] = (byte) 0;
        }
    }

    /**
     * Return to the empty state. Writes only the injected transient arrays,
     * so the result is bit-identical to a freshly cleared transient array.
     */
    private void clearAll() {
        wipe(workspace);
        wipe(digestScratch);
        for (short i = 0; i < SCALAR_COUNT; i++) {
            scalars[i] = (short) 0;
        }
        handlerSlot[IDX_HANDLER] = null;
    }

    private void fail(short statusWord) {
        clearAll();
        failure.setStatusWord(statusWord);
        throw failure;
    }

    private void rejectWithoutClearing(short statusWord) {
        failure.setStatusWord(statusWord);
        throw failure;
    }
}
