package devs.pikachu.protect.utility;

import java.util.Base64;

public final class StringCodec {
    private StringCodec() {
    }

    public static String encode(String value, int key, int salt) {
        byte[] bytes = new byte[value.length() * 2];
        for (int i = 0; i < value.length(); i++) {
            int mix = key + i * salt;
            mix ^= mix >>> 16;
            char encoded = (char) (value.charAt(i) ^ mix);
            bytes[i * 2] = (byte) (encoded >>> 8);
            bytes[i * 2 + 1] = (byte) encoded;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
