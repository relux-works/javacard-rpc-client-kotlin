package javacard.framework;
import java.util.IdentityHashMap;
public final class JCSystem {
    public static final byte CLEAR_ON_RESET = 0;
    public static final byte CLEAR_ON_DESELECT = 1;
    private static final IdentityHashMap<Object, Boolean> TRANSIENT_ARRAYS =
            new IdentityHashMap<Object, Boolean>();
    private JCSystem() { }
    private static <T> T registerTransient(T array) {
        TRANSIENT_ARRAYS.put(array, Boolean.TRUE);
        return array;
    }
    public static boolean isTransientForTest(Object array) {
        return TRANSIENT_ARRAYS.containsKey(array);
    }
    public static byte[] makeTransientByteArray(short length, byte event) {
        return registerTransient(new byte[length]);
    }
    public static short[] makeTransientShortArray(short length, byte event) {
        return registerTransient(new short[length]);
    }
    public static Object[] makeTransientObjectArray(short length, byte event) {
        return registerTransient(new Object[length]);
    }
}
