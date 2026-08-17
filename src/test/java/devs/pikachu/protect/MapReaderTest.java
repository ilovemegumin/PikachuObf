package devs.pikachu.protect;

import devs.pikachu.protect.utility.MapReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class MapReaderTest {
    @TempDir
    Path temp;

    @Test
    void readsLastLineWithoutTrailingNewline() throws Exception {
        Path map = temp.resolve("names.txt");
        Files.writeString(map, "alpha\n# ignored\nbeta\ngamma", StandardCharsets.UTF_8);
        assertArrayEquals(new String[]{"alpha", "beta", "gamma"}, MapReader.read(map));
    }
}
