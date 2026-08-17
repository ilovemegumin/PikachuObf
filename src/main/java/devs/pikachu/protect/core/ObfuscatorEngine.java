package devs.pikachu.protect.core;

import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.transformer.ClassTransformer;
import devs.pikachu.protect.transformer.impl.junk.JunkCodeTransformer;
import devs.pikachu.protect.transformer.impl.misc.SyntheticTransformer;
import devs.pikachu.protect.transformer.impl.number.NumberTransformer;
import devs.pikachu.protect.transformer.impl.ref.InvokeDynamicTransformer;
import devs.pikachu.protect.transformer.impl.renamer.ClassRenamePass;
import devs.pikachu.protect.transformer.impl.renamer.LocalVariableTransformer;
import devs.pikachu.protect.transformer.impl.string.ReverseTransformer;
import devs.pikachu.protect.transformer.impl.string.StringConcatLoweringTransformer;
import devs.pikachu.protect.transformer.impl.string.StringEncryptionTransformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ObfuscatorEngine {
    private final ObfuscationConfig config;
    private final List<String> warnings = new ArrayList<>();

    public ObfuscatorEngine(ObfuscationConfig config) {
        this.config = config;
    }

    public Result run() throws Exception {
        JarArchive archive = JarArchive.read(config.input, config);
        new ClassRenamePass(config).apply(archive);
        ClassHierarchy hierarchy = ClassHierarchy.create(archive, config.libraries);
        List<ClassTransformer> pipeline = createPipeline();
        Set<JarClass> transformed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (JarClass jarClass : archive.classes()) {
            if (jarClass.selected() && jarClass.dirty()) {
                transformed.add(jarClass);
            }
        }
        if (!pipeline.isEmpty() || config.fixVersion) {
            for (JarClass jarClass : archive.classes()) {
                if (!jarClass.selected()) {
                    continue;
                }
                ClassNode baseline = cloneNode(jarClass.node());
                TransformContext context = new TransformContext(config, jarClass.originalInternalName());
                try {
                    if (config.fixVersion && jarClass.node().version < Opcodes.V1_8) {
                        jarClass.node().version = Opcodes.V1_8;
                    }
                    for (ClassTransformer transformer : pipeline) {
                        transformer.transform(jarClass.node(), context);
                    }
                    byte[] testBytes = writeClass(jarClass.node(), hierarchy);
                    if (!config.dontVerify) {
                        Verifier.verify(testBytes);
                    }
                    jarClass.markDirty();
                    transformed.add(jarClass);
                } catch (Throwable e) {
                    jarClass.node(baseline);
                    warnings.add(jarClass.originalInternalName() + " は変換をロールバックしました: " + rootMessage(e));
                }
            }
        }
        writeArchive(archive, hierarchy);
        return new Result(archive.classes().size(), transformed.size(), List.copyOf(warnings));
    }

    private List<ClassTransformer> createPipeline() {
        List<ClassTransformer> pipeline = new ArrayList<>();
        if (config.obfLocalVar || config.delLocalVar) {
            pipeline.add(new LocalVariableTransformer());
        }
        if (config.useStringObf) {
            pipeline.add(new StringConcatLoweringTransformer());
        }
        if (config.useReverse) {
            pipeline.add(new ReverseTransformer());
        }
        if (config.useStringObf) {
            pipeline.add(new StringEncryptionTransformer());
        }
        if (config.useNumberObf) {
            pipeline.add(new NumberTransformer());
        }
        if (config.useJunkCode || config.useMoreJunkCode || config.useMoreJunkCode2 || config.useSuperJunkCode) {
            pipeline.add(new JunkCodeTransformer());
        }
        if (config.addSyntheticFlag) {
            pipeline.add(new SyntheticTransformer());
        }
        if (config.useInvokeDynamicObf) {
            pipeline.add(new InvokeDynamicTransformer());
        }
        return pipeline;
    }

    private void writeArchive(JarArchive archive, ClassHierarchy hierarchy) throws Exception {
        Path parent = config.output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent == null ? Path.of(".") : parent, "pikachuobf-", ".jar.tmp");
        boolean changed = config.classToFolder || archive.classes().stream().anyMatch(JarClass::dirty);
        Set<String> written = new HashSet<>();
        try (OutputStream file = Files.newOutputStream(temp); ZipOutputStream zip = new ZipOutputStream(file)) {
            for (JarArchive.Item item : archive.items()) {
                if (item instanceof JarClass jarClass) {
                    String name = jarClass.outputEntryName(config.classToFolder);
                    requireUnique(written, name);
                    zip.putNextEntry(new ZipEntry(name));
                    byte[] bytes = jarClass.dirty() ? writeClass(jarClass.node(), hierarchy) : jarClass.originalBytes();
                    if (jarClass.dirty() && !config.dontVerify) {
                        Verifier.verify(bytes);
                    }
                    zip.write(bytes);
                    zip.closeEntry();
                    continue;
                }
                JarArchive.ResourceEntry resource = (JarArchive.ResourceEntry) item;
                if (changed && isSignature(resource.name())) {
                    continue;
                }
                requireUnique(written, resource.name());
                ZipEntry entry = new ZipEntry(resource.name());
                zip.putNextEntry(entry);
                if (!resource.directory()) {
                    zip.write(resource.data());
                }
                zip.closeEntry();
            }
        } catch (Throwable e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        try {
            Files.move(temp, config.output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, config.output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireUnique(Set<String> written, String name) throws IOException {
        if (!written.add(name)) {
            throw new IOException("duplicate output entry: " + name);
        }
    }

    private static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return false;
        }
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
    }

    private static byte[] writeClass(ClassNode node, ClassHierarchy hierarchy) {
        SafeClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode cloneNode(ClassNode node) {
        ClassNode clone = new ClassNode(Opcodes.ASM9);
        node.accept(clone);
        return clone;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record Result(int totalClasses, int transformedClasses, List<String> warnings) {
    }
}
