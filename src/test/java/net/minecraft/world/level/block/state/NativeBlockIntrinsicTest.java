package net.minecraft.world.level.block.state;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeBlockIntrinsicTest {
    @BeforeAll
    static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void copiedPropertyFunctionsMatchEveryNativeStateColumn() {
        for (var block : BuiltInRegistries.BLOCK) {
            var copy = BlockBehaviour.Properties.ofFullCopy(block);
            for (var state : block.getStateDefinition().getPossibleStates()) {
                assertSame(state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), copy.mapColor.apply(state), state.toString());
                assertEquals(state.getLightEmission(), copy.lightEmission.applyAsInt(state), state.toString());
            }
        }
    }

    @Test
    void copiedRulesRequireOnlyTheirOwnDependencies() {
        var constant = BlockBehaviour.Properties.ofFullCopy(Blocks.STONE);
        assertSame(MapColor.STONE, constant.mapColor.apply(Blocks.AIR.defaultBlockState()));
        assertEquals(0, constant.lightEmission.applyAsInt(Blocks.WATER.defaultBlockState()));
        var log = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG);
        var otherOwner = Blocks.BASALT.defaultBlockState();
        assertSame(MapColor.WOOD, log.mapColor.apply(otherOwner.setValue(BlockStateProperties.AXIS, Direction.Axis.Y)));
        assertSame(MapColor.PODZOL, log.mapColor.apply(otherOwner.setValue(BlockStateProperties.AXIS, Direction.Axis.X)));
    }

    @Test
    void genericStateConstructionRetainsItsFluidCallback() {
        var values = new Reference2ObjectArrayMap<Property<?>, Comparable<?>>();
        values.put(LiquidBlock.LEVEL, 8);
        var legacy = new BlockState(Blocks.WATER, values, MapCodec.unit(Blocks.WATER::defaultBlockState));
        legacy.initCache();
        assertSame(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8).getFluidState(), legacy.getFluidState());
        assertFalse(legacy.getFluidState().isEmpty());
    }
}
