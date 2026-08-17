package devs.pikachu.protect.transformer.impl.junk;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class JunkCodeTransformer implements ClassTransformer {
    @Override
    public String name() {
        return "JunkCodeTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        int level = level(context);
        if (level == 0) {
            return;
        }
        int divisor = Math.max(2, 9 - level * 2);
        for (MethodNode method : node.methods) {
            if (context.isGeneratedMethod(method.name, method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() < 0 || context.random().nextInt(divisor) != 0) {
                    continue;
                }
                int count = 1 + context.random().nextInt(level + 1);
                for (int i = 0; i < count; i++) {
                    method.instructions.insertBefore(insn, new InsnNode(Opcodes.NOP));
                }
            }
        }
    }

    private static int level(TransformContext context) {
        if (context.config().useSuperJunkCode) {
            return 4;
        }
        if (context.config().useMoreJunkCode2) {
            return 3;
        }
        if (context.config().useMoreJunkCode) {
            return 2;
        }
        return context.config().useJunkCode ? 1 : 0;
    }
}
