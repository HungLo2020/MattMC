package endfeatures;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Run controlled End fixtures once the real server has initialized. */
public final class EndFeatureAgent {
    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                boolean server = name.equals("net/minecraft/server/MinecraftServer");
                boolean region = name.equals("net/minecraft/server/level/WorldGenRegion");
                boolean level = name.equals("net/minecraft/world/level/Level");
                boolean serverLevel = name.equals("net/minecraft/server/level/ServerLevel");
                boolean nativeEnd = name.equals("net/minecraft/world/level/levelgen/NativeEndFeatures");
                boolean decoration = name.equals("net/minecraft/world/level/levelgen/NativeDecoration");
                boolean binding = name.equals("net/minecraft/world/level/chunk/NativeDecorationRegion");
                boolean profile = Boolean.getBoolean("end.profileBoundary") && (decoration || binding);
                if (!server && !region && !level && !serverLevel && !nativeEnd && !profile) return null;
                if (Boolean.getBoolean("end.timing") && !server && !nativeEnd && !profile) return null;
                var reader = new ClassReader(bytes);
                var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                        int profileKind = binding && name.equals("refresh") && descriptor.equals("(ZZ)I") ? 0
                            : binding && name.equals("afterPlacement") && descriptor.equals("(Z)V") ? 1
                            : decoration && name.equals("upcall") ? 2
                            : binding && name.equals("copyWritableState") ? 3 : -1;
                        if (profile && profileKind >= 0) {
                            final int label = profileKind;
                            return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                                int started;
                                protected void onMethodEnter() {
                                    invokeStatic(Type.getType(System.class), new Method("nanoTime", "()J"));
                                    started = newLocal(Type.LONG_TYPE); storeLocal(started);
                                }
                                protected void onMethodExit(int opcode) {
                                    push(label); loadLocal(started);
                                    invokeStatic(Type.getObjectType("endfeatures/EndFeatureAudit"), new Method("profileDuration", "(IJ)V"));
                                }
                            };
                        }
                        if (nativeEnd && name.equals("run") && descriptor.startsWith("(I")) {
                            return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                                protected void onMethodEnter() {
                                    loadArg(0);
                                    invokeStatic(Type.getObjectType("endfeatures/EndFeatureAudit"), new Method("nativeEntry", "(I)V"));
                                }
                            };
                        }
                        if ((region || level) && name.equals("setBlock") && descriptor.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")) {
                            return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                                protected void onMethodEnter() {
                                    loadArg(0); loadArg(1); loadArg(2);
                                    invokeStatic(Type.getObjectType("endfeatures/EndFeatureAudit"), new Method("write", "(Ljava/lang/Object;Ljava/lang/Object;I)V"));
                                }
                            };
                        }
                        if ((region || serverLevel) && name.equals("addFreshEntity") && descriptor.equals("(Lnet/minecraft/world/entity/Entity;)Z")) {
                            return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                                protected void onMethodEnter() {
                                    loadArg(0);
                                    invokeStatic(Type.getObjectType("endfeatures/EndFeatureAudit"), new Method("entity", "(Ljava/lang/Object;)V"));
                                }
                            };
                        }
                        if (!server || !name.equals("tickServer") || !descriptor.equals("(Ljava/util/function/BooleanSupplier;)V")) return method;
                        return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                            protected void onMethodEnter() {
                                loadThis();
                                invokeStatic(Type.getObjectType("endfeatures/EndFeatureAudit"), new Method("capture", "(Ljava/lang/Object;)V"));
                            }
                        };
                    }
                }, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            }
        });
    }
}
