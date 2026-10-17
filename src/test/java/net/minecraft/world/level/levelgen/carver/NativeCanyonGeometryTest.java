package net.minecraft.world.level.levelgen.carver;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeCanyonGeometryTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static long compare(CarverGeometryFixtures.Ellipsoid e) {
        var f=e.frame();if(e.volume()==0)return 0;
        var s=new double[]{e.x(),e.y(),e.z(),e.horizontal(),e.vertical(),e.floor()};
        int height=f[5]-f[4],words=(height+63)/64,columns=(f[3]-f[2]+1)*(f[7]-f[6]+1);long[] actual;
        try(var result=NativeCanyonGeometry.prepare(e.context(),e.nativeChecker(),f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],s[0],s[1],s[2],s[3],s[4])) {
            if(e.volume()<NativeCanyonGeometry.MIN_VOLUME||height<NativeCanyonGeometry.MIN_HEIGHT){assertNull(result);actual=CarverAbiBridge.evaluate(f,s,e.kind()==1?new float[0]:e.widths());}
            else {assertNotNull(result);actual=new long[columns*words];for(int col=0;col<columns;col++)for(int w=0;w<words;w++)actual[col*words+w]=result.word(col,w);}
        }
        var expected=new long[actual.length];var checker=e.originalChecker();int col=0;
        for(int x=f[2];x<=f[3];x++) {
            int wx=e.chunk().getBlockX(x);double w=(wx+0.5-e.x())/e.horizontal();
            for(int z=f[6];z<=f[7];z++,col++) {
                int wz=e.chunk().getBlockZ(z);double q=(wz+0.5-e.z())/e.horizontal();
                if(!(w*w+q*q>=1.0))for(int y=f[5];y>f[4];y--) {
                    double ab=(y-0.5-e.y())/e.vertical();if(!checker.shouldSkip(e.context(),w,ab,q,y)){int iy=f[5]-y;expected[col*words+iy/64]|=1L<<(iy%64);}
                }
            }
        }
        assertArrayEquals(expected,actual,e.toString());return e.volume();
    }
    @Test void originalSeededTrajectoriesAndOrderedDecisions() {
        long cells=0;int fixtures=0,eligible=0;
        for(var config:CarverFixtures.configs())if(config.type().equals("minecraft:canyon"))for(var e:CarverGeometryFixtures.captured(config,2048)){cells+=compare(e);fixtures++;if(e.volume()>=NativeCanyonGeometry.MIN_VOLUME&&e.frame()[5]-e.frame()[4]>=NativeCanyonGeometry.MIN_HEIGHT)eligible++;}
        System.out.println("CARVER_GEOMETRY_PARITY fixtures="+fixtures+" decisions="+cells+" native_batches="+eligible);
    }
    @Test void strictBoundariesWordEdgesAndCoordinateExtremes() {
        var context=CarverFixtures.context(-64,2048);var config=CarverFixtures.configs().getFirst().value();long cells=0;int fixtures=0;
        for(int height:new int[]{16,64,128,384,2048})for(int kind:new int[]{2})for(int seed=0;seed<24;seed++) {
            var c=CarverFixtures.context(-64,height);var random=new Random(seed);
            var widths=new float[height];for(int i=0;i<height;i++)widths[i]=i%2==0?1F:Math.nextUp(1F+random.nextFloat()*3F);
            double radius=new double[]{.125,.5,1.0,Math.nextDown(2.0),2.0,Math.nextUp(2.0),3.0,7.5,16.0}[seed%9];
            var chunk=new ChunkPos(new int[]{0,-1,1_874_990,-1_874_990,Integer.MAX_VALUE}[seed%5],seed%3-1);
            var e=new CarverGeometryFixtures.Ellipsoid(c,config,chunk,chunk.getMinBlockX()+7.5,-32.0,chunk.getMinBlockZ()+7.5,radius,radius*(seed%3+1),kind,new double[]{-1,-.7,0,1}[seed%4],widths);
            cells+=compare(e);fixtures++;
        }
        // All output word boundaries, growth/shrink, and long vertical spans.
        var edgeWidths=new float[2048];Arrays.fill(edgeWidths,1F);
        for(double vertical:new double[]{31,31.5,32,32.5,63,63.5,64,64.5,256,1024}) {
            var e=new CarverGeometryFixtures.Ellipsoid(context,config,new ChunkPos(0,0),7.5,256,7.5,16,vertical,2,0,edgeWidths);
            cells+=compare(e);fixtures++;
        }
        System.out.println("CARVER_EDGE_PARITY fixtures="+fixtures+" decisions="+cells);
    }
    static WorldgenRandom random(long seed,int kind){return new WorldgenRandom(kind==0?new LegacyRandomSource(seed):new XoroshiroRandomSource(seed));}
    @SuppressWarnings({"rawtypes","unchecked"})
    static boolean full(boolean nativeMode,CarverFixtures.Config cfg,CarverTestChunk chunk,CarvingContext context,WorldgenRandom random,CarvingMask mask,ChunkPos origin) {
        if(nativeMode) {
            WorldCarver carver=cfg.type().equals("minecraft:canyon")?WorldCarver.CANYON:cfg.type().contains("nether")?WorldCarver.NETHER_CAVE:WorldCarver.CAVE;
            return carver.carve(context,cfg.value(),chunk,p->CarverFixtures.BIOME,random,chunk.aquifer(),origin,mask);
        }
        JavaWorldCarver carver=cfg.type().equals("minecraft:canyon")?new JavaCanyonWorldCarver(CanyonCarverConfiguration.CODEC):cfg.type().contains("nether")?new JavaNetherWorldCarver(CaveCarverConfiguration.CODEC):new JavaCaveWorldCarver(CaveCarverConfiguration.CODEC);
        return carver.carve(context,cfg.value(),chunk,p->CarverFixtures.BIOME,random,chunk.aquifer(),origin,mask);
    }
    @Test void completeCarvingTracesMasksBlocksAndRng() {
        long longs=0;int fixtures=0;
        for(var base:CarverFixtures.configs())for(int seed=0;seed<128;seed++) {
            var config=seed%4==0?new CarverFixtures.Config(base.name(),base.type(),CarverFixtures.debug(base.value())):base;
            int min=base.type().contains("nether")?0:-64,height=base.type().contains("nether")?128:384;
            var context=CarverFixtures.context(min,height);var pos=new ChunkPos(seed%2==0?1_874_990:-1,seed%2==0?-1_874_990:0);
            var a=new CarverTestChunk(pos,min,height,seed%3==0);var b=new CarverTestChunk(pos,min,height,seed%3==0);a.upgrading=b.upgrading=seed%2==0;
            var ma=new CarvingMask(height,min);var mb=new CarvingMask(height,min);
            ma.setAdditionalMask((x,y,z)->Math.floorMod(x*7+y+z*3,19)==0);mb.setAdditionalMask((x,y,z)->Math.floorMod(x*7+y+z*3,19)==0);
            for(int y=min;y<min+height;y+=7){ma.set(seed%16,y,(seed*7)%16);mb.set(seed%16,y,(seed*7)%16);}
            var ra=random(seed*0x9e3779b97f4a7c15L,seed%2);var rb=random(seed*0x9e3779b97f4a7c15L,seed%2);
            var origin=new ChunkPos(pos.x+seed%3-1,pos.z+(seed/3)%3-1);
            assertEquals(full(false,config,a,context,ra,ma,origin),full(true,config,b,context,rb,mb,origin));
            assertArrayEquals(a.trace.toLongArray(),b.trace.toLongArray(),base.name()+" seed="+seed);
            assertArrayEquals(ma.toArray(),mb.toArray());assertEquals(a.blocks(),b.blocks());assertEquals(ra.getCount(),rb.getCount());
            for(int i=0;i<16;i++)assertEquals(ra.nextLong(),rb.nextLong());longs+=a.trace.size();fixtures++;
        }
        System.out.println("CARVER_FULL_PARITY fixtures="+fixtures+" trace_longs="+longs);
    }
    @Test void statefulMasksColumnFlagsAndNestedCallbacks() {
        var e=CarverGeometryFixtures.captured(CarverFixtures.configs().getFirst(),128).stream().filter(f->f.volume()>=NativeCanyonGeometry.MIN_VOLUME&&f.frame()[5]-f.frame()[4]>=NativeCanyonGeometry.MIN_HEIGHT).findFirst().orElseThrow();
        var chunk=new CarverTestChunk(e.chunk(),e.context().getMinGenY(),e.context().getGenDepth(),false);chunk.recording=false;
        var original=new CarverKernel();var nativeKernel=new CarverKernel();var a=new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY());var b=new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY());
        int[] countA={0},countB={0};a.setAdditionalMask((x,y,z)->++countA[0]%7==0);
        b.setAdditionalMask((x,y,z)->{if(countB[0]++==0){var nested=new CarverKernel();nested.evaluate(true,e,chunk,new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY()),e.nativeChecker(),e.originalChecker());}return countB[0]%7==0;});
        assertEquals(original.evaluate(false,e,chunk,a,e.nativeChecker(),e.originalChecker()),nativeKernel.evaluate(true,e,chunk,b,e.nativeChecker(),e.originalChecker()));
        assertEquals(original.checksum,nativeKernel.checksum);assertEquals(original.calls,nativeKernel.calls);assertEquals(countA[0],countB[0]);assertArrayEquals(a.toArray(),b.toArray());
        try(var columns=NativeCanyonGeometry.prepare(e.context(),e.nativeChecker(),e.frame()[0],e.frame()[1],e.frame()[2],e.frame()[3],e.frame()[4],e.frame()[5],e.frame()[6],e.frame()[7],e.x(),e.y(),e.z(),e.horizontal(),e.vertical())) {
            assertNotNull(columns);long before=columns.word(0,0);var f=e.frame();
            assertNull(NativeCanyonGeometry.prepare(e.context(),e.nativeChecker(),f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],e.x(),e.y(),e.z(),e.horizontal(),e.vertical()));assertEquals(before,columns.word(0,0));
        }
    }
    @Test void customCheckersAndFailureRelease() {
        var e=CarverGeometryFixtures.captured(CarverFixtures.configs().getFirst(),128).stream().filter(f->f.volume()>=NativeCanyonGeometry.MIN_VOLUME&&f.frame()[5]-f.frame()[4]>=NativeCanyonGeometry.MIN_HEIGHT).findFirst().orElseThrow();
        var chunk=new CarverTestChunk(e.chunk(),e.context().getMinGenY(),e.context().getGenDepth(),false);chunk.recording=false;
        var a=new CarverKernel();var b=new CarverKernel();int[] ca={0},cb={0};
        WorldCarver.CarveSkipChecker nc=(ctx,x,y,z,blockY)->++cb[0]%5==0;JavaWorldCarver.CarveSkipChecker jc=(ctx,x,y,z,blockY)->++ca[0]%5==0;
        assertEquals(a.evaluate(false,e,chunk,new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY()),nc,jc),b.evaluate(true,e,chunk,new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY()),nc,jc));assertEquals(a.checksum,b.checksum);assertEquals(ca[0],cb[0]);
        var ma=new CarvingMask(e.context().getGenDepth(),e.context().getMinGenY());ma.setAdditionalMask((x,y,z)->{throw new IllegalStateException("mask failure");});
        assertThrows(IllegalStateException.class,()->b.evaluate(true,e,chunk,ma,e.nativeChecker(),e.originalChecker()));compare(e);
        var f=e.frame();assertNull(NativeCanyonGeometry.prepare(e.context(),e.nativeChecker(),f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],e.x(),e.y(),e.z(),Double.NaN,e.vertical()));
    }
    @Test void decodedRunsAcrossWordBoundaries() {
        var context=CarverFixtures.context(-64,2048);var config=CarverFixtures.configs().getFirst().value();
        for(int kind:new int[]{2})for(double vertical:new double[]{31,31.5,32,32.5,63,63.5,64,64.5,256,1024}) {
            var widths=new float[2048];for(int y=0;y<widths.length;y++)widths[y]=y%5==0?12F:1F;
            var e=new CarverGeometryFixtures.Ellipsoid(context,config,new ChunkPos(0,0),7.5,256,7.5,16,vertical,kind,-1,widths);
            var chunk=new CarverTestChunk(e.chunk(),-64,2048,false);chunk.recording=false;
            var original=new CarverKernel();var migrated=new CarverKernel();
            var a=new CarvingMask(2048,-64);var b=new CarvingMask(2048,-64);
            assertEquals(original.evaluate(false,e,chunk,a,e.nativeChecker(),e.originalChecker()),migrated.evaluate(true,e,chunk,b,e.nativeChecker(),e.originalChecker()));
            assertEquals(original.checksum,migrated.checksum);assertEquals(original.calls,migrated.calls);assertArrayEquals(a.toArray(),b.toArray());
        }
    }
    @Test void concurrencyAndGc() throws Exception {
        var inputs=CarverGeometryFixtures.captured(CarverFixtures.configs().getFirst(),64);
        try(var pool=Executors.newFixedThreadPool(5)){var tasks=new ArrayList<Future<?>>();for(int t=0;t<4;t++)tasks.add(pool.submit(()->{for(var e:inputs)compare(e);}));tasks.add(pool.submit(()->{for(int i=0;i<8;i++)System.gc();}));for(var t:tasks)t.get();}
    }
}
