package fallen;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Injects only at actual Java adoption and cache-publication boundaries. */
public final class FallenChainAllocationAgent {
    public static void premain(String options,Instrumentation instrumentation){
        FallenResourceAgent.premain(options,instrumentation);
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String owner,Class<?> type,ProtectionDomain domain,byte[] bytes){
                boolean program=owner.equals("net/minecraft/world/level/levelgen/NativeDecoration$Program");
                boolean cache=owner.equals("net/minecraft/world/level/levelgen/NativeDecoration$Programs");
                if(!program&&!cache)return null;
                var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                        var visitor=super.visitMethod(access,name,desc,signature,exceptions);
                        return new AdviceAdapter(Opcodes.ASM9,visitor,access,name,desc){
                            void hook(String name){invokeStatic(Type.getObjectType("fallen/FallenChainAllocation"),new Method(name,"()V"));}
                            protected void onMethodEnter(){if(program&&name.equals("<init>"))hook("beforeAdoption");}
                            public void visitFieldInsn(int opcode,String target,String field,String desc){
                                if(cache&&name.equals("program")&&opcode==Opcodes.PUTFIELD&&field.equals("entries"))hook("beforePublication");
                                super.visitFieldInsn(opcode,target,field,desc);
                            }
                        };
                    }
                },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
            }
        });
    }
}
