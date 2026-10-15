package net.minecraft.server.level;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.*;
import net.minecraft.world.level.material.FluidState;
import org.objenesis.ObjenesisStd;

/** One observer of the actual production engines on all three classpaths.
 * Executor/world inputs are controlled; no expected coordinator is embedded. */
public final class VerifyThreadedLighting {
    static PrintWriter trace;
    static long events;
    static void pinClasses() throws Exception {
        String[] names={"net.minecraft.server.level.ThreadedLevelLightEngine",
            "net.minecraft.world.level.lighting.LightEngine","net.minecraft.world.level.lighting.BlockLightEngine",
            "net.minecraft.world.level.lighting.SkyLightEngine","net.minecraft.world.level.lighting.LayerLightSectionStorage"};
        String[] frozen={"1f6a8a7c365c11c9d91adacd1770f6c8afeb8bd2aec484c1024aee81a3bda889",
            "b6be7a9e3e43c4185ae76e3e390acb649acbf2ff7e0b793d3acb5c9592f7c453",
            "2b672b2b513751d8f9fcb89b319270e6d1a3171e5fc0d58d28786628f3ced727",
            "97e441d316271e8cbc125481e86187d970cf29058e7738a56e7fd5e786f8eeec",
            "31076b253b190711c0516653cb0a85718a7b96ea1af0883ed3e837720c051539"};
        for(int i=0;i<names.length;i++) {
            Class<?> type=Class.forName(names[i]);String digest;
            try(var in=type.getResourceAsStream(type.getSimpleName()+".class")){digest=hash(in.readAllBytes());}
            if(Boolean.getBoolean("threadedLight.frozen")&&!digest.equals(frozen[i]))throw new AssertionError("Unexpected Frozen class "+names[i]);
            System.out.println("CLASS "+names[i]+" "+digest+" "+type.getProtectionDomain().getCodeSource().getLocation());
        }
    }
    static void event(String text) { if (trace != null) { trace.println(text); events++; } }
    static Object field(Object value, String name) throws Exception {
        for (Class<?> c=value.getClass(); c!=null; c=c.getSuperclass()) {
            try { var f=c.getDeclaredField(name); f.setAccessible(true); return f.get(value); }
            catch (NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(name);
    }
    static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> c=object.getClass(); c!=null; c=c.getSuperclass()) {
            try { var m=c.getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(object,args); }
            catch (NoSuchMethodException absent) {}
            catch (InvocationTargetException e) { throw cause(e); }
        }
        throw new NoSuchMethodException(name);
    }
    static RuntimeException cause(InvocationTargetException e) {
        if (e.getCause() instanceof Error error) throw error;
        if (e.getCause() instanceof RuntimeException r) return r;
        return new RuntimeException(e.getCause());
    }
    static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    static String layer(DataLayer data) throws Exception {
        if (data==null) return "null";
        return data.isDefinitelyHomogenous()?"lazy:"+data.get(0,0,0):"bytes:"+hash(data.copy().getData());
    }
    @SuppressWarnings("unchecked") static Map<Long,DataLayer> map(Object object) throws Exception {
        try { return (Map<Long,DataLayer>)invoke(object,"snapshotForVerification",new Class<?>[0]); }
        catch (NoSuchMethodException absent) { return (Map<Long,DataLayer>)field(object,"map"); }
    }
    static final class Tasks implements Executor {
        final ArrayDeque<Runnable> queue=new ArrayDeque<>();
        boolean inline,reject;
        long submitted, executed;
        public void execute(Runnable task) { submitted++;if(reject)throw new RejectedExecutionException("backend rejection");if(inline){executed++;task.run();}else queue.addLast(task); }
        boolean step() { var r=queue.pollFirst();if(r==null)return false;executed++;r.run();return true; }
    }
    static final class LightExecutor extends ConsecutiveExecutor {
        boolean reject;
        LightExecutor(Executor executor) { super(executor,"threaded-light-oracle"); }
        @Override public void schedule(Runnable runnable) {
            if(reject)throw new RejectedExecutionException("injected submission rejection");
            super.schedule(runnable);
        }
    }
    /** Constructor is never called. Only this original queue-level boundary is used. */
    static final class MapBoundary extends ChunkMap {
        private MapBoundary(){super(null,null,null,null,null,null,null,null,null,null,null,0,false);}
        @Override protected IntSupplier getChunkQueueLevel(long key){return ()->0;}
    }
    static final class World implements LightChunkGetter,BlockGetter {
        final Map<Long,ProtoChunk> chunks=new TreeMap<>();
        final Set<String> notifications=new TreeSet<>();
        boolean rejectLookup;
        World() {
            var random=new Random(73621);
            for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++) {
                var sections=new LevelChunkSection[4];
                for(int i=0;i<sections.length;i++) sections[i]=new LevelChunkSection(new PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
                var chunk=new ProtoChunk(new ChunkPos(x,z),UpgradeData.EMPTY,sections,
                    new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                    LevelHeightAccessor.create(0,64),null,null);
                for(int i=0;i<24;i++){
                    int y=16+random.nextInt(32);
                    sections[y>>4].setBlockState(random.nextInt(16),y&15,random.nextInt(16),
                        (i%4==0?Blocks.GLOWSTONE:Blocks.STONE).defaultBlockState(),false);
                }
                sections[1].setBlockState(8,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);
                chunks.put(chunk.getPos().toLong(),chunk);chunk.initializeLightSources();
            }
        }
        public LightChunk getChunkForLighting(int x,int z){if(rejectLookup)throw new IllegalStateException("propagation lookup failure");return chunks.get(ChunkPos.asLong(x,z));}
        public BlockGetter getLevel(){return this;}
        public void onLightUpdate(LightLayer kind,SectionPos pos){notifications.add(kind+" "+pos.asLong());}
        public BlockEntity getBlockEntity(BlockPos p){return null;}
        public BlockState getBlockState(BlockPos p){var c=chunks.get(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));return c==null?Blocks.BEDROCK.defaultBlockState():c.getBlockState(p);}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public int getMinY(){return 0;}
        public int getHeight(){return 64;}
    }
    static final class Fixture implements AutoCloseable {
        final Tasks dispatch=new Tasks(),light=new Tasks();
        final LightExecutor executor=new LightExecutor(light);
        final ChunkTaskDispatcher dispatcher=new ChunkTaskDispatcher(executor,dispatch);
        final World world=new World();
        final ThreadedLevelLightEngine engine;
        final long engineConstructionNs;
        final IdentityHashMap<DataLayer,String> published=new IdentityHashMap<>();
        Fixture(boolean inline,boolean sky){
            dispatch.inline=inline;light.inline=inline;
            var map=new ObjenesisStd().newInstance(MapBoundary.class);long began=System.nanoTime();
            engine=new ThreadedLevelLightEngine(world,map,sky,executor,dispatcher);
            engineConstructionNs=System.nanoTime()-began;
        }
        int pending() throws Exception {
            Object tasks=field(engine,"lightTasks");
            return tasks instanceof Collection<?> c?c.size():((Number)invoke(tasks,"pending",new Class<?>[0])).intValue();
        }
        boolean scheduled() throws Exception {
            try{return ((AtomicBoolean)field(engine,"scheduled")).get();}
            catch(NoSuchFieldException absent){return (boolean)invoke(field(engine,"lightTasks"),"scheduled",new Class<?>[0]);}
        }
        void add(boolean post,Runnable runnable) throws Exception {
            Class<?> type=Class.forName("net.minecraft.server.level.ThreadedLevelLightEngine$TaskType");
            Object tag=type.getEnumConstants()[post?1:0];
            invoke(engine,"addTask",new Class<?>[]{int.class,int.class,IntSupplier.class,type,Runnable.class},0,0,(IntSupplier)()->0,tag,runnable);
        }
        void update() throws Exception {invoke(engine,"runUpdate",new Class<?>[0]);}
        void transport() {for(int i=0;i<200000;i++){boolean ran=dispatch.step();ran|=light.step();if(!ran)return;}throw new AssertionError("transport did not quiesce");}
        void drain() throws Exception {
            for(int i=0;i<100;i++){
                transport();engine.tryScheduleUpdate();transport();
                if(pending()==0&&!engine.hasLightWork())return;
            }
            throw new AssertionError("lighting did not quiesce pending="+pending()+" scheduled="+scheduled());
        }
        void initialize() throws Exception {
            for(var c:world.chunks.values()) {
                var future=engine.initializeLight(c,false);future.thenRun(()->event("initialized "+c.getPos()));
            }
            // Production status dependencies complete initialization before the
            // lighting PRE pass. Overlapping them disables light in init POST.
            drain();
            for(var c:world.chunks.values()) {
                var lit=engine.lightChunk(c,false);lit.thenRun(()->event("lit "+c.getPos()+" "+c.isLightCorrect()));
            }
            drain();
        }
        void assertReady() {
            for(var c:world.chunks.values()) {
                var p=c.getPos();
                if(!c.isLightCorrect()||!engine.lightOnInColumn(SectionPos.getZeroNode(p.x,p.z)))throw new AssertionError("Fixture column is not fully lit: "+p);
                int light=engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(p.x*16+8,24,p.z*16+8));
                if(light!=15)throw new AssertionError("Fixture emitter did not propagate: "+p+" light="+light);
            }
        }
        void edit(int i,boolean on) {
            var p=new BlockPos((i*7%48)-16,24+(i%16),(i*11%48)-16);
            var c=world.chunks.get(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));
            c.getSection(p.getY()>>4).setBlockState(p.getX()&15,p.getY()&15,p.getZ()&15,(on?Blocks.GLOWSTONE:Blocks.AIR).defaultBlockState(),false);
            c.getSkyLightSources().update(c,p.getX()&15,p.getY(),p.getZ()&15);engine.checkBlock(p);
        }
        @SuppressWarnings("unchecked") void snapshot(String name) throws Exception {
            event("snapshot "+name+" "+pending()+" "+scheduled()+" "+engine.hasLightWork());
            for(var e:published.entrySet())if(!layer(e.getKey()).equals(e.getValue()))throw new AssertionError("Published layer mutated");
            published.clear();
            for(var kind:LightLayer.values()) {
                Object listener=engine.getLayerListener(kind);
                if(!(listener instanceof LightEngine<?,?>))continue;
                Object storage=field(listener,"storage");
                var updating=map(field(storage,"updatingSectionData"));var visible=map(field(storage,"visibleSectionData"));
                for(String side:List.of("updating","visible","queued")) {
                    var rows=side.equals("updating")?updating:side.equals("visible")?visible:(Map<Long,DataLayer>)field(storage,"queuedSections");
                    for(var e:new TreeMap<>(rows).entrySet())event(kind+" "+side+" "+e.getKey()+" "+layer(e.getValue()));
                }
                for(String key:List.of("sectionStates","changedSections","sectionsAffectedByLightUpdates")){
                    Object v=field(storage,key);event(kind+" "+key+" "+(v instanceof Map<?,?> m?new TreeMap<>(m):new TreeSet<>((Set<?>)v)));
                }
                for(long key:new TreeSet<>(updating.keySet()))event(kind+" sharing "+key+" "+(updating.get(key)==visible.get(key)));
                for(DataLayer data:visible.values())if(data!=null)published.put(data,layer(data));
                try {Object q=field(listener,"lightWork");event(kind+" queues "+invoke(q,"size",new Class<?>[]{int.class},0)+" "+invoke(q,"size",new Class<?>[]{int.class},1));}
                catch(NoSuchFieldException frozen){event(kind+" queues "+invoke(field(listener,"decreaseQueue"),"size",new Class<?>[0])+" "+invoke(field(listener,"increaseQueue"),"size",new Class<?>[0]));}
            }
            for(String n:world.notifications)event("notification "+n);world.notifications.clear();
        }
        public void close(){engine.close();dispatcher.close();executor.close();}
    }
    static void outcome(String label,Checked action) throws Exception {
        try{action.run();event(label+" OK");}catch(RuntimeException e){event(label+" "+e.getClass().getName());}
    }
    interface Checked {void run() throws Exception;}
    static void controls() throws Exception {
        for(boolean inline:new boolean[]{false,true})for(boolean sky:new boolean[]{false,true}) {
            event("world "+inline+" "+sky);
            try(var f=new Fixture(inline,sky)){
                f.initialize();f.assertReady();f.snapshot("initial");
                for(int n:new int[]{0,1,999,1000,1001,2501}) {
                    event("burst "+n);for(int i=0;i<n;i++)f.engine.checkBlock(new BlockPos((i%32)-16,24,(i/32%32)-16));
                    var waiter=f.engine.waitForPendingTasks(0,0);waiter.thenRun(()->event("waiter "+n));
                    f.transport();event("before scheduled drain "+n+" "+f.pending()+" "+waiter.isDone());
                    f.drain();if(!waiter.isDone())throw new AssertionError("waiter lost");f.snapshot("burst"+n);
                }
                Runnable duplicate=()->event("duplicate identity");
                f.add(false,duplicate);f.add(true,duplicate);f.add(false,duplicate);f.add(true,duplicate);f.drain();
                var cancelledChunk=f.world.chunks.get(0L);
                var cancelled=f.engine.lightChunk(cancelledChunk,false);
                event("cancelled future "+cancelled.cancel(false));f.drain();
                event("cancelled flag "+cancelledChunk.isLightCorrect());f.snapshot("cancelled");
                f.engine.lightChunk(cancelledChunk,false);f.drain();
                for(int step=0;step<12;step++){
                    for(int i=0;i<24;i++)f.edit(i,step%2==0);
                    if(step%3==0)f.engine.queueSectionData(LightLayer.BLOCK,SectionPos.of(0,1,0),new DataLayer(step&15));
                    if(step==2)f.engine.retainData(new ChunkPos(0,0),true);
                    if(step==3)f.engine.setLightEnabled(new ChunkPos(0,0),false);
                    if(step==4)f.engine.setLightEnabled(new ChunkPos(0,0),true);
                    f.drain();f.snapshot("edit"+step);
                }
                f.engine.updateChunkStatus(new ChunkPos(0,0));f.drain();f.snapshot("unloaded");
                var c=f.world.chunks.get(0L);f.engine.initializeLight(c,false);f.drain();f.engine.lightChunk(c,false);f.drain();f.assertReady();f.snapshot("reloaded");
            }
        }
        for(String failure:List.of("pre","post","propagation"))try(var f=new Fixture(false,true)) {
            event("failure "+failure);f.initialize();f.assertReady();var once=new AtomicBoolean(true);
            Runnable bad=()->{event("bad "+failure);if(once.getAndSet(false))throw new IllegalStateException("injected "+failure);};
            f.add(false,()->event("pre A"));f.add(failure.equals("post"),bad);f.add(true,()->event("post B"));
            f.transport();
            if(failure.equals("propagation")){once.set(false);f.engine.checkBlock(new BlockPos(0,25,0));f.transport();f.world.rejectLookup=true;}
            outcome("update",f::update);event("remaining "+f.pending());f.world.rejectLookup=false;outcome("retry",f::update);f.snapshot("failure done");
        }
        for(boolean post:new boolean[]{false,true})try(var f=new Fixture(false,false)) {
            event("reentry "+post);var once=new AtomicBoolean(true);
            f.add(post,()->{event("outer");if(once.getAndSet(false))try{f.add(false,()->event("appended"));f.transport();f.update();}catch(Exception e){throw new RuntimeException(e);}});
            f.add(false,()->event("second pre"));f.add(true,()->event("second post"));f.transport();
            outcome("nested update",f::update);outcome("nested retry",f::update);event("remaining "+f.pending());
        }
        for(String mode:List.of("pre","post","rejection","backend-rejection"))try(var f=new Fixture(false,false)) {
            event("scheduled failure "+mode);var once=new AtomicBoolean(true);
            f.add(mode.equals("post"),()->{event("scheduled callback");if(!mode.endsWith("rejection")&&once.getAndSet(false))throw new IllegalStateException("scheduled failure");});f.transport();
            f.executor.reject=mode.equals("rejection");f.light.reject=mode.equals("backend-rejection");
            outcome("schedule",()->f.engine.tryScheduleUpdate());outcome("execute",f::transport);
            event("after failed schedule "+f.scheduled()+" "+f.pending());f.executor.reject=false;f.light.reject=false;
            outcome("schedule again",()->f.engine.tryScheduleUpdate());outcome("execute again",f::transport);event("still scheduled "+f.scheduled());
        }
    }
    public static void main(String[] args) throws Exception {
        pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try(var out=new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {trace=out;controls();trace=null;}
        System.out.println("TRACE "+hash(Files.readAllBytes(Path.of(args[0])))+" "+events);
    }
}
