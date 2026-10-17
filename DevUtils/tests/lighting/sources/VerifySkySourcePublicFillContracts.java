package net.minecraft.world.level.lighting;
import java.io.IOException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;
/** Public custom fill behavior; no reflected storage or native-specific calls. */
public final class VerifySkySourcePublicFillContracts {
 static final IOException MARKER=new IOException("original custom fill failure");
 @SuppressWarnings("unchecked") static<T extends Throwable>void fail()throws T{throw(T)MARKER;}
 static final class Control{
  final String mode;final ChunkSkyLightSources source;final List<String> calls=new ArrayList<>();
  BlockPos.MutableBlockPos first,second;int sectionReads,blockReads;
  Control(String mode){
   this.mode=mode;source=new ChunkSkyLightSources(LevelHeightAccessor.create(0,32));
   for(int z=0;z<16;z++)for(int x=0;x<16;x++){
    var reader=new VerifySkySourcePublicUpdateContracts.Reader(source,"occlude");
    if(!source.update(reader,x,4,z)||source.getLowestSourceY(x,z)!=5)throw new AssertionError("public setup");
    first=(BlockPos.MutableBlockPos)reader.retained.get(0);second=(BlockPos.MutableBlockPos)reader.retained.get(1);
   }
  }
  void nested(){
   calls.add("nested:begin");var reader=new VerifySkySourcePublicUpdateContracts.Reader(source,"occlude");
   boolean changed=source.update(reader,10,7,10);calls.add("nested:"+changed+":"+reader.trace);
  }
 }
 static final class Section extends LevelChunkSection{
  Control control;final int index;
  Section(Control control,int index){super(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);this.control=control;this.index=index;}
  @Override public boolean hasOnlyAir(){
   if(control==null)return super.hasOnlyAir();control.calls.add("air:"+index);
   return index==1&&control.mode.equals("gap");
  }
  @Override public BlockState getBlockState(int x,int y,int z){
   if(control==null)return super.getBlockState(x,y,z);
   int n=++control.blockReads;control.calls.add("block:"+index+":"+x+","+y+","+z);
   if(control.mode.equals("throw-block")&&n==7)VerifySkySourcePublicFillContracts.<RuntimeException>fail();
   if(control.mode.equals("mutate-first"))control.first.setY(12);
   if(control.mode.equals("mutate-second")&&y==15){control.second.setY(20);return Blocks.AIR.defaultBlockState();}
   if((control.mode.equals("reenter-block")||control.mode.equals("reenter-fail"))&&n==1){
    control.nested();if(control.mode.equals("reenter-fail"))VerifySkySourcePublicFillContracts.<RuntimeException>fail();
   }
   return Blocks.STONE.defaultBlockState();
  }
 }
 static final class Chunk extends ProtoChunk{
  Control control;
  Chunk(Control control){super(new ChunkPos(0,0),UpgradeData.EMPTY,new LevelChunkSection[]{new Section(control,0),new Section(control,1)},new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(0,32),null,null);this.control=control;}
  @Override public int getHighestFilledSectionIndex(){if(control==null)return super.getHighestFilledSectionIndex();control.calls.add("highest");return control.mode.equals("empty")?-1:1;}
  @Override public LevelChunkSection getSection(int index){
   if(control==null)return super.getSection(index);
   int n=++control.sectionReads;control.calls.add("section:"+index);
   if(control.mode.equals("throw-section")&&n==7)VerifySkySourcePublicFillContracts.<RuntimeException>fail();
   if(control.mode.equals("reenter-section")&&n==1)control.nested();
   return super.getSection(index);
  }
 }
 static void run(String mode){
  Control c=new Control(mode);Chunk chunk=new Chunk(c);Throwable actual=null;
  try{c.source.fillFrom(chunk);}catch(Throwable failure){actual=failure;}
  List<Integer> values=new ArrayList<>();for(int z=0;z<16;z++)for(int x=0;x<16;x++)values.add(c.source.getLowestSourceY(x,z));
  System.out.println("SKY_FILL "+mode+" original="+(actual==MARKER)+" type="+(actual==null?"none":actual.getClass().getName())+" max="+c.source.getHighestLowestSourceY()+" sectionReads="+c.sectionReads+" blockReads="+c.blockReads+" values="+values+" calls="+c.calls+" aliases="+c.first.getX()+","+c.first.getY()+","+c.first.getZ()+"/"+c.second.getX()+","+c.second.getY()+","+c.second.getZ());
 }
 public static void main(String[]args){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(String mode:List.of("empty","solid","gap","throw-section","throw-block","mutate-first","mutate-second","reenter-section","reenter-block","reenter-fail"))run(mode);}
}
