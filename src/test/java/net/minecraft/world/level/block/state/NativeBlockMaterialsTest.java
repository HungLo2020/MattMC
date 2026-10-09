package net.minecraft.world.level.block.state;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.HashSet;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeBlockMaterialsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void nativeOffsetViewsMatchGenericFunctionsAndReuseImmutableVectors() {
        var seen=new HashSet<String>();var random=new Random(142861L);
        for(var block:BuiltInRegistries.BLOCK) {
            var state=block.defaultBlockState();var properties=BlockBehaviour.Properties.ofFullCopy(block);
            String key=state.sodium$getOffsetType()+"/"+state.sodium$getMaxHorizontalOffset()+"/"+state.sodium$getMaxVerticalOffset();
            if(!seen.add(key))continue;
            for(int sample=0;sample<1000;sample++) {
                var pos=new BlockPos(random.nextInt(),random.nextInt(),random.nextInt());
                var expected=properties.offsetFunction==null?net.minecraft.world.phys.Vec3.ZERO:properties.offsetFunction.evaluate(state,pos);
                var actual=state.getOffset(pos);
                assertEquals(Double.doubleToRawLongBits(expected.x),Double.doubleToRawLongBits(actual.x),key);
                assertEquals(Double.doubleToRawLongBits(expected.y),Double.doubleToRawLongBits(actual.y),key);
                assertEquals(Double.doubleToRawLongBits(expected.z),Double.doubleToRawLongBits(actual.z),key);
                assertSame(actual,state.getOffset(pos),key);
            }
        }
    }
    @Test void manuallyConstructedStatesRetainStateDependentSoundCallbacks() {
        for(boolean cracked:new boolean[]{false,true}) {
            var canonical=Blocks.DECORATED_POT.defaultBlockState().setValue(DecoratedPotBlock.CRACKED,cracked);
            var values=new Reference2ObjectArrayMap<Property<?>,Comparable<?>>(canonical.getValues());
            var legacy=new BlockState(Blocks.DECORATED_POT,values,MapCodec.unit(Blocks.DECORATED_POT::defaultBlockState));
            assertSame(canonical.getSoundType(),legacy.getSoundType());
        }
    }
}
