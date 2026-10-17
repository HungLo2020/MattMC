package fullgeneration;

import com.google.gson.Gson;
import fallen.RegionSaveAgent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/** One controlled executor operation; no dirty/status writes, manufactured
 * cleanup, production tracing additions, or alternate unload continuation. */
public final class RegionSaveOrder {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Gson JSON = new Gson();
    private static final Set<String> SELECTED = Set.of("62,-46", "62,-47", "62,-48", "63,-48", "1,-1", "1,2", "2,2", "0,0");
    private static final Path OUTPUT = Path.of(System.getProperty("audit.output"));
    private static String key(ChunkPos p) { return p.x + "," + p.z; }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception { var m = type.getDeclaredMethod(name,args); m.setAccessible(true); return m; }
    private static Object field(Object object,String name) throws Exception { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> events() {
        return RegionSaveAgent.bufferedEvents().stream().map(s -> (Map<String,Object>)JSON.fromJson(s,Map.class)).toList();
    }
    private static boolean actualSave(Map<String,Object> e) { return e.get("event").equals("snapshot") && Boolean.TRUE.equals(e.get("actualSave")); }
    private static Set<String> saved(List<Map<String,Object>> events) { var out = new HashSet<String>(); for (var e:events) if(actualSave(e)) out.add((String)e.get("chunk")); return out; }
    public static void capture(Object value) {
        if (!STARTED.compareAndSet(false,true)) return;
        var server=(MinecraftServer)value;
        new Thread(() -> {
            var result=new TreeMap<String,Object>();result.put("mode",System.getProperty("save.order.mode"));result.put("preconditions",false);
            try {
                // All requests and release/unload operations execute within one
                // actual server-executor job. Synchronous getChunk uses its
                // existing managed loop; ordinary tick eager saves cannot
                // interleave with this controlled job.
                server.submit(() -> { try { exercise(server,result); } catch(Exception e) {throw new CompletionException(e);} }).get(300,TimeUnit.SECONDS);
            } catch(Throwable failure) {result.put("failure",failure.toString());failure.printStackTrace();}
            finally {
                try {Files.writeString(OUTPUT.resolve("save-order-controller.json"),JSON.toJson(result));} catch(Exception e) {e.printStackTrace();}
                server.halt(false);
            }
        },"save-order-controller").start();
    }
    @SuppressWarnings("unchecked") private static void exercise(MinecraftServer server,Map<String,Object> result) throws Exception {
        var level=server.getLevel(net.minecraft.world.level.Level.END); if(level==null)throw new IllegalStateException("No End");
        var source=level.getChunkSource();var map=source.chunkMap;
        var update=method(ServerChunkCache.class,"runDistanceManagerUpdates");
        var process=method(ChunkMap.class,"processUnloads",BooleanSupplier.class);
        var queue=(Queue<Runnable>)field(map,"unloadQueue");
        boolean early=System.getProperty("save.order.mode").equals("early");
        var cases=new ArrayList<Map<String,Object>>();result.put("cases",cases);
        for(var center:List.of(new ChunkPos(64,-48),new ChunkPos(0,0))) {
            var positions=FullGenAudit.positions(center.x,center.z,2);
            var margin=FullGenAudit.positions(center.x,center.z,3);
            for(var pos:margin) {
                source.addTicketWithRadius(TicketType.PLAYER_LOADING,pos,FullGenAudit.FEATURES_RADIUS);
                update.invoke(source);
                if(source.getChunk(pos.x,pos.z,ChunkStatus.FEATURES,false)==null)throw new IllegalStateException("FEATURES not available "+pos);
            }
            for(var pos:positions)source.addTicketWithRadius(TicketType.PLAYER_LOADING,pos,0);
            update.invoke(source);
            for(var pos:positions)if(source.getChunk(pos.x,pos.z,ChunkStatus.FULL,false)==null)throw new IllegalStateException("FULL not available "+pos);
            var selected=positions.stream().filter(p->SELECTED.contains(key(p))).toList();
            var selectedKeys=new HashSet<String>();selected.forEach(p->selectedKeys.add(key(p)));
            var row=new TreeMap<String,Object>();row.put("center",key(center));row.put("fullChunks",positions.size());row.put("featuresMargin",margin.size());row.put("selected",selectedKeys);cases.add(row);
            var before=events();var premature=new HashSet<>(saved(before));premature.retainAll(selectedKeys);row.put("successfulSavesBeforeControlledBoundary",premature);
            if(!premature.isEmpty())throw new IllegalStateException("Unsupported: genuine earlier save already cleared selected dirty state "+premature);
            var dirty=new TreeMap<String,Boolean>();
            for(var pos:selected) {
                var chunk=source.getChunk(pos.x,pos.z,ChunkStatus.FULL,false);dirty.put(key(pos),chunk.isUnsaved());
                SerializableChunkData.copyOf(level,chunk); // genuine baseline original public reads
            }
            row.put("naturallyDirtyBeforeBoundary",dirty);
            if(dirty.values().stream().anyMatch(v->!v))throw new IllegalStateException("Unsupported: selected chunk naturally clean before release");
            if(early) {
                level.save(null,true,false);map.flushWorker();
                var heldSaves=new HashSet<>(saved(events()));heldSaves.retainAll(selectedKeys);row.put("savedWhileTicketsHeld",heldSaves);
                if(!heldSaves.containsAll(selectedKeys))throw new IllegalStateException("Unsupported: actual early save did not save every selected chunk while tickets held");
            }
            // Original ticket removal, with all originally admitted tickets.
            for(var pos:positions)source.removeTicketWithRadius(TicketType.PLAYER_LOADING,pos,0);
            for(var pos:margin)source.removeTicketWithRadius(TicketType.PLAYER_LOADING,pos,FullGenAudit.FEATURES_RADIUS);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);boolean reached=false;
            while(System.nanoTime()<deadline) {
                update.invoke(source);
                // Real processUnloads chooses/drops holders and schedules/runs
                // the original save-future continuations. Its real budget
                // supplier permits queued unload work and becomes false when
                // empty, before the later eager-save loop.
                process.invoke(map,(BooleanSupplier)()->!queue.isEmpty());
                source.pollTask();
                var current=events();var cleanup=new HashSet<String>();for(var e:current)if(e.get("event").equals("live-updateChunkStatus-enter"))cleanup.add((String)e.get("chunk"));
                if(cleanup.containsAll(selectedKeys)&&saved(current).containsAll(selectedKeys)) {reached=true;break;}
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            row.put("genuineUnloadContinuationsReached",reached);map.flushWorker();
            if(!reached)throw new IllegalStateException("Unsupported: real save-future/unload queue did not reach all selected chunks under legitimate controls");
            var current=events();var last=new TreeMap<String,Map<String,Object>>();for(var e:current)if(actualSave(e)&&selectedKeys.contains(e.get("chunk")))last.put((String)e.get("chunk"),e);
            row.put("lastSuccessfulSaveCallers",last.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().get("callers"))));
            for(var entry:last.entrySet()) {
                var callers=(List<String>)entry.getValue().get("callers");boolean unload=callers.stream().anyMatch(s->s.contains("lambda$scheduleUnload"));
                if(!early&&!unload)throw new IllegalStateException("Unsupported: last actual successful save used wrong route "+entry.getKey()+" "+callers);
                double saveOrder=((Number)entry.getValue().get("order")).doubleValue();
                if(current.stream().noneMatch(e->e.get("event").equals("live-updateChunkStatus-enter")&&entry.getKey().equals(e.get("chunk"))&&((Number)e.get("order")).doubleValue()>saveOrder))throw new IllegalStateException("Own cleanup enqueue did not follow original save");
            }
            row.put("preconditions",true);
        }
        result.put("preconditions",true);result.put("noForcedDirtyStatusOrLightCleanup",true);
    }
}
