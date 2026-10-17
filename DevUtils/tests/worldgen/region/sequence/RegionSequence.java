package fallen;

import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FallenTreeConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.util.valueproviders.ConstantInt;

/** The actual production applyBiomeDecoration loop, with three real fallen-tree
 * programs. Fixture generation settings only select the work; no loop or
 * feature algorithm is reproduced. All binding/adoption/retirement is timed. */
public final class RegionSequence {
    static final boolean RESOURCES=Boolean.getBoolean("region.sequence.resources");
    record Retired(List<Integer> tags,java.lang.ref.WeakReference<Object> generator,java.lang.ref.WeakReference<Object> chunk){}
    static final List<Retired> RETIRED=new ArrayList<>();
    static final com.sun.management.ThreadMXBean THREADS=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static String statistics(){
        try{return (String)Class.forName("net.minecraft.world.level.levelgen.NativeDecoration").getMethod("statistics").invoke(null);}
        catch(ClassNotFoundException frozen){return "frozen";}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    static long counter(String stats,String key){
        var m=java.util.regex.Pattern.compile("(?:^| )"+key+"=(\\d+)").matcher(stats);
        return m.find()?Long.parseLong(m.group(1)):-1;
    }
    static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();}
    static long jit(){return ManagementFactory.getCompilationMXBean().getTotalCompilationTime();}
    static Map<String,Object> one(ServerLevel level,boolean callbacks)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
            Heightmap.primeHeightmaps(f.centerChunk,EnumSet.allOf(Heightmap.Types.class));
            var registry=level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            var oak=registry.getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_oak_tree"));
            var birch=registry.getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_birch_tree"));
            var spruce=registry.getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_spruce_tree"));
            long[] callbackState={0,0};
            if(callbacks){
                var original=(FallenTreeConfiguration)birch.config();
                var effect=new TreeDecorator(){
                    protected TreeDecoratorType<?> type(){return TreeDecoratorType.TRUNK_VINE;}
                    public void place(TreeDecorator.Context context){
                        callbackState[0]++;
                        callbackState[1]=FallenCost.fold(callbackState[1],f.region.getBlockState(new BlockPos(0,80,0)).toString().hashCode());
                        callbackState[1]=FallenCost.fold(callbackState[1],Double.doubleToRawLongBits(context.random().nextGaussian()));
                        // Mutate the actual escaped metadata array. A resumed
                        // native consumer must reconcile that same VM state.
                        f.region.setBlock(new BlockPos(4,80,4),Blocks.STONE.defaultBlockState(),3);
                        var map=f.centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
                        new net.minecraft.util.SimpleBitStorage(net.minecraft.util.Mth.ceillog2(f.region.getHeight()+1),256,map.getRawData())
                            .set(4+4*16,81-f.region.getMinY());
                    }
                };
                birch=new ConfiguredFeature<>(Feature.FALLEN_TREE,new FallenTreeConfiguration.FallenTreeConfigurationBuilder(original.trunkProvider,original.logLength)
                    .stumpDecorators(List.of(effect)).logDecorators(original.logDecorators).build());
            }
            var first=new PlacedFeature(Holder.direct(oak),List.of(HeightRangePlacement.uniform(VerticalAnchor.absolute(80),VerticalAnchor.absolute(80))));
            var second=new PlacedFeature(Holder.direct(birch),List.of(RandomOffsetPlacement.of(ConstantInt.of(8),ConstantInt.of(0)),HeightRangePlacement.uniform(VerticalAnchor.absolute(80),VerticalAnchor.absolute(80))));
            var third=new PlacedFeature(Holder.direct(spruce),List.of(RandomOffsetPlacement.of(ConstantInt.of(4),ConstantInt.of(0)),
                HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG)));
            var settings=new BiomeGenerationSettings.PlainBuilder().addFeature(0,Holder.direct(first)).addFeature(0,Holder.direct(second))
                .addFeature(0,Holder.direct(third)).build();
            var plains=level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            var flat=new FlatLevelGeneratorSettings(Optional.empty(),plains,List.of()){
                @Override public BiomeGenerationSettings adjustGenerationSettings(Holder<Biome> biome){return settings;}
            };
            var generator=new FlatLevelSource(flat);
            var structures=level.structureManager().forWorldGenRegion(f.region);
            int warm=Integer.getInteger("region.sequence.warmups",32),groups=Integer.getInteger("region.sequence.groups",8),batch=Integer.getInteger("region.sequence.batch",8);
            long id=Thread.currentThread().threadId(),checksum=0;
            int coldTag=RESOURCES?FallenAllocation.begin():-1;long[] cold=null;
            try{generator.applyBiomeDecoration(f.region,f.centerChunk,structures);}
            finally{if(RESOURCES)cold=FallenAllocation.end(coldTag);}
            FallenCost.clear(f);
            for(int i=0;i<warm;i++){generator.applyBiomeDecoration(f.region,f.centerChunk,structures);FallenCost.clear(f);}
            callbackState[0]=0;callbackState[1]=0;
            String before=statistics();var samples=new ArrayList<Map<String,Object>>();
            var allocationRows=new ArrayList<Map<String,Object>>();var tags=new ArrayList<Integer>();
            if(RESOURCES)tags.add(coldTag);
            for(int group=0;group<groups;group++){
                long nanos=0,cpu=0,bytes=0,g0=gc(),j0=jit();
                for(int i=0;i<batch;i++){
                    long allocated=THREADS.getThreadAllocatedBytes(id),cpu0=THREADS.getCurrentThreadCpuTime(),t0=System.nanoTime();
                    int tag=RESOURCES?FallenAllocation.begin():-1;
                    try{generator.applyBiomeDecoration(f.region,f.centerChunk,structures);}
                    finally{if(RESOURCES){allocationRows.add(FallenAllocation.sample(tag,FallenAllocation.end(tag)));tags.add(tag);}}
                    nanos+=System.nanoTime()-t0;cpu+=THREADS.getCurrentThreadCpuTime()-cpu0;bytes+=THREADS.getThreadAllocatedBytes(id)-allocated;
                    checksum=FallenCost.fold(checksum,FallenCost.clear(f));
                }
                long gd=gc()-g0,jd=jit()-j0;
                samples.add(Map.of("operations",batch,"nanos",nanos,"threadCpuNanos",cpu,"javaBytes",bytes,"gc",gd,"compilationMs",jd,"quiet",gd==0&&jd==0));
            }
            String after=statistics();long sequenced=counter(after,"sequenced")-counter(before,"sequenced");
            long nativeFeatures=counter(after,"nativePlacements")-counter(before,"nativePlacements");
            var result=new LinkedHashMap<String,Object>();result.put("name",callbacks?"callback-triple":"native-triple");result.put("samples",samples);
            result.put("checksum",checksum);result.put("callbacks",List.of(callbackState[0],callbackState[1]));result.put("snapshot",f.snapshot());
            if(RESOURCES){
                result.put("allocationCold",FallenAllocation.sample(coldTag,cold));result.put("allocationSamples",allocationRows);
                RETIRED.add(new Retired(tags,new java.lang.ref.WeakReference<>(generator),new java.lang.ref.WeakReference<>(f.centerChunk)));
            }
            result.put("sequenced",sequenced);result.put("nativeFeatures",nativeFeatures);result.put("statisticsBefore",before);result.put("statisticsAfter",after);
            if(!before.equals("frozen")&&sequenced!=groups*batch)throw new AssertionError("Production native sequence was not used on every measured operation: "+sequenced);
            if(!before.equals("frozen")&&nativeFeatures!=3L*groups*batch)throw new AssertionError("Expected three actual native features per operation: "+nativeFeatures);
            if(callbacks&&callbackState[0]!=groups*batch)throw new AssertionError("Expected one actual callback per operation");
            return result;
        }
    }
    public static void run(MinecraftServer server){
        var output=Path.of(System.getProperty("audit.output"));
        try{
            THREADS.setThreadAllocatedMemoryEnabled(true);var rows=new ArrayList<Map<String,Object>>();
            if(RESOURCES)Files.writeString(output.resolve("allocator-controls.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(FallenAllocation.control()));
            for(boolean callbacks:List.of(false,true)){rows.add(one(server.overworld(),callbacks));System.out.println("REGION_SEQUENCE "+callbacks);}
            Files.writeString(output.resolve("sequence.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
            if(RESOURCES){
                var retired=new ArrayList<Map<String,Object>>();
                for(var owner:RETIRED){
                    for(int i=0;i<100&&(owner.generator.get()!=null||owner.chunk.get()!=null);i++){System.gc();Thread.sleep(50);}
                    long live=0;for(int tag:owner.tags)live+=FallenAllocation.snapshot(tag)[11];
                    if(owner.generator.get()!=null||owner.chunk.get()!=null||live!=0)throw new AssertionError("Sequence native resources did not retire: "+live);
                    retired.add(Map.of("generatorCollected",true,"chunkCollected",true,"taggedRustLiveRequestedBytes",live));
                }
                Files.writeString(output.resolve("retirement.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(retired));
            }
            Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":2}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}
