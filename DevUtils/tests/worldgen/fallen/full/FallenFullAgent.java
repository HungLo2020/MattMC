package fullgeneration;

import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** The existing FULL verifier plus an opt-in genuine fallen-adapter trap.
 * It observes actual registered configuration identity and changes no world
 * or random input. Keep it disabled for acceptance timing measurements. */
public final class FallenFullAgent {
    static final ConcurrentHashMap<String,AtomicInteger> ENTRIES=new ConcurrentHashMap<>();
    static final AtomicInteger ENTITY_WAITS=new AtomicInteger();
    public static void premain(String options,Instrumentation instrumentation){
        FullGenAgent.premain(options,instrumentation);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try{
                var counts=new TreeMap<String,Integer>();ENTRIES.forEach((name,value)->counts.put(name,value.get()));
                var data=Map.of("javaEntries",counts.values().stream().mapToInt(Integer::intValue).sum(),"variants",counts,"entityWaits",ENTITY_WAITS.get());
                Files.writeString(Path.of(System.getProperty("audit.output"),"fallen-consumer.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
            }catch(Exception failure){failure.printStackTrace();}
        },"fallen-full-observer"));
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String owner,Class<?> type,ProtectionDomain domain,byte[] bytes){
                boolean audit=owner.equals("fullgeneration/FullGenAudit");
                if(!audit&&!owner.equals("net/minecraft/world/level/levelgen/feature/FallenTreeFeature"))return null;
                var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                        var method=super.visitMethod(access,name,desc,signature,exceptions);
                        if(audit&&name.equals("fingerprint"))return new AdviceAdapter(Opcodes.ASM9,method,access,name,desc){
                            protected void onMethodEnter(){loadArg(0);invokeStatic(Type.getObjectType("fullgeneration/FallenFullAgent"),new Method("canonicalBlockEntities","(Ljava/lang/Object;)V"));}
                        };
                        if(audit&&name.equals("entities"))return new AdviceAdapter(Opcodes.ASM9,method,access,name,desc){
                            protected void onMethodEnter(){loadArg(0);loadArg(1);invokeStatic(Type.getObjectType("fullgeneration/FallenFullAgent"),new Method("awaitEntities","(Ljava/lang/Object;Ljava/lang/Object;)V"));}
                        };
                        if(!name.equals("place")||!desc.equals("(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z"))return method;
                        return new AdviceAdapter(Opcodes.ASM9,method,access,name,desc){
                            protected void onMethodEnter(){loadArg(0);invokeStatic(Type.getObjectType("fullgeneration/FallenFullAgent"),new Method("entry","(Ljava/lang/Object;)V"));}
                        };
                    }
                },ClassReader.EXPAND_FRAMES);return writer.toByteArray();
            }
        });
    }
    /** SerializableChunkData builds this list from an unordered position set.
     * Sort only the audit copy's complete NBT entries; preserve duplicates,
     * every field and all nested list order. */
    public static void canonicalBlockEntities(Object value){
        var data=(net.minecraft.nbt.CompoundTag)value;
        if(data.get("block_entities")instanceof net.minecraft.nbt.ListTag entries)
            entries.sort(Comparator.comparing(FullGenAudit::canonical));
    }
    public static void awaitEntities(Object world,Object position){
        if(!Boolean.getBoolean("fallen.fullAwaitEntities"))return;
        var level=(net.minecraft.server.level.ServerLevel)world;
        var pos=(net.minecraft.world.level.ChunkPos)position;
        if(!level.areEntitiesLoaded(pos.toLong()))ENTITY_WAITS.incrementAndGet();
        level.waitForEntities(pos,0);
    }
    public static void entry(Object value){
        var context=(FeaturePlaceContext<?>)value;
        String name="unregistered";
        for(var entry:context.level().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).entrySet()){
            if(entry.getValue().config()==context.config()){name=entry.getKey().location().toString();break;}
        }
        ENTRIES.computeIfAbsent(name,key->new AtomicInteger()).incrementAndGet();
        if(Boolean.getBoolean("fallen.fullRequireNative"))throw new AssertionError("FULL generation entered Java configured fallen adapter: "+name);
    }
}
