package net.minecraft.world.level.lighting;
import java.io.IOException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;
/** Canonical chunk receiver with actual escaped positions and custom height effects. */
public final class VerifySkyCanonicalEffects {
 static final IOException MARKER=new IOException("original height accessor failure");
 @SuppressWarnings("unchecked")static<T extends Throwable>void fail()throws T{throw(T)MARKER;}
 static final class Height implements LevelHeightAccessor{
  final String mode;final ChunkSkyLightSources source;final List<String> calls=new ArrayList<>();int minReads;boolean active;
  Height(String mode,ChunkSkyLightSources source){this.mode=mode;this.source=source;}
  public int getMinY(){
   if(active){int n=++minReads;calls.add("min:"+n);if(mode.equals("throw-min5")&&n==5)VerifySkyCanonicalEffects.<RuntimeException>fail();
    if(mode.equals("reenter-min")&&n==1){calls.add("nested:begin");var reader=new VerifySkySourcePublicUpdateContracts.Reader(source,"occlude");calls.add("nested:"+source.update(reader,10,7,10)+":"+reader.trace);}}
   return 0;
  }
  public int getHeight(){if(active)calls.add("height");return 32;}
 }
 static void run(String mode){
  ChunkSkyLightSources source=new ChunkSkyLightSources(LevelHeightAccessor.create(0,32));BlockPos first=null,second=null;
  for(int z=0;z<16;z++)for(int x=0;x<16;x++){var reader=new VerifySkySourcePublicUpdateContracts.Reader(source,"occlude");if(!source.update(reader,x,4,z))throw new AssertionError("setup");first=reader.retained.get(0);second=reader.retained.get(1);}
  Height height=new Height(mode,source);LevelHeightAccessor actual=mode.equals("canonical")?LevelHeightAccessor.create(0,32):height;
  LevelChunkSection[] sections=new LevelChunkSection[2];for(int i=0;i<2;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.STONE.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
  ProtoChunk chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),actual,null,null);
  height.active=true;Throwable error=null;try{source.fillFrom(chunk);}catch(Throwable thrown){error=thrown;}height.active=false;
  List<Integer> values=new ArrayList<>();for(int z=0;z<16;z++)for(int x=0;x<16;x++)values.add(source.getLowestSourceY(x,z));
  System.out.println("SKY_CANONICAL "+mode+" original="+(error==MARKER)+" type="+(error==null?"none":error.getClass().getName())+" max="+source.getHighestLowestSourceY()+" values="+values+" calls="+height.calls+" aliases="+first.getX()+","+first.getY()+","+first.getZ()+"/"+second.getX()+","+second.getY()+","+second.getZ());
 }
 public static void main(String[]args){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(String mode:List.of("canonical","dynamic","throw-min5","reenter-min"))run(mode);}
}
