package devs.pikachu.protect.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ClassHierarchy {
    private final Map<String, Info> classes = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    public static ClassHierarchy create(JarArchive archive, Iterable<Path> libraries) throws IOException {
        ClassHierarchy hierarchy = new ClassHierarchy();
        hierarchy.indexArchive(archive);
        for (Path path : libraries) {
            hierarchy.indexJar(path);
        }
        return hierarchy;
    }

    public void indexArchive(JarArchive archive) {
        for (JarClass jarClass : archive.classes()) {
            index(jarClass.node());
        }
    }

    public void rebuildArchive(JarArchive archive) {
        for (JarClass jarClass : archive.classes()) {
            classes.remove(jarClass.originalInternalName());
        }
        indexArchive(archive);
    }

    private void index(ClassNode node) {
        classes.put(node.name, new Info(node.name, node.superName, node.interfaces.toArray(String[]::new), node.access));
    }

    private void indexJar(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("library not found: " + path);
        }
        try (InputStream input = Files.newInputStream(path); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try {
                    ClassReader reader = new ClassReader(zip);
                    classes.put(reader.getClassName(), new Info(reader.getClassName(), reader.getSuperName(), reader.getInterfaces(), reader.getAccess()));
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    public String commonSuperClass(String left, String right) {
        if (left.equals(right)) {
            return left;
        }
        if (left.startsWith("[") || right.startsWith("[")) {
            return commonArray(left, right);
        }
        if (isAssignableFrom(left, right)) {
            return left;
        }
        if (isAssignableFrom(right, left)) {
            return right;
        }
        Info leftInfo = resolve(left);
        Info rightInfo = resolve(right);
        if (leftInfo == null || rightInfo == null || leftInfo.isInterface() || rightInfo.isInterface()) {
            return "java/lang/Object";
        }
        String current = leftInfo.superName;
        while (current != null) {
            if (isAssignableFrom(current, right)) {
                return current;
            }
            Info info = resolve(current);
            current = info == null ? null : info.superName;
        }
        return "java/lang/Object";
    }

    private String commonArray(String left, String right) {
        if (!left.startsWith("[") || !right.startsWith("[")) {
            return "java/lang/Object";
        }
        Type a = Type.getType(left);
        Type b = Type.getType(right);
        int da = a.getDimensions();
        int db = b.getDimensions();
        Type ea = a.getElementType();
        Type eb = b.getElementType();
        if (da == db) {
            if (ea.getSort() == Type.OBJECT && eb.getSort() == Type.OBJECT) {
                return arrayDescriptor(da, "L" + commonSuperClass(ea.getInternalName(), eb.getInternalName()) + ";");
            }
            if (ea.equals(eb)) {
                return left;
            }
            if (da == 1) {
                return "java/lang/Object";
            }
            return arrayDescriptor(da - 1, "Ljava/lang/Object;");
        }
        int min = Math.min(da, db);
        Type smallerElement = da < db ? ea : eb;
        if (smallerElement.getSort() == Type.OBJECT) {
            return arrayDescriptor(min, "Ljava/lang/Object;");
        }
        if (min == 1) {
            return "java/lang/Object";
        }
        return arrayDescriptor(min - 1, "Ljava/lang/Object;");
    }

    private static String arrayDescriptor(int dimensions, String elementDescriptor) {
        return "[".repeat(Math.max(0, dimensions)) + elementDescriptor;
    }

    public boolean isAssignableFrom(String target, String source) {
        if (target.equals(source) || target.equals("java/lang/Object")) {
            return true;
        }
        if (target.startsWith("[") || source.startsWith("[")) {
            return target.equals(source) || target.equals("java/lang/Object");
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (target.equals(current)) {
                return true;
            }
            Info info = resolve(current);
            if (info == null) {
                continue;
            }
            if (info.superName != null) {
                queue.addLast(info.superName);
            }
            queue.addAll(Arrays.asList(info.interfaces));
        }
        return false;
    }

    private Info resolve(String name) {
        Info info = classes.get(name);
        if (info != null || missing.contains(name)) {
            return info;
        }
        try (InputStream input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (input == null) {
                missing.add(name);
                return null;
            }
            ClassReader reader = new ClassReader(input);
            info = new Info(reader.getClassName(), reader.getSuperName(), reader.getInterfaces(), reader.getAccess());
            classes.put(name, info);
            return info;
        } catch (IOException | RuntimeException e) {
            missing.add(name);
            return null;
        }
    }

    private record Info(String name, String superName, String[] interfaces, int access) {
        boolean isInterface() {
            return (access & Opcodes.ACC_INTERFACE) != 0;
        }
    }
}
