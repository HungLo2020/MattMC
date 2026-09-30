package surfaceverification;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.management.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class WorldgenAudit {
 static final boolean HASH=Boolean.getBoolean("audit.hash");
 static volatile boolean hashEnabled;
 static final java.util.concurrent.ConcurrentMap<String,String> surfaceHashes=new java.util.concurrent.ConcurrentHashMap<>();
 static final java.util.concurrent.ConcurrentMap<String,String> carverHashes=new java.util.concurrent.ConcurrentHashMap<>();
 public static void recordCarvers(Object value) {
  if(!hashEnabled)return;
  try {var chunk=(ChunkAccess)value;carverHashes.put(chunk.getPos().toString(),net.minecraft.world.level.levelgen.NativeSurfaceVerification.fingerprint(chunk));}
  catch(Exception e){throw new RuntimeException(e);}
 }
 public static void recordSurface(Object value) {
  if(!hashEnabled)return;
  try {var chunk=(ChunkAccess)value;surfaceHashes.put(chunk.getPos().toString(),net.minecraft.world.level.levelgen.NativeSurfaceVerification.fingerprint(chunk));}
  catch(Exception e){throw new RuntimeException(e);}
 }
 static final AtomicBoolean started=new AtomicBoolean();
 static final Path output=Path.of(System.getProperty("audit.output")).toAbsolutePath();
 static final Gson gson=new GsonBuilder().setPrettyPrinting().create();
 static final List<Map<String,Object>> results=new ArrayList<>(),saves=new ArrayList<>();
 static final com.sun.management.OperatingSystemMXBean os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
 static final com.sun.management.ThreadMXBean threads=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static final int radius=Integer.getInteger("audit.radius",5),rounds=Integer.getInteger("audit.rounds",3),warmRadius=Integer.getInteger("audit.warmRadius",3);
 public static void capture(Object object) {
  if(started.compareAndSet(false,true))new Thread(()->run((MinecraftServer)object),"worldgen-audit-controller").start();
 }
 static long allocated(){long n=0;for(long b:threads.getThreadAllocatedBytes(threads.getAllThreadIds()))if(b>0)n+=b;return n;}
 static long gcTime(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();}
 static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();}
 static void write() throws Exception {Files.writeString(output.resolve("timings.json"),gson.toJson(results));}
 static List<ChunkPos> positions(int centerX,int centerZ,int radius){List<ChunkPos> ps=new ArrayList<>();for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++)ps.add(new ChunkPos(centerX+x,centerZ+z));return ps;}
 static Map<String,Object> generate(MinecraftServer server,ServerLevel level,List<ChunkPos> ps,String label)throws Exception {
  long wall=System.nanoTime(),cpu=os.getProcessCpuTime(),alloc=allocated(),gc=gcTime(),gcn=gcCount(),jit=ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
  boolean serial=Boolean.getBoolean("audit.serial");
  if(!serial)server.submit(()->{for(ChunkPos p:ps)level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING,p,0);}).get();
  List<CompletableFuture<ChunkResult<ChunkAccess>>> futures=new ArrayList<>();
  long[] latencies=new long[ps.size()];
  for(int index=0;index<ps.size();index++) {ChunkPos p=ps.get(index);int slot=index;if(serial)server.submit(()->level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING,p,0)).get();long issued=System.nanoTime();futures.add(level.getChunkSource().getChunkFuture(p.x,p.z,ChunkStatus.FULL,true).whenComplete((v,error)->latencies[slot]=System.nanoTime()-issued));if(serial)futures.getLast().get(15,TimeUnit.MINUTES);}
  CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(15,TimeUnit.MINUTES);
  long end=System.nanoTime(),cpuEnd=os.getProcessCpuTime(),allocEnd=allocated();
  long checksum=0;for(var f:futures){ChunkAccess c=f.join().orElse(null);if(c==null)throw new IllegalStateException("Chunk generation failed: "+f.join().getError());if(!c.getPersistedStatus().isOrAfter(ChunkStatus.FULL))throw new IllegalStateException("Incomplete chunk");checksum=31*checksum+c.getPos().toLong();}
  Map<String,String> fullHashes=new TreeMap<>();
  if(HASH && label.startsWith("measure"))for(var f:futures){var c=f.join().orElse(null);fullHashes.put(c.getPos().toString(),net.minecraft.world.level.levelgen.NativeSurfaceVerification.fingerprint(c));}
  Map<String,Object> row=new LinkedHashMap<>();if(!fullHashes.isEmpty())row.put("full_fingerprints",fullHashes);row.put("label",label);row.put("dimension",level.dimension().location().toString());row.put("target_chunks",ps.size());row.put("first_chunk_x",ps.getFirst().x);row.put("first_chunk_z",ps.getFirst().z);row.put("wall_seconds",(end-wall)/1e9);row.put("process_cpu_seconds",(cpuEnd-cpu)/1e9);row.put("allocated_bytes",allocEnd-alloc);row.put("gc_ms",gcTime()-gc);row.put("gc_count",gcCount()-gcn);row.put("jit_ms",ManagementFactory.getCompilationMXBean().getTotalCompilationTime()-jit);Arrays.sort(latencies);row.put("chunk_request_p50_ms",latencies[latencies.length/2]/1e6);row.put("chunk_request_p95_ms",latencies[Math.min(latencies.length-1,(int)Math.floor(latencies.length*.95))]/1e6);row.put("checksum",checksum);row.put("heap_used_bytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
  System.out.println("AUDIT_RESULT "+new Gson().toJson(row));results.add(row);write();
  return row;
 }
 static void release(MinecraftServer server,ServerLevel level,List<ChunkPos> ps)throws Exception {
  server.submit(()->{for(ChunkPos p:ps)level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING,p,0);}).get();
  // Separate generation from explicit save/drain; allow expired UNKNOWN tickets
  // and neighboring dependency chunks to unload between independent regions.
  long before=System.nanoTime(),cpu=os.getProcessCpuTime(),alloc=allocated();server.submit(()->level.save(null,true,false)).get();
  var save=new LinkedHashMap<String,Object>();save.put("dimension",level.dimension().location().toString());save.put("after",results.getLast().get("label"));save.put("wall_seconds",(System.nanoTime()-before)/1e9);save.put("process_cpu_seconds",(os.getProcessCpuTime()-cpu)/1e9);save.put("allocated_bytes",allocated()-alloc);saves.add(save);Files.writeString(output.resolve("saves.json"),gson.toJson(saves));
  System.out.println("AUDIT_SAVE "+new Gson().toJson(save));
  Thread.sleep(1500);
 }
 static void run(MinecraftServer server){
  try {
   Files.createDirectories(output);threads.setThreadAllocatedMemoryEnabled(true);
   List<ServerLevel> levels=new ArrayList<>();server.getAllLevels().forEach(levels::add);levels.sort(Comparator.comparing(l->l.dimension().location().toString()));
   String select=System.getProperty("audit.dimension","");if(!select.isBlank())levels.removeIf(l->!l.dimension().location().getPath().equals(select));
   if(levels.isEmpty())throw new IllegalArgumentException("No matching dimensions");
   System.out.println("AUDIT_LEVELS "+levels.stream().map(l->l.dimension().location().toString()).toList());
   for(ServerLevel level:levels) {
    String name=level.dimension().location().getPath();
    for(int w=0;w<Integer.getInteger("audit.warmups",2);w++){
     var ps=positions(2048+w*128,2048,warmRadius);generate(server,level,ps,"warmup-"+w);release(server,level,ps);
    }
    for(int i=0;i<rounds;i++){
     String stem=name+"-"+i;
     surfaceHashes.clear();carverHashes.clear();hashEnabled=HASH;
     var ps=positions(4096+i*128,4096,radius);generate(server,level,ps,"measure-"+i);
     hashEnabled=false;
     if(HASH)Files.writeString(output.resolve(stem+"-surface.json"),gson.toJson(new TreeMap<>(surfaceHashes)));
     if(HASH)Files.writeString(output.resolve(stem+"-carvers.json"),gson.toJson(new TreeMap<>(carverHashes)));
     release(server,level,ps);
    }
   }
   Files.writeString(output.resolve("complete.json"),gson.toJson(Map.of("success",true,"rows",results.size(),"pid",ProcessHandle.current().pid())));
   System.out.println("AUDIT_COMPLETE");server.halt(false);
  }catch(Throwable t){t.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),t.toString());}catch(Exception ignored){}server.halt(false);}
 }
}
