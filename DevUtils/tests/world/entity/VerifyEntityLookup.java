import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;

/** Same production EntityLookup calls against Current and untouched Frozen. */
public final class VerifyEntityLookup {
    static final List<String> events=new ArrayList<>();
    static class Member implements EntityAccess {
        final int label;
        int key;
        UUID uuid;
        Runnable beforeId,beforeUuid;
        BlockPos position=BlockPos.ZERO;
        boolean always;
        EntityInLevelCallback callback=EntityInLevelCallback.NULL;
        Member(int label,int key,UUID uuid){this.label=label;this.key=key;this.uuid=uuid;}
        @Override public int getId(){events.add("id:"+label);var hook=beforeId;beforeId=null;if(hook!=null)hook.run();return key;}
        @Override public UUID getUUID(){events.add("uuid:"+label);var hook=beforeUuid;beforeUuid=null;if(hook!=null)hook.run();return uuid;}
        @Override public boolean isRemoved(){return false;}
        @Override public BlockPos blockPosition(){return position;}
        @Override public AABB getBoundingBox(){return new AABB(position);}
        @Override public void setLevelCallback(EntityInLevelCallback callback){this.callback=callback;}
        @Override public Stream<Member> getSelfAndPassengers(){return Stream.of(this);}
        @Override public Stream<Member> getPassengersAndSelf(){return Stream.of(this);}
        @Override public void setRemoved(Entity.RemovalReason reason){callback.onRemove(reason);}
        @Override public boolean shouldBeSaved(){return true;}
        @Override public boolean isAlwaysTicking(){return always;}
        @Override public String toString(){return "Member["+label+"]";}
    }
    static int label(Member m){return m==null ? -1 : m.label;}
    @SuppressWarnings("unchecked") static <E extends Throwable> void sneaky(Throwable t)throws E{throw (E)t;}
    static void call(DataOutputStream out,Runnable operation)throws IOException {
        try{operation.run();out.writeUTF("ok");}
        catch(Throwable t){out.writeUTF(t.getClass().getName());out.writeUTF(t instanceof NullPointerException ? "null" : String.valueOf(t.getMessage()));}
        out.writeInt(events.size());for(var event:events)out.writeUTF(event);events.clear();
    }
    static void state(DataOutputStream out,EntityLookup<Member> lookup,Member[] members)throws IOException {
        out.writeInt(lookup.count());
        var iterator=lookup.getAllEntities().iterator();var labels=new ArrayList<Integer>();
        for(int i=0;i<4096 && iterator.hasNext();i++)labels.add(label(iterator.next()));
        out.writeBoolean(iterator.hasNext());out.writeInt(labels.size());for(int value:labels)out.writeInt(value);
        for(var member:members){out.writeInt(label(lookup.getEntity(member.key)));out.writeInt(label(lookup.getEntity(member.uuid)));}
        out.writeInt(label(lookup.getEntity((UUID)null)));out.writeInt(label(lookup.getEntity(new UUID(0,0))));
        if(!events.isEmpty())throw new AssertionError("State reads invoked member hooks");
    }
    static Member[] members(int count) {
        int[] keys={0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,1,2,3,4};
        var members=new Member[count];
        for(int i=0;i<count;i++)members[i]=new Member(i,i<8 ? keys[i] : i,new UUID(i+1,i*17L));
        return members;
    }
    static void typed(DataOutputStream out,EntityLookup<Member> lookup,Runnable mutation,int abortAt)throws IOException {
        boolean[] first={true};int[] casts={0};var observed=new ArrayList<Integer>();
        EntityTypeTest<Member,Member> type=new EntityTypeTest<>() {
            @Override public Member tryCast(Member m){if(++casts[0]>4096)throw new IllegalStateException("bounded callback prefix");events.add("cast:"+label(m));if(first[0]){first[0]=false;if(mutation!=null)mutation.run();}return m;}
            @Override public Class<? extends Member> getBaseClass(){throw new AssertionError("Unexpected base-class lookup");}
        };
        call(out,() -> lookup.getEntities(type,m -> {observed.add(label(m));return observed.size()>=abortAt ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;}));
        out.writeInt(observed.size());for(int value:observed)out.writeInt(value);
    }
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static LevelCallback<Member> callbacks() {
        return new LevelCallback<>() {
            public void onCreated(Member m){events.add("created:"+m.label);}
            public void onDestroyed(Member m){events.add("destroyed:"+m.label);}
            public void onTickingStart(Member m){events.add("tick-start:"+m.label);}
            public void onTickingEnd(Member m){events.add("tick-end:"+m.label);}
            public void onTrackingStart(Member m){events.add("track-start:"+m.label);}
            public void onTrackingEnd(Member m){events.add("track-end:"+m.label);}
            public void onSectionChange(Member m){events.add("section:"+m.label);}
        };
    }
    static void managerState(DataOutputStream out,LevelEntityGetter<Member> getter,Member[] members,String stats)throws IOException {
        out.writeUTF(stats);
        var list=new ArrayList<Integer>();for(var member:getter.getAll())list.add(label(member));
        out.writeInt(list.size());for(int value:list)out.writeInt(value);
        for(var member:members){out.writeInt(label(getter.get(member.key)));out.writeInt(label(getter.get(member.uuid)));}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var bytes=new ByteArrayOutputStream();int cases=0;
        try(var out=new DataOutputStream(bytes)) {
            var lookup=new EntityLookup<Member>();var m=members(64);var live=lookup.getAllEntities();
            call(out,() -> lookup.getEntities(null,null));cases++;
            var empty=live.iterator();call(out,empty::next);call(out,empty::remove);cases++;
            for(var member:m){call(out,() -> lookup.add(member));state(out,lookup,m);cases++;}
            var iterator=live.iterator();out.writeInt(label(iterator.next()));call(out,iterator::remove);cases++;
            // Replacement changes ID values but leaves independent UUID entries.
            m[63].key=m[0].key;call(out,() -> lookup.remove(m[63]));call(out,() -> lookup.add(m[63]));state(out,lookup,m);cases++;
            m[0].uuid=null;call(out,() -> lookup.add(m[0]));m[1].uuid=new UUID(0,0);call(out,() -> lookup.add(m[1]));state(out,lookup,m);cases++;
            // Duplicate UUID must not call ID or repeat the UUID getter.
            m[2].uuid=m[3].uuid;m[2].beforeId=() -> {throw new AssertionError("duplicate ID getter");};call(out,() -> lookup.add(m[2]));m[2].beforeId=null;cases++;
            Throwable failure=new IOException("checked ID getter");
            m[4].uuid=new UUID(10000,4);m[4].beforeId=() -> VerifyEntityLookup.<RuntimeException>sneaky(failure);
            call(out,() -> {try{lookup.add(m[4]);}catch(Throwable caught){if(caught!=failure)throw new AssertionError("Getter identity lost");VerifyEntityLookup.<RuntimeException>sneaky(caught);}});state(out,lookup,m);cases++;
            // Remove commits UUID before ID getter, including reentrant mutation.
            m[5].beforeId=() -> {events.add("during-remove:"+label(lookup.getEntity(m[5].uuid)));lookup.add(m[6]);};call(out,() -> lookup.remove(m[5]));state(out,lookup,m);cases++;
            m[7].uuid=new UUID(10000,7);m[7].beforeId=() -> {lookup.remove(m[7]);lookup.add(m[7]);};call(out,() -> lookup.add(m[7]));state(out,lookup,m);cases++;
            Random random=new Random(0x4c4f4f4b55504cL);
            for(int i=0;i<4096;i++) {
                var a=m[random.nextInt(m.length)];var b=m[random.nextInt(m.length)];int operation=random.nextInt(7);out.writeInt(operation);
                if(operation==0)call(out,() -> lookup.add(a));
                else if(operation==1)call(out,() -> lookup.remove(a));
                else if(operation==2){a.uuid=random.nextBoolean()?null:new UUID(random.nextInt(96),random.nextInt(96));a.key=random.nextInt(96)-48;call(out,() -> lookup.add(a));}
                else if(operation==3)typed(out,lookup,() -> {lookup.remove(a);lookup.add(b);},4096);
                else if(operation==4)typed(out,lookup,null,1+random.nextInt(10));
                else if(operation==5){a.beforeUuid=() -> lookup.remove(b);call(out,() -> lookup.add(a));}
                else {a.beforeId=() -> {lookup.remove(b);lookup.add(b);};call(out,() -> lookup.remove(a));}
                a.beforeId=null;a.beforeUuid=null;state(out,lookup,m);cases++;
            }
            // Live iterator indices after collision shifts, growth and shrinking.
            for(int mode=0;mode<8;mode++) {
                var l=new EntityLookup<Member>();var group=members(512);
                for(int i=0;i<32;i++)l.add(group[i]);events.clear();
                int action=mode;
                typed(out,l,() -> {
                    if(action==0)l.remove(group[0]);
                    else if(action==1)l.remove(group[1]);
                    else if(action==2)for(int i=0;i<24;i++)l.remove(group[i]);
                    else if(action==3)for(int i=32;i<512;i++)l.add(group[i]);
                    else if(action==4){for(int i=0;i<32;i++)l.remove(group[i]);l.add(group[0]);}
                    else if(action==5){group[31].key=group[1].key;l.remove(group[31]);l.add(group[31]);}
                    else if(action==6)l.getEntities(EntityTypeTest.forClass(Member.class), e -> AbortableIterationConsumer.Continuation.CONTINUE);
                    else throw new IllegalStateException("typed callback");
                },4096);
                state(out,l,group);cases++;
            }
            // Production client/server manager consumers, not a parallel model.
            for(int mode=0;mode<8;mode++) {
                var group=members(4);group[0].always=(mode&1)!=0;
                var client=new TransientEntitySectionManager<>(Member.class,callbacks());
                for(var member:group){call(out,() -> client.addEntity(member));cases++;}
                call(out,() -> client.startTicking(new ChunkPos(0,0)));managerState(out,client.getEntityGetter(),group,client.gatherStats());cases++;
                for(var member:group){member.position=new BlockPos(32,mode*16,-32);call(out,member.callback::onMove);cases++;}
                call(out,() -> client.startTicking(new ChunkPos(2,-2)));call(out,() -> client.stopTicking(new ChunkPos(2,-2)));
                managerState(out,client.getEntityGetter(),group,client.gatherStats());cases++;
                for(var member:group){call(out,() -> member.setRemoved(Entity.RemovalReason.DISCARDED));cases++;}
                managerState(out,client.getEntityGetter(),group,client.gatherStats());
                EntityPersistentStorage<Member> storage=new EntityPersistentStorage<>() {
                    public CompletableFuture<ChunkEntities<Member>> loadEntities(ChunkPos p){events.add("load:"+p.x+":"+p.z);return CompletableFuture.completedFuture(new ChunkEntities<>(p,List.of()));}
                    public void storeEntities(ChunkEntities<Member> chunk){events.add("store:"+chunk.getEntities().count());}
                    public void flush(boolean sync){events.add("flush:"+sync);}
                };
                var server=new PersistentEntitySectionManager<>(Member.class,callbacks(),storage);
                for(var member:group){call(out,() -> {if(!server.addNewEntity(member))throw new AssertionError("Initial UUID rejected");});cases++;}
                for(var visibility:Visibility.values()) {
                    call(out,() -> server.updateChunkStatus(new ChunkPos(2,-2),visibility));
                    call(out,server::processPendingLoads);managerState(out,server.getEntityGetter(),group,server.gatherStats());cases++;
                }
                for(var member:group){member.position=BlockPos.ZERO;call(out,member.callback::onMove);cases++;}
                call(out,() -> server.updateChunkStatus(new ChunkPos(0,0),Visibility.TICKING));call(out,server::processPendingLoads);
                managerState(out,server.getEntityGetter(),group,server.gatherStats());cases++;
                var reason=Entity.RemovalReason.values()[mode%5];
                for(var member:group){call(out,() -> member.setRemoved(reason));cases++;}
                managerState(out,server.getEntityGetter(),group,server.gatherStats());
            }
        }
        var data=bytes.toByteArray();Files.write(Path.of(args[0]),data);
        System.out.println("LOOKUP_CASES "+cases+" bytes="+data.length+" sha="+sha(data));
        var classes=new ArrayList<>(List.of("net.minecraft.world.level.entity.EntityLookup","net.minecraft.world.level.entity.TransientEntitySectionManager","net.minecraft.world.level.entity.TransientEntitySectionManager$Callback","net.minecraft.world.level.entity.PersistentEntitySectionManager","net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback","net.minecraft.world.level.entity.LevelEntityGetterAdapter"));
        if(args[1].equals("current"))classes.addAll(List.of("net.minecraft.world.level.entity.NativeEntityLookup","net.minecraft.world.level.entity.NativeEntityLookup$1"));
        for(String name:classes)try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        if(args[1].equals("current")) {
            var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line -> line.endsWith(" "+nativePath)))throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(nativePath))+" "+nativePath);
        }
    }
}
