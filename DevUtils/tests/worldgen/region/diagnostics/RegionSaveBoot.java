package fallen;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Install before resolving any snapshot/storage/helper class. */
public final class RegionSaveBoot {
    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String owner, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                boolean snapshot = owner.equals("net/minecraft/world/level/chunk/storage/SerializableChunkData");
                boolean storage = owner.equals("net/minecraft/world/level/chunk/storage/RegionFileStorage");
                boolean light = Boolean.getBoolean("region.save.live") && (owner.startsWith("net/minecraft/world/level/lighting/") || owner.equals("net/minecraft/server/level/ThreadedLevelLightEngine"));
                if (!snapshot && !storage && !light) return null;
                var reader = new ClassReader(bytes);
                var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        var original = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (snapshot && name.equals("copyOf") && descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;")) {
                            return new AdviceAdapter(Opcodes.ASM9, original, access, name, descriptor) {
                                @Override protected void onMethodExit(int opcode) {
                                    if (opcode != ARETURN) return;
                                    dup(); loadArg(0); loadArg(1);
                                    invokeStatic(Type.getObjectType("fallen/RegionSaveAgent"), new Method("snapshot", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"));
                                }
                            };
                        }
                        if (snapshot && name.equals("encode") && descriptor.equals("()Lnet/minecraft/world/level/chunk/storage/SerializableChunkData$Encoded;")) {
                            return new AdviceAdapter(Opcodes.ASM9, original, access, name, descriptor) {
                                @Override protected void onMethodExit(int opcode) {
                                    if (opcode != ARETURN) return;
                                    dup(); loadThis();
                                    invokeStatic(Type.getObjectType("fallen/RegionSaveAgent"), new Method("encoded", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                                }
                            };
                        }
                        if (storage && (name.equals("writeTape") && descriptor.equals("(Lnet/minecraft/world/level/ChunkPos;[B)V")
                                || name.equals("write") && descriptor.equals("(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"))) {
                            return new AdviceAdapter(Opcodes.ASM9, original, access, name, descriptor) {
                                void observe(String phase) {
                                    loadThis(); loadArg(0); loadArg(1); push(phase);
                                    invokeStatic(Type.getObjectType("fallen/RegionSaveAgent"), new Method("write", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V"));
                                }
                                @Override protected void onMethodEnter() { observe(name + "-enter"); }
                                @Override protected void onMethodExit(int opcode) { observe(name + (opcode == ATHROW ? "-throw" : "-return")); }
                            };
                        }
                        if (light && Boolean.getBoolean("region.save.native")) {
                            MethodVisitor nativeHook = nativeMethod(owner, access, name, descriptor, original);
                            if (nativeHook != original) return nativeHook;
                        }
                        if (light) return lightMethod(owner, access, name, descriptor, original);
                        return original;
                    }
                }, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            }
        });
    }

