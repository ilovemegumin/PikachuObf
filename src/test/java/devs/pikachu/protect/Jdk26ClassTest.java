package devs.pikachu.protect;

import devs.pikachu.protect.cli.CommandLineParser;
import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.core.ObfuscatorEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Jdk26ClassTest {
    @TempDir
    Path temp;

    @Test
    void processesClassFileVersion70() throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V26, Opcodes.ACC_PUBLIC, "v26/Test", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_M1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        Path input = temp.resolve("input.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(input))) {
            jar.putNextEntry(new JarEntry("v26/Test.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
        Path output = temp.resolve("output.jar");
        ObfuscationConfig config = CommandLineParser.parse(new String[]{input.toString(), output.toString(), "-useNumberObf", "-reverse", "-seed", "1"});
        new ObfuscatorEngine(config).run();

        try (JarFile jar = new JarFile(output.toFile()); InputStream inputStream = jar.getInputStream(jar.getJarEntry("v26/Test.class"))) {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(inputStream).accept(node, 0);
            assertEquals(Opcodes.V26, node.version);
        }
    }
}
