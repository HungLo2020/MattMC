import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;

/** Identical calls to actual Current/Frozen collections and section consumers. */
public final class VerifySectionMembers {
    static final List<String> events = new ArrayList<>();
    static int cases;
    static class Member implements EntityAccess {
        final int id;
        Runnable equalHook, boundsHook;
        boolean equalResult;
        Member(int id) { this.id=id; }
        @Override public boolean equals(Object other) {
            events.add("eq:"+id+":"+label(other));
            Runnable hook=equalHook;equalHook=null;if(hook!=null)hook.run();
            return equalResult || this==other;
        }
        @Override public int hashCode() { return id; }
        @Override public int getId() { return id; }
        @Override public UUID getUUID() { return new UUID(0,id); }
        @Override public boolean isRemoved() { return false; }
        @Override public BlockPos blockPosition() { return BlockPos.ZERO; }
        @Override public AABB getBoundingBox() {
            events.add("bounds:"+id);Runnable hook=boundsHook;boundsHook=null;if(hook!=null)hook.run();
            return new AABB(id,0,0,id+1,1,1);
        }
        @Override public void setLevelCallback(EntityInLevelCallback callback) {}
        @Override public Stream<Member> getSelfAndPassengers() { return Stream.of(this); }
        @Override public Stream<Member> getPassengersAndSelf() { return Stream.of(this); }
        @Override public void setRemoved(Entity.RemovalReason reason) {}
        @Override public boolean shouldBeSaved() { return true; }
        @Override public boolean isAlwaysTicking() { return false; }
        @Override public String toString() { return "Member["+id+"]"; }
    }
    static final class Typed extends Member { Typed(int id) { super(id); } }
    static String label(Object object) {
        return object==null ? "null" : object instanceof Member m ? "m"+m.id : object.toString();
    }
    static String labels(Collection<?> values) {
        var result=new ArrayList<String>();for(Object value:values)result.add(label(value));return result.toString();
    }
    static void call(PrintWriter out,String name,Runnable operation) {
        out.print(name+":");
        try { operation.run();out.print("ok"); }
        catch(Throwable failure) {
            out.print(failure.getClass().getName()+":"+String.valueOf(failure.getMessage()));
        }
        out.println(" events="+events);events.clear();cases++;
    }
    static void state(PrintWriter out,ClassInstanceMultiMap<?> values) {
        out.println("size="+values.size());call(out,"snapshot",()->events.add(labels(values.getAllInstances())));
    }
    static String sha(byte[] bytes)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static Runnable mutate(ClassInstanceMultiMap<Member> values,Member[] members,int mode) {
        return () -> {
            events.add("mutation:"+mode);
            switch(mode) {
                case 0 -> values.add(new Member(100));
                case 1 -> { for(int i=0;i<24;i++)values.add(new Member(100+i)); }
                case 2 -> values.remove(members[0]);
                case 3 -> { for(Member m:members)values.remove(m); }
                case 4 -> { for(Member m:members)values.remove(m);values.add(new Member(100)); }
                case 5 -> throw new IllegalStateException("callback failure");
                case 6 -> values.find(Typed.class);
                default -> {}
            }
        };
    }
    public static void main(String[] args)throws Exception {
        var buffer=new StringWriter();
        try(var out=new PrintWriter(buffer)) {
            // Root and cached typed membership, duplicates, live immutable views and snapshots.
            var values=new ClassInstanceMultiMap<Member>(Member.class);
            var typed=values.find(Typed.class);var root=values.find(Member.class);
            Member[] members=new Member[48];for(int i=0;i<members.length;i++)members[i]=(i%2==0?new Typed(i):new Member(i));
            Random random=new Random(0x53454354494f4eL);
            for(int step=0;step<4096;step++) {
                Member m=members[random.nextInt(members.length)];int operation=random.nextInt(5);
                call(out,"random:"+step+":"+operation,()-> {
                    if(operation<2)events.add("add="+values.add(m));
                    else if(operation==2)events.add("remove="+values.remove(m));
                    else if(operation==3)events.add("contains="+values.contains(m));
                    else events.add("snapshot="+labels(values.getAllInstances()));
                });
                out.println("root="+labels(root)+" typed="+labels(typed)+" size="+values.size());
            }
            call(out,"null-add",()->events.add("result="+values.add(null)));
            call(out,"null-remove",()->events.add("result="+values.remove(null)));
            call(out,"null-contains",()->values.contains(null));
            call(out,"wrong-class",()->values.find(String.class));
            call(out,"null-class",()->values.find(null));
            call(out,"immutable-clear",typed::clear);
            call(out,"immutable-remove",()->typed.remove(members[0]));
            var narrow=new ClassInstanceMultiMap<Number>(Number.class);
            call(out,"wrong-base-add",()->events.add("result="+((ClassInstanceMultiMap)narrow).add("wrong")));
            call(out,"wrong-base-contains",()->narrow.contains("wrong"));
            // Typed views delegate ArrayList.toArray, while the root collection
            // uses AbstractCollection. Preserve VM failure and copied prefix.
            for(int size:new int[]{0,1,3,10,11})for(int targetSize:new int[]{0,1,3,12})for(int kind=0;kind<2;kind++) {
                var map=new ClassInstanceMultiMap<Object>(Object.class);
                for(int i=0;i<size;i++)map.add(i==0?"ok":i);
                Collection<Object> selected=kind==0?map:map.find(Object.class);
                var target=new String[targetSize];Arrays.fill(target,"sentinel");
                call(out,"typed-array:"+size+":"+targetSize+":"+kind,()->events.add("result="+Arrays.toString(selected.toArray(target))));
                out.println("destination="+Arrays.toString(target));
                call(out,"object-array",()->events.add(Arrays.toString(selected.toArray())));
                var large=new Object[size+2];Arrays.fill(large,"sentinel");
                call(out,"large-array",()->events.add("same="+(selected.toArray(large)==large)+":"+Arrays.toString(large)));
                call(out,"generated-array",()->events.add(Arrays.toString(selected.toArray(n->{map.add("generated");return new Object[n];}))));
                call(out,"null-array",()->selected.toArray((Object[])null));
                call(out,"null-generator",()->selected.toArray((java.util.function.IntFunction<Object[]>)null));
            }
            // Equality can grow/remove/reenter/throw; callbacks run outside native locks.
            for(int size:new int[]{0,1,3,10,11,15,16,24})for(int mode=0;mode<8;mode++)for(int action=0;action<3;action++) {
                var map=new ClassInstanceMultiMap<Member>(Member.class);var group=new Member[size];
                for(int i=0;i<size;i++){group[i]=new Member(i);map.add(group[i]);}
                var sought=new Member(999);sought.equalResult=(action!=1);sought.equalHook=mutate(map,group,mode);
                int operation=action;
                call(out,"equal:"+size+":"+mode+":"+action,()->events.add("result="+(operation==2?map.find(Member.class).contains(sought):map.remove(sought))));
                state(out,map);
                call(out,"post-add",()->events.add("result="+map.add(new Member(998))));state(out,map);
                call(out,"post-equality-array",()->events.add(Arrays.toString(map.find(Member.class).toArray(new Member[1]))));
            }
            // Root iterator wrapper, typed ArrayList iterator, streams and spliterators.
            for(int size:new int[]{0,1,3,10,11})for(int mode=0;mode<8;mode++)for(int action=0;action<9;action++) {
                var map=new ClassInstanceMultiMap<Member>(Member.class);var group=new Member[size];
                for(int i=0;i<size;i++){group[i]=new Member(i);map.add(group[i]);}
                Collection<Member> selected=(action%2==0?map:map.find(Member.class));
                Runnable mutation=mutate(map,group,mode);boolean[] first={true};
                Consumer<Member> consumer=m->{events.add("visit:"+label(m));if(first[0]){first[0]=false;mutation.run();}};
                int operation=action;
                call(out,"iterate:"+size+":"+mode+":"+action,()-> {
                    if(operation<2)selected.forEach(consumer);
                    else if(operation<4)selected.iterator().forEachRemaining(consumer);
                    else if(operation<6)selected.spliterator().forEachRemaining(consumer);
                    else if(operation==6)selected.stream().forEach(consumer);
                    else if(operation==7){var cursor=selected.spliterator();events.add("characteristics="+cursor.characteristics());events.add("estimate="+cursor.estimateSize());while(cursor.tryAdvance(consumer)){};}
                    else {var cursor=selected.spliterator();var split=cursor.trySplit();events.add("split="+(split==null?"null":split.estimateSize()));if(split!=null)split.forEachRemaining(consumer);cursor.forEachRemaining(consumer);}
                });state(out,map);
            }
            for(boolean empty:new boolean[]{true,false}) {
                var map=new ClassInstanceMultiMap<Member>(Member.class);if(!empty)map.add(new Member(1));
                var iterator=map.iterator();var typedIterator=map.find(Member.class).iterator();
                call(out,"iterator-remove:"+empty,iterator::remove);call(out,"typed-iterator-remove:"+empty,typedIterator::remove);
                map.add(new Member(2));
                call(out,"iterator-next:"+empty,()->events.add(label(iterator.next())));
                call(out,"typed-iterator-next:"+empty,()->events.add(label(typedIterator.next())));
            }
            // Actual section bounds/cast/consumer callbacks and visibility policy inputs.
            for(int mode=0;mode<6;mode++)for(int type=0;type<3;type++) {
                var section=new EntitySection<Member>(Member.class,Visibility.HIDDEN);
                Member[] group={new Typed(0),new Member(1),new Typed(2)};for(Member m:group)section.add(m);
                int mutation=mode,kind=type;boolean[] first={true};
                Runnable hook=()->{events.add("section-mutation:"+mutation);switch(mutation){
                    case 0 -> section.add(new Typed(3));
                    case 1 -> section.remove(group[1]);
                    case 2 -> {for(Member m:group)section.remove(m);}
                    case 3 -> throw new IllegalStateException("section callback");
                    default -> {}
                }};
                EntityTypeTest<Member,Member> test=new EntityTypeTest<>() {
                    public Class<? extends Member> getBaseClass(){events.add("base-class");return kind==2?Typed.class:Member.class;}
                    public Member tryCast(Member m){events.add("cast:"+label(m));if(kind==1 && first[0]){first[0]=false;hook.run();}return m;}
                };
                if(type==0)group[0].boundsHook=()->{if(first[0]){first[0]=false;hook.run();}};
                AbortableIterationConsumer<Member> consumer=m->{events.add("accept:"+label(m));return mutation==4?AbortableIterationConsumer.Continuation.ABORT:AbortableIterationConsumer.Continuation.CONTINUE;};
                call(out,"section-query:"+mode+":"+type,()->events.add("result="+(kind==0?section.getEntities(new AABB(-1,-1,-1,8,8,8),consumer):section.getEntities(test,new AABB(-1,-1,-1,8,8,8),consumer))));
                call(out,"section-stream",()->section.getEntities().forEach(m->events.add(label(m))));
                for(Visibility status:Visibility.values())call(out,"status:"+status,()->events.add("old="+section.updateChunkStatus(status)+",new="+section.getStatus()+",size="+section.size()+",empty="+section.isEmpty()));
            }
        }
        byte[] data=buffer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(Path.of(args[0]),data);
        System.out.println("MEMBERS_CASES "+cases+" bytes="+data.length+" sha="+sha(data));
        var classes=new ArrayList<>(List.of("net.minecraft.util.ClassInstanceMultiMap","net.minecraft.world.level.entity.EntitySection"));
        if(args[1].equals("current"))classes.addAll(List.of("net.minecraft.util.NativeClassInstanceStorage","net.minecraft.util.NativeClassInstanceStorage$Group","net.minecraft.util.NativeClassInstanceStorage$Group$1","net.minecraft.util.NativeClassInstanceStorage$Group$MemberSpliterator","net.minecraft.util.NativeClassInstanceStorage$Captured"));
        for(String name:classes)try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        if(args[1].equals("current")) {
            var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+nativePath)))throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(nativePath))+" "+nativePath);
        }
    }
}
