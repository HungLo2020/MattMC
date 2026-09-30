package surfaceverification;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
public class AuditAgent {
 public static void premain(String options, Instrumentation instrumentation) {
  instrumentation.addTransformer(new ClassFileTransformer() {
   @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes) {
    boolean server=name.equals("net/minecraft/server/MinecraftServer");
    boolean surfaces=Boolean.getBoolean("audit.hash") && name.equals("net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator");
    if(!server && !surfaces)return null;
    ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
    reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
     @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
      MethodVisitor mv=super.visitMethod(access,name,descriptor,signature,exceptions);
      if(surfaces && (name.equals("buildSurface") || name.equals("applyCarvers")) && descriptor.startsWith("(Lnet/minecraft/server/level/WorldGenRegion;")) {
       boolean carvers=name.equals("applyCarvers");
       return new AdviceAdapter(Opcodes.ASM9,mv,access,name,descriptor) {
        @Override protected void onMethodExit(int opcode){if(opcode!=ATHROW){loadArg(carvers?5:3);invokeStatic(Type.getObjectType("surfaceverification/WorldgenAudit"),new Method(carvers?"recordCarvers":"recordSurface","(Ljava/lang/Object;)V"));}}
       };
      }
      if(!name.equals("tickServer") || !descriptor.equals("(Ljava/util/function/BooleanSupplier;)V"))return mv;
      return new MethodVisitor(Opcodes.ASM9,mv) {
       @Override public void visitCode(){super.visitCode();visitVarInsn(Opcodes.ALOAD,0);visitMethodInsn(Opcodes.INVOKESTATIC,"surfaceverification/WorldgenAudit","capture","(Ljava/lang/Object;)V",false);}
      };
     }
    },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
   }
  });
 }
}
