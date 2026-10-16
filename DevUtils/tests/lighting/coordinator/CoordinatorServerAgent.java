package lightcoordinator;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Keeps the existing player/threaded audit and adds per-engine policy proof. */
public final class CoordinatorServerAgent {
    public static void premain(String options, Instrumentation instrumentation) {
        threadedlighting.ThreadedLightingServerAgent.premain(options, instrumentation);
        CoordinatorServerAudit.initialize();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                boolean coordinator = name.equals("net/minecraft/world/level/lighting/NativeLightCoordinator");
                boolean level = name.equals("net/minecraft/world/level/lighting/NativeLevelLighting");
                boolean lifecycle = name.equals("net/minecraft/server/level/PlayerLoadingServerAudit");
                if (!coordinator && !level && !lifecycle) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor base = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (lifecycle && method.equals("await") && descriptor.equals(
                                "(Lnet/minecraft/server/MinecraftServer;Ljava/util/List;Ljava/lang/String;Ljava/util/function/BooleanSupplier;)V")) {
                            return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                                @Override protected void onMethodExit(int opcode) {
                                    if (opcode == RETURN) {
                                        loadArg(0); loadArg(2);
                                        invokeStatic(Type.getObjectType("lightcoordinator/CoordinatorServerAudit"),
                                            new Method("afterAwait", "(Ljava/lang/Object;Ljava/lang/String;)V"));
                                    }
                                }
                            };
                        }
                        if (level && (method.equals("runUpdates") && descriptor.equals("()I")
                                || method.equals("resumeTask") && descriptor.equals("(Ljava/lang/foreign/MemorySegment;J)V"))) {
                            return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                                @Override protected void onMethodExit(int opcode) {
                                    if (opcode == IRETURN || opcode == RETURN) {
                                        loadThis();
                                        invokeStatic(Type.getObjectType("lightcoordinator/CoordinatorServerAudit"),
                                            new Method("observeLevel", "(Ljava/lang/Object;)V"));
                                    }
                                }
                            };
                        }
                        if (!coordinator || !method.equals("runLightUpdates") || !descriptor.equals("()I")) return base;
                        return new AdviceAdapter(Opcodes.ASM9, base, access, method, descriptor) {
                            @Override protected void onMethodExit(int opcode) {
                                if (opcode == IRETURN) {
                                    loadThis();
                                    invokeStatic(Type.getObjectType("lightcoordinator/CoordinatorServerAudit"),
                                        new Method("observe", "(Ljava/lang/Object;)V"));
                                }
                            }
                        };
                    }
                }, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            }
        });
    }
}