    private static MethodVisitor nativeMethod(String owner, int access, String name, String descriptor, MethodVisitor original) {
        String pkg = "net/minecraft/world/level/lighting/";
        boolean binding = owner.equals(pkg + "NativeLightCoordinator") && name.equals("bindingForPropagation");
        boolean command = owner.equals(pkg + "NativeLevelLighting") && name.equals("command");
        boolean status = owner.equals(pkg + "NativeLevelLighting") && name.equals("updateStatus");
        boolean resume = owner.equals(pkg + "NativeLevelLighting") && name.equals("resume");
        boolean adoption = owner.equals(pkg + "NativeLightingMap") && name.equals("adoptSnapshot");
        if (!(binding || command || status || resume || adoption)) return original;
        return new AdviceAdapter(Opcodes.ASM9, original, access, name, descriptor) {
            void call(String method, String desc) { invokeStatic(Type.getObjectType("fallen/RegionNativeTrace"), new Method(method, desc)); }
            @Override protected void onMethodEnter() {
                if (status) { loadThis(); loadArg(0); loadArg(1); call("updateStatus", "(Ljava/lang/Object;JZ)V"); }
                if (command) { loadThis(); loadArg(0); loadArg(1); loadArg(2); call("command", "(Ljava/lang/Object;IJZ)V"); }
                if (resume) { loadThis(); loadArg(0); loadArg(1); loadArg(2); call("resume", "(Ljava/lang/Object;JLjava/lang/Object;J)V"); }
            }
            @Override protected void onMethodExit(int opcode) {
                if (binding && opcode == ARETURN) { dup(); loadThis(); call("binding", "(Ljava/lang/Object;Ljava/lang/Object;)V"); }
                if (adoption && opcode == ARETURN) { dup(); loadThis(); loadArg(0); call("adopted", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"); }
                if (resume) { call("resumeEnd", "()V"); }
            }
        };
    }

    private static MethodVisitor lightMethod(String owner, int access, String name, String descriptor, MethodVisitor original) {
        String pkg = "net/minecraft/world/level/lighting/";
        boolean storage = owner.equals(pkg + "LayerLightSectionStorage");
        boolean engine = owner.equals(pkg + "LightEngine");
        boolean level = owner.equals(pkg + "LevelLightEngine");
        boolean map = owner.equals(pkg + "DataLayerStorageMap");
        boolean coordinator = owner.equals(pkg + "NativeLightCoordinator");
        boolean threaded = owner.equals("net/minecraft/server/level/ThreadedLevelLightEngine");
        boolean sky = owner.equals(pkg + "SkyLightSectionStorage");
        boolean constructor = name.equals("<init>") && descriptor.contains("LightChunkGetter");
        boolean mapConstructor = map && name.equals("<init>") && descriptor.contains("NativeLightingMap");
        boolean sourceRead = storage && name.equals("getDataLayerData") && descriptor.startsWith("(J)");
        boolean mapRead = storage && name.equals("getDataLayer") && descriptor.startsWith("(L" + pkg + "DataLayerStorageMap;J)");
        boolean engineRead = engine && name.equals("getDataLayerData");
        boolean listener = level && name.equals("getLayerListener");
        boolean published = storage && name.equals("publishNativeMap");
        boolean update = threaded && name.equals("updateChunkStatus");
        boolean mutation = storage && (name.equals("queueSectionData") || name.equals("updateSectionStatus") || name.equals("retainData") || name.equals("onNodeRemoved"))
            || sky && name.equals("onNodeRemoved") || map && name.equals("removeLayer") || coordinator && name.equals("queueSectionData");
        boolean run = coordinator && name.equals("run") && descriptor.startsWith("(IJJ") && Type.getArgumentTypes(descriptor).length == 6;
        if (!(constructor && (storage || engine || level) || mapConstructor || sourceRead || mapRead || engineRead || listener || published || update || mutation || run)) return original;
        return new AdviceAdapter(Opcodes.ASM9, original, access, name, descriptor) {
            void call(String method, String desc) { invokeStatic(Type.getObjectType("fallen/RegionLightTrace"), new Method(method, desc)); }
            void store() {
                loadThis();
                if (coordinator) getField(Type.getObjectType(owner), "storage", Type.getObjectType(pkg + "LayerLightSectionStorage"));
            }
            void boundary(String phase, boolean returned) {
                if (run) {
                    loadThis(); store(); loadArg(0); loadArg(1); loadArg(2); push(phase);
                    call("coordinator", "(Ljava/lang/Object;Ljava/lang/Object;IJJLjava/lang/String;)V"); return;
                }
                if (update) { loadThis(); loadArg(0); push(phase); call("updateChunk", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V"); return; }
                if (!mutation) return;
                loadThis(); if (coordinator) store(); else visitInsn(ACONST_NULL);
                loadArg(0);
                if (name.equals("updateSectionStatus") || name.equals("retainData")) loadArg(1); else push(-1);
                if (name.equals("queueSectionData")) loadArg(1); else visitInsn(ACONST_NULL);
                push(name + "-" + phase);
                call("boundary", "(Ljava/lang/Object;Ljava/lang/Object;JILjava/lang/Object;Ljava/lang/String;)V");
            }
            @Override protected void onMethodEnter() { if (!constructor && !mapConstructor) boundary("enter", false); }
            @Override protected void onMethodExit(int opcode) {
                if (opcode == ATHROW) { if (!constructor && !mapConstructor) boundary("throw", false); return; }
                if (constructor) {
                    if (storage) {
                        loadThis(); loadArg(1); loadArg(0); loadArg(2); loadThis();
                        getField(Type.getObjectType(owner), "visibleSectionData", Type.getObjectType(pkg + "DataLayerStorageMap"));
                        call("storageCreated", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
                    } else if (engine) { loadThis(); loadArg(0); loadArg(1); call("engineCreated", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"); }
                    else if (level) { loadThis(); loadArg(0); call("levelCreated", "(Ljava/lang/Object;Ljava/lang/Object;)V"); }
                    return;
                }
                if (mapConstructor) { loadThis(); loadArg(0); call("mapCreated", "(Ljava/lang/Object;Ljava/lang/Object;)V"); return; }
                if (sourceRead || mapRead) {
                    dup(); loadThis(); if (mapRead) loadArg(0); else visitInsn(ACONST_NULL);
                    loadArg(mapRead ? 1 : 0); push(sourceRead ? "data-for-save" : "map-read");
                    call("sourceRead", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;JLjava/lang/String;)V");
                } else if (engineRead) { dup(); loadThis(); loadArg(0); call("engineRead", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"); }
                else if (listener) { dup(); loadThis(); loadArg(0); call("listener", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"); }
                else if (published) {
                    loadThis(); loadArg(0); loadThis(); getField(Type.getObjectType(owner), "visibleSectionData", Type.getObjectType(pkg + "DataLayerStorageMap"));
                    call("published", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
                }
                boundary("return", true);
            }
        };
    }

}
