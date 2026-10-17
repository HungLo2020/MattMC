package net.minecraft.world.level.lighting;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.function.BiConsumer;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;
public final class VerifyBlockStateRetirement {
 static Object field(Object object,String name)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
 static final class Ephemeral extends BlockState {
  final int emission;Ephemeral(int emission){super(Blocks.AIR,new Reference2ObjectArrayMap<>(),MapCodec.<BlockState>unit(()->null));this.emission=emission;}
  @Override public int getLightEmission(){return emission;}
 }
 static boolean alive(WeakReference<?> weak){return weak.get()!=null;}
 static final class Getter implements LightChunkGetter,BlockGetter {
  final boolean retain;final int emission;WeakReference<BlockState> weak;BlockState strong;boolean notified,collected,control,earlySeen,earlyAlive;
  Getter(boolean retain,int emission){this.retain=retain;this.emission=emission;}
  final LightChunk chunk=new LightChunk(){
   public BlockState getBlockState(BlockPos pos){var state=new Ephemeral(emission);if(weak==null){if(Block.BLOCK_STATE_REGISTRY.getId(state)!=-1)throw new AssertionError("Unregistered actual state required");weak=new WeakReference<>(state);if(retain)strong=state;}return state;}
   public BlockEntity getBlockEntity(BlockPos pos){return null;}
   public FluidState getFluidState(BlockPos pos){return Blocks.AIR.defaultBlockState().getFluidState();}
   public int getMinY(){return 0;}public int getHeight(){return 64;}
   public void findBlockLightSources(BiConsumer<BlockPos,BlockState> consumer){}
   public ChunkSkyLightSources getSkyLightSources(){return null;}
  };
  public LightChunk getChunkForLighting(int x,int z){return x==0&&z==0?chunk:null;}
  public BlockGetter getLevel(){return this;}
  public void onLightUpdate(LightLayer layer,SectionPos section){
   if(notified)return;notified=true;if(weak==null)throw new AssertionError("Original block read missing");
   try{
    if(retain){for(int i=0;i<3;i++){System.gc();Thread.sleep(20);}control=alive(weak);if(!control)throw new AssertionError("Actual retained state control collected");strong=null;}
    long deadline=System.nanoTime()+5_000_000_000L;while(alive(weak)&&System.nanoTime()<deadline){System.gc();Thread.sleep(10);}collected=!alive(weak);
   }catch(InterruptedException failure){throw new RuntimeException(failure);}
  }
  public BlockEntity getBlockEntity(BlockPos pos){return null;}public BlockState getBlockState(BlockPos pos){return Blocks.AIR.defaultBlockState();}
  public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}public int getMinY(){return 0;}public int getHeight(){return 64;}
 }
 @SuppressWarnings("unchecked")static void run(boolean level,boolean retain,int emission)throws Exception {
  var getter=new Getter(retain,emission);LevelLightEngine parent=level?new LevelLightEngine(getter,true,false):null;
  var engine=level?(BlockLightEngine)parent.getLayerListener(LightLayer.BLOCK):new BlockLightEngine(getter);Object storage=field(engine,"storage");var map=(DataLayerStorageMap<?>)field(storage,"updatingSectionData");long key=SectionPos.asLong(0,1,0);
  map.setLayer(key,new DataLayer(){@Override public int get(int x,int y,int z){if(!getter.earlySeen&&getter.weak!=null){getter.earlySeen=true;for(int i=0;i<3;i++)System.gc();getter.earlyAlive=alive(getter.weak);}return super.get(x,y,z);}});map.clearCache();((LongSet)field(storage,"changedSections")).add(key);((LongSet)field(storage,"sectionsAffectedByLightUpdates")).add(key);
  engine.setLightEnabled(new ChunkPos(0,0),true);engine.checkBlock(new BlockPos(8,24,8));if(level)parent.runLightUpdates();else engine.runLightUpdates();
  if(!getter.notified||!getter.earlySeen)throw new AssertionError("Original stored read/notification missing");
  System.out.println("BLOCK_STATE_RETIRE level="+level+" emission="+emission+" retain-control="+retain+" retained-alive="+getter.control+" alive-during-stored-get="+getter.earlyAlive+" collected-before-notify="+getter.collected);
 }
 public static void main(String[] args)throws Exception{SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(boolean level:new boolean[]{false,true})for(int emission:new int[]{0,7})for(boolean retain:new boolean[]{false,true})run(level,retain,emission);}
}
