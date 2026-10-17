import java.lang.foreign.Arena;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;

/** Production world collection against Frozen and the previous Current consumer. */
public final class BenchmarkEntityCollection {
    static volatile long sink;
    static class Dynamic extends VerifySectionMembers.SpatialEntity {
        @Override public boolean isSpectator(){return false;}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void legacy(LevelEntityGetter getter,AABB box,EntityAccess excluded,Predicate predicate,ArrayList result) {
        getter.get(box,e->{if(e!=excluded && predicate.test(e))result.add(e);});
    }
    static MethodHandle collector(String mode) throws Exception {
        var signature=MethodType.methodType(void.class,AABB.class,EntityAccess.class,Predicate.class,ArrayList.class);
        if(mode.equals("current"))return MethodHandles.publicLookup().findVirtual(LevelEntityGetter.class,"collectEntities",signature);
        return MethodHandles.lookup().findStatic(BenchmarkEntityCollection.class,"legacy",signature.insertParameterTypes(0,LevelEntityGetter.class));
    }
    static long run(MethodHandle collector,AABB query,EntityAccess excluded,int iterations) throws Throwable {
        long checksum=0;
        for(int i=0;i<iterations;i++) {
            var result=new ArrayList<VerifySectionMembers.SpatialEntity>();
            collector.invokeExact(query,excluded,(Predicate)EntitySelector.NO_SPECTATORS,(ArrayList)result);
            checksum+=result.size();
            if(!result.isEmpty())checksum+=result.getFirst().ordinal+result.getLast().ordinal;
        }
        sink=checksum;return checksum;
    }
    public static void main(String[] args) throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        String mode=args[0];
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var compiler=ManagementFactory.getCompilationMXBean();
        long thread=Thread.currentThread().threadId();
        var base=collector(mode);
        var lines=new ArrayList<String>();
        try(var scope=Arena.ofConfined()) {
            for(int sections:new int[]{1,64,256})for(int perSection:new int[]{1,128}) {
                if(sections>1 && perSection>1)continue;
                for(int dynamic:new int[]{0,16}) {
                    var storage=new EntitySectionStorage<VerifySectionMembers.SpatialEntity>(VerifySectionMembers.SpatialEntity.class,k->Visibility.TRACKED);
                    var getter=new LevelEntityGetterAdapter<>(new EntityLookup<VerifySectionMembers.SpatialEntity>(),storage);
                    var call=base.bindTo(getter);
                    var entities=new ArrayList<VerifySectionMembers.SpatialEntity>();
                    for(int i=0;i<sections*perSection;i++) {
                        var entity=VerifySectionMembers.spatial(i,scope,dynamic!=0 && i%dynamic==0?Dynamic.class:VerifySectionMembers.SpatialEntity.class);
                        entity.setBoundingBox(new AABB(0,0,0,1,1,1));entities.add(entity);
                        storage.getOrCreateSection(SectionPos.asLong(i/perSection,0,0)).add(entity);
                    }
                    for(int hits:new int[]{0,1}) {
                        AABB query=hits==0?new AABB(-1,2,-1,sections*16,3,2):new AABB(-1,-1,-1,sections*16,2,2);
                        EntityAccess excluded=entities.get(entities.size()/2);
                        var actual=new ArrayList<VerifySectionMembers.SpatialEntity>();
                        call.invokeExact(query,excluded,(Predicate)EntitySelector.NO_SPECTATORS,(ArrayList)actual);
                        var expected=new ArrayList<>(entities);expected.remove(excluded);if(hits==0)expected.clear();
                        if(!actual.equals(expected))throw new AssertionError("Collected identity/order mismatch");
                        for(int i=0;i<64;i++)run(call,query,excluded,4096);
                        int accepted=0;
                        for(int attempt=0;attempt<16 && accepted<8;attempt++) {
                            long jit=compiler.getTotalCompilationTime(),allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                            long checksum=run(call,query,excluded,4096);
                            long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated,compilation=compiler.getTotalCompilationTime()-jit;
                            if(compilation==0)accepted++;
                            lines.add((compilation==0?"COLLECTION_TIMING ":"COLLECTION_REJECTED_JIT ")+sections+" "+perSection+" "+dynamic+" "+hits+" "+elapsed+" "+bytes+" "+checksum+" "+compilation);
                        }
                        if(accepted!=8)throw new AssertionError("Compiler did not settle within 16 attempts");
                    }
                }
            }
        }
        for(String line:lines)System.out.println(line);
        for(Class<?> type:List.of(LevelEntityGetter.class,LevelEntityGetterAdapter.class,EntitySectionStorage.class,net.minecraft.world.level.Level.class)) {
            var resource=type.getResource("/"+type.getName().replace('.','/')+".class");
            try(var stream=resource.openStream()){System.out.println("CLASS_IMAGE "+VerifySectionMembers.sha(stream.readAllBytes())+" "+resource);}
        }
        if(!mode.equals("frozen")) {
            var nativeFile=java.nio.file.Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so");
            System.out.println("NATIVE_IMAGE "+VerifySectionMembers.sha(java.nio.file.Files.readAllBytes(nativeFile)));
        }
    }
}
