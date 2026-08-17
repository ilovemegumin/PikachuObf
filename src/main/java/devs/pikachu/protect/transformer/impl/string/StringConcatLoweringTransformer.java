package devs.pikachu.protect.transformer.impl.string;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

public final class StringConcatLoweringTransformer implements ClassTransformer {
    private static final String OWNER = "java/lang/invoke/StringConcatFactory";

    @Override
    public String name() {
        return "StringConcatLoweringTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        for (MethodNode method : node.methods) {
            if (context.isGeneratedMethod(method.name, method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof InvokeDynamicInsnNode indy) {
                    InsnList replacement = lower(method, indy);
                    if (replacement != null) {
                        method.instructions.insertBefore(indy, replacement);
                        method.instructions.remove(indy);
                    }
                }
                insn = next;
            }
        }
    }

    private static InsnList lower(MethodNode method, InvokeDynamicInsnNode indy) {
        Handle bsm = indy.bsm;
        if (!OWNER.equals(bsm.getOwner())) {
            return null;
        }
        Type methodType = Type.getMethodType(indy.desc);
        if (!methodType.getReturnType().equals(Type.getType(String.class))) {
            return null;
        }
        Type[] arguments = methodType.getArgumentTypes();
        List<Part> parts;
        if (bsm.getName().equals("makeConcat")) {
            parts = new ArrayList<>(arguments.length);
            for (int i = 0; i < arguments.length; i++) {
                parts.add(Part.dynamic(i));
            }
        } else if (bsm.getName().equals("makeConcatWithConstants")) {
            if (indy.bsmArgs.length == 0 || !(indy.bsmArgs[0] instanceof String recipe)) {
                return null;
            }
            parts = parseRecipe(recipe, arguments.length, indy.bsmArgs.length - 1);
            if (parts == null) {
                return null;
            }
        } else {
            return null;
        }

        int[] locals = new int[arguments.length];
        int nextLocal = method.maxLocals;
        for (int i = 0; i < arguments.length; i++) {
            locals[i] = nextLocal;
            nextLocal += arguments[i].getSize();
        }
        method.maxLocals = Math.max(method.maxLocals, nextLocal);

        InsnList list = new InsnList();
        for (int i = arguments.length - 1; i >= 0; i--) {
            list.add(new VarInsnNode(storeOpcode(arguments[i]), locals[i]));
        }
        list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));

        int staticIndex = 1;
        for (Part part : parts) {
            if (part.kind == Kind.LITERAL) {
                if (!part.literal.isEmpty()) {
                    list.add(new LdcInsnNode(part.literal));
                    append(list, Type.getType(String.class));
                }
            } else if (part.kind == Kind.DYNAMIC) {
                Type type = arguments[part.index];
                list.add(new VarInsnNode(loadOpcode(type), locals[part.index]));
                append(list, type);
            } else {
                Object value = indy.bsmArgs[staticIndex++];
                Type type = constantType(value);
                if (type == null) {
                    return null;
                }
                list.add(new LdcInsnNode(value));
                append(list, type);
            }
        }
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
        return list;
    }

    private static List<Part> parseRecipe(String recipe, int dynamicCount, int staticCount) {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int dynamicIndex = 0;
        int staticIndex = 0;
        for (int i = 0; i < recipe.length(); i++) {
            char ch = recipe.charAt(i);
            if (ch != '\u0001' && ch != '\u0002') {
                literal.append(ch);
                continue;
            }
            if (!literal.isEmpty()) {
                parts.add(Part.literal(literal.toString()));
                literal.setLength(0);
            }
            if (ch == '\u0001') {
                if (dynamicIndex >= dynamicCount) {
                    return null;
                }
                parts.add(Part.dynamic(dynamicIndex++));
            } else {
                if (staticIndex >= staticCount) {
                    return null;
                }
                parts.add(Part.constant(staticIndex++));
            }
        }
        if (!literal.isEmpty()) {
            parts.add(Part.literal(literal.toString()));
        }
        if (dynamicIndex != dynamicCount || staticIndex != staticCount) {
            return null;
        }
        return parts;
    }

    private static void append(InsnList list, Type type) {
        String descriptor;
        switch (type.getSort()) {
            case Type.BOOLEAN -> descriptor = "(Z)Ljava/lang/StringBuilder;";
            case Type.CHAR -> descriptor = "(C)Ljava/lang/StringBuilder;";
            case Type.BYTE, Type.SHORT, Type.INT -> descriptor = "(I)Ljava/lang/StringBuilder;";
            case Type.FLOAT -> descriptor = "(F)Ljava/lang/StringBuilder;";
            case Type.LONG -> descriptor = "(J)Ljava/lang/StringBuilder;";
            case Type.DOUBLE -> descriptor = "(D)Ljava/lang/StringBuilder;";
            case Type.OBJECT -> descriptor = type.getInternalName().equals("java/lang/String")
                    ? "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                    : "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            case Type.ARRAY, Type.METHOD -> descriptor = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            default -> throw new IllegalArgumentException("unsupported concat type: " + type);
        }
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", descriptor, false));
    }

    private static Type constantType(Object value) {
        if (value instanceof String) {
            return Type.getType(String.class);
        }
        if (value instanceof Integer) {
            return Type.INT_TYPE;
        }
        if (value instanceof Float) {
            return Type.FLOAT_TYPE;
        }
        if (value instanceof Long) {
            return Type.LONG_TYPE;
        }
        if (value instanceof Double) {
            return Type.DOUBLE_TYPE;
        }
        if (value instanceof Type || value instanceof Handle) {
            return Type.getType(Object.class);
        }
        if (value instanceof ConstantDynamic dynamic) {
            return Type.getType(dynamic.getDescriptor());
        }
        return null;
    }

    private static int storeOpcode(Type type) {
        return switch (type.getSort()) {
            case Type.FLOAT -> Opcodes.FSTORE;
            case Type.LONG -> Opcodes.LSTORE;
            case Type.DOUBLE -> Opcodes.DSTORE;
            case Type.OBJECT, Type.ARRAY, Type.METHOD -> Opcodes.ASTORE;
            default -> Opcodes.ISTORE;
        };
    }

    private static int loadOpcode(Type type) {
        return switch (type.getSort()) {
            case Type.FLOAT -> Opcodes.FLOAD;
            case Type.LONG -> Opcodes.LLOAD;
            case Type.DOUBLE -> Opcodes.DLOAD;
            case Type.OBJECT, Type.ARRAY, Type.METHOD -> Opcodes.ALOAD;
            default -> Opcodes.ILOAD;
        };
    }

    private enum Kind {
        LITERAL,
        DYNAMIC,
        CONSTANT
    }

    private static final class Part {
        private final Kind kind;
        private final String literal;
        private final int index;

        private Part(Kind kind, String literal, int index) {
            this.kind = kind;
            this.literal = literal;
            this.index = index;
        }

        private static Part literal(String value) {
            return new Part(Kind.LITERAL, value, -1);
        }

        private static Part dynamic(int index) {
            return new Part(Kind.DYNAMIC, null, index);
        }

        private static Part constant(int index) {
            return new Part(Kind.CONSTANT, null, index);
        }
    }
}
