package devs.pikachu.protect.transformer.impl.misc;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class SyntheticTransformer implements ClassTransformer {
    @Override
    public String name() {
        return "SyntheticTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        for (FieldNode field : node.fields) {
            field.access |= Opcodes.ACC_SYNTHETIC;
        }
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>") && !method.name.equals("<clinit>")) {
                method.access |= Opcodes.ACC_SYNTHETIC;
            }
        }
    }
}
