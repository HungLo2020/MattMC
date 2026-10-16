package fallen;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Actual compiled-chain ownership failures; requested native heap, never timing. */
public final class FallenChainAllocation {
    static String armed;
    static int hits;
    static final OutOfMemoryError FAILURE=new OutOfMemoryError("Chain adoption sentinel");
    public static void beforeAdoption(){if("adoption".equals(armed)){armed=null;hits++;throw FAILURE;}}
    public static void beforePublication(){if("publication".equals(armed)){armed=null;hits++;throw FAILURE;}}
    static boolean containsFailure(Throwable failure){
        for(int i=0;i<12&&failure!=null;i++,failure=failure.getCause())if(failure==FAILURE)return true;
        return false;
    }
    static Map<String,Object> one(MinecraftServer server,String boundary)throws Throwable{
        try(var fixture=new FallenAudit.Fixture(server.overworld())){
            fixture.nativeRegion();
            var attached=new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP));
            var configuration=FallenAudit.config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                List.of(TrunkVineDecorator.INSTANCE,attached),List.of(attached,TrunkVineDecorator.INSTANCE));
            var feature=new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.FALLEN_TREE,configuration)),List.of());
            Object before=FallenAudit.field(fixture.programs,"entries");
            int tag=FallenAllocation.begin();int previous=hits;Throwable caught=null;long[] values;
            armed=boundary;
            try{fixture.prepare(feature);}catch(Throwable failure){caught=failure;}
            finally{armed=null;values=FallenAllocation.end(tag);}
            if(caught==null||!containsFailure(caught)||hits!=previous+1)throw new AssertionError("Failure hook not reached exactly once",caught);
            if(FallenAudit.field(fixture.programs,"entries")!=before)throw new AssertionError("Failed program was published");
            if(values[8]==0)throw new AssertionError("No actual Rust program allocation observed");
            for(int i=0;i<60&&FallenAllocation.snapshot(tag)[11]!=0;i++){System.gc();Thread.sleep(50);}
            var after=FallenAllocation.snapshot(tag);
            fixture.prepare(feature); // The failed compile must remain retryable.
            var random=new WorldgenRandom(new XoroshiroRandomSource(42));
            if(!fixture.placeParent(feature,random,new BlockPos(8,80,8)))throw new AssertionError("Retry did not place");
            return Map.of("boundary",boundary,"injectedIdentityPreserved",true,"oldCacheUnchanged",true,
                "retryPlaced",true,"nativeAtFailure",FallenAllocation.sample(tag,values),
                "nativeAfterCollection",FallenAllocation.sample(tag,after),"allTaggedRustRetired",after[11]==0);
        }
    }
    static Map<String,Object> replacement(MinecraftServer server)throws Throwable{
        try(var fixture=new FallenAudit.Fixture(server.overworld())){
            fixture.nativeRegion();
            var feature=new java.util.concurrent.atomic.AtomicReference<PlacedFeature>();
            var previous=new java.util.concurrent.atomic.AtomicReference<java.lang.ref.WeakReference<Object>>();
            var observed=new ArrayList<Boolean>();
            int tag=FallenAllocation.begin();
            long[] allocated;
            var configuration=FallenAudit.config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                List.of(new TreeDecorator(){
                    protected TreeDecoratorType<?> type(){return TreeDecoratorType.TRUNK_VINE;}
                    public void place(TreeDecorator.Context context){
                        try{
                            Holder.TAG_GENERATION.incrementAndGet();
                            fixture.prepare(feature.get());
                            System.gc();Thread.sleep(50);
                            observed.add(previous.get().get()!=null&&FallenAllocation.snapshot(tag)[11]>0);
                        }catch(Throwable failure){throw FallenAudit.<RuntimeException>propagate(failure);}
                    }
                }),List.of(new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP))));
            feature.set(new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.FALLEN_TREE,configuration)),List.of()));
            try{fixture.prepare(feature.get());}finally{allocated=FallenAllocation.end(tag);}
            previous.set(new java.lang.ref.WeakReference<>(((Map<?,?>)FallenAudit.field(fixture.programs,"entries")).get(feature.get())));
            var random=new WorldgenRandom(new XoroshiroRandomSource(42));
            if(!fixture.placeParent(feature.get(),random,new BlockPos(8,80,8)))throw new AssertionError("Replaced in-flight program did not finish");
            if(!observed.equals(List.of(true)))throw new AssertionError("Active native program was not fenced across replacement");
            for(int i=0;i<60&&(previous.get().get()!=null||FallenAllocation.snapshot(tag)[11]!=0);i++){System.gc();Thread.sleep(50);}
            long[] after=FallenAllocation.snapshot(tag);
            if(previous.get().get()!=null)throw new AssertionError("Replaced Java program retained after return");
            return Map.of("boundary","replacement","inFlightOwnerStayedLive",true,"replacedOwnerCollected",true,
                "nativeAtFailure",FallenAllocation.sample(tag,allocated),"nativeAfterCollection",FallenAllocation.sample(tag,after),
                "allTaggedRustRetired",after[11]==0);
        }
    }
    public static void run(MinecraftServer server){
        var output=Path.of(System.getProperty("audit.output"));
        try{
            var controls=FallenAllocation.control();var rows=new ArrayList<Map<String,Object>>();
            for(String boundary:List.of("adoption","publication"))rows.add(one(server,boundary));
            rows.add(replacement(server));
            boolean passed=rows.stream().allMatch(row->Boolean.TRUE.equals(row.get("allTaggedRustRetired")));
            Files.writeString(output.resolve("allocation-boundaries.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("scope","Actual Program constructor/cache publication failures; requested tagged libc heap only, no latency or whole-process memory claim",
                    "controls",controls,"cases",rows,"passed",passed)));
            Files.writeString(output.resolve("complete.json"),new com.google.gson.Gson().toJson(Map.of("success",true,"cases",3,"ownershipPassed",passed)));
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}}
        finally{armed=null;server.halt(false);}
    }
}
