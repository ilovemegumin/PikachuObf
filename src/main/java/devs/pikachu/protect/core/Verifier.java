package devs.pikachu.protect.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public final class Verifier {
    private Verifier() {
    }

    public static void verify(byte[] bytes) throws Exception {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                continue;
            }
            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicVerifier());
            analyzer.analyze(node.name, method);
        }
    }
}
