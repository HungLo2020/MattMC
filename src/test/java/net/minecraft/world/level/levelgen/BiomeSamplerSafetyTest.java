package net.minecraft.world.level.levelgen;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomeSamplerSafetyTest {
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static Climate.Sampler sampler(DensityFunction first) {
        var zero=DensityFunctions.zero();return new Climate.Sampler(first,zero,zero,zero,zero,zero,List.of());
    }
    @Test void rejectsCustomFunctionsWithoutCallingAnyUserCode() {
        DensityFunction callback=new DensityFunction.SimpleFunction() {
            public double compute(FunctionContext c){throw new AssertionError("Speculative compute");}
            public DensityFunction mapAll(Visitor v){throw new AssertionError("Speculative visitor");}
            public double minValue(){throw new AssertionError("Speculative metadata");}
            public double maxValue(){throw new AssertionError("Speculative metadata");}
            public KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new AssertionError("Speculative codec");}
        };
        assertTrue(BiomeSamplerSafety.canBatch(sampler(DensityFunctions.zero())));
        assertFalse(BiomeSamplerSafety.canBatch(sampler(callback)));
        assertFalse(BiomeSamplerSafety.canBatch(sampler(DensityFunctions.cacheOnce(callback))));
        assertFalse(BiomeSamplerSafety.canBatch(sampler(new NoiseChunk.Cache2D(DensityFunctions.zero(),callback))));
        assertTrue(BiomeSamplerSafety.canBatch(sampler(new NoiseChunk.Cache2D(DensityFunctions.zero()))));
        assertFalse(BiomeSamplerSafety.canBatch(sampler(new NoiseChunk.Cache2D(DensityFunctions.zero()) {
            @Override public double compute(FunctionContext c){throw new AssertionError("Subclass callback");}
        })));
    }

    @Test @SuppressWarnings("unchecked") void rejectsCustomHoldersWithoutInvokingTheirAccessors() {
        var holder=(net.minecraft.core.Holder<DensityFunction>)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class<?>[]{net.minecraft.core.Holder.class},
            (proxy,method,args) -> {throw new AssertionError("Custom holder callback: "+method.getName());});
        assertFalse(BiomeSamplerSafety.canBatch(sampler(new DensityFunctions.HolderHolder(holder))));
        assertTrue(BiomeSamplerSafety.canBatch(sampler(new DensityFunctions.HolderHolder(net.minecraft.core.Holder.direct(DensityFunctions.zero())))));
    }
}
