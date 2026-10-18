package net.minecraft.world.level.lighting;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;
public final class SkyBorderEffects {
 static final class Getter implements LightChunkGetter {
  final SkyPropagationEffects.Getter base=new SkyPropagationEffects.Getter();boolean active,once;String mode;SkyLightEngine engine;BlockPos.MutableBlockPos mutable;
  void nested(String kind){if(active&&!once&&mode.equals("nested-"+kind)){once=true;base.calls.add("nested-start");engine.checkNode(BlockPos.asLong(10,40,10));mutable.set(26,40,8);base.calls.add("nested-end");}}
  final LightChunk chunk=new LightChunk(){public BlockState getBlockState(BlockPos p){nested("state");return base.chunk.getBlockState(p);}public BlockEntity getBlockEntity(BlockPos p){return null;}public FluidState getFluidState(BlockPos p){return base.chunk.getFluidState(p);}public int getMinY(){return 0;}public int getHeight(){return 64;}public void findBlockLightSources(BiConsumer<BlockPos,BlockState> c){}public ChunkSkyLightSources getSkyLightSources(){return base.sources;}};
  public LightChunk getChunkForLighting(int x,int z){nested("lookup");base.getChunkForLighting(x,z);return chunk;}public BlockGetter getLevel(){return base;}public void onLightUpdate(LightLayer l,SectionPos p){}
 }
 static final class Storage extends SkyLightSectionStorage {
  boolean active,fail;final List<String>writes=new ArrayList<>();Storage(LightChunkGetter g){super(g);}
  @Override protected void setStoredLevel(long p,int value){if(active){writes.add(BlockPos.getX(p)+":"+BlockPos.getY(p)+":"+BlockPos.getZ(p)+"="+value);if(fail&&BlockPos.getY(p)==29)throw SkyPropagationEffects.exact(SkyPropagationEffects.MARKER);}super.setStoredLevel(p,value);}
 }
 static void run(String mode)throws Throwable{
  var getter=new Getter();getter.mode=mode;getter.base.mode="value";var storage=new Storage(getter);var engine=new SkyLightEngine(getter,storage);getter.engine=engine;getter.base.engine=engine;
  var f=SkyLightEngine.class.getDeclaredField("mutablePos");f.setAccessible(true);getter.mutable=(BlockPos.MutableBlockPos)f.get(engine);
  engine.updateSectionStatus(SectionPos.of(0,2,0),false);engine.runLightUpdates();var map=storage.updatingSectionData;
  for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)for(int y=0;y<=4;y++)map.setLayer(SectionPos.asLong(x,y,z),null);
  for(int[]s:new int[][]{{0,2,0},{0,0,0},{1,2,0},{1,1,0}})map.setLayer(SectionPos.asLong(s[0],s[1],s[2]),new DataLayer());map.clearCache();
  long p=BlockPos.asLong(15,32,8);boolean decrease=mode.equals("decrease");if(decrease){map.getLayer(SectionPos.asLong(1,2,0)).set(0,0,8,3);engine.enqueueDecrease(p,512L|5L);}else engine.enqueueIncrease(p,2048L|1024L|512L|2L);
  storage.fail=mode.equals("checked-write");storage.active=true;getter.active=true;getter.base.active=true;int work=-1;Throwable failure=null;
  try{work=engine.runLightUpdates();}catch(Throwable e){failure=e;}storage.active=false;getter.active=false;getter.base.active=false;
  var text=new StringBuilder();for(int[]s:new int[][]{{0,2,0},{0,0,0},{1,2,0},{1,1,0}}){var layer=map.getLayer(SectionPos.asLong(s[0],s[1],s[2]));if(layer==null)text.append("null");else for(int i=0;i<4096;i++)text.append(Integer.toHexString(layer.get(i&15,i>>8,i>>4&15)));}
  var digest=java.security.MessageDigest.getInstance("SHA-256");var utf=java.nio.charset.StandardCharsets.UTF_8;var hex=java.util.HexFormat.of();
  System.out.println("SKY_BORDER mode="+mode+" work="+work+" marker="+(failure==SkyPropagationEffects.MARKER)+" error="+(failure==null?"none":failure.getClass().getSimpleName())+" pending="+engine.hasLightWork()+" calls="+getter.base.calls.size()+" callsha="+hex.formatHex(digest.digest(String.join(",",getter.base.calls).getBytes(utf)))+" writes="+storage.writes.size()+" writesha="+hex.formatHex(digest.digest(String.join(",",storage.writes).getBytes(utf)))+" layersha="+hex.formatHex(digest.digest(text.toString().getBytes(utf)))+" prefix="+storage.writes.subList(0,Math.min(6,storage.writes.size())));
 }
 public static void main(String[]args)throws Throwable{SharedConstants.tryDetectVersion();Bootstrap.bootStrap();for(String mode:List.of("increase","decrease","checked-write","nested-lookup","nested-state"))run(mode);}
}
