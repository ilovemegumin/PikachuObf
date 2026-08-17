package devs.pikachu.protect;

import devs.pikachu.protect.cli.CommandLineParser;
import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.core.ObfuscatorEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class IntegrationTest {
    @TempDir
    Path temp;

    @Test
    void fullPipelineKeepsProgramWorkingAndRemovesPlainStrings() throws Exception {
        Path input = createInputJar();
        Path output = temp.resolve("output.jar");
        ObfuscationConfig config = CommandLineParser.parse(new String[]{
                input.toString(), output.toString(), "-full", "-reverse", "-seed", "123456"
        });
        new ObfuscatorEngine(config).run();

        try (JarFile jar = new JarFile(output.toFile())) {
            String mainClass = jar.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            assertNotNull(mainClass);
            assertFalse(mainClass.equals("sample.App"));
            for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                byte[] bytes = jar.getInputStream(entry).readAllBytes();
                String raw = new String(bytes, StandardCharsets.ISO_8859_1);
                assertFalse(raw.contains("https://auth.example/api"));
            }
            try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class<?> main = Class.forName(mainClass, true, loader);
                Method method = main.getMethod("main", String[].class);
                PrintStream old = System.out;
                ByteArrayOutputStream capture = new ByteArrayOutputStream();
                try {
                    System.setOut(new PrintStream(capture, true, StandardCharsets.UTF_8));
                    method.invoke(null, (Object) new String[0]);
                } finally {
                    System.setOut(old);
                }
                assertEquals("https://auth.example/api:2:HELLO:9", capture.toString(StandardCharsets.UTF_8));
            }
        }
    }

    private Path createInputJar() throws Exception {
        Path src = temp.resolve("src/sample");
        Path classes = temp.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(classes);
        Files.writeString(src.resolve("Helper.java"), """
                package sample;
                public final class Helper {
                    public static int max(int a, int b) { return Math.max(a, b); }
                }
                """);
        Files.writeString(src.resolve("App.java"), """
                package sample;
                import java.util.List;
                public final class App {
                    public static final String URL = "https://auth.example/api";
                    public static int color() { return -1; }
                    public static String run(List<String> values) {
                        String text = "hello";
                        return URL + ":" + values.size() + ":" + text.toUpperCase() + ":" + Helper.max(4, 9);
                    }
                    public static void main(String[] args) {
                        if (color() != -1) throw new IllegalStateException("bad color");
                        String actual = run(List.of("a", "b"));
                        String expected = "https://auth.example/api:2:HELLO:9";
                        if (!actual.equals(expected)) throw new IllegalStateException(actual);
                        System.out.print(actual);
                    }
                }
                """);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        int result = compiler.run(null, null, null, "-d", classes.toString(), src.resolve("Helper.java").toString(), src.resolve("App.java").toString());
        assertEquals(0, result);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "sample.App");
        Path jarPath = temp.resolve("input.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            List<Path> classFiles = new ArrayList<>();
            try (var stream = Files.walk(classes)) {
                stream.filter(Files::isRegularFile).forEach(classFiles::add);
            }
            for (Path classFile : classFiles) {
                String name = classes.relativize(classFile).toString().replace('\\', '/');
                jar.putNextEntry(new JarEntry(name));
                jar.write(Files.readAllBytes(classFile));
                jar.closeEntry();
            }
        }
        return jarPath;
    }
}
