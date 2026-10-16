package fallen;
import java.lang.invoke.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Complete public feature calls on real preconditioned proto regions. Setup
 * restores the empty fixture between calls outside the operation interval.
 * Every call includes its real native/VM effects and final adoption. Allocation
 * is actual Java bytes on the operation thread; native allocation profiling is
 * a separate required diagnostic, never inferred from that Java measurement. */
public final class FallenCost {
    static final AtomicBoolean STARTED=new AtomicBoolean();
    static final BlockPos ORIGIN=new BlockPos(8,80,8);
    static final com.sun.management.ThreadMXBean THREADS=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static final MethodHandle NATIVE=nativeHandle();
    static final String SIDE=System.getProperty("fallen.cost.side");
    static MethodHandle nativeHandle(){
        if("frozen".equals(System.getProperty("fallen.cost.side")))return null;
        try{
            var owner=Class.forName("net.minecraft.world.level.levelgen.NativeDecoration");
            var programs=Class.forName(owner.getName()+"$Programs");
            var region=Class.forName("net.minecraft.world.level.chunk.NativeDecorationRegion");
            return MethodHandles.publicLookup().findStatic(owner,"placeWithRandom",MethodType.methodType(Boolean.class,programs,region,ChunkGenerator.class,WorldGenLevel.class,WorldgenRandom.class,PlacedFeature.class,BlockPos.class))
                .asType(MethodType.methodType(Boolean.class,Object.class,Object.class,ChunkGenerator.class,WorldGenLevel.class,WorldgenRandom.class,PlacedFeature.class,BlockPos.class));
        }catch(Throwable failure){throw new ExceptionInInitializerError(failure);}
    }
    public static void capture(Object value){if(STARTED.compareAndSet(false,true))((MinecraftServer)value).execute(()->run((MinecraftServer)value));}
    static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();}
    static long compiled(){return ManagementFactory.getCompilationMXBean().getTotalCompilationTime();}
    static long fold(long hash,long value){return(hash^value)*1099511628211L;}
    static long clear(FallenAudit.Fixture f){
        long hash=1469598103934665603L;var cursor=new BlockPos.MutableBlockPos();
        for(int y=80;y<=83;y++)for(int z=-16;z<32;z++)for(int x=-16;x<32;x++){
            cursor.set(x,y,z);var state=f.region.getBlockState(cursor);
            if(!state.isAir()){
                hash=fold(hash,cursor.asLong());hash=fold(hash,state.toString().hashCode());
                f.region.setBlock(cursor,Blocks.AIR.defaultBlockState(),19);
            }
        }
        return hash;
    }
    static boolean place(String mode,FallenAudit.Fixture f,ConfiguredFeature<?,?> configured,PlacedFeature placed,WorldgenRandom random)throws Throwable{
        if(mode.equals("configured")){if(!configured.place(f.region,f.generator,random,ORIGIN))throw new AssertionError("Configured result false");return false;}
        if(mode.equals("placed")||NATIVE==null){if(!placed.place(f.region,f.generator,random,ORIGIN))throw new AssertionError("Placed result false");return false;}
        Boolean result=(Boolean)NATIVE.invokeExact(f.programs,f.binding,f.generator,(WorldGenLevel)f.region,random,placed,ORIGIN);
        if(result==null){if(!placed.place(f.region,f.generator,random,ORIGIN))throw new AssertionError("Fallback result false");return false;}
        if(!result)throw new AssertionError("Native result false");return true;
    }
    static Map<String,Object> one(ServerLevel level,String variant,String mode)throws Throwable{
        var registry=level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        var configured=registry.getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_"+variant+"_tree"));
        var leaf=new PlacedFeature(Holder.direct(configured),List.of());
        var placed=mode.equals("selector")?new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.RANDOM_BOOLEAN_SELECTOR,new RandomBooleanFeatureConfiguration(Holder.direct(leaf),Holder.direct(leaf)))),List.of()):leaf;
        var f=new FallenAudit.Fixture(level);
        for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        long bindStart=System.nanoTime();
        if(NATIVE!=null&&(mode.equals("native")||mode.equals("selector")))f.nativeRegion();
        long bindNanos=System.nanoTime()-bindStart;
        var random=new WorldgenRandom(new XoroshiroRandomSource(42));
        int warm=Integer.getInteger("fallen.cost.warmups",256),groups=Integer.getInteger("fallen.cost.groups",12),batch=Integer.getInteger("fallen.cost.batch",32);
        long id=Thread.currentThread().threadId(),checksum=0;int nativeCalls=0;
        long coldAllocated=THREADS.getThreadAllocatedBytes(id),coldStart=System.nanoTime();
        boolean coldNative=place(mode,f,configured,placed,random);
        long coldNanos=System.nanoTime()-coldStart,coldBytes=THREADS.getThreadAllocatedBytes(id)-coldAllocated;
        clear(f);
        for(int i=0;i<warm;i++){random.setSeed(42+i);place(mode,f,configured,placed,random);clear(f);}
        var samples=new ArrayList<Map<String,Object>>();
        for(int group=0;group<groups;group++){
            long elapsed=0,cpu=0,bytes=0,gcBefore=gc(),jitBefore=compiled();
            for(int i=0;i<batch;i++){
                random.setSeed(8191L+group*batch+i);
                long allocated=THREADS.getThreadAllocatedBytes(id),cpuBefore=THREADS.getCurrentThreadCpuTime(),start=System.nanoTime();
                boolean used=place(mode,f,configured,placed,random);
                elapsed+=System.nanoTime()-start;cpu+=THREADS.getCurrentThreadCpuTime()-cpuBefore;bytes+=THREADS.getThreadAllocatedBytes(id)-allocated;
                if(used)nativeCalls++;
                checksum=fold(checksum,clear(f));checksum=fold(checksum,random.nextLong());
            }
            long gcDelta=gc()-gcBefore,jitDelta=compiled()-jitBefore;
            samples.add(Map.of("operations",batch,"nanos",elapsed,"threadCpuNanos",cpu,"javaBytes",bytes,"gc",gcDelta,"compilationMs",jitDelta,"quiet",gcDelta==0&&jitDelta==0));
        }
        var snapshot=f.snapshot();String rng=FallenAudit.randomState(random);long retirement=System.nanoTime();f.close();retirement=System.nanoTime()-retirement;
        return Map.of("variant",variant,"mode",mode,"samples",samples,"checksum",checksum,"random",rng,"snapshot",snapshot,"nativeCalls",nativeCalls,"bindingNanos",bindNanos,"regionRetirementNanos",retirement,"cold",Map.of("nanos",coldNanos,"javaBytes",coldBytes,"native",coldNative));
    }
    static void run(MinecraftServer server){
        try{
            THREADS.setThreadAllocatedMemoryEnabled(true);var rows=new ArrayList<Map<String,Object>>();
            for(String variant:List.of("oak","birch","super_birch","jungle","spruce"))for(String mode:List.of("configured","placed","native","selector")){
                rows.add(one(server.overworld(),variant,mode));System.out.println("FALLEN_COST "+variant+" "+mode);
            }
            var output=Path.of(System.getProperty("audit.output"));Files.writeString(output.resolve("costs.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":20}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of(System.getProperty("audit.output"),"failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}
