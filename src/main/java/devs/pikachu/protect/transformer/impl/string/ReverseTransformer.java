package devs.pikachu.protect.transformer.impl.string;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import devs.pikachu.protect.utility.HashPreimage;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ReverseTransformer implements ClassTransformer {
    @Override
    public String name() {
        return "ReverseTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        for (MethodNode method : node.methods) {
            if (context.isGeneratedMethod(method.name, method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                Integer value = integerValue(insn);
                if (value != null) {
                    InsnList replacement = new InsnList();
                    replacement.add(new LdcInsnNode(HashPreimage.forInt(value)));
                    replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false));
                    method.instructions.insertBefore(insn, replacement);
                    method.instructions.remove(insn);
                }
                insn = next;
            }
        }
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
