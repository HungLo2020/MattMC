package threadedlighting;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Adds nonvacuity counters to the existing real player/world lifecycle audit. */
public final class ThreadedLightingServerAgent {
    public static void premain(String options,Instrumentation instrumentation) {
        playerloading.PlayerLoadingServerAgent.premain(options,instrumentation);
        ThreadedLightingServerAudit.initialize();
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes) {
                if(!name.equals("net/minecraft/server/level/ThreadedLevelLightEngine"))return null;
                ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                        MethodVisitor base=super.visitMethod(access,method,descriptor,signature,exceptions);
                        if(!method.equals("runUpdate")||!descriptor.equals("()V"))return base;
                        return new AdviceAdapter(Opcodes.ASM9,base,access,method,descriptor){
                            final Type audit=Type.getObjectType("threadedlighting/ThreadedLightingServerAudit");
                            @Override protected void onMethodEnter(){loadThis();invokeStatic(audit,new Method("enter","(Ljava/lang/Object;)V"));}
                            @Override protected void onMethodExit(int opcode){if(opcode==RETURN)invokeStatic(audit,new Method("complete","()V"));}
                        };
                    }
                },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
            }
        });
    }
}
