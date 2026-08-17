package devs.pikachu.protect.core;

import org.objectweb.asm.ClassWriter;

public final class SafeClassWriter extends ClassWriter {
    private final ClassHierarchy hierarchy;

    public SafeClassWriter(int flags, ClassHierarchy hierarchy) {
        super(flags);
        this.hierarchy = hierarchy;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        return hierarchy.commonSuperClass(type1, type2);
    }
}
