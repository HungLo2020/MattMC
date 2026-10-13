import java.lang.foreign.Arena;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Predicate;
import net.alexscaves.server.entity.util.ShakesScreen;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;

/** Includes collection and the original consumer's capability loop on both sides. */
public final class BenchmarkScreenShakeSources {
    static volatile long sink;
    static class Shake extends VerifySectionMembers.SpatialEntity implements ShakesScreen {
        @Override public float getScreenShakeAmount(float partial){return 0;}
    }
    static class Callback extends VerifySectionMembers.SpatialEntity {
        @Override public boolean isSpectator(){return false;}
    }
    static class CallbackShake extends Shake {
        @Override public boolean isSpectator(){return false;}
    }
    static MethodHandle collector(String mode) throws Exception {
        var args=MethodType.methodType(void.class,AABB.class,EntityAccess.class,ArrayList.class);
        if(mode.equals("current"))return MethodHandles.publicLookup().findVirtual(LevelEntityGetter.class,"collectScreenShakeSources",args);
        var old=args.insertParameterTypes(2,Predicate.class);
        var method=mode.equals("frozen")
            ?MethodHandles.lookup().findStatic(BenchmarkEntityCollection.class,"legacy",old.insertParameterTypes(0,LevelEntityGetter.class))
            :MethodHandles.publicLookup().findVirtual(LevelEntityGetter.class,"collectEntities",old);
        return MethodHandles.insertArguments(method,3,EntitySelector.NO_SPECTATORS);
    }
    static long consume(List<VerifySectionMembers.SpatialEntity> values) {
        long checksum=0;
        for(var entity:values)if(entity instanceof ShakesScreen)checksum+=entity.ordinal+1;
        return checksum;
    }
    static long run(MethodHandle collector,AABB query,EntityAccess excluded,int iterations) throws Throwable {
        long checksum=0;
        for(int i=0;i<iterations;i++) {
            var result=new ArrayList<VerifySectionMembers.SpatialEntity>();
            collector.invokeExact(query,excluded,(ArrayList)result);
            checksum+=consume(result);
        }
        sink=checksum;return checksum;
    }
    public static void main(String[] args) throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        String mode=args[0];var base=collector(mode);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var compiler=ManagementFactory.getCompilationMXBean();long thread=Thread.currentThread().threadId();
        var lines=new ArrayList<String>();
        try(var scope=Arena.ofConfined()) {
            for(int sections:new int[]{1,64,256})for(int perSection:new int[]{1,128}) {
                if(sections>1 && perSection>1)continue;
                for(int sources:new int[]{0,16,1})for(int callbacks:new int[]{0,16}) {
                    var storage=new EntitySectionStorage<VerifySectionMembers.SpatialEntity>(VerifySectionMembers.SpatialEntity.class,k->Visibility.TRACKED);
                    var getter=new LevelEntityGetterAdapter<>(new EntityLookup<VerifySectionMembers.SpatialEntity>(),storage);
                    var call=base.bindTo(getter);var entities=new ArrayList<VerifySectionMembers.SpatialEntity>();
                    for(int i=0;i<sections*perSection;i++) {
                        boolean shake=sources>0 && i%sources==0, callback=callbacks>0 && i%callbacks==0;
                        var entity=VerifySectionMembers.spatial(i,scope,shake?(callback?CallbackShake.class:Shake.class):(callback?Callback.class:VerifySectionMembers.SpatialEntity.class));
                        entity.setBoundingBox(new AABB(0,0,0,1,1,1));entities.add(entity);
                        storage.getOrCreateSection(SectionPos.asLong(i/perSection,0,0)).add(entity);
                    }
                    var query=new AABB(-1,-1,-1,sections*16,2,2);EntityAccess excluded=entities.get(entities.size()/2);
                    var actual=new ArrayList<VerifySectionMembers.SpatialEntity>();call.invokeExact(query,excluded,(ArrayList)actual);
                    var expected=new ArrayList<>(entities);expected.remove(excluded);
                    if(mode.equals("current"))expected.removeIf(e->!(e instanceof ShakesScreen));
                    if(!actual.equals(expected))throw new AssertionError("Source identities/order differ");
                    for(int i=0;i<64;i++)run(call,query,excluded,4096);
                    int accepted=0;
                    for(int attempt=0;attempt<16 && accepted<8;attempt++) {
                        long jit=compiler.getTotalCompilationTime(),allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                        long checksum=run(call,query,excluded,4096);
                        long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated,compilation=compiler.getTotalCompilationTime()-jit;
                        if(compilation==0)accepted++;
                        lines.add((compilation==0?"COLLECTION_TIMING ":"COLLECTION_REJECTED_JIT ")+sections+" "+perSection+" "+sources+" "+callbacks+" "+elapsed+" "+bytes+" "+checksum+" "+compilation);
                    }
                    if(accepted!=8)throw new AssertionError("Compiler did not settle within 16 attempts");
                }
            }
        }
        for(String line:lines)System.out.println(line);
        for(Class<?> type:List.of(LevelEntityGetter.class,LevelEntityGetterAdapter.class,EntitySectionStorage.class,net.minecraft.world.level.Level.class)) {
            var resource=type.getResource("/"+type.getName().replace('.','/')+".class");
            try(var stream=resource.openStream()){System.out.println("CLASS_IMAGE "+VerifySectionMembers.sha(stream.readAllBytes())+" "+resource);}
        }
        if(!mode.equals("frozen")) {
            var path=java.nio.file.Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so");
            var mapped=java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/maps")).stream().filter(s->s.contains("mattmc_rust") && s.contains(".so")).toList();
            if(mapped.isEmpty() || mapped.stream().anyMatch(s->!s.endsWith(path.toString())))throw new AssertionError("Native mapping differs");
            System.out.println("NATIVE_IMAGE "+VerifySectionMembers.sha(java.nio.file.Files.readAllBytes(path)));
        }
    }
}
