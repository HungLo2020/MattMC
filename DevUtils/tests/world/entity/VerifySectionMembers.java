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
    static class SpatialEntity extends Entity {
        int ordinal;
        SpatialEntity(){super(net.minecraft.world.entity.EntityType.PIG,null);}
        @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b){}
        @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in){}
        @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out){}
        @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float damage){return false;}
    }
    static final class SpatialTyped extends SpatialEntity {}
    static final class SpectatorEntity extends SpatialEntity {
        Runnable spectatorHook;
        boolean spectator;
        @Override public boolean isSpectator() {
            events.add("spectator:"+ordinal);
            Runnable hook=spectatorHook;spectatorHook=null;if(hook!=null)hook.run();
            return spectator;
        }
    }
    static class ShakeEntity extends SpatialEntity implements net.alexscaves.server.entity.util.ShakesScreen {
        @Override public float getScreenShakeAmount(float partial){throw new AssertionError("Effect invoked during collection");}
    }
    static class ShakeCallback extends ShakeEntity {
        Runnable hook;
        boolean spectator;
        @Override public boolean isSpectator(){events.add("shake-spectator:"+ordinal);var call=hook;hook=null;if(call!=null)call.run();return spectator;}
    }
    static void shakeCollect(LevelEntityGetter<SpatialEntity> getter,AABB query,SpatialEntity excluded,ArrayList<SpatialEntity> result) {
        try {
            var method=LevelEntityGetter.class.getMethod("collectScreenShakeSources",AABB.class,EntityAccess.class,ArrayList.class);
            method.invoke(getter,query,excluded,result);
        } catch(NoSuchMethodException frozen) {
            var all=new ArrayList<SpatialEntity>();
            collect(getter,query,excluded,net.minecraft.world.entity.EntitySelector.NO_SPECTATORS,all);
            for(var e:all)if(e instanceof net.alexscaves.server.entity.util.ShakesScreen)result.add(e);
        } catch(java.lang.reflect.InvocationTargetException e){throw propagate(e.getCause());}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static void screenShakeQueries(PrintWriter out) throws Exception {
        for(int size:new int[]{0,1,3,129,257})for(int frequency:new int[]{0,1,16})for(int scattered=0;scattered<2;scattered++)for(int mode=0;mode<10;mode++) {
            try(var scope=java.lang.foreign.Arena.ofConfined()) {
                var storage=new EntitySectionStorage<SpatialEntity>(SpatialEntity.class,k->Visibility.TRACKED);
                LevelEntityGetter<SpatialEntity> getter=new LevelEntityGetterAdapter<>(new EntityLookup<SpatialEntity>(),storage);
                var members=new ArrayList<SpatialEntity>();var sections=new ArrayList<EntitySection<SpatialEntity>>();
                int middle=size/2;
                for(int i=0;i<size;i++) {
                    boolean shake=frequency>0 && i%frequency==0;
                    var e=spatial(i,scope,i==middle?(shake?ShakeCallback.class:SpectatorEntity.class):(shake?ShakeEntity.class:SpatialEntity.class));
                    e.setBoundingBox(new AABB(0,0,0,1,1,1));members.add(e);
                    var section=storage.getOrCreateSection(net.minecraft.core.SectionPos.asLong(scattered==0?0:i,0,0));section.add(e);sections.add(section);
                }
                var query=new AABB(-1,-1,-1,16.0*Math.max(2,size)+1,2,2);
                if(size>0) {
                    var special=members.get(middle);final int action=mode;
                    Runnable hook=()->{
                        switch(action) {
                            case 2 -> {if(middle+1<size)members.get(middle+1).setBoundingBox(new AABB(-10,0,0,-9,1,1));}
                            case 3 -> {if(middle+1<size)sections.get(middle+1).remove(members.get(middle+1));}
                            case 4 -> sections.get(middle).add(members.get(0));
                            case 5 -> {var nested=new ArrayList<SpatialEntity>();shakeCollect(getter,query,null,nested);events.add("nested:"+nested.stream().map(e->e.ordinal).toList());}
                            case 6 -> {try{var added=spatial(size,scope,ShakeEntity.class);added.setBoundingBox(new AABB(0,0,0,1,1,1));storage.getOrCreateSection(net.minecraft.core.SectionPos.asLong(size,0,0)).add(added);}catch(Exception e){throw new IllegalStateException(e);}}
                            case 7 -> throw new IllegalStateException("selector failure");
                        }
                    };
                    if(special instanceof ShakeCallback c){c.hook=hook;c.spectator=mode==1;}
                    else {var c=(SpectatorEntity)special;c.spectatorHook=hook;c.spectator=mode==1;}
                    if(mode==8)special.setBoundingBox(new AABB(0,0,0,1,1,1){@Override public boolean intersects(AABB box){events.add("custom-bounds");return true;}});
                }
                var excluded=size>0 && mode==9?members.get(middle):null;
                call(out,"shake:"+size+":"+frequency+":"+scattered+":"+mode,()->{
                    var result=new ArrayList<SpatialEntity>();shakeCollect(getter,query,excluded,result);
                    events.add("sources:"+result.stream().map(e->e.ordinal).toList());
                });
            }
        }
    }
    static final java.lang.reflect.Method COLLECT=collectionMethod();
    static java.lang.reflect.Method collectionMethod() {
        try{return LevelEntityGetter.class.getMethod("collectEntities",AABB.class,EntityAccess.class,java.util.function.Predicate.class,ArrayList.class);}
        catch(NoSuchMethodException frozen){return null;}
    }
    static void collect(LevelEntityGetter<SpatialEntity> getter,AABB box,SpatialEntity excluded,
        java.util.function.Predicate<? super SpatialEntity> predicate,ArrayList<SpatialEntity> result) {
        if(COLLECT==null){getter.get(box,e->{if(e!=excluded && predicate.test(e))result.add(e);});return;}
        try{COLLECT.invoke(getter,box,excluded,predicate,result);}
        catch(java.lang.reflect.InvocationTargetException e){throw propagate(e.getCause());}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @SuppressWarnings("unchecked") static <E extends Throwable> RuntimeException propagate(Throwable t)throws E{throw (E)t;}

    static void collectedQueries(PrintWriter out) throws Exception {
        for(int size:new int[]{0,1,3,129,257})for(int mode=0;mode<15;mode++)for(int scattered=0;scattered<2;scattered++) {
            try(var scope=java.lang.foreign.Arena.ofConfined()) {
                var storage=new EntitySectionStorage<SpatialEntity>(SpatialEntity.class,k->Visibility.TRACKED);
                var getter=new LevelEntityGetterAdapter<>(new EntityLookup<SpatialEntity>(),storage);
                var members=new ArrayList<SpatialEntity>();
                var sections=new ArrayList<EntitySection<SpatialEntity>>();
                int midpoint=size/2;
                for(int i=0;i<size;i++) {
                    var entity=spatial(i,scope,i==midpoint?SpectatorEntity.class:SpatialEntity.class);
                    entity.setBoundingBox(new AABB(0,0,0,1,1,1));
                    var section=storage.getOrCreateSection(net.minecraft.core.SectionPos.asLong(scattered==0?0:i,0,0));
                    section.add(entity);members.add(entity);sections.add(section);
                }
                AABB query=new AABB(-1,-1,-1,16.0*Math.max(2,size)+1,2,2);
                final int action=mode;
                SpectatorEntity special=size==0?null:(SpectatorEntity)members.get(midpoint);
                if(special!=null) {
                    special.spectator=mode==1;
                    special.spectatorHook=()->{
                        switch(action) {
                            case 3 -> {if(midpoint+1<size)members.get(midpoint+1).setBoundingBox(new AABB(-50,-50,-50,-49,-49,-49));}
                            case 4 -> {if(midpoint+1<size)sections.get(midpoint+1).remove(members.get(midpoint+1));}
                            case 5 -> sections.get(midpoint).add(members.get(0));
                            case 6 -> sections.get(midpoint).updateChunkStatus(Visibility.HIDDEN);
                            case 7 -> {
                                var nested=new ArrayList<SpatialEntity>();collect(getter,query,null,net.minecraft.world.entity.EntitySelector.NO_SPECTATORS,nested);
                                events.add("nested:"+nested.stream().map(e->e.ordinal).toList());
                            }
                            case 8 -> throw new IllegalStateException("spectator callback");
                            case 9 -> storage.getOrCreateSection(net.minecraft.core.SectionPos.asLong(size+1,0,0)).add(members.get(0));
                            case 10 -> {if(midpoint+1<size)members.get(midpoint+1).setBoundingBox(null);}
                            default -> {}
                        }
                    };
                    if(mode==11)special.setBoundingBox(new AABB(100,100,100,101,101,101){
                        @Override public boolean intersects(AABB other){events.add("collection-bounds");return true;}
                    });
                    if(mode==12)sections.get(0).updateChunkStatus(null);
                    if(mode==13)special.setBoundingBox(new AABB(-50,-50,-50,-49,-49,-49));
                }
                SpatialEntity excluded=mode==2?special:null;
                java.util.function.Predicate<? super SpatialEntity> predicate=mode==14?e->{events.add("predicate:"+e.ordinal);return true;}:net.minecraft.world.entity.EntitySelector.NO_SPECTATORS;
                var result=new ArrayList<SpatialEntity>();
                call(out,"collection:"+size+":"+mode+":"+scattered,()->{
                    try {collect(getter,query,excluded,predicate,result);}
                    finally{events.add("result:"+result.stream().map(e->e.ordinal).toList());}
                });
            }
        }
    }

    static SpatialEntity spatial(int id,java.lang.foreign.Arena scope) throws Exception { return spatial(id,scope,SpatialEntity.class); }
    static SpatialEntity spatial(int id,java.lang.foreign.Arena scope,Class<? extends SpatialEntity> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        var entity=(SpatialEntity)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
        entity.ordinal=id;entity.setId(id);
        AABB box=new AABB(id*2,0,0,id*2+1,1,1);
        try {
            var geometry=Entity.class.getDeclaredField("nativeGeometry");geometry.setAccessible(true);
            var ownerClass=Class.forName("net.minecraft.world.entity.NativeEntityGeometry");
            var owner=ownerClass.getConstructor().newInstance();
            var body=ownerClass.getMethod("create",java.lang.foreign.Arena.class,double.class,double.class,double.class,AABB.class).invoke(owner,scope,0d,0d,0d,box);
            geometry.set(entity,body);
        }catch(NoSuchFieldException frozen){}
        entity.setBoundingBox(box);return entity;
    }
    static void spatialQueries(PrintWriter out) throws Exception {
        for(int size:new int[]{0,1,3,16,129})for(int mode=0;mode<14;mode++)for(int kind=0;kind<4;kind++) {
            try(var scope=java.lang.foreign.Arena.ofConfined()) {
                var section=new EntitySection<SpatialEntity>(SpatialEntity.class,Visibility.TRACKED);
                var members=new ArrayList<SpatialEntity>();
                for(int i=0;i<size;i++){var entity=spatial(i,scope);members.add(entity);section.add(entity);}
                final int action=mode;
                AABB query=switch(mode) {
                    case 12 -> null;
                    case 8 -> new AABB(Double.NaN,0,0,1,1,1);
                    case 9 -> new AABB(1,0,0,2,1,1);
                    case 10 -> new AABB(Double.NEGATIVE_INFINITY,-1,-1,Double.POSITIVE_INFINITY,2,2);
                    default -> new AABB(-1,-1,-1,3,2,2);
                };
                if(mode==13 && size>1)members.get(1).setBoundingBox(null);
                if(mode==11 && size>1)members.get(1).setBoundingBox(new AABB(100,100,100,101,101,101){
                    @Override public boolean intersects(AABB other){events.add("custom-box");return true;}
                });
                AbortableIterationConsumer<SpatialEntity> accept=e->{
                    events.add("accept:"+e.ordinal);
                    if(e.ordinal==0 && members.size()>1)switch(action) {
                        case 1 -> members.get(1).setBoundingBox(query);
                        case 2 -> members.get(1).setBoundingBox(new AABB(100,100,100,101,101,101));
                        case 3 -> section.remove(members.get(1));
                        case 4 -> section.add(members.get(0));
                        case 5 -> {section.remove(members.get(1));return AbortableIterationConsumer.Continuation.ABORT;}
                        case 6 -> throw new IllegalStateException("spatial callback");
                        case 7 -> section.getEntities(query,nested->{events.add("nested:"+nested.ordinal);return AbortableIterationConsumer.Continuation.CONTINUE;});
                        default -> {}
                    }
                    return AbortableIterationConsumer.Continuation.CONTINUE;
                };
                EntityTypeTest<SpatialEntity,SpatialEntity> type=kind==1?EntityTypeTest.forClass(SpatialEntity.class):kind==2?EntityTypeTest.forExactClass(SpatialEntity.class):new EntityTypeTest<>() {
                    public Class<SpatialEntity> getBaseClass(){return SpatialEntity.class;}
                    public SpatialEntity tryCast(SpatialEntity value){events.add("cast:"+value.ordinal);if(value.ordinal==2)value.setBoundingBox(query);return value;}
                };
                int queryKind=kind;
                call(out,"spatial:"+size+":"+mode+":"+kind,()->events.add("result="+(queryKind==0?section.getEntities(query,accept):section.getEntities(type,query,accept))));
            }
        }
    }
    static String label(Object object) {
        return object==null ? "null" : object instanceof Member m ? "m"+m.id : object.toString();
    }
    static void worldQueries(PrintWriter out) throws Exception {
        for(int kind=0;kind<4;kind++) {
            var storage=new EntitySectionStorage<Member>(Member.class,k->Visibility.TRACKED);
            var section=storage.getOrCreateSection(0);
            var member=new Member(0);section.add(member);
            var sought=new Member(1);sought.equalResult=true;sought.equalHook=()->section.remove(member);
            call(out,"world-failed-removal:"+kind,()->section.remove(sought));
            int queryKind=kind;
            call(out,"world-after-failed-removal:"+kind,()->{
                var query=new AABB(0,0,0,1,1,1);
                if(queryKind==0)storage.getEntities(query,e->AbortableIterationConsumer.Continuation.CONTINUE);
                else if(queryKind==1)storage.getEntities(EntityTypeTest.forClass(Member.class),query,e->AbortableIterationConsumer.Continuation.CONTINUE);
                else if(queryKind==2)storage.forEachAccessibleNonEmptySection(query,s->{events.add("section:"+s.size());return AbortableIterationConsumer.Continuation.CONTINUE;});
                else {section.updateChunkStatus(Visibility.HIDDEN);storage.getEntities(query,e->AbortableIterationConsumer.Continuation.CONTINUE);}
            });
        }
        for(int size:new int[]{1,3,129})for(int mode=0;mode<21;mode++)for(int kind=0;kind<4;kind++) {
            try(var scope=java.lang.foreign.Arena.ofConfined()) {
                var storage=new EntitySectionStorage<SpatialEntity>(SpatialEntity.class,k->Visibility.TRACKED);
                long firstKey=net.minecraft.core.SectionPos.asLong(0,0,0),futureKey=net.minecraft.core.SectionPos.asLong(1,0,0);
                var section=storage.getOrCreateSection(firstKey);
                var future=storage.getOrCreateSection(futureKey);
                var members=new ArrayList<SpatialEntity>();
                AABB query=mode==19?null:new AABB(-1,-1,-1,33,2,2);
                AABB inside=new AABB(0,0,0,1,1,1),outside=new AABB(0,8,0,1,9,1);
                for(int i=0;i<size;i++){var e=spatial(i,scope);e.setBoundingBox(mode==0 || i%2==0?inside:outside);members.add(e);section.add(e);}
                var later=spatial(size,scope);later.setBoundingBox(mode==2?outside:inside);
                future.add(later);
                if(mode==3)future.updateChunkStatus(Visibility.HIDDEN);
                if(mode==9)storage.remove(futureKey);
                if(mode==15)later.setBoundingBox(new AABB(0,8,0,1,9,1){
                    @Override public boolean intersects(AABB other){events.add("custom-world-bounds");section.updateChunkStatus(Visibility.HIDDEN);return true;}
                });
                if(mode==16)later.setBoundingBox(null);
                int action=mode,queryKind=kind;
                AbortableIterationConsumer<SpatialEntity> consumer=e->{
                    events.add("world-accept:"+e.ordinal);
                    if(e.ordinal==0)switch(action) {
                        case 1 -> {if(size>1)members.get(1).setBoundingBox(inside);}
                        case 2 -> later.setBoundingBox(inside);
                        case 3 -> future.updateChunkStatus(Visibility.TRACKED);
                        case 4 -> future.updateChunkStatus(Visibility.HIDDEN);
                        case 5 -> future.updateChunkStatus(null);
                        case 6 -> storage.remove(futureKey);
                        case 7 -> {storage.remove(firstKey);storage.getOrCreateSection(firstKey).add(later);}
                        case 8 -> {storage.remove(futureKey);storage.getOrCreateSection(futureKey).add(later);}
                        case 9 -> storage.getOrCreateSection(futureKey).add(later);
                        case 10 -> storage.getEntities(query,nested->{events.add("world-nested:"+nested.ordinal);return AbortableIterationConsumer.Continuation.CONTINUE;});
                        case 11 -> {return AbortableIterationConsumer.Continuation.ABORT;}
                        case 12 -> throw new IllegalStateException("world query callback");
                        case 13 -> section.remove(members.get(size-1));
                        case 14 -> section.add(later);
                        case 17 -> section.remove(e);
                        default -> {}
                    }
                    return AbortableIterationConsumer.Continuation.CONTINUE;
                };
                EntityTypeTest<SpatialEntity,SpatialEntity> type=kind==1?EntityTypeTest.forClass(SpatialEntity.class):kind==2?EntityTypeTest.forExactClass(SpatialEntity.class):new EntityTypeTest<>() {
                    public Class<SpatialEntity> getBaseClass(){return SpatialEntity.class;}
                    public SpatialEntity tryCast(SpatialEntity e){events.add("world-cast:"+e.ordinal);if(action==18)e.setBoundingBox(inside);return e;}
                };
                call(out,"world:"+size+":"+mode+":"+kind,()->{
                    if(queryKind==0)storage.getEntities(query,action==20?null:consumer);
                    else storage.getEntities(type,query,action==20?null:consumer);
                });
                out.println("world-after:"+storage.count()+":"+section.size()+":"+section.getStatus()+":"+future.size()+":"+future.getStatus());
            }
        }
    }
    static void typedWorldQueries(PrintWriter out) throws Exception {
        for(int size:new int[]{0,1,5,260})for(int warm=0;warm<2;warm++)for(int mode=0;mode<9;mode++) {
            try(var scope=java.lang.foreign.Arena.ofConfined()) {
                var storage=new EntitySectionStorage<SpatialEntity>(SpatialEntity.class,k->Visibility.TRACKED);
                var section=storage.getOrCreateSection(0);
                long futureKey=net.minecraft.core.SectionPos.asLong(1,0,0);
                var future=storage.getOrCreateSection(futureKey);
                var box=new AABB(-1,-1,-1,33,2,2);var outside=new AABB(0,8,0,1,9,1);
                var members=new ArrayList<SpatialEntity>();
                for(int i=0;i<size;i++) {
                    var e=spatial(i,scope,i%2==0?SpatialTyped.class:SpatialEntity.class);
                    e.setBoundingBox(mode==1?outside:box);section.add(e);members.add(e);
                }
                var later=spatial(size,scope,SpatialTyped.class);later.setBoundingBox(mode==2?outside:box);future.add(later);
                var type=EntityTypeTest.<SpatialEntity,SpatialTyped>forClass(SpatialTyped.class);
                if(warm!=0)storage.getEntities(type,box,e->AbortableIterationConsumer.Continuation.CONTINUE);
                int action=mode;
                call(out,"typed-world:"+size+":"+warm+":"+mode,()->storage.getEntities(type,box,e->{
                    events.add("typed:"+e.ordinal);
                    if(e.ordinal==0)switch(action) {
                        case 2 -> later.setBoundingBox(box);
                        case 3 -> future.updateChunkStatus(Visibility.HIDDEN);
                        case 4 -> {storage.remove(futureKey);storage.getOrCreateSection(futureKey).add(later);}
                        case 5 -> {section.remove(members.getLast());return AbortableIterationConsumer.Continuation.ABORT;}
                        case 6 -> {if(size>1)section.remove(members.getLast());}
                        case 7 -> storage.getEntities(type,box,n->{events.add("typed-nested:"+n.ordinal);return AbortableIterationConsumer.Continuation.CONTINUE;});
                        case 8 -> {if(size>2)members.get(2).setBoundingBox(outside);}
                        default -> {}
                    }
                    return AbortableIterationConsumer.Continuation.CONTINUE;
                }));
            }
        }
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
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var buffer=new StringWriter();
        try(var out=new PrintWriter(buffer)) {
            spatialQueries(out);
            worldQueries(out);
            typedWorldQueries(out);
            collectedQueries(out);
            screenShakeQueries(out);
            try(var scope=java.lang.foreign.Arena.ofConfined()) {
                int x=(1<<21)-2;
                double edge=x*16.0;
                var storage=new EntitySectionStorage<SpatialEntity>(SpatialEntity.class,k->Visibility.TRACKED);
                var entity=spatial(123,scope);entity.setBoundingBox(new AABB(edge,0,0,edge+1,1,1));
                storage.getOrCreateSection(net.minecraft.core.SectionPos.asLong(x,0,0)).add(entity);
                var getter=new LevelEntityGetterAdapter<>(new EntityLookup<SpatialEntity>(),storage);
                var result=new ArrayList<SpatialEntity>();
                call(out,"collection-range-failure-prefix",()->{
                    try{collect(getter,new AABB(edge-1,-1,-1,edge+17,2,2),null,net.minecraft.world.entity.EntitySelector.NO_SPECTATORS,result);}
                    finally{events.add("result:"+result.stream().map(e->e.ordinal).toList());}
                });
            }

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
        var classes=new ArrayList<>(List.of("net.minecraft.util.ClassInstanceMultiMap","net.minecraft.world.level.entity.EntitySection","net.minecraft.world.level.entity.EntitySectionStorage","net.minecraft.world.level.entity.EntityTypeTest","net.minecraft.world.entity.Entity","net.minecraft.world.level.Level","net.minecraft.world.level.entity.LevelEntityGetter","net.minecraft.world.level.entity.LevelEntityGetterAdapter"));
        classes.add(EntityTypeTest.forClass(Member.class).getClass().getName());
        classes.add(EntityTypeTest.forExactClass(Member.class).getClass().getName());
        if(args[1].equals("current"))classes.addAll(List.of("net.minecraft.util.NativeClassInstanceStorage","net.minecraft.util.NativeClassInstanceStorage$1","net.minecraft.util.NativeClassInstanceStorage$Group","net.minecraft.util.NativeClassInstanceStorage$Group$1","net.minecraft.util.NativeClassInstanceStorage$Group$MemberSpliterator","net.minecraft.util.NativeClassInstanceStorage$Captured","net.minecraft.world.entity.NativeEntityGeometry","net.minecraft.world.entity.NativeEntityGeometry$Body"));
        if(args[1].equals("current"))classes.addAll(List.of("net.minecraft.util.ClassInstanceMultiMap$1","net.minecraft.util.ClassInstanceMultiMap$QueryBatch","net.minecraft.world.level.entity.NativeEntitySectionIndex","net.minecraft.world.level.entity.NativeEntitySectionIndex$Cursor"));
        for(String name:classes)try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        if(args[1].equals("current")) {
            var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+nativePath)))throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(nativePath))+" "+nativePath);
        }
    }
}
