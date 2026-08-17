package devs.pikachu.protect.utility;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class NameGenerator {
    private static final char[] FIRST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] REST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final Random random;
    private final Set<String> used = new HashSet<>();

    public NameGenerator(Random random) {
        this.random = random;
    }

    public String next(int length) {
        int actualLength = Math.max(2, length);
        String value;
        do {
            StringBuilder builder = new StringBuilder(actualLength);
            builder.append(FIRST[random.nextInt(FIRST.length)]);
            for (int i = 1; i < actualLength; i++) {
                builder.append(REST[random.nextInt(REST.length)]);
            }
            value = builder.toString();
        } while (!used.add(value));
        return value;
    }
}
