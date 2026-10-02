package net.minecraft.world.level.levelgen.feature;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeGeodeGeometryTest {
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        // Bind just the relevant test tags, so protected/invalid blocks really
        // exercise predicates rather than relying on unloaded tag membership.
        try {
            var holder=net.minecraft.world.level.block.Blocks.BEDROCK.builtInRegistryHolder();
            var tags=new HashSet<>(holder.tags().toList());
            for(var config:GeodeFixtures.configs()) {tags.add(config.value().geodeBlockSettings.cannotReplace);tags.add(config.value().geodeBlockSettings.invalidBlocks);}
            var bind=net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags",Collection.class);
            bind.setAccessible(true);bind.invoke(holder,tags);
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static long compare(GeodeFixtures.Grid grid) {
        try(var actual=NativeGeodeGeometry.prepare(grid.noise(),grid.origin(),grid.min(),grid.max(),grid.points(),grid.cracks(),grid.crackOffset(),grid.multiplier())) {
            long width=Math.abs((long)grid.max()-grid.min())+1;
            double[] direct=null;
            if(width*width*width<512){assertNull(actual);direct=NativeGeodeGeometryBridge.fields(grid);}
            else assertNotNull(actual);
            final double[] raw=direct;int[] at={0};
            JavaGeodeFields.evaluate(grid,(p,s,t)->{
                int i=at[0]++;
                assertEquals(Double.doubleToRawLongBits(s),Double.doubleToRawLongBits(raw==null?actual.body(i):raw[i*2]),"body " +p+" cell="+i);
                assertEquals(Double.doubleToRawLongBits(t),Double.doubleToRawLongBits(raw==null?actual.crack(i):raw[i*2+1]),"crack "+p+" cell="+i);
            });return at[0];
        }
    }
    @Test void registeredInputsExactDensityBits() {
        long cells=0;int fixtures=0;
        for(var config:GeodeFixtures.configs())for(int seed=0;seed<32;seed++) {
            var pos=new BlockPos(new int[]{0,-1234,29_999_900,-29_999_900}[seed%4],seed%3==0?-50:240,-(seed%4)*12345);
            RandomSource random=seed%2==0?new LegacyRandomSource(seed):new XoroshiroRandomSource(seed);
            cells+=compare(GeodeFixtures.grid(config.value(),seed*0x9e3779b97f4a7c15L,random,pos,null));fixtures++;
        }
        System.out.println("GEODE_FIELD_PARITY fixtures="+fixtures+" cells="+cells+" doubles="+(cells*2));
    }
    @Test void codecPointCountsOffsetsAndBounds() {
        long cells=0;int fixtures=0;
        var random=new Random(739287);var noise=GeodeFixtures.noise(Long.MIN_VALUE);
        for(int n=0;n<=64;n++)for(int offset:new int[]{0,1,2,10}) {
            List<Pair<BlockPos,Integer>> points=new LinkedList<>();List<BlockPos> cracks=new LinkedList<>();
            for(int p=0;p<n;p++)points.add(Pair.of(new BlockPos(random.nextInt(31)-15,random.nextInt(31)-15,random.nextInt(31)-15),offset));
            for(int p=0;p<n%5;p++)cracks.add(new BlockPos(p,0,0));
            cells+=compare(new GeodeFixtures.Grid(noise,BlockPos.ZERO,4,-4,points,cracks,offset,n%3==0?0.0:1.0));fixtures++;
        }
        for(var origin:List.of(new BlockPos(Integer.MIN_VALUE+32,Integer.MAX_VALUE-32,0),new BlockPos(0,-32,0)))
            for(double multiplier:new double[]{-0.0,Double.MIN_VALUE,Math.nextDown(.05),.05,Math.nextUp(.05),1.0})
                for(int offset:new int[]{0,1,Integer.MAX_VALUE}) {
                    cells+=compare(new GeodeFixtures.Grid(noise,origin,-1,1,List.of(Pair.of(origin,offset)),List.of(origin),offset,multiplier));fixtures++;
                }
        // Largest supported cube, wrapped endpoints, single cell and grown scratch.
        for(int[] bounds:new int[][]{{-19,20},{0,0},{-16,16},{-3,3}}) {
            var origin=new BlockPos(Integer.MAX_VALUE,20,Integer.MIN_VALUE);
            if(bounds[0]<0 && bounds[1]>0)origin=BlockPos.ZERO;
            cells+=compare(new GeodeFixtures.Grid(noise,origin,bounds[0],bounds[1],List.of(Pair.of(origin,1)),List.of(),0,.05));fixtures++;
        }
        System.out.println("GEODE_EDGE_PARITY fixtures="+fixtures+" cells="+cells);
    }
    static RandomSource rng(int type,long seed){return type==0?new LegacyRandomSource(seed):type==1?new XoroshiroRandomSource(seed):new WorldgenRandom(new XoroshiroRandomSource(seed));}
    static boolean place(boolean nativeMode,GeodeTestWorld w,GeodeConfiguration c,RandomSource random,BlockPos origin){
        var context=new FeaturePlaceContext<>(Optional.empty(),w.level,null,random,origin,c);
        return nativeMode?new GeodeFeature(GeodeConfiguration.CODEC).place(context):new JavaGeodeFeature(GeodeConfiguration.CODEC).place(context);
    }
    @Test void completePlacementTraceBlocksTicksAndRng() {
        long events=0;int fixtures=0;
        for(var config:GeodeFixtures.configs())for(int variant=0;variant<12;variant++) {
            long seed=variant*0x9e3779b97f4a7c15L;
            var a=new GeodeTestWorld(seed,variant%5);var b=new GeodeTestWorld(seed,variant%5);
            a.deny=b.deny=variant%2==0;
            var ra=rng(variant%3,seed+1);var rb=rng(variant%3,seed+1);
            var pos=new BlockPos(variant%2==0?29_999_900:-17,80+(variant%3)*16,variant%2==0?-29_999_900:15);
            assertEquals(place(false,a,config.value(),ra,pos),place(true,b,config.value(),rb,pos),config.name());
            assertArrayEquals(a.trace.toLongArray(),b.trace.toLongArray(),config.name()+" variant="+variant);
            assertEquals(a.blocks(),b.blocks());
            if(ra instanceof WorldgenRandom wa)assertEquals(wa.getCount(),((WorldgenRandom)rb).getCount());
            for(int draw=0;draw<16;draw++)assertEquals(ra.nextLong(),rb.nextLong());events+=a.trace.size();fixtures++;
        }
        System.out.println("GEODE_PLACEMENT_PARITY fixtures="+fixtures+" trace_longs="+events);
    }
    @Test void callbacksExceptionsAndReentry() {
        var config=GeodeFixtures.configs().getFirst().value();
        var a=new GeodeTestWorld(81,0);var b=new GeodeTestWorld(81,0);
        a.onWrite=()->place(false,new GeodeTestWorld(93,0),config,new LegacyRandomSource(2),new BlockPos(40,80,40));
        b.onWrite=()->place(true,new GeodeTestWorld(93,0),config,new LegacyRandomSource(2),new BlockPos(40,80,40));
        var ra=new LegacyRandomSource(19);var rb=new LegacyRandomSource(19);
        assertEquals(place(false,a,config,ra,new BlockPos(0,80,0)),place(true,b,config,rb,new BlockPos(0,80,0)));
        assertArrayEquals(a.trace.toLongArray(),b.trace.toLongArray());assertEquals(a.blocks(),b.blocks());
        for(int i=0;i<16;i++)assertEquals(ra.nextLong(),rb.nextLong());
        a=new GeodeTestWorld(81,0);b=new GeodeTestWorld(81,0);a.failWrite=b.failWrite=true;
        ra=new LegacyRandomSource(19);rb=new LegacyRandomSource(19);
        var aa=a;var bb=b;var raa=ra;var rbb=rb;
        assertThrows(IllegalStateException.class,()->place(false,aa,config,raa,new BlockPos(0,80,0)));
        assertThrows(IllegalStateException.class,()->place(true,bb,config,rbb,new BlockPos(0,80,0)));
        assertArrayEquals(a.trace.toLongArray(),b.trace.toLongArray());for(int i=0;i<16;i++)assertEquals(ra.nextLong(),rb.nextLong());
        compare(GeodeFixtures.grid(config,9,new LegacyRandomSource(17),BlockPos.ZERO,null));
    }
    @Test void fallbackEligibilityAndLeaseProtection() {
        var config=GeodeFixtures.configs().getFirst().value();var grid=GeodeFixtures.grid(config,9,new LegacyRandomSource(17),BlockPos.ZERO,null);
        try(var fields=NativeGeodeGeometry.prepare(grid.noise(),grid.origin(),grid.min(),grid.max(),grid.points(),grid.cracks(),grid.crackOffset(),grid.multiplier())) {
            assertNotNull(fields);long bits=Double.doubleToRawLongBits(fields.body(0));
            assertNull(NativeGeodeGeometry.prepare(grid.noise(),grid.origin(),grid.min(),grid.max(),grid.points(),grid.cracks(),grid.crackOffset(),grid.multiplier()));
            assertEquals(bits,Double.doubleToRawLongBits(fields.body(0)));
        }
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),new BlockPos(0,0,0){},-1,1,grid.points(),grid.cracks(),0,0.0));
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),BlockPos.ZERO,0,64,grid.points(),grid.cracks(),0,0.0));
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),BlockPos.ZERO,0,40,grid.points(),grid.cracks(),0,0.0));
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),new BlockPos(Integer.MAX_VALUE,0,0),-1,1,grid.points(),grid.cracks(),0,0.0));
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),BlockPos.ZERO,-1,1,List.of(Pair.of(new BlockPos.MutableBlockPos(),1)),List.of(),0,0.0));
        for(double multiplier:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1.0,Math.nextUp(1.0)})
            assertNull(NativeGeodeGeometry.prepare(grid.noise(),BlockPos.ZERO,-1,1,grid.points(),grid.cracks(),0,multiplier));
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),BlockPos.ZERO,-1,1,List.of(Pair.of(BlockPos.ZERO,-1)),List.of(),0,.05));
        assertNull(NativeGeodeGeometry.prepare(grid.noise(),BlockPos.ZERO,-1,1,grid.points(),grid.cracks(),-1,.05));
        for(int[] bounds:new int[][]{{0,64},{0,40}}) {
            var c=GeodeFixtures.bounds(config,bounds[0],bounds[1]);var a=new GeodeTestWorld(81,0);var b=new GeodeTestWorld(81,0);
            var ra=new LegacyRandomSource(0);var rb=new LegacyRandomSource(0);
            assertEquals(place(false,a,c,ra,new BlockPos(0,80,0)),place(true,b,c,rb,new BlockPos(0,80,0)));
            assertArrayEquals(a.trace.toLongArray(),b.trace.toLongArray());assertEquals(ra.nextLong(),rb.nextLong());
        }
    }
    @Test void programmaticCompatibilityAndRandomProviders() {
        var base=GeodeFixtures.configs().getFirst().value();
        var randomProvider=new net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider() {
            @Override protected net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType<?> type() {
                return net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType.SIMPLE_STATE_PROVIDER;
            }
            @Override public net.minecraft.world.level.block.state.BlockState getState(RandomSource random,BlockPos pos) {
                return (random.nextInt(3)==0?net.minecraft.world.level.block.Blocks.GLASS:net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK).defaultBlockState();
            }
        };
        var old=base.geodeBlockSettings;
        var blocks=new GeodeBlockSettings(randomProvider,randomProvider,randomProvider,randomProvider,randomProvider,
            old.innerPlacements,old.cannotReplace,old.invalidBlocks);
        for(int variant=0;variant<8;variant++) {
            int min=variant==5?4:-8,max=variant==5?-4:8;
            var points=variant==3?net.minecraft.util.valueproviders.ConstantInt.of(-1):base.pointOffset;
            double multiplier=variant==1?Double.NaN:variant==2?Double.POSITIVE_INFINITY:base.noiseMultiplier;
            var config=new GeodeConfiguration(blocks,base.geodeLayerSettings,new GeodeCrackSettings(variant%2,2,variant==4?-1:2),
                base.usePotentialPlacementsChance,base.useAlternateLayer0Chance,false,base.outerWallDistance,
                base.distributionPoints,points,min,max,multiplier,100);
            var a=new GeodeTestWorld(19,variant==6?4:0);var b=new GeodeTestWorld(19,variant==6?4:0);
            var ra=new WorldgenRandom(new XoroshiroRandomSource(61));var rb=new WorldgenRandom(new XoroshiroRandomSource(61));
            BlockPos pa=variant==7?new BlockPos.MutableBlockPos(0,80,0):new BlockPos(0,80,0);
            BlockPos pb=variant==7?new BlockPos.MutableBlockPos(0,80,0):new BlockPos(0,80,0);
            assertEquals(place(false,a,config,ra,pa),place(true,b,config,rb,pb));
            assertArrayEquals(a.trace.toLongArray(),b.trace.toLongArray(),"programmatic variant="+variant);assertEquals(a.blocks(),b.blocks());
            assertEquals(ra.getCount(),rb.getCount());for(int draw=0;draw<16;draw++)assertEquals(ra.nextLong(),rb.nextLong());
        }
    }
    @Test void parallelCallsAndConcurrentGc() throws Exception {
        var config=GeodeFixtures.configs().getFirst().value();
        try(var pool=Executors.newFixedThreadPool(5)) {
            var tasks=new ArrayList<Future<?>>();
            for(int thread=0;thread<4;thread++){final int t=thread;tasks.add(pool.submit(()->{
                for(int seed=0;seed<16;seed++)compare(GeodeFixtures.grid(config,t*100+seed,new LegacyRandomSource(seed),new BlockPos(seed,0,-seed),null));
            }));}
            tasks.add(pool.submit(()->{for(int i=0;i<8;i++)System.gc();}));
            for(var task:tasks)task.get();
        }
    }
}
