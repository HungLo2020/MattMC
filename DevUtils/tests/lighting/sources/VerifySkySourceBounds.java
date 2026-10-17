package net.minecraft.world.level.lighting;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.LevelHeightAccessor;
/** Original constructor observations; reflection is an original-only metadata adapter. */
public final class VerifySkySourceBounds {
 static final IOException MARKER=new IOException("original height bounds effect");
 @SuppressWarnings("unchecked")static<T extends Throwable>void fail()throws T{throw(T)MARKER;}
 static void run(String name,int low,int high,int throwing)throws Exception{
  List<String> calls=new ArrayList<>();LevelHeightAccessor height=new LevelHeightAccessor(){
   public int getMinY(){calls.add("min");if(throwing==1)VerifySkySourceBounds.<RuntimeException>fail();return low;}
   public int getMaxY(){calls.add("max");if(throwing==2)VerifySkySourceBounds.<RuntimeException>fail();return high;}
   public int getHeight(){throw new AssertionError("unexpected getHeight");}
  };
  Throwable error=null;ChunkSkyLightSources source=null;try{source=new ChunkSkyLightSources(height);}catch(Throwable failure){error=failure;}
  int bits=-1,words=-1;String visible="none";
  if(source!=null){try{Method method=ChunkSkyLightSources.class.getMethod("nativeBits");bits=(int)method.invoke(source);Method raw=ChunkSkyLightSources.class.getMethod("nativeRaw");words=((long[])raw.invoke(source)).length;}catch(NoSuchMethodException original){Field field=ChunkSkyLightSources.class.getDeclaredField("heightmap");field.setAccessible(true);BitStorage packed=(BitStorage)field.get(source);bits=packed.getBits();words=packed.getRaw().length;}
   visible=source.getLowestSourceY(0,0)+":"+source.getHighestLowestSourceY();}
  if(throwing!=0&&error!=MARKER)throw new AssertionError("bounds Throwable replaced");
  System.out.println("SKY_BOUNDS "+name+" low="+low+" high="+high+" calls="+calls+" bits="+bits+" words="+words+" visible="+visible+" original="+(error==MARKER)+" type="+(error==null?"none":error.getClass().getName())+" message="+(error==null?"none":error.getMessage()));
 }
 public static void main(String[]args)throws Exception{
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();int[][] bounds={{0,15},{0,31},{-64,319},{-2048,2047},{0,-1},{0,-2},{0,-3},{0,-4},{0,Integer.MAX_VALUE},{0,Integer.MAX_VALUE-1},{0,Integer.MAX_VALUE-2},{Integer.MIN_VALUE,Integer.MAX_VALUE},{Integer.MAX_VALUE,Integer.MIN_VALUE},{Integer.MIN_VALUE,Integer.MIN_VALUE},{Integer.MAX_VALUE,Integer.MAX_VALUE},{-1,-1},{1,0},{17,16}};
  for(int i=0;i<bounds.length;i++)run("case"+i,bounds[i][0],bounds[i][1],0);run("throw-min",0,31,1);run("throw-max",0,31,2);
 }
}
