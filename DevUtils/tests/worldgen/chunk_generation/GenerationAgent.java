package fullgeneration;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Start-only hook: no per-request or stage instrumentation in timing samples. */
public final class GenerationAgent {
    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!name.equals("net/minecraft/server/MinecraftServer")) return null;
                var reader = new ClassReader(bytes);
                var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!name.equals("tickServer") || !descriptor.equals("(Ljava/util/function/BooleanSupplier;)V")) return method;
                        return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                            protected void onMethodEnter() {
                                loadThis();
                                invokeStatic(Type.getObjectType("fullgeneration/GenerationAudit"), new Method("capture", "(Ljava/lang/Object;)V"));
                            }
                        };
                    }
                }, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            }
        });
    }
}
