package net.minecraft.world.level.lighting;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.ref.WeakReference;
import java.lang.ref.Reference;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;

/** Identical observer compiled separately against unchanged Frozen/pre/current.
 * Actual engines choose every transition; the observer records their effects. */
public final class VerifyLightCoordinator {
    private static PrintWriter out;
    private static long rows;
    private static void row(Object value) { out.println(value); rows++; }
    private static void pinClasses()throws Exception{
        String[] names={"LightEngine","BlockLightEngine","SkyLightEngine","LayerLightSectionStorage"};
        String[] hashes={"b6be7a9e3e43c4185ae76e3e390acb649acbf2ff7e0b793d3acb5c9592f7c453",
            "2b672b2b513751d8f9fcb89b319270e6d1a3171e5fc0d58d28786628f3ced727",
            "97e441d316271e8cbc125481e86187d970cf29058e7738a56e7fd5e786f8eeec",
            "31076b253b190711c0516653cb0a85718a7b96ea1af0883ed3e837720c051539"};
        boolean frozen=Boolean.getBoolean("coordinator.frozen")||Boolean.getBoolean("threadedLight.frozen");
        for(int i=0;i<names.length;i++){
            Class<?> type=Class.forName("net.minecraft.world.level.lighting."+names[i]);String digest;
            try(var bytes=type.getResourceAsStream(names[i]+".class")){digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));}
            if(frozen&&!digest.equals(hashes[i]))throw new AssertionError("Unexpected Frozen class "+names[i]);
            System.out.println("COORDINATOR_CLASS "+names[i]+" "+digest);
        }
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {
            try { Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object); }
            catch (NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static String layer(DataLayer data) throws Exception {
        if(data==null)return "null";
        if(data.isDefinitelyHomogenous())return "lazy:"+data.get(0,0,0);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.copy().getData()));
    }
    @SuppressWarnings("unchecked") private static Map<Long,DataLayer> map(DataLayerStorageMap<?> map) throws Exception {
        try {
            var method=DataLayerStorageMap.class.getDeclaredMethod("snapshotForVerification");method.setAccessible(true);
            return (Map<Long,DataLayer>)method.invoke(map);
        } catch(NoSuchMethodException frozen) { return map.map; }
    }
    private static final class World implements LightChunkGetter,BlockGetter {
        final Map<Long,ProtoChunk> chunks=new TreeMap<>();
        BiConsumer<LightLayer,SectionPos> notification;
        long notifications;
        World() {
            for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
                LevelChunkSection[] sections=new LevelChunkSection[4];
                for(int i=0;i<4;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
                sections[1].setBlockState(8,8,8,Blocks.GLOWSTONE.defaultBlockState(),false);
                for(int bx=0;bx<16;bx++)for(int bz=0;bz<16;bz++)
                    sections[2].setBlockState(bx,15,bz,Blocks.STONE.defaultBlockState(),false);
                var chunk=new ProtoChunk(new ChunkPos(x,z),UpgradeData.EMPTY,sections,
                    new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                    LevelHeightAccessor.create(0,64),null,null);
                chunk.initializeLightSources();chunks.put(chunk.getPos().toLong(),chunk);
            }
        }
        public LightChunk getChunkForLighting(int x,int z){return chunks.get(ChunkPos.asLong(x,z));}
        public BlockGetter getLevel(){return this;}
        public void onLightUpdate(LightLayer kind,SectionPos pos){
            notifications++;row("notify "+kind+" "+pos.asLong());
            if(notification!=null)notification.accept(kind,pos);
        }
        public BlockEntity getBlockEntity(BlockPos pos){return null;}
        public BlockState getBlockState(BlockPos pos){var c=chunks.get(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));return c==null?Blocks.BEDROCK.defaultBlockState():c.getBlockState(pos);}
        public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        public int getMinY(){return 0;}
        public int getHeight(){return 64;}
    }
    private static final class Fixture {
        final World world=new World();
        final LevelLightEngine engine;
        final IdentityHashMap<DataLayer,String> held=new IdentityHashMap<>();
        Fixture(boolean sky)throws Exception{
            engine=new LevelLightEngine(world,true,sky);
            for(var chunk:world.chunks.values())for(int y=0;y<4;y++)
                if(!chunk.getSection(y).hasOnlyAir())engine.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
            drain("allocate");
            for(var chunk:world.chunks.values())engine.propagateLightSources(chunk.getPos());
            drain("light");
            for(var chunk:world.chunks.values()){
                ChunkPos p=chunk.getPos();
                if(!engine.lightOnInColumn(SectionPos.getZeroNode(p.x,p.z)))throw new AssertionError("disabled column");
                if(engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(p.x*16+8,24,p.z*16+8))!=15)
                    throw new AssertionError("nonvacuity: missing emitter");
                if(sky && engine.getLayerListener(LightLayer.SKY).getLightValue(new BlockPos(p.x*16+8,63,p.z*16+8))!=15)
                    throw new AssertionError("nonvacuity: missing sky");
            }
            row("ready "+sky+" columns=9 block=15 sky="+(sky?15:"absent"));
            snapshot("initial");
        }
        void pass(String name)throws Exception{
            try { row("pass "+name+" count="+engine.runLightUpdates()); }
            catch(RuntimeException error){row("pass "+name+" throws="+error.getClass().getName());}
            snapshot(name);
        }
        void drain(String name)throws Exception{
            for(int i=0;i<20;i++){
                row("drain "+name+" "+i+" "+engine.runLightUpdates());
                if(!engine.hasLightWork())return;
            }
            throw new AssertionError("work did not quiesce");
        }
        void edit(int x,int y,int z,BlockState state){
            var chunk=world.chunks.get(ChunkPos.asLong(x>>4,z>>4));var section=chunk.getSection(y>>4);
            boolean empty=section.hasOnlyAir();
            section.setBlockState(x&15,y&15,z&15,state,false);
            if(empty!=section.hasOnlyAir())engine.updateSectionStatus(SectionPos.of(x>>4,y>>4,z>>4),section.hasOnlyAir());
            chunk.getSkyLightSources().update(chunk,x&15,y,z&15);
            engine.checkBlock(new BlockPos(x,y,z));
        }
        void snapshot(String name)throws Exception{
            row("snapshot "+name+" pending="+engine.hasLightWork());
            for(var e:held.entrySet())if(!layer(e.getKey()).equals(e.getValue()))throw new AssertionError("Published layer changed");
            for(LightLayer kind:LightLayer.values()){
                if(!(engine.getLayerListener(kind) instanceof LightEngine<?,?> light))continue;
                var storage=light.storage;var updating=map(storage.updatingSectionData);var visible=map(storage.visibleSectionData);
                for(var pair:List.of(Map.entry("updating",updating),Map.entry("visible",visible),Map.entry("queued",storage.queuedSections)))
                    for(var e:new TreeMap<>(pair.getValue()).entrySet())row(kind+" "+pair.getKey()+" "+e.getKey()+" "+layer(e.getValue()));
                row(kind+" states "+new TreeMap<>(storage.sectionStates));
                row(kind+" changed "+new ArrayList<>(storage.changedSections));
                row(kind+" affected "+new ArrayList<>(storage.sectionsAffectedByLightUpdates));
                for(long key:new TreeSet<>(updating.keySet()))row(kind+" sharing "+key+" "+(updating.get(key)==visible.get(key)));
                for(var data:visible.values())if(data!=null)held.putIfAbsent(data,layer(data));
                if(storage instanceof SkyLightSectionStorage sky){
                    row("sky lowest "+sky.getBottomSectionY());
                    for(var p:world.chunks.values())row("sky top "+p.getPos()+" "+sky.getTopSectionY(SectionPos.getZeroNode(p.getPos().x,p.getPos().z)));
                }
            }
        }
        void assertNativePolicy()throws Exception{
            if(!normalNativeComposition())return;
            for(LightLayer kind:LightLayer.values()){
                if(!(engine.getLayerListener(kind) instanceof LightEngine<?,?> light))continue;
                Object owner=field(light.storage,"coordinator");
                if(owner==null)throw new AssertionError("Stock storage did not admit native policy");
                try{
                    MemorySegment metadata=(MemorySegment)field(owner,"metadata");
                    long[] phases=new long[4];
                    for(int i=0;i<4;i++){
                        phases[i]=metadata.get(ValueLayout.JAVA_LONG,40L+i*Long.BYTES);
                        if(phases[i]<=0)throw new AssertionError("Native node/section/reconcile/publication phase missing: "+kind+" "+i);
                    }
                    System.out.println("NATIVE_POLICY "+kind+" "+Arrays.toString(phases));
                }finally{Reference.reachabilityFence(owner);Reference.reachabilityFence(light);}
            }
        }
    }
    private static void ordinary(boolean sky)throws Exception{
        row("ordinary "+sky);var f=new Fixture(sky);
        for(int round=0;round<4;round++){
            for(int i=0;i<32;i++)f.edit((i%16)-1,24+i%8,(i*7%32)-8,(round%2==0?Blocks.GLOWSTONE:Blocks.AIR).defaultBlockState());
            f.pass("edits"+round);
        }
        for(int count:new int[]{0,1,999,1000,1001,2501}){
            for(int i=0;i<count;i++)f.engine.checkBlock(new BlockPos(i%32-16,24+i%16,i/32%32-16));
            f.pass("nodes"+count);
        }
        var pos=SectionPos.of(0,1,0);var chunk=new ChunkPos(0,0);
        for(LightLayer kind:LightLayer.values())f.engine.queueSectionData(kind,pos,new DataLayer(7));
        f.engine.retainData(chunk,true);f.engine.updateSectionStatus(pos,true);f.pass("retain-remove");
        f.engine.updateSectionStatus(pos,false);f.pass("restore");
        f.engine.updateSectionStatus(pos,true);f.engine.updateSectionStatus(pos,false);f.pass("cancel-remove");
        f.engine.retainData(chunk,false);f.engine.queueSectionData(LightLayer.BLOCK,pos,null);f.pass("clear-queued");
        for(int i=0;i<3;i++){
            f.edit(i,47,0,Blocks.AIR.defaultBlockState());f.pass("open-roof"+i);
            f.edit(i,47,0,Blocks.STONE.defaultBlockState());f.pass("close-roof"+i);
        }
        f.assertNativePolicy();
    }
    private static void notifications(boolean nested)throws Exception{
        row("callback "+nested);var f=new Fixture(true);final int[] called={0};
        f.world.notification=(kind,pos)->{
            int n=++called[0];
            if(n==1){
                row("callback visible="+f.engine.getLayerListener(kind).getDataLayerData(pos).get(0,0,0));
                if(nested){
                    try{row("nested count="+f.engine.runLightUpdates());}
                    catch(RuntimeException error){row("nested throws="+error.getClass().getName());}
                } else {
                    f.engine.checkBlock(new BlockPos(0,24,0));
                    throw new IllegalArgumentException("notification failure");
                }
            }
        };
        f.edit(15,24,0,Blocks.GLOWSTONE.defaultBlockState());f.pass("callback-first");
        f.world.notification=null;f.pass("callback-retry");
        row("callback visits="+called[0]);
        f.assertNativePolicy();
    }
    private static void sharedStorage()throws Exception{
        row("shared-storage");World world=new World();
        BlockLightSectionStorage storage=new BlockLightSectionStorage(world);
        BlockLightEngine left=new BlockLightEngine(world,storage),right=new BlockLightEngine(world,storage);
        for(var chunk:world.chunks.values())for(int y=0;y<4;y++)
            if(!chunk.getSection(y).hasOnlyAir())left.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
        left.runLightUpdates();
        for(var chunk:world.chunks.values())left.propagateLightSources(chunk.getPos());
        left.runLightUpdates();
        row("shared ready "+left.getLightValue(new BlockPos(8,24,8))+" "+right.getLightValue(new BlockPos(8,24,8)));
        left.checkBlock(new BlockPos(0,24,0));
        row("shared left-node "+left.hasLightWork()+" "+right.hasLightWork());
        row("shared right-run "+right.runLightUpdates()+" "+left.hasLightWork()+" "+right.hasLightWork());
        left.runLightUpdates();
        left.enqueueIncrease(BlockPos.asLong(8,24,8),LightEngine.QueueEntry.increaseLightFromEmission(15,true));
        row("shared left-work "+left.hasLightWork()+" "+right.hasLightWork());
        row("shared right-no-drain "+right.runLightUpdates()+" "+left.hasLightWork()+" "+right.hasLightWork());
        left.runLightUpdates();row("shared settled "+left.hasLightWork()+" "+right.hasLightWork());
    }
    /** A focused setup adapter, never an alternate expected algorithm. Frozen
     * and pre use their actual LightEngine queues; the unadmitted native
     * composition probe redirects the same API calls to the new private owner. */
    private static final boolean COMPOSITION_PROTOTYPE=Boolean.getBoolean("coordinator.prototypeComposition");
    private static Object call(Object receiver,String name,Class<?>[] types,Object... args){
        try{
            var method=receiver.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(receiver,args);
        }catch(InvocationTargetException error){throw unchecked(error.getCause());}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable> RuntimeException unchecked(Throwable error)throws E{throw(E)error;}
    private static final class CheckedMarker extends Exception {}
    private interface Hook {void accept(Probe probe,long pos);}
    private static final class Probe extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap,BlockLightSectionStorage> {
        final String name;
        Object nativeBinding;
        Hook hook;
        Probe(World world,BlockLightSectionStorage storage,String name){super(world,storage);this.name=name;}
        @Override protected void checkNode(long pos){row("context "+name+" node "+pos);if(hook!=null)hook.accept(this,pos);}
        @Override protected void propagateIncrease(long pos,long entry,int level){row("context "+name+" increase "+pos);}
        @Override protected void propagateDecrease(long pos,long entry){row("context "+name+" decrease "+pos);}
        @Override public void propagateLightSources(ChunkPos pos){}
        private it.unimi.dsi.fastutil.longs.LongSet nativeNodes(){return(it.unimi.dsi.fastutil.longs.LongSet)call(nativeBinding,"set",new Class<?>[]{int.class},0);}
        @Override public void checkBlock(BlockPos pos){if(nativeBinding==null)super.checkBlock(pos);else nativeNodes().add(pos.asLong());}
        @Override public boolean hasLightWork(){return(nativeBinding!=null&&!nativeNodes().isEmpty())||super.hasLightWork();}
        @Override public int runLightUpdates(){return nativeBinding==null?super.runLightUpdates():((Number)call(nativeBinding,"runLightUpdates",new Class<?>[0])).intValue();}
        void queue(long pos){enqueueDecrease(pos,LightEngine.QueueEntry.decreaseAllDirections(1));}
        void write(long pos,int value){if(nativeBinding==null)storage.setStoredLevel(pos,value);else call(nativeBinding,"setStored",new Class<?>[]{long.class,int.class},pos,value);}
        void publish(){if(nativeBinding==null)storage.swapSectionMap();else call(nativeBinding,"publish",new Class<?>[0]);}
        @SuppressWarnings("unchecked") it.unimi.dsi.fastutil.longs.Long2ObjectMap<DataLayer> queued(){return nativeBinding==null?storage.queuedSections:(it.unimi.dsi.fastutil.longs.Long2ObjectMap<DataLayer>)call(nativeBinding,"queued",new Class<?>[0]);}
        void inconsistent(){if(nativeBinding==null)storage.hasInconsistencies=true;else call(nativeBinding,"markInconsistent",new Class<?>[0]);}
        void queueData(long key,DataLayer data){if(nativeBinding==null)storage.queueSectionData(key,data);else{queued().put(key,data);inconsistent();}}
    }
    private record Pair(Probe left,Probe right,WeakReference<Object> root){}
    private static boolean normalNativeComposition(){
        try{LightEngine.class.getDeclaredField("lightCoordinator");return true;}catch(NoSuchFieldException legacy){return false;}
    }
    private static Object bindingForProof(Probe probe)throws Exception{
        return probe.nativeBinding!=null?probe.nativeBinding:normalNativeComposition()?field(probe,"lightCoordinator"):null;
    }
    private static Pair pair(String leftName,String rightName)throws Exception{
        World world=new World();BlockLightSectionStorage storage=new BlockLightSectionStorage(world);
        Probe left=new Probe(world,storage,leftName),right=new Probe(world,storage,rightName);
        Object root=null;
        if(COMPOSITION_PROTOTYPE){
            if(normalNativeComposition())throw new AssertionError("Normal native route already present; do not add a second prototype owner");
            Class<?> type=Class.forName("net.minecraft.world.level.lighting.NativeLightCoordinator");
            Class<?> mapType=Class.forName("net.minecraft.world.level.lighting.NativeLightingMap");
            var constructor=type.getDeclaredConstructor(LayerLightSectionStorage.class,LightLayer.class,mapType);constructor.setAccessible(true);
            root=constructor.newInstance(storage,LightLayer.BLOCK,field(storage.updatingSectionData,"nativeMap"));
            left.nativeBinding=call(root,"forEngine",new Class<?>[]{LightEngine.class},left);
            right.nativeBinding=call(root,"forEngine",new Class<?>[]{LightEngine.class},right);
            if(((Number)call(left.nativeBinding,"enginesForVerification",new Class<?>[0])).intValue()!=2)throw new AssertionError("Missing independent native bindings");
        } else if(normalNativeComposition()){
            Object binding=bindingForProof(left);
            if(binding==null||((Number)call(binding,"enginesForVerification",new Class<?>[0])).intValue()!=2)throw new AssertionError("Normal native engine bindings missing");
            root=field(storage,"coordinator");
        }
        return new Pair(left,right,new WeakReference<>(root));
    }
    private static void interleavedComposition(boolean checked)throws Exception{
        row("interleaved "+checked);Pair pair=pair("left","right");Probe left=pair.left,right=pair.right;
        Throwable marker=checked?new CheckedMarker():new IllegalArgumentException("marker");boolean[] once={false};
        WeakReference<Probe> original=new WeakReference<>(left);
        left.hook=(self,pos)->{
            if(!once[0]){
                once[0]=true;
                right.checkBlock(new BlockPos(2,0,0));row("interleaved inner "+right.runLightUpdates());
                System.gc();
                if(original.get()!=self)throw new AssertionError("Originating engine released during callback");
                throw unchecked(marker);
            }
        };
        left.checkBlock(new BlockPos(1,0,0));
        row("interleaved admission "+left.hasLightWork()+" "+right.hasLightWork());
        try{left.runLightUpdates();throw new AssertionError("Missing original callback failure");}
        catch(Throwable error){if(error!=marker)throw new AssertionError("Callback Throwable replaced",error);row("interleaved throwable "+error.getClass().getName()+" same=true");}
        row("interleaved failed pending "+left.hasLightWork()+" "+right.hasLightWork());
        row("interleaved retry "+left.runLightUpdates());
        left.queue(11);right.checkBlock(new BlockPos(3,0,0));
        row("interleaved right "+right.runLightUpdates()+" left-pending="+left.hasLightWork());
        row("interleaved left "+left.runLightUpdates());
        row("interleaved settled "+left.hasLightWork()+" "+right.hasLightWork());
    }
    private static void collect(WeakReference<?> reference)throws Exception{
        for(int i=0;i<200&&reference.get()!=null;i++){System.gc();Thread.sleep(10);}
        if(reference.get()!=null)throw new AssertionError("Unreachable engine/root retained");
    }
    private record Disposal(Probe survivor,WeakReference<Probe> retired,WeakReference<Object> root){}
    private static Disposal disposal()throws Exception{
        Pair pair=pair("discarded","survivor");pair.left.checkBlock(new BlockPos(7,0,0));
        pair.right.checkBlock(new BlockPos(8,0,0));pair.right.queue(99);
        return new Disposal(pair.right,new WeakReference<>(pair.left),pair.root);
    }
    private static WeakReference<Probe> disposalContract()throws Exception{
        Disposal d=disposal();collect(d.retired);
        Object binding=bindingForProof(d.survivor);
        if(binding!=null){
            if(COMPOSITION_PROTOTYPE)collect(d.root);
            for(int i=0;i<200&&((Number)call(binding,"enginesForVerification",new Class<?>[0])).intValue()!=1;i++){System.gc();Thread.sleep(10);}
            if(((Number)call(binding,"enginesForVerification",new Class<?>[0])).intValue()!=1)throw new AssertionError("Retired binding leaked or removed survivor");
        }
        row("disposal collected=true survivor-pending="+d.survivor.hasLightWork());
        row("disposal survivor-run "+d.survivor.runLightUpdates());
        row("disposal settled "+d.survivor.hasLightWork());
        return new WeakReference<>(d.survivor);
    }
    private static void layerComposition(int mode)throws Exception{
        row("layer-composition "+mode);Pair pair=pair("writer","layer-reader");
        long key=SectionPos.asLong(0,1,0),pos=BlockPos.asLong(8,24,8);
        byte[] aliased=new byte[2048];Arrays.fill(aliased,(byte)0x77);
        int[] copies={0};
        DataLayer source=mode==0?new DataLayer(7):mode==1?new DataLayer(aliased):new DataLayer(7){
            @Override public DataLayer copy(){
                copies[0]++;row("layer custom-copy "+copies[0]);
                pair.right.checkBlock(new BlockPos(9,0,0));
                row("layer reentrant-other "+pair.right.runLightUpdates());
                return new DataLayer(7);
            }
        };
        pair.left.storage.updatingSectionData.setLayer(key,source);
        pair.left.storage.changedSections.add(key);pair.left.storage.swapSectionMap();
        DataLayer held=pair.left.getDataLayerData(SectionPos.of(key));
        row("layer shared-before "+(held==pair.right.getDataLayerData(SectionPos.of(key)))+" "+held.get(8,8,8));
        pair.left.write(pos,9);
        DataLayer current=pair.left.storage.updatingSectionData.getLayer(key);
        row("layer COW "+(current!=held)+" old="+held.get(8,8,8)+" updating="+current.get(8,8,8)+" visible="+pair.right.getDataLayerData(SectionPos.of(key)).get(8,8,8));
        pair.left.publish();
        row("layer after-publication same="+(current==pair.right.getDataLayerData(SectionPos.of(key)))+" visible="+pair.right.getDataLayerData(SectionPos.of(key)).get(8,8,8)+" old="+held.get(8,8,8)+" copies="+copies[0]);
        if(mode==1){aliased[0]=0x22;row("layer external-array old="+held.get(0,0,0)+" copy="+current.get(0,0,0));}
    }
    private static void queuedIdentityComposition()throws Exception{
        row("queued-identity");Pair pair=pair("queue-writer","queue-reader");long key=SectionPos.asLong(0,1,0);
        DataLayer original=new DataLayer(7);pair.left.storage.updatingSectionData.setLayer(key,original);
        pair.left.storage.changedSections.add(key);pair.left.storage.swapSectionMap();
        Object visible=pair.left.storage.visibleSectionData;
        pair.left.queueData(key,original);pair.right.runLightUpdates();
        row("queued same-object same-visible="+(visible==pair.left.storage.visibleSectionData)+" same-layer="+(original==pair.right.getDataLayerData(SectionPos.of(key))));
        visible=pair.left.storage.visibleSectionData;DataLayer replacement=new DataLayer(7);
        pair.left.queueData(key,replacement);pair.right.runLightUpdates();
        row("queued new-object same-visible="+(visible==pair.left.storage.visibleSectionData)+" exact-layer="+(replacement==pair.right.getDataLayerData(SectionPos.of(key))));
    }
    private static void composition()throws Exception{
        interleavedComposition(false);interleavedComposition(true);
        for(int i=0;i<3;i++)layerComposition(i);
        queuedIdentityComposition();
        collect(disposalContract());
        if(COMPOSITION_PROTOTYPE||normalNativeComposition()){
            Class<?> type=Class.forName("net.minecraft.world.level.lighting.NativeLightCoordinator");
            var live=type.getDeclaredMethod("liveForVerification");live.setAccessible(true);
            for(int i=0;i<200&&((Number)live.invoke(null)).longValue()!=0;i++){System.gc();Thread.sleep(10);}
            if(((Number)live.invoke(null)).longValue()!=0)throw new AssertionError("Native storage owner leaked after all engine bindings retired");
            System.out.println("NATIVE_COMPOSITION mode="+(COMPOSITION_PROTOTYPE?"prototype":"normal")+" bindings=2 independent-work/context=true owners-after=0");
        }
    }
    private static boolean cacheEmpty(LightEngine<?,?> engine)throws Exception{
        for(long pos:(long[])field(engine,"lastChunkPos"))if(pos!=ChunkPos.INVALID_CHUNK_POS)return false;
        for(Object chunk:(Object[])field(engine,"lastChunk"))if(chunk!=null)return false;
        return true;
    }
    private static void seedCache(LightEngine<?,?> engine,World world)throws Exception{
        ((long[])field(engine,"lastChunkPos"))[0]=42;
        ((Object[])field(engine,"lastChunk"))[0]=world.chunks.values().iterator().next();
    }
    private static void assertIdle(LightEngine<?,?> engine,boolean expected)throws Exception{
        if(!normalNativeComposition())return;
        Object owner=field(engine,"lightCoordinator");
        try{
            MemorySegment header=(MemorySegment)field(owner,"engineMetadata");
            if((header.get(ValueLayout.JAVA_INT,4)!=0)!=expected)throw new AssertionError("Incorrect native empty eligibility");
        }finally{Reference.reachabilityFence(owner);Reference.reachabilityFence(engine);}
    }
    private static void idleContract()throws Exception{
        row("idle-contract");var f=new Fixture(true);
        for(var kind:LightLayer.values()){
            var engine=(LightEngine<?,?>)f.engine.getLayerListener(kind);assertIdle(engine,true);seedCache(engine,f.world);
        }
        row("idle empty "+f.engine.runLightUpdates());
        for(var kind:LightLayer.values()){
            var engine=(LightEngine<?,?>)f.engine.getLayerListener(kind);
            row("idle cache "+kind+" "+cacheEmpty(engine));
            var storage=engine.storage;long key=SectionPos.asLong(0,1,0);
            Object visible=storage.visibleSectionData;
            storage.changedSections.add(key);assertIdle(engine,false);
            row("idle changed-only "+kind+" pending="+engine.hasLightWork()+" count="+engine.runLightUpdates()+" published="+(visible!=storage.visibleSectionData));
            storage.sectionsAffectedByLightUpdates.add(key);assertIdle(engine,false);
            row("idle affected-only "+kind+" pending="+engine.hasLightWork()+" count="+engine.runLightUpdates());
            assertIdle(engine,true);
        }
        var block=(LightEngine<?,?>)f.engine.getLayerListener(LightLayer.BLOCK);
        var clear=LightEngine.class.getDeclaredMethod("clearChunkCache");clear.setAccessible(true);
        // Modeled eligibility/clear boundary: no production callback is added
        // to private cache clearing. A newer producer must stay pending.
        assertIdle(block,true);block.checkBlock(new BlockPos(8,24,8));clear.invoke(block);
        row("idle boundary node pending="+block.hasLightWork()+" count="+block.runLightUpdates());
        assertIdle(block,true);block.enqueueDecrease(BlockPos.asLong(8,24,8),LightEngine.QueueEntry.decreaseAllDirections(1));clear.invoke(block);
        row("idle boundary queue pending="+block.hasLightWork()+" count="+block.runLightUpdates());
        World world=new World();BlockLightEngine[] custom={null};boolean[] once={false};
        var storage=new BlockLightSectionStorage(world){
            @Override protected void markNewInconsistencies(LightEngine<BlockDataLayerStorageMap,?> engine){
                try{row("idle custom reconcile cache-empty="+cacheEmpty(engine));}catch(Exception error){throw new AssertionError(error);}
                super.markNewInconsistencies(engine);
                if(!once[0]){once[0]=true;engine.checkBlock(new BlockPos(1,80,1));}
            }
            @Override protected void swapSectionMap(){row("idle custom publish");super.swapSectionMap();}
        };
        custom[0]=new BlockLightEngine(world,storage);seedCache(custom[0],world);
        row("idle custom first "+custom[0].runLightUpdates()+" pending="+custom[0].hasLightWork());
        row("idle custom retry "+custom[0].runLightUpdates()+" pending="+custom[0].hasLightWork());
    }
    public static void main(String[] args)throws Exception{
        pinClasses();
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try(var writer=new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))){
            out=writer;
            if(args.length>1&&args[1].equals("shared"))sharedStorage();
            else if(args.length>1&&args[1].equals("composition"))composition();
            else {ordinary(false);ordinary(true);notifications(false);notifications(true);sharedStorage();idleContract();composition();}
        }
        System.out.println("COORDINATOR_ROWS "+rows);
    }
}
