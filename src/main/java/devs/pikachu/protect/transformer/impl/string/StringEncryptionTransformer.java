package devs.pikachu.protect.transformer.impl.string;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import devs.pikachu.protect.utility.StringCodec;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

public final class StringEncryptionTransformer implements ClassTransformer {
    @Override
    public String name() {
        return "StringEncryptionTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        if ((node.access & Opcodes.ACC_ANNOTATION) != 0) {
            return;
        }
        if ((node.access & Opcodes.ACC_INTERFACE) != 0 && node.version < Opcodes.V1_8) {
            return;
        }
        node.version = Math.max(node.version, Opcodes.V1_8);
        List<MethodNode> originalMethods = new ArrayList<>(node.methods);
        boolean hasString = hasStringConstants(node, originalMethods);
        if (!hasString) {
            return;
        }
        String decoder = context.ensureDecoder(node);
        int rounds = context.config().useStringObfT ? 2 : 1;
        for (MethodNode method : originalMethods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value) {
                    method.instructions.insertBefore(insn, encryptedLoad(node.name, (node.access & Opcodes.ACC_INTERFACE) != 0, decoder, value, rounds, context));
                    method.instructions.remove(insn);
                }
                insn = next;
            }
        }
        List<FieldNode> fields = new ArrayList<>();
        for (FieldNode field : node.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 && field.value instanceof String) {
                fields.add(field);
            }
        }
        if (!fields.isEmpty()) {
            MethodNode clinit = findClinit(node);
            if (clinit == null) {
                clinit = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                clinit.instructions.add(new InsnNode(Opcodes.RETURN));
                node.methods.add(clinit);
            }
            InsnList init = new InsnList();
            for (FieldNode field : fields) {
                String value = (String) field.value;
                field.value = null;
                init.add(encryptedLoad(node.name, (node.access & Opcodes.ACC_INTERFACE) != 0, decoder, value, rounds, context));
                init.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, field.name, field.desc));
            }
            clinit.instructions.insert(init);
        }
    }

    private static boolean hasStringConstants(ClassNode node, List<MethodNode> methods) {
        for (FieldNode field : node.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 && field.value instanceof String) {
                return true;
            }
        }
        for (MethodNode method : methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MethodNode findClinit(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.name.equals("<clinit>") && method.desc.equals("()V")) {
                return method;
            }
        }
        return null;
    }

    public static InsnList encryptedLoad(String owner, boolean ownerInterface, String decoder, String value, int rounds, TransformContext context) {
        int actualRounds = Math.max(1, rounds);
        int[] keys = new int[actualRounds];
        String encoded = value;
        for (int i = 0; i < actualRounds; i++) {
            int key = context.random().nextInt();
            keys[i] = key;
            encoded = StringCodec.encode(encoded, key, context.codecSalt());
        }
        InsnList list = literal(encoded);
        for (int i = actualRounds - 1; i >= 0; i--) {
            list.add(new LdcInsnNode(keys[i]));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, decoder, "(Ljava/lang/String;I)Ljava/lang/String;", ownerInterface));
        }
        return list;
    }

    private static InsnList literal(String value) {
        InsnList list = new InsnList();
        if (value.length() <= 20000) {
            list.add(new LdcInsnNode(value));
            return list;
        }
        list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
        for (int start = 0; start < value.length(); start += 20000) {
            int end = Math.min(value.length(), start + 20000);
            list.add(new LdcInsnNode(value.substring(start, end)));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        }
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
        return list;
    }
}
