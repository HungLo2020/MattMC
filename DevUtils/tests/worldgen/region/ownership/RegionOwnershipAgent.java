package fallen;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Hooks actual owner adoption, projection and VM replay operations only. */
public final class RegionOwnershipAgent {
    public static void premain(String options,Instrumentation instrumentation){
        FallenResourceAgent.premain(options,instrumentation);
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String owner,Class<?> type,ProtectionDomain domain,byte[] bytes){
                boolean release=owner.equals("net/minecraft/world/level/chunk/NativeDecorationRegion$Release");
                if(!release&&!owner.equals("net/minecraft/world/level/chunk/NativeDecorationRegion"))return null;
                var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                        var visitor=super.visitMethod(access,name,desc,signature,exceptions);
                        if(release&&name.equals("run")&&Boolean.getBoolean("region.killRelease")){
                            visitor.visitCode();visitor.visitInsn(Opcodes.RETURN);visitor.visitMaxs(0,1);visitor.visitEnd();return null;
                        }
                        return new AdviceAdapter(Opcodes.ASM9,visitor,access,name,desc){
                            boolean adopted;
                            boolean ackArgument;
                            void hook(String point){push(point);invokeStatic(Type.getObjectType("fallen/RegionOwnership"),new Method("boundary","(Ljava/lang/String;)V"));}
                            protected void onMethodEnter(){
                                if(name.equals("projectHeightmap"))hook("projection");
                                if(name.equals("replayEvent")){
                                    loadArg(0);loadArg(1);loadArg(2);loadArg(3);loadArg(4);
                                    invokeStatic(Type.getObjectType("fallen/RegionOwnership"),new Method("beforeEffect","(JJJJJ)V"));
                                }
                            }
                            protected void onMethodExit(int opcode){
                                if(name.equals("replayEvent")&&opcode==RETURN){
                                    loadArg(0);loadArg(1);loadArg(2);loadArg(3);loadArg(4);
                                    invokeStatic(Type.getObjectType("fallen/RegionOwnership"),new Method("afterEffect","(JJJJJ)V"));
                                }
                            }
                            public void visitFieldInsn(int opcode,String target,String field,String descriptor){
                                if(name.equals("replayPending")&&opcode==GETSTATIC&&field.equals("ACK"))ackArgument=true;
                                super.visitFieldInsn(opcode,target,field,descriptor);
                            }
                            public void visitMethodInsn(int opcode,String target,String called,String descriptor,boolean isInterface){
                                if(name.equals("replayPending")&&ackArgument&&target.equals("java/lang/foreign/MemorySegment")&&called.equals("ofAddress")){
                                    ackArgument=false;invokeStatic(Type.getObjectType("fallen/RegionOwnership"),new Method("ackArgument","()V"));
                                }
                                if(release&&name.equals("run")&&target.equals("java/lang/foreign/MemorySegment")&&called.equals("ofAddress"))
                                    invokeStatic(Type.getObjectType("fallen/RegionOwnership"),new Method("releaseArgument","()V"));
                                if(name.equals("<init>")&&!adopted&&target.equals("java/lang/foreign/MemorySegment")&&called.equals("ofAddress")){
                                    adopted=true;hook("adoption");
                                }
                                if(name.equals("replayPending")&&target.equals("java/lang/Math")&&called.equals("toIntExact"))hook("claim");
                                if(name.equals("replayPending")&&Boolean.getBoolean("region.killAck")&&target.equals("java/lang/invoke/MethodHandle")
                                        &&called.equals("invokeExact")&&(descriptor.equals("(Ljava/lang/foreign/MemorySegment;I)V")||descriptor.equals("(JI)V"))){
                                    pop();push(0); // Deliberately forget the completed VM prefix.
                                }
                                if(name.equals("replayPending")&&target.equals("java/lang/invoke/MethodHandle")&&called.equals("invokeExact")
                                        &&(descriptor.equals("(Ljava/lang/foreign/MemorySegment;I)V")||descriptor.equals("(JI)V")))ackArgument=false;
                                super.visitMethodInsn(opcode,target,called,descriptor,isInterface);
                            }
                        };
                    }
                },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
            }
        });
    }
}
