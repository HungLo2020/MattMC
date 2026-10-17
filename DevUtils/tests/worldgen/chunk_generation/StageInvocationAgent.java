package stageinvocation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Test-only kill for the original Java-owned stage policy, after real dispatch. */
public final class StageInvocationAgent {
    public static void fail() {
        throw new AssertionError("Java still owns generation stage orchestration");
    }
    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!name.equals("net/minecraft/server/level/GenerationChunkHolder")) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor base = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (!method.equals("applyStep")) return base;
                        return new MethodVisitor(Opcodes.ASM9, base) {
                            @Override public void visitMethodInsn(int opcode, String owner, String target,
                                    String descriptor, boolean isInterface) {
                                if (owner.equals("net/minecraft/server/level/GenerationChunkHolder")
                                        && (target.equals("isStatusDisallowed") || target.equals("acquireStatusBump"))
                                        || owner.equals("net/minecraft/server/level/GeneratingChunkMap") && target.equals("applyStep")
                                        || owner.equals("java/util/concurrent/CompletableFuture") && target.equals("handle")) {
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "stageinvocation/StageInvocationAgent", "fail", "()V", false);
                                }
                                super.visitMethodInsn(opcode, owner, target, descriptor, isInterface);
                            }
                        };
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }
}
