package devs.pikachu.protect.transformer;

import devs.pikachu.protect.core.TransformContext;
import org.objectweb.asm.tree.ClassNode;

public interface ClassTransformer {
    String name();
    void transform(ClassNode node, TransformContext context);
}
