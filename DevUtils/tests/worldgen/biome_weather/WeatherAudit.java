package biomeweather;

import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;

/** Calls actual public biome consumers on a real server level and block storage. */
public final class WeatherAudit {
 static final AtomicBoolean STARTED=new AtomicBoolean();
 static final Path OUTPUT=Path.of(System.getProperty("audit.output"));
 static volatile long sink;
 public static void capture(Object value){if(STARTED.compareAndSet(false,true))new Thread(()->run((MinecraftServer)value),"weather-audit").start();}
 static long[] stats(String type)throws Exception {
  try{var cls=Class.forName("net.minecraft.world.level.biome."+type);var method=cls.getDeclaredMethod("statistics");method.setAccessible(true);return (long[])method.invoke(null);}
  catch(ClassNotFoundException absent){return new long[0];}
 }
 static long work(int kind,Biome biome,ServerLevel level,BlockPos.MutableBlockPos cursor,int count){
  long result=0;
  for(int i=0;i<count;i++){
   if(kind==3)cursor.set(512+(i&4095),200,512);
   else cursor.set((kind==2?544:512)+(i&15),kind==2?71:70,512+((i>>>4)&15));
   if(kind==0||kind==3)result+=biome.getPrecipitationAt(cursor,63).ordinal();
   else if(kind==1)result+=biome.shouldFreeze(level,cursor,false)?1:0;
   else result+=biome.shouldSnow(level,cursor)?1:0;
  }
  sink=result;return result;
 }
 static List<Map<String,Object>> benchmark(MinecraftServer server)throws Exception {
  var level=server.overworld();var registry=server.registryAccess().lookupOrThrow(Registries.BIOME);
  Biome cold=registry.getValue(Biomes.SNOWY_PLAINS),frozen=registry.getValue(Biomes.FROZEN_OCEAN);if(cold==null||frozen==null)throw new AssertionError("missing actual registered biome");
  var cursor=new BlockPos.MutableBlockPos();
  for(int z=512;z<528;z++)for(int x=512;x<560;x++){
   if(x>=528&&x<544)continue;
   level.getChunk(x>>4,z>>4);level.setBlock(cursor.set(x,69,z),Blocks.STONE.defaultBlockState(),2);
   level.setBlock(cursor.set(x,70,z),(x<528?Blocks.WATER:Blocks.STONE).defaultBlockState(),2);
   level.setBlock(cursor.set(x,71,z),Blocks.AIR.defaultBlockState(),2);
  }
  var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();var compilation=ManagementFactory.getCompilationMXBean();
  var rows=new ArrayList<Map<String,Object>>();String[] names={"precipitation_cached","freeze_water_cached","snow_ground_cached","precipitation_frozen_misses"};
  for(int kind=0;kind<4;kind++){
   Biome biome=kind==3?frozen:cold;int count=4096;long expected=(kind==0||kind==3)?count*2L:count;
   long[] parentBefore=stats("NativeBiomeWeather"),temperatureBefore=stats("NativeBiomeTemperature");long total=0;
   for(int i=0;i<24;i++){long actual=work(kind,biome,level,cursor,count);if(actual!=expected)throw new AssertionError(names[kind]+" warm output "+actual);total+=count;}
   List<Map<String,Object>> samples=new ArrayList<>();int quiet=0;
   for(int attempt=0;attempt<24&&quiet<6;attempt++){
    long jit=compilation.getTotalCompilationTime(),gc=gcMillis(),allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
    long actual=work(kind,biome,level,cursor,count);long elapsed=System.nanoTime()-start;
    cpu=bean.getCurrentThreadCpuTime()-cpu;allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;gc=gcMillis()-gc;jit=compilation.getTotalCompilationTime()-jit;
    if(actual!=expected)throw new AssertionError(names[kind]+" sample output "+actual);total+=count;
    samples.add(Map.of("count",count,"elapsed_ns",elapsed,"cpu_ns",cpu,"java_bytes",allocated,"jit_ms",jit,"gc_ms",gc,"sum",actual));if(jit==0&&gc==0)quiet++;
   }
   if(quiet<6)throw new AssertionError("insufficient quiet weather samples "+names[kind]);
   long[] parentAfter=stats("NativeBiomeWeather"),temperatureAfter=stats("NativeBiomeTemperature");
   if(parentAfter.length!=0&&parentAfter[1]-parentBefore[1]!=total)throw new AssertionError("weather owner count does not match actual public calls");
   if(parentAfter.length!=0&&parentAfter[0]!=parentBefore[0])throw new AssertionError("weather frame retained after real last use");
   if(temperatureAfter.length!=0&&temperatureAfter[6]!=temperatureBefore[6])throw new AssertionError("temperature child retained after real last use");
   var row=new LinkedHashMap<String,Object>();row.put("name",names[kind]);row.put("samples",samples);row.put("quiet_samples",quiet);row.put("actual_public_calls",total);row.put("parent_before",parentBefore);row.put("parent_after",parentAfter);row.put("temperature_before",temperatureBefore);row.put("temperature_after",temperatureAfter);rows.add(row);
  }
  // Time only public queries above; now verify that no query changed the scene.
  long water=0,stone=0,air=0;for(int z=512;z<528;z++)for(int x=512;x<560;x++){
   if(x>=528&&x<544)continue;
   if(level.getBlockState(cursor.set(x,70,z)).is(Blocks.WATER))water++;
   if(level.getBlockState(cursor.set(x,70,z)).is(Blocks.STONE))stone++;
   if(level.getBlockState(cursor.set(x,71,z)).isAir())air++;
  }
  if(water!=256||stone!=256||air!=512)throw new AssertionError("weather queries changed actual world");
  Files.writeString(OUTPUT.resolve("scene.json"),new GsonBuilder().create().toJson(Map.of("water",water,"stone",stone,"air",air,"world","actual ServerLevel")));
  return rows;
 }
 static long gcMillis(){long total=0;for(var bean:ManagementFactory.getGarbageCollectorMXBeans())if(bean.getCollectionTime()>=0)total+=bean.getCollectionTime();return total;}
 static void run(MinecraftServer server){
  try{
   List<Map<String,Object>> rows=server.submit(()->{try{return benchmark(server);}catch(Exception failure){throw new RuntimeException(failure);}}).get();
   Files.writeString(OUTPUT.resolve("weather.json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));
   Files.writeString(OUTPUT.resolve("complete.json"),"{\"passed\":true}");server.halt(false);
  }catch(Throwable failure){try{Files.writeString(OUTPUT.resolve("failure.txt"),failure.toString());}catch(Exception ignored){}failure.printStackTrace();server.halt(false);}
 }
}
