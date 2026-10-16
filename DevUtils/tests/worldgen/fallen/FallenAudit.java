package fallen;

import com.google.gson.GsonBuilder;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.*;
import net.minecraft.util.valueproviders.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.*;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Real public feature calls in isolated real WorldGenRegions. The agent only
 * observes actual calls; this class contains no replacement placement algorithm. */
public final class FallenAudit {
    static final AtomicBoolean STARTED=new AtomicBoolean();
    static final Path OUTPUT=Path.of(System.getProperty("audit.output"));
    static final List<Map<String,Object>> ROWS=new ArrayList<>();
    static Tape tape;
    static int programAdapterEntries,stockVmDecorationWrites,legacyFallenBodies,legacyAttachedBodies;
    static final class Tape {
        final List<String> events=new ArrayList<>();
        final Error failure=new AssertionError("Fallen effect sentinel");
        final WorldgenRandom random;
        final Exception checked=new Exception("Fallen checked sentinel");
        BlockPos origin=new BlockPos(8,80,8);
        int writes,failAt,moveAt;
        String fixtureName;
        Class<? extends Throwable> expectedError;
        BlockPos firstCursor;
        Runnable onWrite;
        Tape(WorldgenRandom random){this.random=random;}
        void event(String value){events.add(value+" rng="+randomState(random));}
    }
    static Object field(Object target,String name){
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{
            var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);
        }catch(NoSuchFieldException missing){}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        throw new AssertionError("Missing field "+name);
    }
    static String randomState(WorldgenRandom random){
        Object source=field(random,"randomSource");String nested="";
        while(source instanceof WorldgenRandom inner){nested+="|innerCount="+inner.getCount();source=field(inner,"randomSource");}
        Object generator=field(source,"randomNumberGenerator"),gaussian=field(random,"gaussianSource");
        return random.getCount()+":"+field(generator,"seedLo")+":"+field(generator,"seedHi")+":"
            +field(gaussian,"haveNextNextGaussian")+":"+Double.doubleToRawLongBits((Double)field(gaussian,"nextNextGaussian"))+nested;
    }
    public static void write(Object value,Object state,int flags){
        Tape t=tape;if(t==null)return;BlockPos pos=(BlockPos)value;
        if(flags==19&&t.fixtureName.matches("parent-(oak|birch|super_birch|jungle|spruce)"))stockVmDecorationWrites++;
        t.event("write "+pos.toShortString()+" "+state+" flags="+flags+" "+pos.getClass().getSimpleName());
        if(t.firstCursor==null)t.firstCursor=pos;
        if(++t.writes==t.failAt)throw t.failure;
        if(t.writes==t.moveAt&&pos instanceof BlockPos.MutableBlockPos p)p.move(0,1,0);
        if(t.onWrite!=null){Runnable action=t.onWrite;t.onWrite=null;action.run();}
    }
    public static void read(Object value){Tape t=tape;if(t!=null)t.event("read "+((BlockPos)value).toShortString());}
    public static void mark(Object value){Tape t=tape;if(t!=null)t.event("mark "+((BlockPos)value).toShortString());}
    public static void support(Object state,Object pos,Object direction){Tape t=tape;if(t!=null){t.event("support "+((BlockPos)pos).toShortString()+" "+direction);if(t.fixtureName.equals("parent-dynamic-support-position")&&((BlockState)state).getBlock().hasDynamicShape())t.event("dynamic-query "+((BlockPos)pos).toShortString()+" "+direction);}}
    public static void legacyAdapter(){
        if(tape!=null&&tape.fixtureName.matches("parent-(oak|birch|super_birch|jungle|spruce)")){
            programAdapterEntries++;
            if(Boolean.getBoolean("fallen.programMarker"))throw new AssertionError("Old configured fallen-tree adapter was invoked");
        }
    }
    public static void legacyBody(){if(tape!=null)legacyFallenBodies++;if(Boolean.getBoolean("fallen.marker"))throw new AssertionError("Old Java fallen-tree body was invoked");}
    public static void legacyDecorator(){if(tape!=null)legacyAttachedBodies++;if(Boolean.getBoolean("fallen.marker"))throw new AssertionError("Old Java attached-log body was invoked");}
    public static void capture(Object value){
        if(STARTED.compareAndSet(false,true)){
            MinecraftServer server=(MinecraftServer)value;server.execute(()->run(server));
        }
    }
    static final class HolderStub extends GenerationChunkHolder {
        final ProtoChunk chunk;
        HolderStub(ProtoChunk chunk) { super(chunk.getPos()); this.chunk = chunk; }
        @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk; }
        @Override protected void addSaveDependency(CompletableFuture<?> dependency) {}
        @Override public int getTicketLevel() { return 0; }
        @Override public int getQueueLevel() { return 0; }
    }

    static final class Fixture implements AutoCloseable {
        final ProtoChunk[] chunks;
        final ProtoChunk centerChunk;
        final WorldGenRegion region;
        final ChunkGenerator generator;
        Object binding;
        java.lang.reflect.Method nativePlace;
        final IdentityHashMap<ConfiguredFeature<?, ?>, PlacedFeature> placements = new IdentityHashMap<>();
        final ServerLevel server;
        Fixture(ServerLevel server) { this(server, new ChunkPos(0, 0)); }
        Fixture(ServerLevel server, ChunkPos center) { this(server,center,1); }
        Fixture(ServerLevel server, ChunkPos center,int radius) {
            this.server = server;
            int side=radius*2+1;chunks=new ProtoChunk[side*side];
            generator = server.getChunkSource().getGenerator();
            var factory = PalettedContainerFactory.create(server.registryAccess());
            var cache = StaticCache2D.<GenerationChunkHolder>create(center.x, center.z, radius, (x, z) -> {
                var chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, server, factory, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunks[(z - center.z + radius) * side + x - center.x + radius] = chunk;
                return new HolderStub(chunk);
            });
            centerChunk=chunks[radius*side+radius];
            region = new WorldGenRegion(server, cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), centerChunk);
        }
        Object programs;
        Object nativeRegion(){
            try{
                if(binding==null)binding=Class.forName("net.minecraft.world.level.levelgen.NativeDecoration")
                    .getMethod("bind",WorldGenLevel.class,ChunkAccess.class,ChunkGenerator.class).invoke(null,region,centerChunk,generator);
                if(binding==null)throw new AssertionError("Reference region declined");
                if(programs==null)programs=Class.forName("net.minecraft.world.level.levelgen.NativeDecoration$Programs").getConstructor().newInstance();
                return binding;
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        }
        void prepare(PlacedFeature placed){
            var binding=nativeRegion();
            try{
                var method=programs.getClass().getDeclaredMethod("program",PlacedFeature.class,ChunkGenerator.class,binding.getClass());
                method.setAccessible(true);if(method.invoke(programs,placed,generator,binding)==null)throw new AssertionError("Reference parent program declined");
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        }
        boolean placeParent(PlacedFeature placed,WorldgenRandom random,BlockPos origin)throws Throwable{
            try{
                Object result=Class.forName("net.minecraft.world.level.levelgen.NativeDecoration")
                    .getMethod("placeWithRandom",programs.getClass(),binding.getClass(),ChunkGenerator.class,WorldGenLevel.class,WorldgenRandom.class,PlacedFeature.class,BlockPos.class)
                    .invoke(null,programs,binding,generator,region,random,placed,origin);
                return result==null?placed.place(region,generator,random,origin):(boolean)result;
            }catch(InvocationTargetException e){throw e.getCause();}
        }
        Map<String,Object> snapshot() throws Exception {
            List<String> blocks = new ArrayList<>(), maps = new ArrayList<>(), entities = new ArrayList<>(), blockEntities = new ArrayList<>();
            int nonAir = 0;
            for (var chunk : chunks) {
                for (var section : chunk.getSections()) {
                    var packed = section.getStates().pack(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
                    blocks.add(packed.paletteEntries() + " " + packed.storage().map(s -> Arrays.toString(s.toArray())).orElse("-") + " " + packed.bitsPerEntry());
                    for (int i = 0; i < 4096; i++) if (!section.getBlockState(i & 15, i >> 8, (i >> 4) & 15).isAir()) nonAir++;
                }
                for (var type : Heightmap.Types.values()) maps.add(type + ":" + (chunk.hasPrimedHeightmap(type)
                    ? Arrays.toString(chunk.getOrCreateHeightmapUnprimed(type).getRawData()) : "-"));
                maps.add("marks:" + Arrays.deepToString(chunk.getPostProcessing()));
                for (var pos : chunk.getBlockEntitiesPos().stream().sorted().toList())
                    blockEntities.add(pos + ":" + canonical(chunk.getBlockEntityNbtForSaving(pos, server.registryAccess())));
                for (var tag : chunk.getEntities()) {
                    var copy = tag.copy(); copy.remove("UUID");
                    entities.add(canonical(copy));
                }
            }
            return Map.of("blocks", digest(blocks.toString()), "metadata", digest(maps.toString()), "nonAir", nonAir,
                "blockEntities", blockEntities, "entities", entities);
        }
        public void close() throws Exception {
            if (binding != null) Class.forName("net.minecraft.world.level.levelgen.NativeDecoration")
                .getMethod("release", binding.getClass()).invoke(null, binding);
        }
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException propagate(Throwable failure)throws E{throw (E)failure;}
    static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var out = new TreeMap<String,String>();
            for (var key : compound.keySet()) out.put(key, canonical(compound.get(key)));
            return out.toString();
        }
        if (tag instanceof ListTag list) {
            var out = new ArrayList<String>(); for (var child : list) out.add(canonical(child)); return out.toString();
        }
        return String.valueOf(tag);
    }
    static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static FallenTreeConfiguration config(BlockStateProvider provider,int length,List<TreeDecorator> stump,List<TreeDecorator> logs){
        return new FallenTreeConfiguration.FallenTreeConfigurationBuilder(provider,ConstantInt.of(length))
            .stumpDecorators(stump).logDecorators(logs).build();
    }
    static class Provider extends BlockStateProvider {
        final BlockState state;final List<BlockPos> retained=new ArrayList<>();
        Provider(BlockState state){this.state=state;}
        protected BlockStateProviderType<?> type(){return BlockStateProviderType.SIMPLE_STATE_PROVIDER;}
        public BlockState getState(RandomSource random,BlockPos pos){
            tape.event("provider "+pos.toShortString()+" "+pos.getClass().getSimpleName());
            retained.add(pos);return state;
        }
    }
    static final class Effect extends TreeDecorator {
        final java.util.function.Consumer<TreeDecorator.Context> action;
        Effect(java.util.function.Consumer<TreeDecorator.Context> action){this.action=action;}
        protected TreeDecoratorType<?> type(){return TreeDecoratorType.TRUNK_VINE;}
        public void place(TreeDecorator.Context c){
            tape.event("decorator logs="+c.logs()+" leaves="+c.leaves());action.accept(c);
        }
    }
    interface Setup {FallenTreeConfiguration prepare(Fixture f,Tape t) throws Exception;}
    static void fixture(ServerLevel level,String name,long seed,String shape,Setup setup)throws Throwable{
        if(!name.matches(System.getProperty("fallen.fixtures",".*")))return;
        boolean edge=name.contains("edge-read-radius");
        try(var f=edge?new Fixture(level,new ChunkPos(0,0),2):new Fixture(level)){
            int low=edge?-32:-16,high=edge?48:32;
            for(int z=low;z<high;z++)for(int x=low;x<high;x++){
                int distance=Math.max(Math.abs(x-8),Math.abs(z-8));
                boolean gap=shape.equals("void")
                    ||shape.equals("gap2")&&(distance==5||distance==6)
                    ||shape.equals("gap3")&&(distance>=5&&distance<=7)
                    ||shape.equals("gap-reset")&&(distance==5||distance==6||distance==8||distance==9);
                if(!gap){var ground=new BlockPos(x,79,z);if(edge)f.region.getChunk(ground).setBlockState(ground,Blocks.STONE.defaultBlockState(),3);else f.region.setBlock(ground,Blocks.STONE.defaultBlockState(),3);}
            }
            var random=name.equals("parent-wrapped-worldgen-source")?new WorldgenRandom(new WorldgenRandom(new XoroshiroRandomSource(seed))):
                name.equals("parent-delegated-draw-throws")?new WorldgenRandom(new XoroshiroRandomSource(seed){
                    public long nextLong(){if(tape!=null){tape.event("source-delegated-throw");throw tape.failure;}return super.nextLong();}
                }):name.equals("parent-worldgen-override-throws")?new WorldgenRandom(new XoroshiroRandomSource(seed)){
                    public int next(int bits){if(tape!=null){tape.event("wrapper-override-throw");throw tape.failure;}return super.next(bits);}
                }:name.equals("parent-custom-worldgen-random")?new WorldgenRandom(new XoroshiroRandomSource(seed)){
                public int next(int bits){if(tape!=null)tape.event("custom-next "+bits);return super.next(bits);}
            }:name.equals("parent-custom-xoroshiro-source")?new WorldgenRandom(new XoroshiroRandomSource(seed){
                public long nextLong(){if(tape!=null)tape.event("custom-source-next");return super.nextLong();}
            }):name.equals("probability-equal")?new WorldgenRandom(new XoroshiroRandomSource(seed)){public float nextFloat(){tape.event("fixed-zero-float");return 0.0F;}}:new WorldgenRandom(new XoroshiroRandomSource(seed));Tape t=new Tape(random);
            t.fixtureName=name;
            if(edge)t.origin=new BlockPos(15,80,8);
            if(name.contains("mutable-origin"))t.origin=new BlockPos.MutableBlockPos(8,80,8);
            FallenTreeConfiguration config=setup.prepare(f,t);
            var row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("seed",seed);
            PlacedFeature parent=null;
            if(name.startsWith("parent-")){
                var leaf=new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.FALLEN_TREE,config)),List.of());
                parent=new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.RANDOM_BOOLEAN_SELECTOR,new RandomBooleanFeatureConfiguration(Holder.direct(leaf),Holder.direct(leaf)))),List.of());
                if(Boolean.getBoolean("fallen.nativeParent"))f.prepare(parent);
            }
            tape=t;
            try{row.put("result",parent==null?Feature.FALLEN_TREE.place(config,f.region,f.generator,random,t.origin):
                Boolean.getBoolean("fallen.nativeParent")?f.placeParent(parent,random,t.origin):parent.place(f.region,f.generator,random,t.origin));}
            catch(Throwable failure){
                row.put("error",failure.getClass().getName()+":"+failure.getMessage());
                row.put("sameError",failure==t.failure);
                if(name.contains("checked-throw"))row.put("sameChecked",failure==t.checked);
                if(t.failAt==0&&t.expectedError==null)throw failure;
                if(t.expectedError!=null){if(!t.expectedError.isInstance(failure))throw new AssertionError("Wrong effect failure",failure);}
                else if(failure!=t.failure)throw new AssertionError("Effect identity changed",failure);
            }finally{tape=null;}
            if((t.failAt!=0||t.expectedError!=null)&&!row.containsKey("error"))throw new AssertionError("Missing requested failure "+name);
            if(t.failAt==0&&t.expectedError==null&&!Boolean.TRUE.equals(row.get("result")))throw new AssertionError("Original fallen result must be true");
            row.put("events",t.events);row.put("random",randomState(random));row.put("nextRandom",List.of(random.nextLong(),random.nextLong(),random.nextLong()));
            row.put("firstCursorAfter",t.firstCursor==null?"-":t.firstCursor.toShortString());row.put("snapshot",f.snapshot());
            // Region programs own these block operations directly. Keep the
            // actual callback trace, complete block/metadata image and RNG;
            // Java method-entry probes describe only the VM backend.
            if(Boolean.getBoolean("fallen.logicalParent")&&name.startsWith("parent-")){
                row.put("events",t.events.stream().filter(event->!event.startsWith("write ")&&!event.startsWith("read ")
                    &&!event.startsWith("mark ")&&!event.startsWith("support ")).toList());
                row.remove("firstCursorAfter");
            }
            ROWS.add(row);System.out.println("FALLEN_FIXTURE "+name+" events="+t.events.size()+" writes="+t.writes);
        }
    }
    record Retained(TreeDecorator.Context context,java.lang.ref.WeakReference<BlockStateProvider> provider,WorldgenRandom random){}
    static Retained retainContext(Fixture f)throws Throwable{
        var random=new WorldgenRandom(new XoroshiroRandomSource(42));
        var retained=new java.util.concurrent.atomic.AtomicReference<TreeDecorator.Context>();
        var provider=new Provider(Blocks.OAK_LOG.defaultBlockState());
        var decorator=new TreeDecorator(){
            protected TreeDecoratorType<?> type(){return TreeDecoratorType.TRUNK_VINE;}
            public void place(TreeDecorator.Context context){retained.set(context);}
        };
        var cfg=config(provider,4,List.of(decorator),List.of());
        Tape t=new Tape(random);t.fixtureName="retained-context";tape=t;
        try{Feature.FALLEN_TREE.place(cfg,f.region,f.generator,random,new BlockPos(8,80,8));}
        finally{tape=null;}
        return new Retained(retained.get(),new java.lang.ref.WeakReference<>(provider),random);
    }
    static void retainedContextFixture(ServerLevel level)throws Throwable{
        try(var f=new Fixture(level)){
            for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
            Retained retained=retainContext(f);
            for(int i=0;i<30&&retained.provider().get()!=null;i++){System.gc();Thread.sleep(50);}
            retained.context().setBlock(new BlockPos(8,82,8),Blocks.RED_MUSHROOM.defaultBlockState());
            ROWS.add(Map.of("name","retained-context-lifetime","providerCollected",retained.provider().get()==null,
                "retainedLogs",retained.context().logs().size(),"random",randomState(retained.random()),"snapshot",f.snapshot()));
            java.lang.ref.Reference.reachabilityFence(retained);
        }
    }
    static void run(MinecraftServer server){
        try{
            ServerLevel level=server.overworld();var registry=server.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            for(String variant:List.of("oak","birch","super_birch","jungle","spruce")){
                var id=net.minecraft.resources.ResourceLocation.parse("minecraft:fallen_"+variant+"_tree");
                var configured=registry.getValue(id);if(configured==null)throw new AssertionError("Missing "+id);
                var config=(FallenTreeConfiguration)configured.config();
                fixture(level,"parent-"+variant,42,"flat",(f,t)->config);
                for(long seed:new long[]{42,8191})fixture(level,"registered-"+variant+"-"+seed,seed,"flat",(f,t)->config);
            }
            fixture(level,"parent-public-random-count-provider",42,"flat",(f,t)->config(new BlockStateProvider(){
                protected BlockStateProviderType<?> type(){return BlockStateProviderType.SIMPLE_STATE_PROVIDER;}
                public BlockState getState(RandomSource r,BlockPos p){return (((WorldgenRandom)r).getCount()&1)==0?Blocks.OAK_LOG.defaultBlockState():Blocks.BIRCH_LOG.defaultBlockState();}
            },8,List.of(),List.of()));
            fixture(level,"parent-count-wrap",42,"flat",(f,t)->{
                var count=WorldgenRandom.class.getDeclaredField("count");count.setAccessible(true);count.setInt(t.random,Integer.MAX_VALUE-1);
                return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of());
            });
            fixture(level,"parent-stump-reseeds-live-random",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                List.of(new Effect(c->{t.event("reseed-before");c.random().setSeed(739);c.random().nextInt(5);t.event("reseed-after");})),List.of()));
            fixture(level,"parent-callback-gaussian-then-throw",42,"flat",(f,t)->{
                t.expectedError=Error.class;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                    List.of(new Effect(c->{c.random().nextGaussian();t.event("consumed-before-throw");throw t.failure;})),List.of());
            });
            for(String name:List.of("parent-custom-worldgen-random","parent-custom-xoroshiro-source"))fixture(level,name,42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of()));
            fixture(level,"parent-shared-random-reentry",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                List.of(new Effect(c->{t.event("rng-nested-before");Feature.FALLEN_TREE.place(config(BlockStateProvider.simple(Blocks.BIRCH_LOG),4,List.of(),List.of()),f.region,f.generator,c.random(),new BlockPos(3,80,3));t.event("rng-nested-after");})),List.of()));
            fixture(level,"parent-wrapped-worldgen-source",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of()));
            for(String name:List.of("parent-delegated-draw-throws","parent-worldgen-override-throws"))fixture(level,name,42,"flat",(f,t)->{
                t.expectedError=Error.class;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of());
            });
            fixture(level,"parent-fork-no-counter-increment",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                List.of(new Effect(c->{int before=((WorldgenRandom)c.random()).getCount();c.random().fork();c.random().forkPositional();if(((WorldgenRandom)c.random()).getCount()!=before)throw new AssertionError("Fork changed wrapper count");t.event("after-forks");})),List.of()));
            for(int length:new int[]{-2,0,1,2,3,4,16})fixture(level,"length-"+length,42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),length,List.of(),List.of()));
            for(String shape:List.of("void","gap2","gap3","gap-reset"))fixture(level,shape,42,shape,(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),16,List.of(),List.of()));
            for(long seed:new long[]{0,1,2,3,4,5,6,7})fixture(level,"directions-"+seed,seed,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of()));
            fixture(level,"axis-less",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.STONE),8,List.of(),List.of()));
            for(int at:new int[]{1,3})fixture(level,"throw-write-"+at,42,"flat",(f,t)->{t.failAt=at;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of());});
            fixture(level,"mutable-world-write-position",42,"flat",(f,t)->{t.moveAt=2;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of());});
            fixture(level,"provider-position-alias",42,"flat",(f,t)->{
                var p=new Provider(Blocks.OAK_LOG.defaultBlockState());
                var log=new Effect(c->{t.event("retainedProviderPositions "+p.retained);});
                return config(p,8,List.of(),List.of(log));
            });
            for(String riskName:Boolean.getBoolean("fallen.extended")?List.of("stump-mutates-later-log-list","parent-stump-mutates-later-log-list"):List.of("stump-mutates-later-log-list"))fixture(level,riskName,42,"flat",(f,t)->{
                List<TreeDecorator> logs=new ArrayList<>();
                var added=new Effect(c->{t.event("later-added-decorator");c.setBlock(c.logs().getFirst().above(),Blocks.RED_MUSHROOM.defaultBlockState());});
                var stump=new Effect(c->{t.event("stump-adds-later");logs.add(added);});
                return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,new ArrayList<>(List.of(stump)),logs);
            });
            fixture(level,"stump-nested-placement",42,"flat",(f,t)->{
                var nested=new Effect(c->{t.event("nested-begin");Feature.FALLEN_TREE.place(config(BlockStateProvider.simple(Blocks.BIRCH_LOG),4,List.of(),List.of()),f.region,f.generator,c.random(),new BlockPos(3,80,3));t.event("nested-end");});
                return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(nested),List.of());
            });
            fixture(level,"attached-log-all-directions",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),16,List.of(),List.of(
                new AttachedToLogsDecorator(1.0F,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.values())))));
            fixture(level,"direct-empty-directions-not-codec-valid",42,"flat",(f,t)->{
                t.failAt=0;
                return config(BlockStateProvider.simple(Blocks.OAK_LOG),2,List.of(),List.of(new AttachedToLogsDecorator(1.0F,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of())));
            });
            fixture(level,"null-provider-prefix",42,"flat",(f,t)->{t.expectedError=NullPointerException.class;return config(null,8,List.of(),List.of());});
            fixture(level,"null-stump-list-prefix",42,"flat",(f,t)->{t.expectedError=NullPointerException.class;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,null,List.of());});
            fixture(level,"null-log-list-prefix",42,"flat",(f,t)->{t.expectedError=NullPointerException.class;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),null);});
            fixture(level,"stump-throws-before-draw",42,"flat",(f,t)->{t.expectedError=Error.class;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(new Effect(c->{throw t.failure;})),List.of());});
            fixture(level,"log-list-foreach-mutation",42,"flat",(f,t)->{
                t.expectedError=ConcurrentModificationException.class;List<TreeDecorator> logs=new ArrayList<>();
                logs.add(new Effect(c->{logs.add(new Effect(ignored->{}));}));
                return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),logs);
            });
            fixture(level,"empty-directions-after-log-prefix",42,"flat",(f,t)->{t.expectedError=IllegalArgumentException.class;return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new AttachedToLogsDecorator(1.0F,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of())));});
            for(float probability:new float[]{Float.NaN,-1.0F,2.0F})fixture(level,"direct-probability-"+probability,42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new AttachedToLogsDecorator(probability,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP)))));
            fixture(level,"probability-equal",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new AttachedToLogsDecorator(0.0F,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP)))));
            if(Boolean.getBoolean("fallen.extended")){
                for(String checkedName:List.of("checked-throw-stump","parent-checked-throw-stump"))fixture(level,checkedName,42,"flat",(f,t)->{
                    t.expectedError=Exception.class;
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(new Effect(c->{c.random().nextInt(7);throw FallenAudit.<RuntimeException>propagate(t.checked);})),List.of());
                });
                for(String name:List.of("mutable-origin-stump","parent-mutable-origin-stump"))fixture(level,name,42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,
                    List.of(new Effect(c->{((BlockPos.MutableBlockPos)t.origin).move(3,0,0);t.event("moved-original-origin "+t.origin);})),List.of()));
                fixture(level,"provider-retained-stump-mutates",42,"flat",(f,t)->{
                    var p=new Provider(Blocks.OAK_LOG.defaultBlockState()){
                        public BlockState getState(RandomSource r,BlockPos pos){
                            if(!retained.isEmpty())((BlockPos.MutableBlockPos)retained.getFirst()).move(0,1,0);
                            return super.getState(r,pos);
                        }
                    };
                    return config(p,8,List.of(),List.of(new Effect(c->{t.event("retained-stump-final "+p.retained);})));
                });
                for(String riskName:Boolean.getBoolean("fallen.extended")?List.of("attached-live-direction-callback","parent-attached-live-direction-callback"):List.of("attached-live-direction-callback"))fixture(level,riskName,42,"flat",(f,t)->{
                    List<Direction> directions=new AbstractList<>(){
                        public int size(){t.event("direction-size");t.random.nextInt(9);return 2;}
                        public Direction get(int index){t.event("direction-get "+index);t.random.setSeed(739+index);return index==0?Direction.UP:Direction.NORTH;}
                    };
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),directions)));
                });
                for(String riskName:Boolean.getBoolean("fallen.extended")?List.of("attached-raw-log-reference-cast","parent-attached-raw-log-reference-cast"):List.of("attached-raw-log-reference-cast"))fixture(level,riskName,42,"flat",(f,t)->{
                    t.expectedError=ClassCastException.class;
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(
                        new Effect(c->{((List)c.logs()).add("malformed-log");}),
                        new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP))));
                });
                for(String riskName:Boolean.getBoolean("fallen.extended")?List.of("vine-null-log-original-prefix","parent-vine-null-log-original-prefix"):List.of("vine-null-log-original-prefix"))fixture(level,riskName,42,"flat",(f,t)->{
                    t.expectedError=NullPointerException.class;
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new Effect(c->{c.logs().add(null);}),TrunkVineDecorator.INSTANCE));
                });
            }
            if(Boolean.getBoolean("fallen.extended")){
                fixture(level,"parent-checked-throw-attached-provider",42,"flat",(f,t)->{
                    t.expectedError=Exception.class;
                    var provider=new BlockStateProvider(){
                        protected BlockStateProviderType<?> type(){return BlockStateProviderType.SIMPLE_STATE_PROVIDER;}
                        public BlockState getState(RandomSource r,BlockPos p){r.nextGaussian();t.event("attached-gaussian-before-checked");throw FallenAudit.<RuntimeException>propagate(t.checked);}
                    };
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new AttachedToLogsDecorator(1,provider,List.of(Direction.UP))));
                });
                fixture(level,"parent-attached-mutable-log-reference",42,"flat",(f,t)->{
                    var retained=new java.util.concurrent.atomic.AtomicReference<BlockPos.MutableBlockPos>();
                    var before=new Effect(c->{var pos=c.logs().getFirst().mutable();retained.set(pos);c.logs().set(0,pos);});
                    List<Direction> directions=new AbstractList<>(){
                        public int size(){return 1;}
                        public Direction get(int index){retained.get().move(1,0,0);t.event("mutated-shuffled-reference "+retained.get());return Direction.UP;}
                    };
                    var after=new Effect(c->{t.event("retained-mutable-final "+retained.get());});
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(before,new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),directions),after));
                });
                fixture(level,"parent-attached-provider-reentry",42,"flat",(f,t)->{
                    var provider=new BlockStateProvider(){
                        protected BlockStateProviderType<?> type(){return BlockStateProviderType.SIMPLE_STATE_PROVIDER;}
                        public BlockState getState(RandomSource r,BlockPos p){t.event("attached-provider-nested-before");Feature.FALLEN_TREE.place(config(BlockStateProvider.simple(Blocks.BIRCH_LOG),4,List.of(),List.of()),f.region,f.generator,r,new BlockPos(3,80,3));t.event("attached-provider-nested-after");return Blocks.RED_MUSHROOM.defaultBlockState();}
                    };
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(new AttachedToLogsDecorator(1,provider,List.of(Direction.UP))));
                });
                fixture(level,"parent-log-decorator-random-prefix",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(
                    new Effect(c->{c.random().nextLong();c.random().nextGaussian();t.event("before-attached-real-random");}),new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP)))));
                fixture(level,"parent-dynamic-support-position",42,"flat",(f,t)->{
                    var state=Blocks.SHULKER_BOX.defaultBlockState();if(!state.getBlock().hasDynamicShape())throw new AssertionError("Selected support must have actual dynamic shape");
                    for(int z=-16;z<32;z++)for(int x=-16;x<32;x++)f.region.setBlock(new BlockPos(x,79,z),state,3);
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of());
                });
            }
            if(Boolean.getBoolean("fallen.extended")&&"retained-context-lifetime".matches(System.getProperty("fallen.fixtures",".*")))retainedContextFixture(level);
            if(Boolean.getBoolean("fallen.extended")){
                fixture(level,"parent-edge-read-radius-post",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),32,List.of(),List.of()));
                fixture(level,"parent-edge-read-radius-post-mark",42,"flat",(f,t)->{
                    for(int x=32;x<48;x++)for(int y=81;y<=82;y++){var above=new BlockPos(x,y,8);f.region.getChunk(above).setBlockState(above,Blocks.STONE.defaultBlockState(),3);}
                    return config(BlockStateProvider.simple(Blocks.OAK_LOG),32,List.of(),List.of());
                });
                fixture(level,"parent-edge-read-radius-attached",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),16,List.of(),List.of(new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.EAST)))));
            }
            var gson=new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(OUTPUT.resolve("fixtures.json"),gson.toJson(ROWS));
            Files.writeString(OUTPUT.resolve("complete.json"),gson.toJson(Map.of("success",true,"fixtures",ROWS.size(),"programAdapterEntries",programAdapterEntries,"stockVmDecorationWrites",stockVmDecorationWrites,"legacyFallenBodies",legacyFallenBodies,"legacyAttachedBodies",legacyAttachedBodies)));
            System.out.println("FALLEN_COMPLETE "+ROWS.size());
        }catch(Throwable failure){
            failure.printStackTrace();try{Files.writeString(OUTPUT.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}
        }
        server.halt(false);
    }
}
