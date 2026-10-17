package fallen;

import java.lang.foreign.MemorySegment;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Actual owner adoption/replay/retirement and FEATURES rollback controls.
 * Instrumented requested-heap observations are never acceptance timings. */
public final class RegionOwnership {
    static final OutOfMemoryError FAILURE=new OutOfMemoryError("Region ownership sentinel");
    static String armed;
    static int effectCountdown;
    static boolean observingEffects;
    static boolean failReleaseArgument;
    static int releaseArgumentFailures;
    static boolean failAckArgument;
    static int ackArgumentFailures;
    public static void ackArgument(){if(failAckArgument){failAckArgument=false;ackArgumentFailures++;throw FAILURE;}}
    public static void releaseArgument(){if(failReleaseArgument){failReleaseArgument=false;releaseArgumentFailures++;throw FAILURE;}}
    static final List<String> effects=new ArrayList<>();
    public static void boundary(String point){
        if(point.equals(armed)){armed=null;throw FAILURE;}
    }
    public static void beforeEffect(long kind,long x,long y,long z,long argument){
        if(effectCountdown>0&&--effectCountdown==0)throw FAILURE;
    }
    public static void afterEffect(long kind,long x,long y,long z,long argument){if(observingEffects)effects.add(kind+":"+x+","+y+","+z+":"+argument);}
    static boolean sentinel(Throwable failure){
        for(int i=0;i<16&&failure!=null;i++,failure=failure.getCause())if(failure==FAILURE)return true;
        return false;
    }
    static void ground(FallenAudit.Fixture f){
        for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        Heightmap.primeHeightmaps(f.centerChunk,EnumSet.allOf(Heightmap.Types.class));
    }
    static TreeDecorator effect(java.util.function.Consumer<TreeDecorator.Context> action){
        return new TreeDecorator(){
            protected TreeDecoratorType<?> type(){return TreeDecoratorType.TRUNK_VINE;}
            public void place(TreeDecorator.Context context){action.accept(context);}
        };
    }
    static PlacedFeature feature(List<TreeDecorator> stump,List<TreeDecorator> logs){
        return new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.FALLEN_TREE,
            FallenAudit.config(BlockStateProvider.simple(Blocks.CHEST),8,stump,logs))),List.of());
    }
    static NativeDecorationRegion direct(FallenAudit.Fixture f){
        var binding=NativeDecorationRegion.bind(f.region,f.centerChunk,new WorldGenerationContext(f.generator,f.region),true);
        if(binding==null)throw new AssertionError("Canonical region declined");return binding;
    }
    static void collected(int tag)throws Throwable{
        for(int i=0;i<100&&FallenAllocation.snapshot(tag)[11]!=0;i++){System.gc();Thread.sleep(25);}
        if(FallenAllocation.snapshot(tag)[11]!=0)throw new AssertionError("Tagged Rust owner retained: "+Arrays.toString(FallenAllocation.snapshot(tag)));
    }
    static Map<String,Object> adoption(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            try(var warm=direct(f)){}
            int tag=FallenAllocation.begin();Throwable caught=null;long[] at;
            armed="adoption";
            try{direct(f);}catch(Throwable failure){caught=failure;}
            finally{armed=null;at=FallenAllocation.end(tag);}
            if(!sentinel(caught)||at[8]==0)throw new AssertionError("Actual owner adoption failure was not observed",caught);
            collected(tag);
            try(var retry=direct(f)){if(retry.refresh(true,true)!=0)throw new AssertionError("Adoption retry declined");}
            return Map.of("case","adoption","sameSentinel",true,"nativeAtFailure",FallenAllocation.sample(tag,at),
                "nativeAfterCollection",FallenAllocation.sample(tag,FallenAllocation.snapshot(tag)),"retry",true);
        }
    }
    static Map<String,Object> replay(ServerLevel level,String point)throws Throwable{
        Map<String,Object> expected;List<String> trace;
        var feature=feature(List.of(),List.of());
        try(var f=new FallenAudit.Fixture(level)){
            ground(f);f.prepare(feature);effects.clear();observingEffects=true;
            if(!f.placeParent(feature,new WorldgenRandom(new XoroshiroRandomSource(42)),new BlockPos(8,80,8)))throw new AssertionError("Control did not place");
            expected=f.snapshot();trace=List.copyOf(effects);observingEffects=false;
        }
        if(trace.size()<2)throw new AssertionError("No real effect prefix");
        try(var f=new FallenAudit.Fixture(level)){
            ground(f);f.prepare(feature);effects.clear();observingEffects=true;armed=point;effectCountdown=point.equals("effect")?2:0;Throwable caught=null;
            var random=new WorldgenRandom(new XoroshiroRandomSource(42));
            try{f.placeParent(feature,random,new BlockPos(8,80,8));}catch(Throwable failure){caught=failure;}
            finally{armed=null;effectCountdown=0;}
            if(!sentinel(caught))throw new AssertionError("Expected actual "+point+" failure",caught);
            int completed=effects.size();
            if(point.equals("effect")&&completed!=1)throw new AssertionError("Missing successful replay prefix");
            var binding=(NativeDecorationRegion)f.binding;
            // A subsequent public exposure must reconcile unfinished adoption
            // before it can overwrite the working metadata or run new work.
            if(binding.refresh(true,true)!=0)throw new AssertionError("Retry refresh declined");
            if(!trace.equals(effects)||!expected.equals(f.snapshot()))throw new AssertionError("Lost/duplicated projection or effect after "+point+" completed="+completed+" trace="+effects);
            observingEffects=false;
            return Map.of("case",point,"completedBeforeFailure",completed,"effects",trace.size(),"orderedRetryExact",true,"snapshotExact",true);
        }
    }
    static Map<String,Object> rollback(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            ground(f);var originals=f.centerChunk.getSections().clone();var before=f.snapshot();boolean[] fail={true};int[] deferred={0};
            var replaced=effect(context->{
                f.centerChunk.getSections()[f.centerChunk.getSectionIndex(80)]=new LevelChunkSection(PalettedContainerFactory.create(level.registryAccess()));
            });
            var failure=effect(context->{if(fail[0])throw FAILURE;});
            var feature=feature(List.of(replaced),List.of(failure));f.prepare(feature);
            var chunks=new ArrayList<ChunkAccess>(Arrays.asList(f.chunks));
            var tx=DecorationTransaction.begin(chunks,f.centerChunk,level.registryAccess());Throwable caught=null;
            try{
                DecorationTransaction.defer(()->deferred[0]++);
                f.placeParent(feature,new WorldgenRandom(new XoroshiroRandomSource(42)),new BlockPos(8,80,8));
            }catch(Throwable thrown){caught=thrown;}finally{tx.rollback();}
            if(!sentinel(caught)||deferred[0]!=0||!before.equals(f.snapshot())||!Arrays.equals(originals,f.centerChunk.getSections()))throw new AssertionError("Replacement rollback was not exact",caught);
            f.close();f.binding=null;f.programs=null;fail[0]=false;f.prepare(feature);
            tx=DecorationTransaction.begin(chunks,f.centerChunk,level.registryAccess());
            try{
                DecorationTransaction.defer(()->deferred[0]++);
                if(!f.placeParent(feature,new WorldgenRandom(new XoroshiroRandomSource(42)),new BlockPos(8,80,8)))throw new AssertionError("Retry did not place");
                tx.commit();
            }catch(Throwable thrown){tx.rollback();throw thrown;}
            if(deferred[0]!=1)throw new AssertionError("Deferred effects did not commit once");
            return Map.of("case","replacement-rollback","originalSectionsRestored",true,"snapshotExact",true,"deferredDiscarded",true,"retryCommitted",true);
        }
    }
    static Map<String,Object> closeInCallback(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            ground(f);boolean[] rejected={false};
            var effect=effect(context->{
                try{((NativeDecorationRegion)f.binding).close();}
                catch(IllegalStateException expected){rejected[0]=true;}
            });
            var feature=feature(List.of(effect),List.of());f.prepare(feature);
            if(!f.placeParent(feature,new WorldgenRandom(new XoroshiroRandomSource(42)),new BlockPos(8,80,8))||!rejected[0])throw new AssertionError("Active downcall close was not rejected safely");
            return Map.of("case","close-in-callback","closeRejectedWhileBorrowed",true,"placementFinished",true);
        }
    }
    record Retained(int tag,WeakReference<NativeDecorationRegion> region,AtomicReference<MemorySegment> projection,long[] initial){}
    static Retained abandon(FallenAudit.Fixture f)throws Throwable{return abandon(f,false);}
    static Retained abandon(FallenAudit.Fixture f,boolean opaque)throws Throwable{
        int tag=FallenAllocation.begin();var binding=direct(f);var at=FallenAllocation.end(tag);
        MemorySegment projection=binding.words();
        if(opaque){
            var method=NativeDecorationRegion.class.getDeclaredMethod("owner");method.setAccessible(true);
            projection=(MemorySegment)method.invoke(binding);
        }
        return new Retained(tag,new WeakReference<>(binding),new AtomicReference<>(projection),at);
    }
    static Map<String,Object> lifetime(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            try(var warm=direct(f)){}
            int closeTag=FallenAllocation.begin();var binding=direct(f);var held=FallenAllocation.end(closeTag);
            if(held[11]<=0)throw new AssertionError("No actual owner retained");binding.close();collected(closeTag);
            var retained=abandon(f);
            for(int i=0;i<100&&retained.region.get()!=null;i++){System.gc();Thread.sleep(25);}
            if(retained.region.get()!=null||FallenAllocation.snapshot(retained.tag)[11]<=0)throw new AssertionError("Projection did not retain abandoned native owner");
            if(retained.projection.get().getAtIndex(java.lang.foreign.ValueLayout.JAVA_LONG,0)!=2)throw new AssertionError("Retained projection became invalid");
            retained.projection.set(null);collected(retained.tag);
            return Map.of("case","lifetime","explicitCloseRetired",true,"regionCollectedBeforeProjection",true,
                "projectionKeptOwnerLive",true,"lastProjectionCollected",true,"nativeHeld",FallenAllocation.sample(retained.tag,retained.initial),
                "nativeAfterCollection",FallenAllocation.sample(retained.tag,FallenAllocation.snapshot(retained.tag)));
        }
    }
    static Map<String,Object> opaqueLifetime(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            try(var warm=direct(f)){}
            var retained=abandon(f,true);
            for(int i=0;i<100&&retained.region.get()!=null;i++){System.gc();Thread.sleep(25);}
            if(retained.region.get()!=null)throw new AssertionError("Abandoned region remained reachable");
            // Let actual Cleaner work settle while retaining only the exact
            // opaque MemorySegment supplied to owner API downcalls. No native
            // dereference of a possibly freed handle occurs in this control.
            for(int i=0;i<20;i++){
                System.gc();Thread.sleep(50);
                if(FallenAllocation.snapshot(retained.tag)[11]==0)throw new AssertionError("Opaque native handle did not retain owner");
            }
            retained.projection.set(null);collected(retained.tag);
            return Map.of("case","opaque-handle-lifetime","regionCollectedFirst",true,"opaqueHandleKeptOwnerLive",true,"lastHandleRetired",true);
        }
    }
    static Map<String,Object> releaseArgumentFailure(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            try(var warm=direct(f)){}
            int tag=FallenAllocation.begin();var binding=direct(f);var held=FallenAllocation.end(tag);
            failReleaseArgument=true;Throwable caught=null;int before=releaseArgumentFailures;
            try{binding.close();}catch(Throwable failure){caught=failure;}finally{failReleaseArgument=false;}
            if(caught!=null&&!sentinel(caught))throw new AssertionError("Wrong release argument failure",caught);
            collected(tag);
            if(releaseArgumentFailures!=before)throw new AssertionError("Release cleanup still constructs a segment argument");
            return Map.of("case","release-argument-allocation","actualAllocationsRemoved",releaseArgumentFailures==before,
                "sameSentinelIfAllocationAttempted",caught==null||sentinel(caught),"taggedRustRetired",true,"nativeHeld",FallenAllocation.sample(tag,held));
        }
    }
    static Map<String,Object> ackArgumentFailure(ServerLevel level)throws Throwable{
        try(var f=new FallenAudit.Fixture(level)){
            ground(f);var feature=feature(List.of(),List.of());f.prepare(feature);
            // Initialize the genuine deferred child program/cache outside the
            // cleanup gate; its independent lifetime is not this batch's.
            f.placeParent(feature,new WorldgenRandom(new XoroshiroRandomSource(42)),new BlockPos(8,80,8));
            for(int y=80;y<=83;y++)for(int z=-16;z<32;z++)for(int x=-16;x<32;x++){
                var pos=new BlockPos(x,y,z);if(!f.region.getBlockState(pos).isAir())f.region.setBlock(pos,Blocks.AIR.defaultBlockState(),19);
            }
            int tag=FallenAllocation.begin();Throwable caught=null;long[] at;int before=ackArgumentFailures;failAckArgument=true;
            try{f.placeParent(feature,new WorldgenRandom(new XoroshiroRandomSource(42)),new BlockPos(8,80,8));}
            catch(Throwable failure){caught=failure;}
            finally{failAckArgument=false;at=FallenAllocation.end(tag);}
            if(caught!=null&&!sentinel(caught))throw new AssertionError("Wrong acknowledgement argument failure",caught);
            f.close();f.binding=null;f.programs=null;collected(tag);
            if(ackArgumentFailures!=before)throw new AssertionError("Acknowledgement cleanup still constructs a segment argument");
            return Map.of("case","ack-argument-allocation","actualAllocationsRemoved",ackArgumentFailures==before,
                "sameSentinelIfAllocationAttempted",caught==null||sentinel(caught),"taggedRustRetired",true,"nativeAtReturn",FallenAllocation.sample(tag,at));
        }
    }
    static void record(List<Map<String,Object>> rows,Map<String,Object> row)throws Exception{
        rows.add(row);System.out.println("REGION_OWNERSHIP "+row.get("case"));
        Files.writeString(Path.of(System.getProperty("audit.output"),"completed-cases.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }
    public static void run(MinecraftServer server){
        var output=Path.of(System.getProperty("audit.output"));
        try{
            var controls=FallenAllocation.control();
            // Initialize global facts/program machinery outside ownership tags.
            try(var warm=new FallenAudit.Fixture(server.overworld())){warm.nativeRegion();}
            var rows=new ArrayList<Map<String,Object>>();
            record(rows,adoption(server.overworld()));
            for(String point:List.of("projection","claim","effect"))record(rows,replay(server.overworld(),point));
            record(rows,rollback(server.overworld()));record(rows,closeInCallback(server.overworld()));record(rows,lifetime(server.overworld()));record(rows,opaqueLifetime(server.overworld()));record(rows,ackArgumentFailure(server.overworld()));record(rows,releaseArgumentFailure(server.overworld()));
            Files.writeString(output.resolve("ownership.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("controls",controls,"cases",rows,"passed",true)));
            Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":10}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}}
        finally{armed=null;effectCountdown=0;observingEffects=false;server.halt(false);}
    }
}
