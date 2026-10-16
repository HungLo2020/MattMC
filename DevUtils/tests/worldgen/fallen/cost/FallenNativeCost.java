package fallen;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Tagged requested native heap bytes/peak and actual deferred retirement.
 * Uses the original public operation helper. Profiling overhead is excluded
 * from latency evidence. The server keeps ticking during GC/Cleaner observation. */
public final class FallenNativeCost {
    record Retired(String key,List<Integer> tags,int cold,WeakReference<Object> programs,WeakReference<Object> chunk,boolean held){}
    static final List<Retired> RETIRED=new ArrayList<>();
    static volatile Object heldProgram;
    static Map<String,Object> one(ServerLevel level,String variant,String mode)throws Throwable{
        var configured=level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_"+variant+"_tree"));
        var leaf=new PlacedFeature(Holder.direct(configured),List.of());
        var placed=mode.equals("selector")?new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.RANDOM_BOOLEAN_SELECTOR,new RandomBooleanFeatureConfiguration(Holder.direct(leaf),Holder.direct(leaf)))),List.of()):leaf;
        var f=new FallenAudit.Fixture(level);var tags=new ArrayList<Integer>();
        for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        int bind=FallenAllocation.begin();tags.add(bind);
        try{if(FallenCost.NATIVE!=null&&(mode.equals("native")||mode.equals("selector")))f.nativeRegion();}
        finally{FallenAllocation.end(bind);}
        var random=new WorldgenRandom(new XoroshiroRandomSource(42));
        int cold=FallenAllocation.begin();tags.add(cold);boolean coldNative;
        long[] coldValues;
        try{coldNative=FallenCost.place(mode,f,configured,placed,random);}
        finally{coldValues=FallenAllocation.end(cold);}
        FallenCost.clear(f);
        for(int i=0;i<256;i++){random.setSeed(42+i);FallenCost.place(mode,f,configured,placed,random);FallenCost.clear(f);}
        var samples=new ArrayList<Map<String,Object>>();long checksum=0;int nativeCalls=0;
        for(int i=0;i<64;i++){
            random.setSeed(8191L+i);int tag=FallenAllocation.begin();tags.add(tag);boolean used;long[] values;
            try{used=FallenCost.place(mode,f,configured,placed,random);}
            finally{values=FallenAllocation.end(tag);}
            if(used)nativeCalls++;samples.add(FallenAllocation.sample(tag,values));
            checksum=FallenCost.fold(checksum,FallenCost.clear(f));checksum=FallenCost.fold(checksum,random.nextLong());
        }
        var snapshot=f.snapshot();String rng=FallenAudit.randomState(random);
        boolean held=FallenCost.NATIVE!=null&&variant.equals("oak")&&mode.equals("native");
        if(held)heldProgram=f.programs;
        RETIRED.add(new Retired(variant+"/"+mode,tags,cold,new WeakReference<>(f.programs),new WeakReference<>(f.centerChunk),held));
        f.close();var closed=new ArrayList<Map<String,Object>>();
        for(int tag:tags)closed.add(FallenAllocation.sample(tag,FallenAllocation.snapshot(tag)));
        var result=new LinkedHashMap<String,Object>(Map.of("variant",variant,"mode",mode,"bind",FallenAllocation.sample(bind,FallenAllocation.snapshot(bind)),"cold",FallenAllocation.sample(cold,coldValues),"coldNative",coldNative,"samples",samples,"afterExplicitRegionClose",closed,"checksum",checksum,"random",rng,"snapshot",snapshot));
        result.put("nativeCalls",nativeCalls);return result;
    }
    static List<Map<String,Object>> retirement()throws Throwable{
        var rows=new ArrayList<Map<String,Object>>();
        for(var r:RETIRED){
            long rustLive=0,allLive=0;var outstanding=new ArrayList<Map<String,Object>>();
            for(int tag:r.tags){var values=FallenAllocation.snapshot(tag);rustLive+=values[11];allLive+=values[4];if(values[4]!=0)outstanding.add(FallenAllocation.sample(tag,values));}
            rows.add(Map.of("key",r.key,"programsCollected",r.programs.get()==null,"chunkCollected",r.chunk.get()==null,"heldControl",r.held,"rustLiveRequestedBytes",rustLive,"allLiveRequestedBytes",allLive,"outstanding",outstanding));
        }
        return rows;
    }
    static boolean positivesRetired(List<Map<String,Object>> rows){return rows.stream().filter(r->!Boolean.TRUE.equals(r.get("heldControl"))).allMatch(r->Boolean.TRUE.equals(r.get("programsCollected"))&&Boolean.TRUE.equals(r.get("chunkCollected"))&&((Number)r.get("rustLiveRequestedBytes")).longValue()==0);}
    public static void run(MinecraftServer server){
        var output=Path.of(System.getProperty("audit.output"));
        try{
            var controls=FallenAllocation.control();var rows=new ArrayList<Map<String,Object>>();
            for(String variant:List.of("oak","birch","super_birch","jungle","spruce"))for(String mode:List.of("configured","placed","native","selector")){
                rows.add(one(server.overworld(),variant,mode));System.out.println("FALLEN_NATIVE_RESOURCE "+variant+" "+mode);
            }
            Files.writeString(output.resolve("allocations.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("scope","Tagged requested libc heap bytes; no Java heap, mmap/RSS or latency claim. Category1 is actual allocator return address in mattmc_rust; frees retain original tag across threads.","controls",controls,"cases",rows)));
            // A separate observer lets the real server ticks and Cleaner /
            // existing reclaimer run normally; no forced production drain.
            new Thread(()->{
                try{
                    List<Map<String,Object>> before=List.of();long started=System.nanoTime();
                    for(int i=0;i<120;i++){System.gc();Thread.sleep(250);before=retirement();if(positivesRetired(before))break;}
                    Files.writeString(output.resolve("retirement-held.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(before));
                    if(!positivesRetired(before))throw new AssertionError("Positive native resources did not retire");
                    if(FallenCost.NATIVE!=null){
                        var held=before.stream().filter(r->Boolean.TRUE.equals(r.get("heldControl"))).findFirst().orElseThrow();
                        if(Boolean.TRUE.equals(held.get("programsCollected")))throw new AssertionError("Retained program control disappeared");
                        if(FallenCost.SIDE.equals("current")&&((Number)held.get("rustLiveRequestedBytes")).longValue()==0)throw new AssertionError("Retained native program allocation was not observed");
                    }
                    heldProgram=null;List<Map<String,Object>> after=List.of();
                    for(int i=0;i<120;i++){System.gc();Thread.sleep(250);after=retirement();if(after.stream().allMatch(r->Boolean.TRUE.equals(r.get("programsCollected"))&&Boolean.TRUE.equals(r.get("chunkCollected"))&&((Number)r.get("rustLiveRequestedBytes")).longValue()==0))break;}
                    Files.writeString(output.resolve("retirement-released.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(after));
                    if(!after.stream().allMatch(r->Boolean.TRUE.equals(r.get("programsCollected"))&&Boolean.TRUE.equals(r.get("chunkCollected"))&&((Number)r.get("rustLiveRequestedBytes")).longValue()==0))throw new AssertionError("Released native program did not retire");
                    Files.writeString(output.resolve("complete.json"),new com.google.gson.Gson().toJson(Map.of("success",true,"cases",20,"retirementObservationNanos",System.nanoTime()-started)));
                }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}}
                finally{heldProgram=null;server.halt(false);}
            },"fallen-retirement-observer").start();
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}heldProgram=null;server.halt(false);}
    }
}
