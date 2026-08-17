package devs.pikachu.protect;

import devs.pikachu.protect.utility.HashPreimage;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HashPreimageTest {
    @Test
    void preservesSignedIntHashCodes() {
        int[] fixed = {-1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0xFF00FF00, 0x80000001};
        for (int value : fixed) {
            assertEquals(value, HashPreimage.forInt(value).hashCode());
        }
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            int value = random.nextInt();
            assertEquals(value, HashPreimage.forInt(value).hashCode());
        }
    }
}
