package devs.pikachu.protect.utility;

public final class HashPreimage {
    private HashPreimage() {
    }

    public static String forInt(int value) {
        long remaining = Integer.toUnsignedLong(value);
        if (remaining == 0L) {
            return "\u0000";
        }
        StringBuilder builder = new StringBuilder();
        while (remaining != 0L) {
            builder.append((char) (remaining % 31L));
            remaining /= 31L;
        }
        String result = builder.reverse().toString();
        if (result.hashCode() != value) {
            throw new IllegalStateException("hash preimage generation failed");
        }
        return result;
    }
}
