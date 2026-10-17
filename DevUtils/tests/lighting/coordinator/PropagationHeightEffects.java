package net.minecraft.server.level;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.*;
import net.minecraft.world.ticks.ProtoChunkTicks;
/** Original propagation with actual effectful height provider, not check-node admission. */
public final class PropagationHeightEffects {
 static final class Height implements LevelHeightAccessor {
  final List<String> calls=new ArrayList<>();boolean active,once,reject;Runnable nested;
  void observe(String name){if(!active)return;calls.add(name);if(!once){once=true;Runnable action=nested;nested=null;if(action!=null)action.run();}if(reject)throw VerifyBlockInputEffects.sneaky(VerifyBlockInputEffects.MARKER);}
  public int getMinY(){observe("min");return 0;}public int getHeight(){observe("height");return 64;}
 }
 static void run(String mode)throws Throwable {
  try(var fixture=new VerifyThreadedLighting.Fixture(false,false)){
   var level=new LevelLightEngine(fixture.world,true,false);
   for(var chunk:fixture.world.chunks.values())for(int y=0;y<4;y++)if(!chunk.getSection(y).hasOnlyAir())level.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
   level.runLightUpdates();for(var chunk:fixture.world.chunks.values())level.setLightEnabled(chunk.getPos(),true);
   var old=fixture.world.chunks.get(0L);var height=new Height();
   var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,old.getSections(),new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),height,null,null);fixture.world.chunks.put(0L,chunk);
   var block=(BlockLightEngine)level.getLayerListener(LightLayer.BLOCK);
   VerifyBlockInputEffects.invoke(block,BlockLightEngine.class,"checkNode",new Class<?>[]{long.class},BlockPos.asLong(8,24,8));
   if(!block.hasLightWork())throw new AssertionError("Actual propagation work required");
   height.reject=mode.equals("checked");if(mode.equals("nested"))height.nested=()->{height.calls.add("nested-start");try{VerifyBlockInputEffects.invoke(block,BlockLightEngine.class,"checkNode",new Class<?>[]{long.class},BlockPos.asLong(10,24,10));}catch(Throwable failure){throw VerifyBlockInputEffects.sneaky(failure);}height.calls.add("nested-end");};
   height.active=true;Throwable failure=null;int work=-1;
   try{work=block.runLightUpdates();}catch(Throwable actual){failure=actual;}height.active=false;
   if(!height.once)throw new AssertionError("Actual propagation height provider missing");if(mode.equals("checked")&&failure!=VerifyBlockInputEffects.MARKER)throw new AssertionError("Original checked height Throwable changed",failure);if(!mode.equals("checked")&&failure!=null)throw failure;
   var bytes=String.join(",",height.calls).getBytes(java.nio.charset.StandardCharsets.UTF_8);
   System.out.println("PROPAGATION_HEIGHT mode="+mode+" same-marker="+(failure==VerifyBlockInputEffects.MARKER)+" work="+work+" pending="+block.hasLightWork()+" center="+block.getLightValue(new BlockPos(8,24,8))+" neighbour="+block.getLightValue(new BlockPos(9,24,8))+" calls="+height.calls.size()+" sha="+VerifyThreadedLighting.hash(bytes)+" prefix="+height.calls.subList(0,Math.min(20,height.calls.size())));
  }
 }
 public static void main(String[] args)throws Throwable{VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(String mode:List.of("value","checked","nested"))run(mode);}
}
