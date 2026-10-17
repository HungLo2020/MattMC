package fallen;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
/** Starts the real-server cost fixture; installs no voxel/feature probes. */
public final class FallenCostAgent {
    public static void premain(String options,Instrumentation instrumentation){
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String owner,Class<?> type,ProtectionDomain domain,byte[] bytes){
                if(!owner.equals("net/minecraft/server/MinecraftServer"))return null;
                var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                        var visitor=super.visitMethod(access,name,desc,signature,exceptions);
                        if(!name.equals("tickServer")||!desc.equals("(Ljava/util/function/BooleanSupplier;)V"))return visitor;
                        return new AdviceAdapter(Opcodes.ASM9,visitor,access,name,desc){protected void onMethodEnter(){loadThis();invokeStatic(Type.getObjectType("fallen/FallenCost"),new Method("capture","(Ljava/lang/Object;)V"));}};
                    }
                },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
            }
        });
    }
}
