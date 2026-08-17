package devs.pikachu.protect.transformer.impl.ref;

import devs.pikachu.protect.core.TransformContext;
import devs.pikachu.protect.transformer.ClassTransformer;
import devs.pikachu.protect.utility.StringCodec;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

public final class InvokeDynamicTransformer implements ClassTransformer {
    private static final String BSM_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;ILjava/lang/Class;Ljava/lang/String;ILjava/lang/String;II)Ljava/lang/invoke/CallSite;";

    @Override
    public String name() {
        return "InvokeDynamicTransformer";
    }

    @Override
    public void transform(ClassNode node, TransformContext context) {
        if ((node.access & Opcodes.ACC_ANNOTATION) != 0) {
            return;
        }
        List<MethodNode> methods = new ArrayList<>(node.methods);
        boolean hasEligible = false;
        for (MethodNode method : methods) {
            if (context.isGeneratedMethod(method.name, method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && eligible(node, context, call)) {
                    hasEligible = true;
                    break;
                }
            }
            if (hasEligible) {
                break;
            }
        }
        if (!hasEligible) {
            return;
        }
        node.version = Math.max(node.version, Opcodes.V1_8);
        boolean ownerInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
        String decoder = context.ensureDecoder(node);
        String bsmName = uniqueMethodName(node, context);
        MethodNode bsm = bootstrap(node, bsmName, decoder, ownerInterface);
        Handle bsmHandle = new Handle(Opcodes.H_INVOKESTATIC, node.name, bsmName, BSM_DESC, ownerInterface);
        int layers = context.config().dontEncode ? 0 : context.config().useInvokeDynamicObfT ? 2 : 1;
        for (MethodNode method : methods) {
            if (context.isGeneratedMethod(method.name, method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof MethodInsnNode call && eligible(node, context, call)) {
                    int nameKey = context.random().nextInt();
                    int descKey = context.random().nextInt();
                    String encodedName = encodeMetadata(call.name, nameKey, layers, context.codecSalt());
                    String encodedDesc = encodeMetadata(call.desc, descKey, layers, context.codecSalt());
                    String indyDesc = invokedDescriptor(call);
                    InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                            context.names().next(9),
                            indyDesc,
                            bsmHandle,
                            call.getOpcode(),
                            Type.getObjectType(call.owner),
                            encodedName,
                            nameKey,
                            encodedDesc,
                            descKey,
                            layers
                    );
                    method.instructions.set(call, indy);
                }
                insn = next;
            }
        }
        node.methods.add(bsm);
        context.markGenerated(bsm);
    }

    private static boolean eligible(ClassNode node, TransformContext context, MethodInsnNode call) {
        int opcode = call.getOpcode();
        if (opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        if (call.name.equals("<init>") || call.name.equals("<clinit>") || call.owner.startsWith("[")) {
            return false;
        }
        if (call.owner.equals("java/lang/invoke/MethodHandle") && (call.name.equals("invoke") || call.name.equals("invokeExact"))) {
            return false;
        }
        if (call.owner.equals("java/lang/invoke/VarHandle")) {
            return false;
        }
        if (call.owner.equals("java/lang/invoke/MethodHandles") && call.name.equals("lookup")) {
            return false;
        }
        return !call.owner.equals(node.name) || !context.isGeneratedMethod(call.name, call.desc);
    }

    private static String invokedDescriptor(MethodInsnNode call) {
        if (call.getOpcode() == Opcodes.INVOKESTATIC) {
            return call.desc;
        }
        Type methodType = Type.getMethodType(call.desc);
        Type[] original = methodType.getArgumentTypes();
        Type[] arguments = new Type[original.length + 1];
        arguments[0] = Type.getObjectType(call.owner);
        System.arraycopy(original, 0, arguments, 1, original.length);
        return Type.getMethodDescriptor(methodType.getReturnType(), arguments);
    }

    private static String encodeMetadata(String value, int key, int layers, int salt) {
        if (layers == 0) {
            return value;
        }
        String encoded = StringCodec.encode(value, key, salt);
        if (layers > 1) {
            encoded = StringCodec.encode(encoded, ~key, salt);
        }
        return encoded;
    }

    private static String uniqueMethodName(ClassNode node, TransformContext context) {
        while (true) {
            String name = context.names().next(15);
            boolean exists = false;
            for (MethodNode method : node.methods) {
                if (method.name.equals(name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                return name;
            }
        }
    }

    private static MethodNode bootstrap(ClassNode owner, String name, String decoder, boolean ownerInterface) {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        if (ownerInterface) {
            access |= Opcodes.ACC_PUBLIC;
        } else {
            access |= Opcodes.ACC_PRIVATE;
        }
        MethodNode m = new MethodNode(Opcodes.ASM9, access, name, BSM_DESC, null, new String[]{"java/lang/Throwable"});
        decodeArgument(m, 5, 6, 10, owner.name, decoder, ownerInterface);
        decodeArgument(m, 7, 8, 11, owner.name, decoder, ownerInterface);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 11));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;", false));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, 12));
        LabelNode virtual = new LabelNode();
        LabelNode resolved = new LabelNode();
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        m.instructions.add(new LdcInsnNode(Opcodes.INVOKESTATIC));
        m.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, virtual));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 10));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 12));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, 13));
        m.instructions.add(new JumpInsnNode(Opcodes.GOTO, resolved));
        m.instructions.add(virtual);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 10));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 12));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, 13));
        m.instructions.add(resolved);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 13));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, 13));
        m.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/invoke/ConstantCallSite"));
        m.instructions.add(new InsnNode(Opcodes.DUP));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 13));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false));
        m.instructions.add(new InsnNode(Opcodes.ARETURN));
        return m;
    }

    private static void decodeArgument(MethodNode m, int sourceLocal, int keyLocal, int targetLocal, String owner, String decoder, boolean ownerInterface) {
        LabelNode plain = new LabelNode();
        LabelNode oneLayer = new LabelNode();
        LabelNode done = new LabelNode();
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9));
        m.instructions.add(new JumpInsnNode(Opcodes.IFEQ, plain));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, sourceLocal));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, targetLocal));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9));
        m.instructions.add(new InsnNode(Opcodes.ICONST_2));
        m.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPLT, oneLayer));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, targetLocal));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, keyLocal));
        m.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        m.instructions.add(new InsnNode(Opcodes.IXOR));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, decoder, "(Ljava/lang/String;I)Ljava/lang/String;", ownerInterface));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, targetLocal));
        m.instructions.add(oneLayer);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, targetLocal));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, keyLocal));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, decoder, "(Ljava/lang/String;I)Ljava/lang/String;", ownerInterface));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, targetLocal));
        m.instructions.add(new JumpInsnNode(Opcodes.GOTO, done));
        m.instructions.add(plain);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, sourceLocal));
        m.instructions.add(new VarInsnNode(Opcodes.ASTORE, targetLocal));
        m.instructions.add(done);
    }
}
