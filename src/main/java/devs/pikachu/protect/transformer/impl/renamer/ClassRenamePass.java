package devs.pikachu.protect.transformer.impl.renamer;

import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.core.JarArchive;
import devs.pikachu.protect.core.JarClass;
import devs.pikachu.protect.utility.NameGenerator;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class ClassRenamePass {
    private final ObfuscationConfig config;
    private final NameGenerator names;

    public ClassRenamePass(ObfuscationConfig config) {
        this.config = config;
        this.names = new NameGenerator(new Random(config.seed ^ 0x51A7C0DEL));
    }

    public Map<String, String> apply(JarArchive archive) {
        if (!config.classRandomName && !config.packageRemover) {
            return Map.of();
        }
        Map<String, String> classMap = new LinkedHashMap<>();
        Map<String, String> packageMap = new HashMap<>();
        Map<String, Boolean> packageEligibility = new HashMap<>();
        Set<String> occupied = new HashSet<>();
        for (JarClass jarClass : archive.classes()) {
            occupied.add(jarClass.node().name);
            String packageName = packageName(jarClass.node().name);
            packageEligibility.merge(packageName, jarClass.selected(), (left, right) -> left && right);
        }
        String collapsedPackage = config.packageRemover ? "p" + names.next(9) : null;
        for (JarClass jarClass : archive.classes()) {
            if (!jarClass.selected()) {
                continue;
            }
            String original = jarClass.node().name;
            if (original.equals("module-info")) {
                continue;
            }
            String packageName = packageName(original);
            String simpleName = simpleName(original);
            boolean canCollapsePackage = config.packageRemover && packageEligibility.getOrDefault(packageName, false);
            String targetPackage = canCollapsePackage ? collapsedPackage : packageName;
            if (canCollapsePackage) {
                packageMap.put(packageName, targetPackage);
            }
            String targetSimple;
            if (simpleName.equals("package-info")) {
                targetSimple = simpleName;
            } else if (config.classRandomName) {
                targetSimple = names.next(12);
            } else {
                targetSimple = simpleName;
            }
            String target = join(targetPackage, targetSimple);
            while (!target.equals(original) && occupied.contains(target)) {
                targetSimple = config.classRandomName ? names.next(12) : targetSimple + "$" + names.next(5);
                target = join(targetPackage, targetSimple);
            }
            if (!target.equals(original)) {
                classMap.put(original, target);
                occupied.add(target);
            }
        }
        if (classMap.isEmpty() && packageMap.isEmpty()) {
            return classMap;
        }
        MappingRemapper remapper = new MappingRemapper(classMap, packageMap);
        for (JarClass jarClass : archive.classes()) {
            ClassNode output = new ClassNode(Opcodes.ASM9);
            jarClass.node().accept(new ClassRemapper(output, remapper));
            remapClassNameStrings(output, classMap);
            jarClass.node(output);
            jarClass.markDirty();
        }
        remapResources(archive, classMap);
        return classMap;
    }

    private static void remapClassNameStrings(ClassNode node, Map<String, String> classMap) {
        if (classMap.isEmpty()) {
            return;
        }
        Map<String, String> dotted = new HashMap<>();
        for (Map.Entry<String, String> entry : classMap.entrySet()) {
            dotted.put(entry.getKey().replace('/', '.'), entry.getValue().replace('/', '.'));
        }
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String value)) {
                    continue;
                }
                String mapped = classMap.get(value);
                if (mapped == null) {
                    mapped = dotted.get(value);
                }
                if (mapped != null) {
                    ldc.cst = mapped;
                }
            }
        }
    }

    private static void remapResources(JarArchive archive, Map<String, String> classMap) {
        if (classMap.isEmpty()) {
            return;
        }
        Map<String, String> dotted = new HashMap<>();
        for (Map.Entry<String, String> entry : classMap.entrySet()) {
            dotted.put(entry.getKey().replace('/', '.'), entry.getValue().replace('/', '.'));
        }
        for (JarArchive.Item item : archive.items()) {
            if (!(item instanceof JarArchive.ResourceEntry resource) || resource.directory()) {
                continue;
            }
            if (resource.name().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                remapManifest(resource, dotted);
                continue;
            }
            if (resource.name().startsWith("META-INF/services/")) {
                String service = resource.name().substring("META-INF/services/".length());
                String mappedService = dotted.get(service);
                if (mappedService != null) {
                    resource.name("META-INF/services/" + mappedService);
                }
                resource.data(remapServiceBody(resource.data(), dotted));
            }
        }
    }

    private static void remapManifest(JarArchive.ResourceEntry resource, Map<String, String> dotted) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(resource.data()));
            Attributes attributes = manifest.getMainAttributes();
            for (String key : List.of("Main-Class", "Premain-Class", "Agent-Class", "Launcher-Agent-Class")) {
                String value = attributes.getValue(key);
                if (value != null && dotted.containsKey(value)) {
                    attributes.putValue(key, dotted.get(value));
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            manifest.write(output);
            resource.data(output.toByteArray());
        } catch (Exception ignored) {
        }
    }

    private static byte[] remapServiceBody(byte[] data, Map<String, String> dotted) {
        String text = new String(data, StandardCharsets.UTF_8);
        String[] lines = text.split("\\R", -1);
        List<String> output = new ArrayList<>(lines.length);
        for (String line : lines) {
            String stripped = line.strip();
            if (!stripped.isEmpty() && !stripped.startsWith("#") && dotted.containsKey(stripped)) {
                int start = line.indexOf(stripped);
                line = line.substring(0, start) + dotted.get(stripped) + line.substring(start + stripped.length());
            }
            output.add(line);
        }
        return String.join(System.lineSeparator(), output).getBytes(StandardCharsets.UTF_8);
    }

    private static String packageName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }

    private static String join(String packageName, String simpleName) {
        return packageName == null || packageName.isEmpty() ? simpleName : packageName + "/" + simpleName;
    }

    private static final class MappingRemapper extends Remapper {
        private final Map<String, String> classMap;
        private final Map<String, String> packageMap;

        private MappingRemapper(Map<String, String> classMap, Map<String, String> packageMap) {
            super(Opcodes.ASM9);
            this.classMap = classMap;
            this.packageMap = packageMap;
        }

        @Override
        public String map(String internalName) {
            return classMap.getOrDefault(internalName, internalName);
        }

        @Override
        public String mapPackageName(String name) {
            return packageMap.getOrDefault(name, name);
        }
    }
}
