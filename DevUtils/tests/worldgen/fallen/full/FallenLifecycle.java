package fallen;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Real family calls inside the existing FEATURES transaction, including a
 * write after the stump, strict physical rollback, deferred effects and retry. */
public final class FallenLifecycle {
    static final AtomicBoolean STARTED=new AtomicBoolean();
    public static void capture(Object value){if(STARTED.compareAndSet(false,true))((MinecraftServer)value).execute(()->run((MinecraftServer)value));}
    static void run(MinecraftServer server){
        try{
            var rows=new ArrayList<Map<String,Object>>();boolean java=Boolean.getBoolean("mattmc.worldgen.javaDecoration");
            var level=server.overworld();var registry=level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            for(String variant:List.of("oak","birch","super_birch","jungle","spruce"))for(int writes:List.of(1,3)){
                System.out.println("FALLEN_LIFECYCLE "+variant+" writes="+writes);
                try(var f=new FallenAudit.Fixture(level)){
                    for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
                    var feature=registry.getValue(net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_"+variant+"_tree"));
                    var placed=new PlacedFeature(Holder.direct(feature),List.of());
                    if(!java)f.nativeRegion();
                    var before=f.snapshot();var random=new WorldgenRandom(new XoroshiroRandomSource(42));int[] deferred={0};
                    var chunks=new ArrayList<ChunkAccess>(Arrays.asList(f.chunks));
                    var transaction=DecorationTransaction.begin(chunks,f.centerChunk,level.registryAccess());
                    Throwable failure=null;
                    try{
                        if(!DecorationTransaction.defer(()->deferred[0]++))throw new AssertionError("No actual transaction");
                        // Existing native injection counts both set_block and
                        // set_section_state. Fail at the same logical world
                        // write entry as Java's one-count-per-entry hook.
                        NativeDecoration.armTestFailure((java?"javafail:":"fail:")+(java?writes:2*writes-1));
                        if(java)placed.place(f.region,f.generator,random,new BlockPos(8,80,8));
                        else f.placeParent(placed,random,new BlockPos(8,80,8));
                    }catch(Throwable thrown){failure=thrown;}
                    finally{NativeDecoration.armTestFailure(java?"javafail:-1":"fail:-1");transaction.rollback();}
                    boolean injected=failure instanceof IllegalStateException&&(java?failure.getMessage().equals("Injected Java decoration failure"):
                        Set.of("Native decoration returned -3","Native fallen decorator returned -3").contains(failure.getMessage()));
                    if(!injected)throw new AssertionError("Expected actual family write failure",failure);
                    if(!before.equals(f.snapshot())||deferred[0]!=0||f.centerChunk.getPersistedStatus()!=net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS)throw new AssertionError("Partial family publication");
                    String prefix=FallenAudit.randomState(random);
                    if(!java){f.close();f.binding=null;f.programs=null;f.nativeRegion();}
                    random.setSeed(42);transaction=DecorationTransaction.begin(chunks,f.centerChunk,level.registryAccess());boolean placedResult;
                    try{
                        DecorationTransaction.defer(()->deferred[0]++);
                        placedResult=java?placed.place(f.region,f.generator,random,new BlockPos(8,80,8)):f.placeParent(placed,random,new BlockPos(8,80,8));
                        transaction.commit();
                    }catch(Throwable retryFailure){transaction.rollback();throw retryFailure;}
                    var after=f.snapshot();if(!placedResult||before.equals(after)||deferred[0]!=1)throw new AssertionError("Retry did not commit actual family work");
                    rows.add(Map.of("variant",variant,"writes",writes,"error",failure.toString(),"prefixRandom",prefix,"retryRandom",FallenAudit.randomState(random),"retrySnapshot",after,"rollbackExact",true,"deferredAfterRetry",deferred[0]));
                }
            }
            var output=Path.of(System.getProperty("audit.output"));Files.writeString(output.resolve("lifecycle.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":10}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of(System.getProperty("audit.output"),"failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}
