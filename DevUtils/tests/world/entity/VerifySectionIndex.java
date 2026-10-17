import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;

/** Identical production section-storage operations on Current and Frozen. */
public final class VerifySectionIndex {
    static final List<String> events = new ArrayList<>();
    static int cases;
    static final class Member implements EntityAccess {
        final int id;
        final AABB bounds;
        Runnable boundsHook;
        Member(int id,long key) {
            this.id=id;
            double x=SectionPos.x(key)*16.0,y=SectionPos.y(key)*16.0,z=SectionPos.z(key)*16.0;
            bounds=new AABB(x,y,z,x+2,y+2,z+2);
        }
        public int getId(){return id;}
        public UUID getUUID(){return new UUID(0,id);}
        public boolean isRemoved(){return false;}
        public BlockPos blockPosition(){return BlockPos.ZERO;}
        public AABB getBoundingBox(){events.add("bounds:"+id);Runnable h=boundsHook;boundsHook=null;if(h!=null)h.run();return bounds;}
        public void setLevelCallback(EntityInLevelCallback c){}
        public Stream<Member> getSelfAndPassengers(){return Stream.of(this);}
        public Stream<Member> getPassengersAndSelf(){return Stream.of(this);}
        public void setRemoved(Entity.RemovalReason r){}
        public boolean shouldBeSaved(){return true;}
        public boolean isAlwaysTicking(){return false;}
    }
    static final class Fixture {
        final IdentityHashMap<EntitySection<Member>,Integer> identities=new IdentityHashMap<>();
        final EntitySectionStorage<Member> storage;
        Runnable visibilityHook;
        Visibility visibility=Visibility.TICKING;
        int next;
        Fixture() {
            storage=new EntitySectionStorage<>(Member.class,(it.unimi.dsi.fastutil.longs.Long2ObjectFunction<Visibility>)key->{
                events.add("visibility:"+key);Runnable hook=visibilityHook;visibilityHook=null;
                if(hook!=null)hook.run();return visibility;
            });
        }
        EntitySection<Member> create(long key) {
            var s=storage.getOrCreateSection(key);identities.computeIfAbsent(s,ignored->next++);return s;
        }
        String label(EntitySection<Member> s) {return s==null?"null":"s"+identities.get(s)+":"+s.getStatus()+":"+s.size();}
    }
    static void call(PrintWriter out,String label,Runnable action) {
        out.print(label+":");
        try {action.run();out.print("ok");}
        catch(Throwable t){out.print(t.getClass().getName()+":"+t.getMessage());}
        out.println(" events="+events);events.clear();cases++;
    }
    static void state(PrintWriter out,Fixture f,long[] keys) {
        call(out,"count/chunks",()->events.add(f.storage.count()+":"+Arrays.toString(f.storage.getAllChunksWithExistingSections().toLongArray())));
        call(out,"lookups",()->{for(long key:keys)events.add(key+"="+f.label(f.storage.getSection(key)));});
    }
    static long chunk(long key){return ChunkPos.asLong(SectionPos.x(key),SectionPos.z(key));}
    static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        StringWriter buffer=new StringWriter();
        try(PrintWriter out=new PrintWriter(buffer)) {
            long[] keys=new long[96];
            for(int i=0;i<keys.length;i++)keys[i]=SectionPos.asLong(i%7-3,i%11-5,i%5-2);
            Fixture f=new Fixture();Random rng=new Random(0x53454354494e4458L);
            for(int step=0;step<2048;step++) {
                long key=keys[rng.nextInt(keys.length)];int operation=rng.nextInt(6);
                call(out,"random:"+step+":"+operation,()->{
                    switch(operation) {
                        case 0->events.add("create="+f.label(f.create(key)));
                        case 1->f.storage.remove(key);
                        case 2->{var s=f.create(key);s.add(new Member((int)key,key));}
                        case 3->{var s=f.create(key);events.add("status="+s.updateChunkStatus(Visibility.values()[(int)(key&3)%3]));}
                        case 4->events.add(Arrays.toString(f.storage.getExistingSectionPositionsInChunk(chunk(key)).toArray()));
                        default->f.storage.forEachAccessibleNonEmptySection(new AABB(-32,-32,-32,32,32,32),s->{events.add(f.label(s));return AbortableIterationConsumer.Continuation.CONTINUE;});
                    }
                });
                state(out,f,new long[]{key});
            }
            // Signed packing limits, raw keys, nonfinite coordinates and nulls.
            Fixture edge=new Fixture();
            long[] extreme={Long.MIN_VALUE,Long.MAX_VALUE,-1,0,1,SectionPos.asLong(2_097_151,0,-1),SectionPos.asLong(-2_097_152,-524_288,2_097_151)};
            for(long k:extreme)call(out,"edge-create:"+k,()->events.add(edge.label(edge.create(k))));
            state(out,edge,extreme);
            for(long k:extreme)call(out,"edge-chunk:"+k,()->events.add(Arrays.toString(edge.storage.getExistingSectionPositionsInChunk(chunk(k)).toArray())));
            for(double v:new double[]{0,-0.0,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE,-Double.MAX_VALUE,2_097_151.0*16.0+2.0}) {
                AABB box=new AABB(v,v,v,v,v,v);
                call(out,"edge-spatial:"+v,()->edge.storage.forEachAccessibleNonEmptySection(box,s->{events.add(edge.label(s));return AbortableIterationConsumer.Continuation.CONTINUE;}));
            }
            call(out,"null-box",()->edge.storage.forEachAccessibleNonEmptySection(null,null));
            Fixture nulls=new Fixture();var nullSection=nulls.create(0);nullSection.add(new Member(0,0));
            call(out,"null-consumer",()->nulls.storage.forEachAccessibleNonEmptySection(new AABB(0,0,0,1,1,1),null));
            nullSection.updateChunkStatus(null);
            call(out,"null-visibility",()->nulls.storage.forEachAccessibleNonEmptySection(new AABB(0,0,0,1,1,1),s->AbortableIterationConsumer.Continuation.CONTINUE));
            var noVisibility=new EntitySectionStorage<Member>(Member.class,null);
            call(out,"null-visibility-function",()->noVisibility.getOrCreateSection(0));
            // Callback reentry retains the outer find's physical insertion slot.
            for(int size:new int[]{0,1,23,24,25,48,96})for(int mutation=0;mutation<6;mutation++) {
                Fixture g=new Fixture();long outer=SectionPos.asLong(-1,2,-3);
                for(int i=0;i<size;i++)g.create(keys[i]);events.clear();int mode=mutation;
                g.visibilityHook=()->{
                    events.add("nested:"+mode);
                    switch(mode) {
                        case 0->g.create(outer);
                        case 1->{for(long k:keys)g.create(k);}
                        case 2->{for(long k:keys)g.storage.remove(k);}
                        case 3->{g.create(outer);g.storage.remove(outer);}
                        case 4->throw new IllegalStateException("visibility callback");
                        case 5->g.visibility=null;
                    }
                };
                call(out,"reentry:"+size+":"+mode,()->events.add("outer="+g.label(g.create(outer))));
                state(out,g,new long[]{outer,keys[0],keys[23],keys[95]});
                call(out,"after-reentry-query",()->g.storage.forEachAccessibleNonEmptySection(new AABB(-80,-96,-80,80,96,80),s->{events.add(g.label(s));return AbortableIterationConsumer.Continuation.CONTINUE;}));
            }
            // Streams capture a live node, not an immutable array; removal,
            // reinsertion and rotations after creation remain observable.
            for(int size:new int[]{0,1,3,10,40})for(int mutation=0;mutation<8;mutation++)for(int kind=0;kind<3;kind++) {
                Fixture g=new Fixture();long[] group=new long[size];
                for(int i=0;i<size;i++){group[i]=SectionPos.asLong(-2,i-20,3);var s=g.create(group[i]);s.add(new Member(i,group[i]));}
                events.clear();int mode=mutation;
                LongStream positions=g.storage.getExistingSectionPositionsInChunk(ChunkPos.asLong(-2,3));
                Stream<EntitySection<Member>> sections=g.storage.getExistingSectionsInChunk(ChunkPos.asLong(-2,3));
                Runnable mutate=()->{
                    events.add("mutation:"+mode);
                    switch(mode) {
                        case 0->g.create(SectionPos.asLong(-2,-22,3));
                        case 1->g.create(SectionPos.asLong(-2,22,3));
                        case 2->{for(long k:group)g.storage.remove(k);}
                        case 3->{for(long k:group)g.storage.remove(k);for(long k:group)g.create(k);}
                        case 4->{if(size>1)g.storage.remove(group[1]);}
                        case 5->throw new IllegalStateException("stream callback");
                        case 6->{for(int y=-40;y<40;y++)g.create(SectionPos.asLong(-2,y,3));}
                        default->{}
                    }
                };
                boolean[] first={true};int action=kind;
                call(out,"stream:"+size+":"+mode+":"+kind,()->{
                    if(action==0){mutate.run();positions.forEach(k->events.add("key:"+k));}
                    else if(action==1)positions.forEach(k->{events.add("key:"+k);if(first[0]){first[0]=false;mutate.run();}});
                    else sections.forEach(s->{events.add(g.label(s));if(first[0]){first[0]=false;mutate.run();}});
                });state(out,g,group);
            }
            // Spatial search order, aborts, live status and membership changes.
            for(int mutation=0;mutation<7;mutation++) {
                Fixture g=new Fixture();for(int i=0;i<keys.length;i++){var s=g.create(keys[i]);s.add(new Member(i,keys[i]));}events.clear();int mode=mutation;boolean[] first={true};
                call(out,"spatial:"+mode,()->g.storage.forEachAccessibleNonEmptySection(new AABB(-64,-80,-48,64,96,48),s->{
                    events.add(g.label(s));if(first[0]){first[0]=false;switch(mode){
                        case 0->{for(long k:keys)g.storage.remove(k);}
                        case 1->{for(long k:keys)g.create(k).updateChunkStatus(Visibility.HIDDEN);}
                        case 2->{for(long k:keys)g.create(k).add(new Member(999,k));}
                        case 3->{for(int y=-20;y<20;y++)g.create(SectionPos.asLong(0,y,0)).add(new Member(888,SectionPos.asLong(0,y,0)));}
                        case 4->throw new IllegalStateException("spatial callback");
                        default->{}
                    }}return mode==5?AbortableIterationConsumer.Continuation.ABORT:AbortableIterationConsumer.Continuation.CONTINUE;
                }));state(out,g,keys);
            }
        }
        byte[] data=buffer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(Path.of(args[0]),data);
        System.out.println("INDEX_CASES "+cases+" bytes="+data.length+" sha="+sha(data));
        var classes=new ArrayList<>(List.of("net.minecraft.world.level.entity.EntitySectionStorage","net.minecraft.world.level.entity.EntitySection","net.minecraft.util.ClassInstanceMultiMap"));
        if(args[1].equals("current"))classes.addAll(List.of("net.minecraft.world.level.entity.NativeEntitySectionIndex","net.minecraft.world.level.entity.NativeEntitySectionIndex$Cursor","net.minecraft.util.NativeClassInstanceStorage","net.minecraft.util.NativeClassInstanceStorage$Group","net.minecraft.util.NativeClassInstanceStorage$Group$1","net.minecraft.util.NativeClassInstanceStorage$Group$MemberSpliterator","net.minecraft.util.NativeClassInstanceStorage$Captured"));
        for(String name:classes)try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        if(args[1].equals("current")) {
            var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+nativePath)))throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(nativePath))+" "+nativePath);
        }

    }
}
