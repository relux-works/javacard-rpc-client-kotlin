package io.jcrpc.streamdemo.server;

import javacard.framework.JCSystem;

/**
 * Generated boundary for the single applet-level bounded stream session.
 * One implementation owns every streamed method in one selected applet.
 */
public interface StreamDemoStreamEndpoint {
    byte OP_WRITE_OR_INVOKE       = (byte) 0x00;
    byte OP_CLOSE_WRITE           = (byte) 0x01;
    byte OP_GET_PENDING_READ_INFO = (byte) 0x02;
    byte OP_READ_CHUNK            = (byte) 0x03;
    byte OP_CLOSE_READ            = (byte) 0x04;
    byte OP_ABORT                 = (byte) 0x05;

    byte ABORT_EXPLICIT     = (byte) 0x01;
    byte ABORT_DESELECT     = (byte) 0x02;
    byte ABORT_RESET        = (byte) 0x03;
    byte ABORT_HOST_FAILURE = (byte) 0x04;

    /**
     * Return the response length. Protocol failures use the runtime's one
     * reusable StreamStatusWordException; generated skeleton handlers use
     * failStream(statusWord). No command-path allocation is permitted.
     */
    short dispatch(
            byte methodId,
            byte operation,
            boolean hasRequestStream,
            short requestMaxLength,
            short requestChunkSize,
            boolean hasResponseStream,
            short responseMaxLength,
            short responseChunkSize,
            short exactShortResponseLength,
            Handler handler,
            byte p1,
            byte p2,
            byte[] requestBuffer,
            short requestOffset,
            short requestLength,
            byte[] responseBuffer,
            short responseOffset,
            short responseCapacity);

    void abort(byte reason);

    interface Handler {
        /**
         * Execute one generated method. Input and output may share storage.
         * Return 0..outputCapacity. For a fixed short response, outputCapacity
         * is the exact declared width and the returned length must equal it.
         * Use the generated skeleton's failStream(statusWord) helper for a
         * business status word.
         */
        short execute(
                byte methodId,
                byte[] input,
                short inputOffset,
                short inputLength,
                byte[] output,
                short outputOffset,
                short outputCapacity);
    }

    interface Sha256 {
        void digest(
                byte[] input,
                short inputOffset,
                short inputLength,
                byte[] output,
                short outputOffset);
    }

    /** One exception object is allocated once; its status is transient RAM. */
    final class StreamStatusWordException extends RuntimeException {
        private final short[] status;

        public StreamStatusWordException(short statusWord) {
            super();
            this.status = JCSystem.makeTransientShortArray(
                    (short) 1, JCSystem.CLEAR_ON_RESET);
            this.status[0] = statusWord;
        }

        public void setStatusWord(short statusWord) {
            // This array is transient RAM; changing status never writes EEPROM.
            this.status[0] = statusWord;
        }

        public short getStatusWord() {
            return status[0];
        }
    }
}
