package devs.pikachu.protect.utility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

public final class MapReader {
    private MapReader() {
    }

    public static String[] read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String line : lines) {
            String value = line.strip();
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            names.add(value);
        }
        return names.toArray(String[]::new);
    }
}
