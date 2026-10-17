package playerloading;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Test-only observers around actual server entry points; no policy replacement. */
public final class PlayerLoadingServerAgent {
    private static final Type AUDIT = Type.getObjectType("net/minecraft/server/level/PlayerLoadingServerAudit");

    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                boolean server = name.equals("net/minecraft/server/MinecraftServer");
                boolean throttle = name.equals("net/minecraft/server/level/ThrottlingChunkTaskDispatcher");
                boolean policy = name.equals("net/minecraft/server/level/NativePlayerLoading");
                if (!server && !throttle && !policy) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor base = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (server && method.equals("tickServer") && descriptor.equals("(Ljava/util/function/BooleanSupplier;)V")) {
                            return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                                @Override protected void onMethodEnter() {
                                    loadThis();
                                    invokeStatic(AUDIT, new Method("capture", "(Ljava/lang/Object;)V"));
                                }
                            };
                        }
                        if (throttle && method.equals("scheduleForExecution")) {
                            return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                                @Override protected void onMethodEnter() {
                                    loadThis();
                                    loadArg(0);
                                    invokeVirtual(Type.getObjectType("net/minecraft/server/level/ChunkTaskPriorityQueue$TasksForChunk"),
                                        new Method("chunkPos", "()J"));
                                    push(true);
                                    invokeStatic(AUDIT, new Method("admission", "(Ljava/lang/Object;JZ)V"));
                                }
                            };
                        }
                        if (throttle && method.equals("onRelease") && descriptor.equals("(J)V")) {
                            return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                                @Override protected void onMethodExit(int opcode) {
                                    if (opcode == RETURN) {
                                        loadThis(); loadArg(0); push(false);
                                        invokeStatic(AUDIT, new Method("admission", "(Ljava/lang/Object;JZ)V"));
                                    }
                                }
                            };
                        }
                        if (policy && method.equals("admits") && descriptor.equals("(J)Z")) {
                            return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                                @Override protected void onMethodExit(int opcode) {
                                    if (opcode == IRETURN) {
                                        dup();
                                        invokeStatic(AUDIT, new Method("nativeRecheck", "(Z)V"));
                                    }
                                }
                            };
                        }
                        return base;
                    }
                }, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            }
        });
    }
}
