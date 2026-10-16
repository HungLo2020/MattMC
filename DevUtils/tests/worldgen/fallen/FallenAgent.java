package fallen;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Optional scoped observation in the fixture window, with real server methods. */
public final class FallenAgent {
    public static void premain(String options,Instrumentation instrumentation){
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String owner,Class<?> type,ProtectionDomain domain,byte[] bytes){
                boolean server=owner.equals("net/minecraft/server/MinecraftServer");
                boolean region=owner.equals("net/minecraft/server/level/WorldGenRegion");
                boolean chunk=owner.equals("net/minecraft/world/level/chunk/ProtoChunk");
                boolean state=owner.equals("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase");
                boolean feature=owner.equals("net/minecraft/world/level/levelgen/feature/FallenTreeFeature");
                boolean decorator=owner.equals("net/minecraft/world/level/levelgen/feature/treedecorators/AttachedToLogsDecorator");
                if(!server&&!region&&!chunk&&!state&&!feature&&!decorator)return null;
                var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                        var mv=super.visitMethod(access,name,desc,signature,exceptions);
                        return new AdviceAdapter(Opcodes.ASM9,mv,access,name,desc){
                            void call(String name,String signature){invokeStatic(Type.getObjectType("fallen/FallenAudit"),new Method(name,signature));}
                            protected void onMethodEnter(){
                                if(server&&name.equals("tickServer")&&desc.equals("(Ljava/util/function/BooleanSupplier;)V")){loadThis();call("capture","(Ljava/lang/Object;)V");}
                                if(region&&name.equals("setBlock")&&desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")){
                                    loadArg(0);loadArg(1);loadArg(2);call("write","(Ljava/lang/Object;Ljava/lang/Object;I)V");
                                }
                                if(region&&name.equals("getBlockState")&&desc.equals("(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")){
                                    loadArg(0);call("read","(Ljava/lang/Object;)V");
                                }
                                if(chunk&&name.equals("markPosForPostprocessing")&&desc.equals("(Lnet/minecraft/core/BlockPos;)V")){
                                    loadArg(0);call("mark","(Ljava/lang/Object;)V");
                                }
                                if(state&&name.equals("isFaceSturdy")&&desc.equals("(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z")){
                                    loadThis();loadArg(1);loadArg(2);call("support","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
                                }
                                if(feature&&name.equals("place")&&desc.equals("(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z"))call("legacyAdapter","()V");
                                if(feature&&name.equals("placeFallenTree"))call("legacyBody","()V");
                            }
                            public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf){
                                if(decorator&&owner.equals("net/minecraft/Util")&&name.equals("shuffledCopy"))call("legacyDecorator","()V");
                                super.visitMethodInsn(opcode,owner,name,desc,itf);
                            }
                        };
                    }
                },ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            }
        });
    }
}
