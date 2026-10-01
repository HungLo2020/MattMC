package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAquiferTest {
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    @Test void globalFluidPolicyMatchesGeneratorThresholds() {
        for(int sea:new int[]{Integer.MIN_VALUE,-100,-54,-53,0,63,320,Integer.MAX_VALUE})
            for(var block:new net.minecraft.world.level.block.Block[]{Blocks.WATER,Blocks.LAVA,Blocks.AIR,Blocks.CAVE_AIR}) {
                var picker=new AquiferFluidPicker(sea,block.defaultBlockState());
                for(int y:new int[]{Integer.MIN_VALUE,-100,-55,-54,-53,0,63,320,Integer.MAX_VALUE}) {
                    var status=picker.computeFluid(-29999999,y,29999999);
                    assertEquals(y<Math.min(-54,sea)?-54:sea,status.fluidLevel());
                    assertSame(y<Math.min(-54,sea)?Blocks.LAVA.defaultBlockState():block.defaultBlockState(),status.fluidType());
                }
            }
    }

    private static NoiseRouter router(DensityFunction barrier,DensityFunction vein) {
        var zero=DensityFunctions.zero();
        return new NoiseRouter(barrier,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,zero,vein,zero,zero);
    }
    @Test void customCallbacksAreRejectedWithoutInvokingThem() {
        DensityFunction callback=new DensityFunction.SimpleFunction() {
            public double compute(FunctionContext context){throw new AssertionError("No speculative evaluation");}
            @Override public DensityFunction mapAll(Visitor visitor){throw new AssertionError("No speculative visitor");}
            public double minValue(){throw new AssertionError("No metadata callback");}
            public double maxValue(){throw new AssertionError("No metadata callback");}
            public KeyDispatchDataCodec<? extends DensityFunction> codec(){throw new AssertionError("No codec callback");}
        };
        assertTrue(NativeAquiferSources.safe(router(DensityFunctions.zero(),DensityFunctions.zero())));
        assertFalse(NativeAquiferSources.safe(router(callback,DensityFunctions.zero())));
        assertFalse(NativeAquiferSources.safe(router(DensityFunctions.zero(),callback)),"Ore callbacks interleave with aquifer calls");
        assertFalse(NativeAquiferSources.safe(router(DensityFunctions.cacheOnce(callback),DensityFunctions.zero())));
    }
}
