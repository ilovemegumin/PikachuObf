package devs.pikachu.protect.core;

import org.objectweb.asm.tree.ClassNode;

public final class JarClass implements JarArchive.Item {
    private final String originalEntryName;
    private final String originalInternalName;
    private final String entryPrefix;
    private final boolean selected;
    private final byte[] originalBytes;
    private ClassNode node;
    private boolean dirty;

    public JarClass(String originalEntryName, byte[] originalBytes, ClassNode node, boolean selected) {
        this.originalEntryName = originalEntryName;
        this.originalInternalName = node.name;
        this.entryPrefix = derivePrefix(originalEntryName, node.name);
        this.node = node;
        this.selected = selected;
        this.originalBytes = originalBytes.clone();
    }

    private static String derivePrefix(String entryName, String internalName) {
        String suffix = internalName + ".class";
        if (entryName.endsWith(suffix)) {
            return entryName.substring(0, entryName.length() - suffix.length());
        }
        int slash = entryName.lastIndexOf('/');
        return slash < 0 ? "" : entryName.substring(0, slash + 1);
    }

    @Override
    public String originalEntryName() {
        return originalEntryName;
    }

    public String originalInternalName() {
        return originalInternalName;
    }

    public ClassNode node() {
        return node;
    }

    public void node(ClassNode node) {
        this.node = node;
    }

    public boolean selected() {
        return selected;
    }

    public byte[] originalBytes() {
        return originalBytes.clone();
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public String outputEntryName(boolean classToFolder) {
        return entryPrefix + node.name + ".class" + (classToFolder ? "/" : "");
    }
}
