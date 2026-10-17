package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.*;
import net.minecraft.core.Holder;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.block.state.BlockState;

/** Packed section resources for the Rust source producer. No column or lighting decisions. */
public final class NativeSkySourceSections {
 private static final MethodHandle ACCESSOR;
 private static final Class<?> CONSTANT_ACCESSOR=LevelHeightAccessor.create(0,16).getClass();
 static {try {ACCESSOR=MethodHandles.privateLookupIn(ChunkAccess.class,MethodHandles.lookup())
  .findGetter(ChunkAccess.class,"levelHeightAccessor",LevelHeightAccessor.class);}
  catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
 public record ChunkData(LevelChunkSection[] sections,int minY,int height,int highest) {}
 public record SectionData(int words,int bits,int count,boolean global) {}
 /** A synchronous CPU-owner pin, cleared before any compatibility effect. */
 public static final class Lease {
  private NativeLiveBlockSection owner;
  /** 0 is air, 1 is the existing primary owner, 2 requests compatibility export. */
  public int acquire(LevelChunkSection section) {
   clear();
   if(section.hasOnlyAir())return 0;
   var container=section.getStates();
   if(container.getClass()!=PalettedContainer.class)return 2;
   owner=container.nativeLiveBlocks();
   return owner==null?2:1;
  }
  public MemorySegment pointer(){return owner.stageOwner();}
  public void clear(){java.lang.ref.Reference.reachabilityFence(owner);owner=null;}
 }
 public static ChunkData prepare(ChunkAccess chunk) throws Throwable {
  if(chunk==null || (chunk.getClass()!=ProtoChunk.class && chunk.getClass()!=LevelChunk.class)
    || !NativeBlockRegistry.ready())return null;
  LevelHeightAccessor accessor=(LevelHeightAccessor)ACCESSOR.invokeExact(chunk);
  boolean pure=accessor.getClass()==CONSTANT_ACCESSOR;
  if(!pure && (accessor.getClass()==net.minecraft.server.level.ServerLevel.class
    || accessor.getClass()==net.minecraft.client.multiplayer.ClientLevel.class)){
   Holder<?> holder=((Level)accessor).dimensionTypeRegistration();
   pure=(holder.getClass()==Holder.Direct.class || holder.getClass()==Holder.Reference.class)
     && holder.isBound() && holder.value() instanceof net.minecraft.world.level.dimension.DimensionType;
  }
  if(!pure)return null; // Do not invoke a custom provider to ask whether it is canonical.
  LevelChunkSection[] sections=chunk.getSectionsForRead();
  for(LevelChunkSection section:sections)if(section==null || section.getClass()!=LevelChunkSection.class)return null;
  return new ChunkData(sections,accessor.getMinY(),accessor.getHeight(),chunk.getHighestFilledSectionIndex());
 }
 public static SectionData export(LevelChunkSection section,MemorySegment words,
   MemorySegment ids,MemorySegment header){
  if(section.hasOnlyAir())return new SectionData(0,-1,1,false);
  var container=section.getStates();
  if(container.getClass()!=PalettedContainer.class)return null;
  var live=container.nativeLiveBlocks();
  if(live!=null){
   int length=live.export(words,ids,header);
   boolean global=header.getAtIndex(ValueLayout.JAVA_INT,2)!=0;
   return new SectionData(length,header.getAtIndex(ValueLayout.JAVA_INT,0),
     global?Block.BLOCK_STATE_REGISTRY.size():header.getAtIndex(ValueLayout.JAVA_INT,3),global);
  }
  var data=container.dataForNativeScan();var storage=data.storage();var palette=data.palette();
  if((storage.getClass()!=SimpleBitStorage.class && storage.getClass()!=ZeroBitStorage.class)
    || storage.getSize()!=4096)return null;
  int count=palette.getSize();boolean global=palette.getClass()==GlobalPalette.class;
  if(global){if(container.registryForNativeScan()!=Block.BLOCK_STATE_REGISTRY
    || count!=Block.BLOCK_STATE_REGISTRY.size())return null;}
  else {
   if(palette.getClass()!=SingleValuePalette.class && palette.getClass()!=LinearPalette.class
     && palette.getClass()!=HashMapPalette.class || count>256)return null;
   for(int index=0;index<count;index++){
    var state=palette.valueFor(index);
    if(state==null || state.getClass()!=BlockState.class)return null;
    int id=Block.BLOCK_STATE_REGISTRY.getId(state);if(id<0)return null;
    ids.setAtIndex(ValueLayout.JAVA_INT,index,id);
   }
  }
  var raw=storage.getRaw();MemorySegment.copy(raw,0,words,ValueLayout.JAVA_LONG,0,raw.length);
  return new SectionData(raw.length,storage.getBits(),count,global);
 }
}
