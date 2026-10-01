package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCellIntegrationTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static NoiseChunk chunk(AtomicInteger visits) {
        var noises = new MappedRegistry<NormalNoise.NoiseParameters>(Registries.NOISE, Lifecycle.stable());
        for (var key : List.of(Noises.CLAY_BANDS_OFFSET, Noises.SURFACE, Noises.SURFACE_SECONDARY,
                Noises.BADLANDS_PILLAR, Noises.BADLANDS_PILLAR_ROOF, Noises.BADLANDS_SURFACE,
                Noises.ICEBERG_PILLAR, Noises.ICEBERG_PILLAR_ROOF, Noises.ICEBERG_SURFACE)) {
            noises.register(key, new NormalNoise.NoiseParameters(-2, 1.), RegistrationInfo.BUILT_IN);
        }
        noises.freeze();
        var zero = DensityFunctions.constant(0.);
        var router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero, zero);
        var settings = new NoiseGeneratorSettings(NoiseSettings.create(0, 16, 1, 1),
                Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(), router,
                SurfaceRules.state(Blocks.STONE.defaultBlockState()), List.of(), 0,
                false, false, false, false);
        var random = RandomState.create(settings, noises, 42);
        Aquifer.FluidPicker fluids = (x, y, z) -> new Aquifer.FluidStatus(0, settings.defaultFluid());
        if (visits == null) {
            return new NoiseChunk(4, random, 0, 0, settings.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, settings, fluids, Blender.empty());
        }
        return new NoiseChunk(4, random, 0, 0, settings.noiseSettings(),
                DensityFunctions.BeardifierMarker.INSTANCE, settings, fluids, Blender.empty()) {
            @Override public NoiseChunk forIndex(int index) {
                visits.incrementAndGet();
                return super.forIndex(index);
            }
        };
    }

    @Test void lazyBlockInterpolationMatchesEagerJavaForArbitraryStagingAndReads() {
        for(boolean subclass:new boolean[]{false,true}) {
            var owner=chunk(subclass?new AtomicInteger():null);
            var interpolator=owner.new NoiseInterpolator(DensityFunctions.zero());
            owner.interpolating=true;
            var rng=new java.util.Random(921334);
            double[] corners=new double[8],xz=new double[4],zv=new double[2];double expected=0;
            for(int step=0;step<100000;step++) {
                int op=rng.nextInt(6);double fraction=step%17==0?Double.longBitsToDouble(rng.nextLong()):rng.nextDouble();
                switch(op) {
                    case 0 -> {
                        owner.updateForY(0,fraction);
                        xz[0]=net.minecraft.util.Mth.lerp(fraction,corners[0],corners[2]);
                        xz[1]=net.minecraft.util.Mth.lerp(fraction,corners[1],corners[3]);
                        xz[2]=net.minecraft.util.Mth.lerp(fraction,corners[4],corners[6]);
                        xz[3]=net.minecraft.util.Mth.lerp(fraction,corners[5],corners[7]);
                    }
                    case 1 -> {owner.updateForX(0,fraction);zv[0]=net.minecraft.util.Mth.lerp(fraction,xz[0],xz[1]);zv[1]=net.minecraft.util.Mth.lerp(fraction,xz[2],xz[3]);}
                    case 2 -> {owner.updateForZ(0,fraction);expected=net.minecraft.util.Mth.lerp(fraction,zv[0],zv[1]);}
                    case 3 -> {
                        for(int i=0;i<8;i++)corners[i]=step%3==0?Double.longBitsToDouble(rng.nextLong()):rng.nextDouble()*4-2;
                        interpolator.slice0[0][0]=corners[0];interpolator.slice1[0][0]=corners[1];
                        interpolator.slice0[0][1]=corners[2];interpolator.slice1[0][1]=corners[3];
                        interpolator.slice0[1][0]=corners[4];interpolator.slice1[1][0]=corners[5];
                        interpolator.slice0[1][1]=corners[6];interpolator.slice1[1][1]=corners[7];
                        interpolator.selectCellYZ(0,0);
                    }
                    case 4 -> {interpolator.updateForZ(fraction);expected=net.minecraft.util.Mth.lerp(fraction,zv[0],zv[1]);}
                    default -> {}
                }
                if(rng.nextBoolean())assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(interpolator.compute(owner)),"step "+step);
            }
        }
    }

    @Test void interpolationOverridesStillReceiveEveryZUpdate() {
        var owner=chunk(null);var visits=new AtomicInteger();
        owner.new NoiseInterpolator(DensityFunctions.zero()) {
            @Override void updateForZ(double fraction) {visits.incrementAndGet();super.updateForZ(fraction);}
        };
        for(int i=0;i<25;i++)owner.updateForZ(i,(double)i/4);
        assertEquals(25,visits.get());
    }

    private record Fixture(NoiseChunk chunk, NoiseChunk.NoiseInterpolator first,
                           DensityFunction source, DensityFunction compiled) {}

    private static Fixture fixture(AtomicInteger visits) {
        var chunk = chunk(visits);
        var first = chunk.new NoiseInterpolator(DensityFunctions.constant(2.));
        var second = chunk.new NoiseInterpolator(DensityFunctions.yClampedGradient(0,16,-1.,1.));
        for (int z = 0; z < 2; z++) for (int y = 0; y < 2; y++) {
            first.slice0[z][y] = 0.25 + z - y;
            first.slice1[z][y] = -0.5 + z + y;
            second.slice0[z][y] = 0.3 - z + y;
            second.slice1[z][y] = 0.2 + z - y;
        }
        first.selectCellYZ(0, 0);
        second.selectCellYZ(0, 0);
        chunk.interpolating = true;
        chunk.fillingCell = true;
        var source = DensityFunctions.min(DensityFunctions.add(first.square(), DensityFunctions.constant(.5)),
                second.square()).squeeze().abs();
        var compiled = NativeCellDensity.compile(source, new HashMap<>());
        assertInstanceOf(NativeCellDensity.class, compiled);
        return new Fixture(chunk, first, source, compiled);
    }

    private static void compareArrays(Fixture fixture) {
        var chunk = fixture.chunk;
        int count = chunk.cellWidth() * chunk.cellWidth() * chunk.cellHeight();
        double[] expected = new double[count], actual = new double[count];
        fixture.source.fillArray(expected, chunk);
        int x = chunk.inCellX, y = chunk.inCellY, z = chunk.inCellZ, index = chunk.arrayIndex;
        long counter = chunk.interpolationCounter, arrayCounter = chunk.arrayInterpolationCounter;
        chunk.forIndex(0);
        fixture.compiled.fillArray(actual, chunk);
        for (int i = 0; i < count; i++) {
            assertEquals(Double.doubleToLongBits(expected[i]), Double.doubleToLongBits(actual[i]));
        }
        assertEquals(x, chunk.inCellX);
        assertEquals(y, chunk.inCellY);
        assertEquals(z, chunk.inCellZ);
        assertEquals(index, chunk.arrayIndex);
        assertEquals(counter, chunk.interpolationCounter);
        assertEquals(arrayCounter, chunk.arrayInterpolationCounter);
    }

    @Test void cellFramesRefreshAndKeepProviderState() {
        var fixture = fixture(null);
        compareArrays(fixture);
        fixture.first.slice0[0][0] = 8.5;
        fixture.first.selectCellYZ(0, 0);
        fixture.chunk.arrayInterpolationCounter++;
        compareArrays(fixture);
    }

    @Test void customProvidersRetainTheirOriginalVisits() {
        var visits = new AtomicInteger();
        var fixture = fixture(visits);
        int count = fixture.chunk.cellWidth() * fixture.chunk.cellWidth() * fixture.chunk.cellHeight();
        fixture.source.fillArray(new double[count], fixture.chunk);
        int original = visits.getAndSet(0);
        fixture.compiled.fillArray(new double[count], fixture.chunk);
        assertTrue(original > 1);
        assertEquals(original, visits.get());
    }

    @Test void otherContextsAndInactiveInterpolationUseTheSourceContract() {
        var fixture = fixture(null);
        var point = new DensityFunction.SinglePointContext(11, 2, -5);
        assertEquals(Double.doubleToLongBits(fixture.source.compute(point)),
                Double.doubleToLongBits(fixture.compiled.compute(point)));
        fixture.chunk.interpolating = false;
        assertThrows(IllegalStateException.class, () -> fixture.compiled.compute(fixture.chunk));
    }
    @Test void privateCellEvaluatorSerializesAsItsOriginalGraph() {
        var fixture = fixture(null);
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        assertEquals(DensityFunction.DIRECT_CODEC.encodeStart(ops, fixture.source).getOrThrow(),
                DensityFunction.DIRECT_CODEC.encodeStart(ops, fixture.compiled).getOrThrow());
    }
    @Test void emptyStructureAdjustmentFusesWithoutDroppingSignedZeroAddition() {
        for(var empty : new DensityFunction[]{Beardifier.EMPTY,DensityFunctions.BeardifierMarker.INSTANCE}) {
            var original=fixture(null);
            var source=DensityFunctions.add(DensityFunctions.mul(original.first,DensityFunctions.constant(.64)).squeeze(),empty);
            var compiled=NativeCellDensity.compile(source,new HashMap<>());
            assertInstanceOf(NativeCellDensity.class,compiled);
            compareArrays(new Fixture(original.chunk,original.first,source,compiled));
        }
    }
}
