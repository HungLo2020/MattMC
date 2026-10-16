package fullgeneration;
import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Defers the existing failure hook until the actual fallen family executes.
 * Changes only audit coordinates and opt-in failure injection, never game data. */
public final class FallenFailureAgent {
    static volatile String pending;
    static final AtomicBoolean FIRED=new AtomicBoolean();
    static final ThreadLocal<Boolean> ARMING=ThreadLocal.withInitial(()->false);
    static volatile String trigger="none";
    static volatile boolean frameActive;
    static volatile int completedArmedFrames;
    public static String defer(String spec){
        if(!ARMING.get()&&Long.parseLong(spec.substring(spec.indexOf(':')+1))>0){pending=spec;return spec.substring(0,spec.indexOf(':')+1)+"-1";}
        return spec;
    }
    public static void nativeEffect(int op,Object args){
        if(op!=24)return;
        long effect=((java.lang.foreign.MemorySegment)args).reinterpret(8).get(java.lang.foreign.ValueLayout.JAVA_LONG,0);
        if(effect!=0){fire("native-fallen-effect-24");if(FIRED.get())frameActive=true;}
        else if(FIRED.get()){
            // A stump-only frame can finish without another write. Disarm at
            // its genuine Done effect so the failure cannot spill into a
            // later unrelated feature, and try the next real fallen frame.
            frameActive=false;completedArmedFrames++;ARMING.set(true);
            try{Class.forName("net.minecraft.world.level.levelgen.NativeDecoration").getMethod("armTestFailure",String.class).invoke(null,"fail:-1");}
            catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            finally{ARMING.remove();FIRED.set(false);}
        }
    }
    public static void javaEntry(){if(Boolean.getBoolean("mattmc.worldgen.javaDecoration"))fire("original-java-fallen-body");}
    public static void javaReturn(){if(trigger.equals("original-java-fallen-body"))frameActive=false;}
    static void fire(String reason){
        String spec=pending;if(spec==null||!FIRED.compareAndSet(false,true))return;
        trigger=reason;frameActive=true;ARMING.set(true);
        try{Class.forName("net.minecraft.world.level.levelgen.NativeDecoration").getMethod("armTestFailure",String.class).invoke(null,spec);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        finally{ARMING.remove();}
        System.out.println("FALLEN_FAILURE_ARMED "+reason+" "+spec);
    }
    public static int center(int value,boolean x){return Integer.getInteger(x?"fallen.failure.centerX":"fallen.failure.centerZ",value);}
    public static void premain(String options,Instrumentation instrumentation){
        FullGenAgent.premain(options,instrumentation);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try{Files.writeString(Path.of(System.getProperty("audit.output"),"fallen-failure.json"),new com.google.gson.Gson().toJson(Map.of("fired",FIRED.get(),"trigger",trigger,"pending",String.valueOf(pending),"frameActive",frameActive,"completedArmedFrames",completedArmedFrames)));}
            catch(Exception failure){failure.printStackTrace();}
        }));
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String owner,Class<?> type,ProtectionDomain domain,byte[] bytes){
                boolean audit=owner.equals("fullgeneration/FullGenAudit"),nativeOwner=owner.equals("net/minecraft/world/level/levelgen/NativeDecoration"),fallen=owner.equals("net/minecraft/world/level/levelgen/feature/FallenTreeFeature");
                if(!audit&&!nativeOwner&&!fallen)return null;
                var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                        var visitor=super.visitMethod(access,name,desc,signature,exceptions);
                        boolean positions=audit&&name.equals("positions")&&desc.equals("(III)Ljava/util/List;");
                        boolean arm=nativeOwner&&name.equals("armTestFailure")&&desc.equals("(Ljava/lang/String;)V");
                        boolean effect=nativeOwner&&name.equals("upcallOriginal");
                        boolean entry=fallen&&name.equals("place")&&desc.equals("(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z");
                        if(!positions&&!arm&&!effect&&!entry)return visitor;
                        return new AdviceAdapter(Opcodes.ASM9,visitor,access,name,desc){protected void onMethodEnter(){
                            Type helper=Type.getObjectType("fullgeneration/FallenFailureAgent");
                            if(positions){loadArg(0);push(true);invokeStatic(helper,new Method("center","(IZ)I"));storeArg(0);loadArg(1);push(false);invokeStatic(helper,new Method("center","(IZ)I"));storeArg(1);}
                            if(arm){loadArg(0);invokeStatic(helper,new Method("defer","(Ljava/lang/String;)Ljava/lang/String;"));storeArg(0);}
                            if(effect){loadArg(0);loadArg(1);invokeStatic(helper,new Method("nativeEffect","(ILjava/lang/Object;)V"));}
                            if(entry)invokeStatic(helper,new Method("javaEntry","()V"));
                        }protected void onMethodExit(int opcode){if(entry&&opcode==IRETURN)invokeStatic(Type.getObjectType("fullgeneration/FallenFailureAgent"),new Method("javaReturn","()V"));}};
                    }
                },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
            }
        });
    }
}
