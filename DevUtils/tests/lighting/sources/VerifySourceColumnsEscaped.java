package net.minecraft.world.level.lighting;
import java.lang.foreign.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
public final class VerifySourceColumnsEscaped {
 static final class Floor implements BlockGetter {
  final int floor;Floor(int height){floor=height-1;}
  public int getMinY(){return 0;}public int getHeight(){return 64;}
  public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p){throw new AssertionError();}
  public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){throw new AssertionError();}
  public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p){return(p.getY()==floor?Blocks.STONE:Blocks.AIR).defaultBlockState();}
 }
 static ChunkSkyLightSources source(int height){var s=new ChunkSkyLightSources(LevelHeightAccessor.create(0,64));var floor=new Floor(height);for(int z=0;z<16;z++)for(int x=0;x<16;x++)s.update(floor,x,height-1,z);return s;}
 static int[] capture(ChunkSkyLightSources[] s)throws Exception{
  Object seed=NativeLightPropagation.skySeed(s[0],s[1],s[2],s[3],s[4]);if(seed==null)throw new AssertionError("native seed declined");
  Field f=seed.getClass().getDeclaredField("nativeColumns");f.setAccessible(true);MemorySegment words=(MemorySegment)f.get(seed);return words.toArray(ValueLayout.JAVA_INT);
 }
 static void check(String name,int[] actual,int[] heights){
  for(int z=0;z<16;z++)for(int x=0;x<16;x++){
   int i=z*16+x;int[] expected={heights[0],z==0?heights[1]:heights[0],z==15?heights[2]:heights[0],x==0?heights[3]:heights[0],x==15?heights[4]:heights[0]};
   for(int side=0;side<5;side++)if(actual[side*256+i]!=expected[side])throw new AssertionError(name+" "+side+" "+x+","+z+" got="+actual[side*256+i]+" expected="+expected[side]);
  }
  System.out.println("SOURCE_COLUMNS_ESCAPED "+name+" "+Arrays.hashCode(actual));
 }
 public static void main(String[]args)throws Exception{
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();ChunkSkyLightSources[] s=new ChunkSkyLightSources[5];long[][] escaped=new long[5][];
  for(int i=0;i<5;i++){s[i]=source(8+i);escaped[i]=s[i].nativeRaw();if(escaped[i]!=s[i].nativeRaw())throw new AssertionError("escaped raw identity");}
  int[] original=capture(s);check("initial",original,new int[]{8,9,10,11,12});
  // Mutate actual escaped arrays without touching any source getter before consumption.
  for(int i=0;i<5;i++){long[] donor=source(25+i).nativeRaw();System.arraycopy(donor,0,escaped[i],0,donor.length);}
  check("outside-five-writes",capture(s),new int[]{25,26,27,28,29});
  for(int i=0;i<5;i++)Arrays.fill(escaped[i],0L);
  check("outside-five-clears",capture(s),new int[]{Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE});
  check("independent-old-snapshot",original,new int[]{8,9,10,11,12});
  java.lang.ref.Reference.reachabilityFence(s);java.lang.ref.Reference.reachabilityFence(escaped);
 }
}
