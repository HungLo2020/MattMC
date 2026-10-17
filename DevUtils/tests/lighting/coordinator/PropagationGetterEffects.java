package net.minecraft.server.level;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.*;
import net.minecraft.world.level.material.FluidState;
public final class PropagationGetterEffects {
 static final class Getter implements LightChunkGetter {
  final VerifyThreadedLighting.World world;final Map<Long,LightChunk> wrappers=new HashMap<>();final List<String> calls=new ArrayList<>();String mode;boolean active,once;BlockLightEngine block;
  Getter(VerifyThreadedLighting.World world){this.world=world;for(var entry:world.chunks.entrySet()){var chunk=entry.getValue();wrappers.put(entry.getKey(),new LightChunk(){
   public BlockState getBlockState(BlockPos pos){if(active){calls.add("state:"+pos.getX()+":"+pos.getY()+":"+pos.getZ());observe("state");}return chunk.getBlockState(pos);}
   public BlockEntity getBlockEntity(BlockPos pos){return chunk.getBlockEntity(pos);}public FluidState getFluidState(BlockPos pos){return chunk.getFluidState(pos);}public int getMinY(){return chunk.getMinY();}public int getHeight(){return chunk.getHeight();}public void findBlockLightSources(BiConsumer<BlockPos,BlockState> consumer){chunk.findBlockLightSources(consumer);}public ChunkSkyLightSources getSkyLightSources(){return chunk.getSkyLightSources();}
  });}}
  void observe(String kind){if(mode.equals("checked-"+kind))throw VerifyBlockInputEffects.sneaky(VerifyBlockInputEffects.MARKER);if(!once&&mode.equals("nested-"+kind)){once=true;calls.add("nested-start");try{VerifyBlockInputEffects.invoke(block,BlockLightEngine.class,"checkNode",new Class<?>[]{long.class},BlockPos.asLong(10,24,10));}catch(Throwable failure){throw VerifyBlockInputEffects.sneaky(failure);}calls.add("nested-end");}}
  public LightChunk getChunkForLighting(int x,int z){if(active){calls.add("lookup:"+x+":"+z);observe("lookup");}return wrappers.get(ChunkPos.asLong(x,z));}
  public BlockGetter getLevel(){return world;}public void onLightUpdate(LightLayer layer,SectionPos section){world.onLightUpdate(layer,section);}
 }
 static void run(String path,String mode)throws Throwable {
  try(var fixture=new VerifyThreadedLighting.Fixture(false,false)){
   var getter=new Getter(fixture.world);getter.mode=mode;
   LevelLightEngine level=path.equals("threaded")?new ThreadedLevelLightEngine(getter,(ChunkMap)VerifyThreadedLighting.field(fixture.engine,"chunkMap"),false,fixture.executor,fixture.dispatcher):new LevelLightEngine(getter,true,false);
   if(level instanceof ThreadedLevelLightEngine threaded){for(var chunk:fixture.world.chunks.values())threaded.initializeLight(chunk,false);fixture.transport();VerifyBlockInputEffects.threadedUpdate(threaded);}else{for(var chunk:fixture.world.chunks.values())for(int y=0;y<4;y++)if(!chunk.getSection(y).hasOnlyAir())level.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);level.runLightUpdates();}
   for(var chunk:fixture.world.chunks.values())level.setLightEnabled(chunk.getPos(),true);if(level instanceof ThreadedLevelLightEngine threaded){fixture.transport();VerifyBlockInputEffects.threadedUpdate(threaded);}
   getter.block=(BlockLightEngine)level.getLayerListener(LightLayer.BLOCK);VerifyBlockInputEffects.invoke(getter.block,BlockLightEngine.class,"checkNode",new Class<?>[]{long.class},BlockPos.asLong(8,24,8));if(!getter.block.hasLightWork())throw new AssertionError("Actual propagation queue required");getter.active=true;Throwable failure=null;String work="unreported-threaded";
   try{if(path.equals("engine"))work=""+getter.block.runLightUpdates();else if(level instanceof ThreadedLevelLightEngine threaded){fixture.transport();VerifyBlockInputEffects.threadedUpdate(threaded);}else work=""+level.runLightUpdates();}catch(Throwable actual){failure=actual;}getter.active=false;
   if(getter.calls.isEmpty())throw new AssertionError("Actual contextual propagation calls missing");if(mode.startsWith("checked-")&&failure!=VerifyBlockInputEffects.MARKER)throw new AssertionError("Original checked Throwable changed",failure);if(!mode.startsWith("checked-")&&failure!=null)throw failure;
   Object storage=VerifyThreadedLighting.field(getter.block,"storage");var map=(DataLayerStorageMap<?>)VerifyThreadedLighting.field(storage,"updatingSectionData");var state=new StringBuilder();for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)for(int y=0;y<4;y++){var data=map.getLayer(SectionPos.asLong(x,y,z));state.append(x).append(':').append(y).append(':').append(z).append('=');if(data==null)state.append("null");else for(int cell=0;cell<4096;cell++)state.append(Integer.toHexString(data.get(cell&15,cell>>8,cell>>4&15)));state.append(';');}
   var utf=java.nio.charset.StandardCharsets.UTF_8;System.out.println("PROPAGATION_GETTER path="+path+" mode="+mode+" same-marker="+(failure==VerifyBlockInputEffects.MARKER)+" work="+work+" pending="+getter.block.hasLightWork()+" calls="+getter.calls.size()+" callsha="+VerifyThreadedLighting.hash(String.join(",",getter.calls).getBytes(utf))+" layersha="+VerifyThreadedLighting.hash(state.toString().getBytes(utf))+" prefix="+getter.calls.subList(0,Math.min(16,getter.calls.size())));
  }
 }
 public static void main(String[] args)throws Throwable{VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(String path:List.of("engine","level","threaded"))for(String mode:List.of("value","checked-state","nested-state","checked-lookup","nested-lookup"))run(path,mode);}
}
