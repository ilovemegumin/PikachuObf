package devs.pikachu.protect.transformer.impl.number;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class NumberTransformer implements ClassTransformer {
    @Override
    public String name() {
        return "NumberTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        for (MethodNode method : node.methods) {
            if (context.isGeneratedMethod(method.name, method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                Integer intValue = integerValue(insn);
                if (intValue != null) {
                    method.instructions.insertBefore(insn, encodeInt(intValue, context));
                    method.instructions.remove(insn);
                } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Long value) {
                    method.instructions.insertBefore(insn, encodeLong(value, context));
                    method.instructions.remove(insn);
                } else if (insn.getOpcode() == Opcodes.LCONST_0 || insn.getOpcode() == Opcodes.LCONST_1) {
                    long value = insn.getOpcode() - Opcodes.LCONST_0;
                    method.instructions.insertBefore(insn, encodeLong(value, context));
                    method.instructions.remove(insn);
                }
                insn = next;
            }
        }
    }

    private static InsnList encodeInt(int value, TransformContext context) {
        InsnList list = new InsnList();
        if (context.config().bigBrainNumberObf) {
            int k1 = context.random().nextInt();
            int k2 = context.random().nextInt();
            list.add(new LdcInsnNode(value ^ k1 ^ k2));
            list.add(new LdcInsnNode(k1));
            list.add(new InsnNode(Opcodes.IXOR));
            list.add(new LdcInsnNode(k2));
            list.add(new InsnNode(Opcodes.IXOR));
        } else {
            int key = context.random().nextInt();
            list.add(new LdcInsnNode(value ^ key));
            list.add(new LdcInsnNode(key));
            list.add(new InsnNode(Opcodes.IXOR));
        }
        return list;
    }

    private static InsnList encodeLong(long value, TransformContext context) {
        InsnList list = new InsnList();
        if (context.config().bigBrainNumberObf) {
            long k1 = context.random().nextLong();
            long k2 = context.random().nextLong();
            list.add(new LdcInsnNode(value ^ k1 ^ k2));
            list.add(new LdcInsnNode(k1));
            list.add(new InsnNode(Opcodes.LXOR));
            list.add(new LdcInsnNode(k2));
            list.add(new InsnNode(Opcodes.LXOR));
        } else {
            long key = context.random().nextLong();
            list.add(new LdcInsnNode(value ^ key));
            list.add(new LdcInsnNode(key));
            list.add(new InsnNode(Opcodes.LXOR));
        }
        return list;
    }

    private static Integer integerValue(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            return opcode - Opcodes.ICONST_0;
        }
        if (insn instanceof IntInsnNode intInsn && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
            return intInsn.operand;
        }
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) {
            return value;
        }
        return null;
    }
}
