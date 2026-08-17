package devs.pikachu.protect.transformer.impl.renamer;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Set;

public final class LocalVariableTransformer implements ClassTransformer {
    @Override
    public String name() {
        return "LocalVariableTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        for (MethodNode method : node.methods) {
            if (context.config().delLocalVar) {
                method.localVariables = null;
                method.parameters = null;
                continue;
            }
            if (!context.config().obfLocalVar || method.localVariables == null) {
                continue;
            }
            Set<String> used = new HashSet<>();
            for (LocalVariableNode local : method.localVariables) {
                String name;
                do {
                    name = nextName(context);
                } while (!used.add(name));
                local.name = name;
            }
            if (method.parameters != null) {
                for (var parameter : method.parameters) {
                    parameter.name = nextName(context);
                }
            }
        }
    }

    private String nextName(TransformContext context) {
        String[] map = context.config().remapStrings;
        if (map.length != 0) {
            return map[context.random().nextInt(map.length)];
        }
        return context.names().next(8);
    }
}
