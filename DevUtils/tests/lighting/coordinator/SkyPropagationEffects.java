package net.minecraft.world.level.lighting;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;
public final class SkyPropagationEffects {
 static final Exception MARKER=new Exception("sky propagation original marker");
 @SuppressWarnings("unchecked")static <T extends Throwable>RuntimeException exact(Throwable e)throws T{throw(T)e;}
 static final class Getter implements LightChunkGetter,BlockGetter{
  SkyLightEngine engine;boolean active;String mode;List<String>calls=new ArrayList<>();ChunkSkyLightSources sources=new ChunkSkyLightSources(LevelHeightAccessor.create(0,64));
  final LightChunk chunk=new LightChunk(){public BlockState getBlockState(BlockPos p){if(active){calls.add("state:"+p.getX()+":"+p.getY()+":"+p.getZ()+":source="+level(8,8,8));if(mode.equals("checked-state"))throw exact(MARKER);}return Blocks.AIR.defaultBlockState();}
   public BlockEntity getBlockEntity(BlockPos p){return null;}public FluidState getFluidState(BlockPos p){return Blocks.AIR.defaultBlockState().getFluidState();}public int getMinY(){return 0;}public int getHeight(){return 64;}public void findBlockLightSources(BiConsumer<BlockPos,BlockState> c){}public ChunkSkyLightSources getSkyLightSources(){return sources;}};
  int level(int x,int y,int z){var layer=engine.storage.updatingSectionData.getLayer(SectionPos.asLong(0,1,0));return layer==null?-1:layer.get(x,y,z);}
  public LightChunk getChunkForLighting(int x,int z){if(active){calls.add("lookup:"+x+":"+z+":source="+level(8,8,8));if(mode.equals("checked-lookup"))throw exact(MARKER);}return chunk;}
  public BlockGetter getLevel(){return this;}public void onLightUpdate(LightLayer l,SectionPos p){}public BlockEntity getBlockEntity(BlockPos p){return null;}public BlockState getBlockState(BlockPos p){return Blocks.AIR.defaultBlockState();}public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}public int getMinY(){return 0;}public int getHeight(){return 64;}
 }
 static void run(String mode)throws Throwable{
  var getter=new Getter();getter.mode=mode;var engine=new SkyLightEngine(getter);getter.engine=engine;engine.updateSectionStatus(SectionPos.of(0,1,0),false);engine.runLightUpdates();
  engine.storage.updatingSectionData.setLayer(SectionPos.asLong(0,1,0),new DataLayer());engine.storage.updatingSectionData.clearCache();
  engine.enqueueIncrease(BlockPos.asLong(8,24,8),2048L|1024L|(16L<<Direction.EAST.ordinal())|2L);getter.active=true;int work=-1;Throwable failure=null;
  try{work=engine.runLightUpdates();}catch(Throwable e){failure=e;}getter.active=false;
  var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join(",",getter.calls).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  System.out.println("SKY_PROPAGATION mode="+mode+" work="+work+" marker="+(failure==MARKER)+" error="+(failure==null?"none":failure.getClass().getSimpleName())+" source="+getter.level(8,8,8)+" target="+getter.level(9,8,8)+" calls="+getter.calls.size()+" callsha="+hash+" prefix="+getter.calls.subList(0,Math.min(8,getter.calls.size())));
 }
 public static void main(String[]args)throws Throwable{SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(String mode:List.of("value","checked-state","checked-lookup"))run(mode);}
}
