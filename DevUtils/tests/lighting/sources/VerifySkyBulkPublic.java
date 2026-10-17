package net.minecraft.world.level.lighting;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.block.Blocks;
/** Frozen-first public values and actual escaped positions; no source private fields. */
public final class VerifySkyBulkPublic {
 static void run(String name,ProtoChunk chunk){
  var source=new ChunkSkyLightSources(LevelHeightAccessor.create(chunk.getMinY(),chunk.getHeight()));
  var reader=new VerifySkySourcePublicUpdateContracts.Reader(source,"occlude");
  if(!source.update(reader,7,chunk.getMinY()+4,3))throw new AssertionError("initial source update");
  BlockPos first=reader.retained.get(0),second=reader.retained.get(1);
  source.fillFrom(chunk);var values=new ArrayList<Integer>();
  for(int z=0;z<16;z++)for(int x=0;x<16;x++)values.add(source.getLowestSourceY(x,z));
  System.out.println("SKY_BULK "+name+" max="+source.getHighestLowestSourceY()+" values="+values
   +" aliases="+first.getX()+","+first.getY()+","+first.getZ()+"/"+second.getX()+","+second.getY()+","+second.getZ());
 }
 public static void main(String[]args){
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  for(String pattern:SkyLightSourcesFixtures.PATTERNS)for(int min:new int[]{-64,0})for(int height:new int[]{16,64})
   run(pattern+":"+min+":"+height,SkyLightSourcesFixtures.chunk(pattern,min,height,1977));
  var last=SkyLightSourcesFixtures.chunk("solid",-64,64,0);
  for(int s=0;s<4;s++)for(int y=0;y<16;y++)last.getSection(s).setBlockState(15,y,15,Blocks.GLASS.defaultBlockState());
  run("last-open-others-blocked",last);
  var gap=SkyLightSourcesFixtures.chunk("glass",-64,64,0);
  var empty=SkyLightSourcesFixtures.chunk("empty",-64,64,0);var sections=gap.getSections().clone();
  sections[0]=empty.getSection(0);sections[2]=empty.getSection(2);run("empty-bottom-and-gap",SkyLightSourcesFixtures.withSections(sections,-64,64));
  int n=0;for(var record:SkyLightSourcesFixtures.records())run("recorded:"+(n++),SkyLightSourcesFixtures.recorded(record));
  if(n!=16)throw new AssertionError("saved fixture count "+n);
 }
}
