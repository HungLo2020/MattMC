package net.minecraft.world.level.lighting;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ProtoChunkTicks;
public final class PropagationAdmissionEffects {
 static boolean nativeMode;static Throwable hookFailure;static int hooks;static Getter getter;
 static MethodHandle handle(String name,FunctionDescriptor type)throws Throwable{return(MethodHandle)Class.forName("net.minecraft.util.NativeLibraryLoader").getMethod("downcallHandle",String.class,String.class,FunctionDescriptor.class).invoke(null,"mattmc_rust",name,type);}
 static void escape(){var section=getter.chunk.getSections()[1];section.getStates().onResize(4,Blocks.STONE.defaultBlockState());section.setBlockState(10,8,8,Blocks.AIR.defaultBlockState(),false);}
 static void afterCapture(){try{hooks++;escape();}catch(Throwable failure){hookFailure=failure;}}
 static final class Getter implements LightChunkGetter,BlockGetter{
  ProtoChunk chunk;BlockLightEngine engine;int calls;BlockPos.MutableBlockPos mutable;
  public LightChunk getChunkForLighting(int x,int z){calls++;if(calls==1){mutable.set(26,24,8);if(!nativeMode)escape();}return x==0&&z==0?chunk:null;}
  public BlockGetter getLevel(){return this;}public void onLightUpdate(LightLayer layer,SectionPos pos){}
  public BlockEntity getBlockEntity(BlockPos pos){return null;}public BlockState getBlockState(BlockPos pos){return Blocks.AIR.defaultBlockState();}
  public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}public int getMinY(){return 0;}public int getHeight(){return 64;}
 }
 public static void main(String[]args)throws Throwable{
  nativeMode=args[0].equals("native");SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  if(nativeMode){if(!(boolean)Class.forName("net.minecraft.world.level.block.NativeBlockRegistry").getMethod("ready").invoke(null))throw new AssertionError("Native registry unavailable");var stub=Linker.nativeLinker().upcallStub(MethodHandles.lookup().findStatic(PropagationAdmissionEffects.class,"afterCapture",MethodType.methodType(void.class)),FunctionDescriptor.ofVoid(),Arena.global());handle("mattmc_test_set_after_capture_hook",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invokeExact(stub);}
  getter=new Getter();var sections=new LevelChunkSection[4];for(int i=0;i<4;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
  sections[1].setBlockState(9,8,8,Blocks.STONE.defaultBlockState(),false);sections[1].setBlockState(10,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);
  getter.chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(0,64),null,null);var engine=new BlockLightEngine(getter);getter.engine=engine;var field=BlockLightEngine.class.getDeclaredField("mutablePos");field.setAccessible(true);getter.mutable=(BlockPos.MutableBlockPos)field.get(engine);
  long key=SectionPos.asLong(0,1,0);engine.storage.updatingSectionData.setLayer(key,new DataLayer());engine.storage.updatingSectionData.clearCache();engine.enqueueIncrease(BlockPos.asLong(8,24,8),2048L|1024L|(16L<<Direction.EAST.ordinal())|2L);
  int work=-1;String error="none";try{work=engine.runLightUpdates();}catch(Throwable failure){error=failure.getClass().getSimpleName();}if(hookFailure!=null)throw hookFailure;
  var layer=engine.storage.updatingSectionData.getLayer(key);System.out.println("PROPAGATION_ADMISSION work="+work+" error="+error+" lookups="+getter.calls+" source="+layer.get(8,8,8)+" target="+layer.get(9,8,8)+" selected="+getter.chunk.getBlockState(new BlockPos(10,24,8)).isAir());System.out.println("ADMISSION_HOOKS "+hooks);
 }
}
