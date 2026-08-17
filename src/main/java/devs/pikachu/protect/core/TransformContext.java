package devs.pikachu.protect.core;

import devs.pikachu.protect.config.ObfuscationConfig;
import devs.pikachu.protect.utility.NameGenerator;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class TransformContext {
    private final ObfuscationConfig config;
    private final Random random;
    private final NameGenerator names;
    private final int codecSalt;
    private final Set<String> generatedMethods = new HashSet<>();
    private String decoderName;

    public TransformContext(ObfuscationConfig config, String className) {
        this.config = config;
        this.random = new Random(config.seed ^ ((long) className.hashCode() << 32) ^ className.length());
        this.codecSalt = random.nextInt() | 1;
        this.names = new NameGenerator(random);
    }

    public ObfuscationConfig config() {
        return config;
    }

    public Random random() {
        return random;
    }

    public NameGenerator names() {
        return names;
    }

    public int codecSalt() {
        return codecSalt;
    }

    public boolean isGeneratedMethod(String name, String descriptor) {
        return generatedMethods.contains(name + descriptor);
    }

    public void markGenerated(MethodNode method) {
        generatedMethods.add(method.name + method.desc);
    }

    public String ensureDecoder(ClassNode node) {
        if (decoderName != null) {
            return decoderName;
        }
        decoderName = uniqueMethodName(node, names.next(14));
        MethodNode method = DecoderFactory.create(node, decoderName, codecSalt);
        node.methods.add(method);
        markGenerated(method);
        return decoderName;
    }

    private String uniqueMethodName(ClassNode node, String candidate) {
        String value = candidate;
        boolean conflict;
        do {
            conflict = false;
            for (MethodNode method : node.methods) {
                if (method.name.equals(value)) {
                    conflict = true;
                    value = names.next(14);
                    break;
                }
            }
        } while (conflict);
        return value;
    }

    private static final class DecoderFactory {
        private static MethodNode create(ClassNode owner, String name, int codecSalt) {
            int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
            if ((owner.access & Opcodes.ACC_INTERFACE) != 0 && owner.version < Opcodes.V9) {
                access |= Opcodes.ACC_PUBLIC;
            } else {
                access |= Opcodes.ACC_PRIVATE;
            }
            MethodNode m = new MethodNode(Opcodes.ASM9, access, name, "(Ljava/lang/String;I)Ljava/lang/String;", null, null);
            var i = m.instructions;
            var loop = new org.objectweb.asm.tree.LabelNode();
            var end = new org.objectweb.asm.tree.LabelNode();
            i.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder", "()Ljava/util/Base64$Decoder;", false));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
            i.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B", false));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ASTORE, 2));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARRAYLENGTH));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_2));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IDIV));
            i.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ASTORE, 3));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ISTORE, 4));
            i.add(loop);
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 4));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 3));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARRAYLENGTH));
            i.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ICMPGE, end));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 4));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_2));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IMUL));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.BALOAD));
            i.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, 255));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IAND));
            i.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 8));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ISHL));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 4));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_2));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IMUL));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IADD));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.BALOAD));
            i.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, 255));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IAND));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IOR));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ISTORE, 5));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 1));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 4));
            i.add(new org.objectweb.asm.tree.LdcInsnNode(codecSalt));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IMUL));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IADD));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ISTORE, 6));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 6));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 6));
            i.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 16));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IUSHR));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IXOR));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ISTORE, 6));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 3));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 4));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 5));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 6));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IXOR));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.I2C));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.CASTORE));
            i.add(new org.objectweb.asm.tree.IincInsnNode(4, 1));
            i.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.GOTO, loop));
            i.add(end);
            i.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/lang/String"));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP));
            i.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 3));
            i.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false));
            i.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
            return m;
        }
    }
}
