package net.minecraft.world.level.levelgen;

import java.util.*;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBeardifierTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static NoiseChunk chunk(int width, int height, int x, int y, int z) throws Exception {
        var noises = new MappedRegistry<NormalNoise.NoiseParameters>(Registries.NOISE, Lifecycle.stable());
        for (var key : List.of(Noises.CLAY_BANDS_OFFSET, Noises.SURFACE, Noises.SURFACE_SECONDARY,
            Noises.BADLANDS_PILLAR, Noises.BADLANDS_PILLAR_ROOF, Noises.BADLANDS_SURFACE,
            Noises.ICEBERG_PILLAR, Noises.ICEBERG_PILLAR_ROOF, Noises.ICEBERG_SURFACE))
            noises.register(key, new NormalNoise.NoiseParameters(-2, 1.), RegistrationInfo.BUILT_IN);
        noises.freeze();
        var zero = DensityFunctions.zero();
        var router = new NoiseRouter(zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero);
        var settings = new NoiseGeneratorSettings(NoiseSettings.create(0, 256, width / 4, height / 4),
            Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(), router,
            SurfaceRules.state(Blocks.STONE.defaultBlockState()), List.of(), 0, false,false,false,false);
        var state = RandomState.create(settings, noises, 42);
        var result = new NoiseChunk(16 / width, state, 0, 0, settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE,
            settings, (a,b,c)->new Aquifer.FluidStatus(0, settings.defaultFluid()), Blender.empty());
        origin(result,x,y,z);
        return result;
    }
    static void origin(NoiseChunk chunk,int x,int y,int z) throws Exception {
        for (var entry : Map.of("cellStartBlockX",x,"cellStartBlockY",y,"cellStartBlockZ",z).entrySet()) {
            var f = NoiseChunk.class.getDeclaredField(entry.getKey()); f.setAccessible(true); f.setInt(chunk,entry.getValue());
        }
    }
    static void same(double[] expected, double[] actual) {
        assertEquals(expected.length,actual.length);
        for(int i=0;i<expected.length;i++) assertEquals(Double.doubleToRawLongBits(expected[i]),Double.doubleToRawLongBits(actual[i]),"index="+i);
    }
    static void javaCell(DensityFunction original,double[] output,int x,int y,int z,int w,int h) {
        int at=0;
        for(int dy=h-1;dy>=0;dy--)for(int dx=0;dx<w;dx++)for(int dz=0;dz<w;dz++)
            output[at++]=original.compute(new DensityFunction.SinglePointContext(x+dx,y+dy,z+dz));
    }
    static JavaBeardifier original(List<Beardifier.Rigid> pieces,List<JigsawJunction> junctions,BoundingBox bounds) {
        return new JavaBeardifier(pieces.stream().map(p->new JavaBeardifier.Rigid(p.box(),p.terrainAdjustment(),p.groundLevelDelta())).toList(),junctions,bounds);
    }
    static BoundingBox all() { return new BoundingBox(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE); }
    @Test void randomizedGeometryMatchesOriginalIncludingIntegerOverflow() {
        var random = new Random(840271);
        for(int trial=0;trial<2000;trial++) {
            var pieces = new ArrayList<Beardifier.Rigid>(); var junctions = new ArrayList<JigsawJunction>();
            int x=trial%7==0?random.nextInt():random.nextInt(65)-32;
            int y=trial%7==0?random.nextInt():random.nextInt(65)-32;
            int z=trial%7==0?random.nextInt():random.nextInt(65)-32;
            for(int i=0;i<1+trial%24;i++) {
                int a=x+random.nextInt(41)-20,b=y+random.nextInt(41)-20,c=z+random.nextInt(41)-20;
                int dx=random.nextInt(16),dy=random.nextInt(16),dz=random.nextInt(16);
                pieces.add(new Beardifier.Rigid(new BoundingBox(Math.min(a,a+dx),Math.min(b,b+dy),Math.min(c,c+dz),
                    Math.max(a,a+dx),Math.max(b,b+dy),Math.max(c,c+dz)),TerrainAdjustment.values()[i%5],trial%11==0?random.nextInt():random.nextInt(17)-8));
            }
            for(int i=0;i<trial%13;i++)junctions.add(new JigsawJunction(x+random.nextInt(49)-24,y+random.nextInt(49)-24,z+random.nextInt(49)-24,0,StructureTemplatePool.Projection.RIGID));
            var bounds = trial%3==0?new BoundingBox(x,y,z,x+7,y+15,z+7):all();
            var original = original(pieces,junctions,bounds);
            var nativeCells = NativeBeardifier.create(List.copyOf(pieces),List.copyOf(junctions),bounds);
            double[] expected = new double[128],actual = new double[128];
            javaCell(original,expected,x,y,z,4,8);nativeCells.fillCell(actual,x,y,z,4,8);same(expected,actual);
        }
    }
    @Test void kernelEdgesAndExtremeGroundOffsetsMatch() {
        for(var kind:TerrainAdjustment.values())for(int delta:new int[]{Integer.MIN_VALUE,-24,-1,0,1,24,Integer.MAX_VALUE}) {
            var pieces=List.of(new Beardifier.Rigid(new BoundingBox(0,0,0,2,2,2),kind,delta));
            var junctions=List.of(new JigsawJunction(0,0,0,0,StructureTemplatePool.Projection.RIGID));
            var original=original(pieces,junctions,all());var nativeCells=NativeBeardifier.create(pieces,junctions,all());
            for(int x:new int[]{Integer.MIN_VALUE,-25,-13,-12,-11,-1,0,1,11,12,13,25,Integer.MAX_VALUE})
                for(int y=-25;y<=25;y++) {
                    double[] a=new double[16],b=new double[16];javaCell(original,a,x,y,-13,4,1);nativeCells.fillCell(b,x,y,-13,4,1);same(a,b);
                }
        }
    }
    @Test void productionFillPreservesOutputAndFinalProviderState() throws Exception {
        var p=List.of(new Beardifier.Rigid(new BoundingBox(-3,-2,-4,5,10,6),TerrainAdjustment.BEARD_BOX,-1));
        for(int w:new int[]{4,8})for(int h:new int[]{4,8,16}) {
            var chunk=chunk(w,h,-4,-8,-4);
            var original=original(p,List.of(),all());var nativeFunction=Beardifier.forGeometry(p,List.of(),all());
            double[] a=new double[w*w*h],b=new double[a.length];original.fillArray(a,chunk);
            int x=chunk.inCellX,y=chunk.inCellY,z=chunk.inCellZ,index=chunk.arrayIndex;
            chunk.inCellX=-123;chunk.inCellY=555;chunk.inCellZ=7;chunk.arrayIndex=9;
            nativeFunction.fillArray(b,chunk);same(a,b);
            assertEquals(x,chunk.inCellX);assertEquals(y,chunk.inCellY);assertEquals(z,chunk.inCellZ);assertEquals(index,chunk.arrayIndex);
        }
    }
    @Test void movedBoxesAreRefreshedBetweenFills() {
        var box=new BoundingBox(0,0,0,2,2,2);var bounds=new BoundingBox(-20,-20,-20,20,20,20);
        var pieces=List.of(new Beardifier.Rigid(box,TerrainAdjustment.BURY,0));
        var original=original(pieces,List.of(),bounds);var nativeCells=NativeBeardifier.create(pieces,List.of(),bounds);
        for(int i=0;i<10;i++) {
            double[] a=new double[128],b=new double[128];javaCell(original,a,0,0,0,4,8);nativeCells.fillCell(b,0,0,0,4,8);same(a,b);
            box.move(1,-1,1);bounds.move(1,-1,1);
        }
    }
    @Test void customProviderAndEmptyEvaluatorKeepOriginalBehavior() {
        var piece=new Beardifier.Rigid(new BoundingBox(0,0,0,1,1,1),TerrainAdjustment.BURY,0);
        var nativeFunction=Beardifier.forGeometry(List.of(piece),List.of(),all());
        var original=original(List.of(piece),List.of(),all());var visits=new ArrayList<Integer>();
        var provider=new DensityFunction.ContextProvider() {
            public DensityFunction.FunctionContext forIndex(int i) {visits.add(i);return new DensityFunction.SinglePointContext(i,0,0);}
            public void fillAllDirectly(double[] output,DensityFunction f) {assertSame(nativeFunction,f);for(int i=0;i<output.length;i++)output[i]=f.compute(forIndex(i));}
        };
        double[] values=new double[17];nativeFunction.fillArray(values,provider);
        for(int i=0;i<values.length;i++)assertEquals(original.compute(new DensityFunction.SinglePointContext(i,0,0)),values[i]);
        assertEquals(java.util.stream.IntStream.range(0,17).boxed().toList(),visits);
        visits.clear();Beardifier.EMPTY.fillArray(values,provider);assertTrue(visits.isEmpty());assertArrayEquals(new double[17],values);
    }
    @Test void sharedEvaluatorHasThreadLocalBuffers() throws Exception {
        var pieces=List.of(new Beardifier.Rigid(new BoundingBox(-5,-5,-5,5,5,5),TerrainAdjustment.ENCAPSULATE,1));
        var original=original(pieces,List.of(),all());var nativeCells=NativeBeardifier.create(pieces,List.of(),all());
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var jobs=new ArrayList<java.util.concurrent.Future<?>>();
            for(int worker=0;worker<4;worker++) {
                final int x=worker*4-8;
                jobs.add(pool.submit(()->{for(int i=0;i<1000;i++) {double[] a=new double[128],b=new double[128];javaCell(original,a,x,-4,-4,4,8);nativeCells.fillCell(b,x,-4,-4,4,8);same(a,b);}}));
            }
            for(var job:jobs)job.get();
        }
    }
}
