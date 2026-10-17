package fallen;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Test-only controller entry plus the existing save/public-return observer. */
public final class RegionSaveOrderBoot {
    public static void premain(String options, Instrumentation instrumentation) {
        RegionSaveBoot.premain(options, instrumentation);
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String owner, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!owner.equals("net/minecraft/server/MinecraftServer")) return null;
                var reader = new ClassReader(bytes); var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (!name.equals("tickServer") || !descriptor.equals("(Ljava/util/function/BooleanSupplier;)V")) return method;
                        return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                            @Override protected void onMethodEnter() {
                                loadThis(); invokeStatic(Type.getObjectType("fullgeneration/RegionSaveOrder"), new Method("capture", "(Ljava/lang/Object;)V"));
                            }
                        };
                    }
                }, ClassReader.EXPAND_FRAMES); return writer.toByteArray();
            }
        });
    }
}
