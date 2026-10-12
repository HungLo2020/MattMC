import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;

/** Actual client/server manager state, asynchronous inbox and persistence callbacks. */
public final class VerifyEntityResidency {
    static final List<String> events=new ArrayList<>();
    static int cases;
    static final class Member implements EntityAccess {
        final int id;UUID uuid;BlockPos position=BlockPos.ZERO;boolean always,saved=true;
        Runnable uuidHook,saveHook,positionHook;
        EntityInLevelCallback callback=EntityInLevelCallback.NULL;
        Member(int id){this.id=id;uuid=new UUID(0,id);}
        public int getId(){events.add("id:"+id);return id;}
        public UUID getUUID(){events.add("uuid:"+id);var h=uuidHook;uuidHook=null;if(h!=null)h.run();return uuid;}
        public boolean isRemoved(){return false;}
        public BlockPos blockPosition(){events.add("position:"+id);var h=positionHook;positionHook=null;if(h!=null)h.run();return position;}
        public AABB getBoundingBox(){return new AABB(position);}
        public void setLevelCallback(EntityInLevelCallback value){events.add("callback:"+id+":"+(value==EntityInLevelCallback.NULL));callback=value;}
        public Stream<Member> getSelfAndPassengers(){return Stream.of(this);}
        public Stream<Member> getPassengersAndSelf(){events.add("passengers:"+id);return Stream.of(this);}
        public void setRemoved(Entity.RemovalReason reason){events.add("removed:"+id+":"+reason);callback.onRemove(reason);}
        public boolean shouldBeSaved(){events.add("saved:"+id);var h=saveHook;saveHook=null;if(h!=null)h.run();return saved;}
        public boolean isAlwaysTicking(){events.add("always:"+id);return always;}
        public String toString(){return "Member["+id+"]";}
    }
    static final class Storage implements EntityPersistentStorage<Member> {
        final LinkedHashMap<Long,CompletableFuture<ChunkEntities<Member>>> pending=new LinkedHashMap<>();
        Runnable loadHook,storeHook,flushHook;
        boolean immediate;
        public CompletableFuture<ChunkEntities<Member>> loadEntities(ChunkPos p){
            events.add("load:"+p.toLong());var h=loadHook;loadHook=null;if(h!=null)h.run();
            var future=new CompletableFuture<ChunkEntities<Member>>();pending.put(p.toLong(),future);
            if(immediate)future.complete(new ChunkEntities<>(p,List.of()));return future;
        }
        void complete(long chunk,List<Member> members){
            var f=pending.remove(chunk);events.add("complete:"+chunk+":"+(f!=null));if(f!=null)f.complete(new ChunkEntities<>(new ChunkPos(chunk),members));
        }
        void all(){var keys=new ArrayList<>(pending.keySet());for(long key:keys)complete(key,List.of());}
        public void storeEntities(ChunkEntities<Member> chunk){events.add("store:"+chunk.getPos().toLong()+":"+chunk.getEntities().map(m->Integer.toString(m.id)).toList());var h=storeHook;storeHook=null;if(h!=null)h.run();}
        public void flush(boolean sync){events.add("flush:"+sync);var h=flushHook;flushHook=null;if(h!=null)h.run();all();}
        public void close(){events.add("close-storage");}
    }
    static LevelCallback<Member> callbacks(){return new LevelCallback<>(){
        public void onCreated(Member m){events.add("created:"+m.id);}
        public void onDestroyed(Member m){events.add("destroyed:"+m.id);}
        public void onTickingStart(Member m){events.add("tick-start:"+m.id);}
        public void onTickingEnd(Member m){events.add("tick-end:"+m.id);}
        public void onTrackingStart(Member m){events.add("track-start:"+m.id);}
        public void onTrackingEnd(Member m){events.add("track-end:"+m.id);}
        public void onSectionChange(Member m){events.add("section:"+m.id);}
    };}
    static void call(PrintWriter out,String name,Runnable operation){
        out.print(name+":");try{operation.run();out.print("ok");}catch(Throwable t){out.print(t.getClass().getName()+":"+String.valueOf(t.getMessage()));}
        out.println(" events="+events);events.clear();cases++;
    }
    static void state(PrintWriter out,PersistentEntitySectionManager<Member> manager,Member[] members,long[] chunks){
        call(out,"server-state",()->{
            events.add("stats:"+manager.gatherStats());
            var ids=new ArrayList<Integer>();for(var m:manager.getEntityGetter().getAll())ids.add(m.id);events.add("visible:"+ids);
            for(var m:members)events.add("known:"+m.id+":"+manager.isLoaded(m.uuid));
            for(long key:chunks){var p=new ChunkPos(key);events.add("chunk:"+key+":"+manager.areEntitiesLoaded(key)+":"+manager.isTicking(p)+":"+manager.canPositionTick(p)+":"+manager.canPositionTick(new BlockPos(p.x*16,0,p.z*16)));}
        });
    }
    static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();StringWriter buffer=new StringWriter();
        try(PrintWriter out=new PrintWriter(buffer)){
            long[] chunks=new long[40];for(int i=0;i<chunks.length;i++)chunks[i]=ChunkPos.asLong(i%7-3,i/7-3);
            Storage storage=new Storage();var server=new PersistentEntitySectionManager<>(Member.class,callbacks(),storage);Member[] members=new Member[24];for(int i=0;i<members.length;i++){members[i]=new Member(i);members[i].position=new BlockPos((i%7-3)*16,0,(i/7-3)*16);members[i].always=(i%5)==0;}
            Random random=new Random(0x5245534944454e43L);
            for(int step=0;step<2048;step++){
                int op=random.nextInt(10);Member m=members[random.nextInt(members.length)];long key=chunks[random.nextInt(chunks.length)];Visibility v=Visibility.values()[random.nextInt(3)];
                call(out,"random:"+step+":"+op,()->{
                    switch(op){
                        case 0->events.add("add:"+server.addNewEntity(m));
                        case 1->server.updateChunkStatus(new ChunkPos(key),v);
                        case 2->storage.complete(key,List.of());
                        case 3->server.processPendingLoads();
                        case 4->server.tick();
                        case 5->server.autoSave();
                        case 6->{m.position=new BlockPos(ChunkPos.getX(key)*16,0,ChunkPos.getZ(key)*16);m.callback.onMove();}
                        case 7->m.setRemoved(Entity.RemovalReason.values()[random.nextInt(5)]);
                        case 8->{m.saved=!m.saved;events.add("saved-now:"+m.id+":"+m.saved);}
                        default->storage.all();
                    }
                });state(out,server,members,new long[]{key,chunks[0],chunks[39]});
            }
            call(out,"save-all",server::saveAll);state(out,server,members,chunks);
            call(out,"dump",()->{try{var text=new StringWriter();server.dumpSections(text);events.add(text.toString());}catch(IOException e){throw new UncheckedIOException(e);}});
            // Independent null and zero UUIDs, duplicate rejection and committed prefixes.
            for(int mode=0;mode<5;mode++){
                Storage s=new Storage();var g=new PersistentEntitySectionManager<>(Member.class,callbacks(),s);Member a=new Member(100),b=new Member(101);int action=mode;
                if(mode==0)a.uuid=null;else if(mode==1)a.uuid=new UUID(0,0);else if(mode==2)b.uuid=a.uuid;
                else if(mode==3)a.positionHook=()->{throw new IllegalStateException("position callback");};
                else a.uuidHook=()->g.addNewEntity(b);
                call(out,"uuid-case:"+action,()->events.add("add:"+g.addNewEntity(a)));call(out,"uuid-second",()->events.add("add:"+g.addNewEntity(b)));state(out,g,new Member[]{a,b},new long[]{0});
                call(out,"uuid-remove",()->a.setRemoved(Entity.RemovalReason.DISCARDED));state(out,g,new Member[]{a,b},new long[]{0});
            }
            // Storage/request callbacks may change residency while unloading iterates.
            for(int size:new int[]{0,1,3,24,25,96})for(int mode=0;mode<6;mode++){
                Storage s=new Storage();s.immediate=true;var g=new PersistentEntitySectionManager<>(Member.class,callbacks(),s);var group=new Member[size];
                for(int i=0;i<size;i++){long key=ChunkPos.asLong(i-48,i%5);group[i]=new Member(1000+i);group[i].position=new BlockPos((i-48)*16,0,(i%5)*16);g.addNewEntity(group[i]);g.updateChunkStatus(new ChunkPos(key),Visibility.TICKING);}g.processPendingLoads();events.clear();int action=mode;
                for(int i=0;i<size;i++)g.updateChunkStatus(new ChunkPos(i-48,i%5),Visibility.HIDDEN);
                events.clear();s.storeHook=()->{events.add("store-hook:"+action);switch(action){
                    case 0->g.updateChunkStatus(new ChunkPos(-48,0),Visibility.TICKING);
                    case 1->{for(int i=0;i<size;i++)g.updateChunkStatus(new ChunkPos(i-48,i%5),Visibility.TRACKED);}
                    case 2->{for(int i=0;i<200;i++)g.updateChunkStatus(new ChunkPos(200+i,0),Visibility.HIDDEN);}
                    case 3->g.tick();
                    case 4->throw new IllegalStateException("store callback");
                    default->{}
                }};
                call(out,"unload-reentry:"+size+":"+action,g::tick);state(out,g,group,new long[]{ChunkPos.asLong(-48,0),0});
                call(out,"unload-after",g::tick);
            }
            // shouldBeSaved observes and can mutate state after the load status capture.
            for(int mode=0;mode<4;mode++){
                Storage s=new Storage();s.immediate=true;var g=new PersistentEntitySectionManager<>(Member.class,callbacks(),s);Member m=new Member(5000);g.addNewEntity(m);g.updateChunkStatus(new ChunkPos(0,0),Visibility.TICKING);g.processPendingLoads();events.clear();int action=mode;
                m.saveHook=()->{events.add("save-hook:"+action);if(action==0)g.updateChunkStatus(new ChunkPos(0,0),Visibility.HIDDEN);else if(action==1)g.processPendingLoads();else if(action==2)m.saved=false;else throw new IllegalStateException("saved callback");};
                call(out,"save-reentry:"+action,g::autoSave);state(out,g,new Member[]{m},new long[]{0});
            }
            // Client ticking membership, statistics and production transitions.
            var client=new TransientEntitySectionManager<>(Member.class,callbacks());Member cm=new Member(9000);client.addEntity(cm);events.clear();
            for(int step=0;step<1024;step++){long key=chunks[step%chunks.length];boolean start=(step%3)!=0;call(out,"client:"+step,()->{if(start)client.startTicking(new ChunkPos(key));else client.stopTicking(new ChunkPos(key));cm.position=new BlockPos(ChunkPos.getX(key)*16,0,ChunkPos.getZ(key)*16);cm.callback.onMove();events.add(client.gatherStats());});}
        }
        byte[] data=buffer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(Path.of(args[0]),data);System.out.println("RESIDENCY_CASES "+cases+" bytes="+data.length+" sha="+sha(data));
        var classes=new ArrayList<>(List.of("net.minecraft.world.level.entity.PersistentEntitySectionManager","net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback","net.minecraft.world.level.entity.TransientEntitySectionManager","net.minecraft.world.level.entity.TransientEntitySectionManager$Callback"));
        if(args[1].equals("current"))classes.addAll(List.of("net.minecraft.world.level.entity.NativeEntityResidency","net.minecraft.world.level.entity.NativeEntityResidency$KnownUuids","net.minecraft.world.level.entity.NativeEntityResidency$EnumView","net.minecraft.world.level.entity.NativeEntityResidency$SetView","net.minecraft.world.level.entity.NativeEntityResidency$Cursor"));
        for(String name:classes)try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        if(args[1].equals("current")){var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+nativePath)))throw new AssertionError("Native image not loaded");System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(nativePath))+" "+nativePath);}
    }
}
