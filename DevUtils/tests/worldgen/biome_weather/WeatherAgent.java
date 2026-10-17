package biomeweather;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;
/** One observation hook after the real server loads; no weather substitution. */
public final class WeatherAgent {
 public static void premain(String options,Instrumentation instrumentation){instrumentation.addTransformer(new ClassFileTransformer(){
  public byte[] transform(ClassLoader loader,String type,Class<?> redef,ProtectionDomain domain,byte[] bytes){
   if(!type.equals("net/minecraft/server/MinecraftServer"))return null;
   var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
   reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
     var method=super.visitMethod(access,name,descriptor,signature,exceptions);
     if(!name.equals("tickServer")||!descriptor.equals("(Ljava/util/function/BooleanSupplier;)V"))return method;
     return new AdviceAdapter(Opcodes.ASM9,method,access,name,descriptor){@Override protected void onMethodEnter(){loadThis();invokeStatic(Type.getObjectType("biomeweather/WeatherAudit"),new Method("capture","(Ljava/lang/Object;)V"));}};
    }
   },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
  }
 });}
}
