package devs.pikachu.protect.core;

import devs.pikachu.protect.config.ObfuscationConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JarArchive {
    private final List<Item> items = new ArrayList<>();
    private final List<JarClass> classes = new ArrayList<>();

    public static JarArchive read(Path path, ObfuscationConfig config) throws IOException {
        JarArchive archive = new JarArchive();
        try (InputStream input = Files.newInputStream(path); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] data = readAll(zip);
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try {
                        ClassReader reader = new ClassReader(data);
                        ClassNode node = new ClassNode(Opcodes.ASM9);
                        reader.accept(node, 0);
                        JarClass jarClass = new JarClass(entry.getName(), data, node, config.shouldTransform(node.name));
                        archive.items.add(jarClass);
                        archive.classes.add(jarClass);
                        continue;
                    } catch (RuntimeException ignored) {
                    }
                }
                archive.items.add(new ResourceEntry(entry.getName(), data, entry.isDirectory()));
            }
        }
        return archive;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public List<JarClass> classes() {
        return Collections.unmodifiableList(classes);
    }

    public interface Item {
        String originalEntryName();
    }

    public static final class ResourceEntry implements Item {
        private final String originalEntryName;
        private String name;
        private byte[] data;
        private final boolean directory;

        public ResourceEntry(String name, byte[] data, boolean directory) {
            this.originalEntryName = name;
            this.name = name;
            this.data = data;
            this.directory = directory;
        }

        @Override
        public String originalEntryName() {
            return originalEntryName;
        }

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }

        public byte[] data() {
            return data;
        }

        public void data(byte[] data) {
            this.data = data;
        }

        public boolean directory() {
            return directory;
        }
    }
}
