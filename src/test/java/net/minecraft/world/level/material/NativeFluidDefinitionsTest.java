package net.minecraft.world.level.material;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeFluidDefinitionsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registryViewsPreserveIdsNamesDefaultsAndAll37StateFacts() {
        List<Fluid> fluids = List.of(Fluids.EMPTY, Fluids.FLOWING_WATER, Fluids.WATER, Fluids.FLOWING_LAVA, Fluids.LAVA);
        List<String> names = List.of("empty", "flowing_water", "water", "flowing_lava", "lava");
        int[] offsets = {0, 1, 17, 19, 35};
        assertEquals(5, BuiltInRegistries.FLUID.size());
        assertEquals(37, Fluid.FLUID_STATE_REGISTRY.size());
        for (int id = 0; id < fluids.size(); id++) {
            Fluid fluid = fluids.get(id);
            assertEquals(id, BuiltInRegistries.FLUID.getId(fluid));
            assertEquals("minecraft:" + names.get(id), BuiltInRegistries.FLUID.getKey(fluid).toString());
            assertEquals(offsets[id], Fluid.FLUID_STATE_REGISTRY.getId(fluid.defaultFluidState()));
            boolean source = id == 2 || id == 4;
            var states = fluid.getStateDefinition().getPossibleStates();
            if (id == 0) {
                assertEquals(1, states.size());
                assertTraits(states.getFirst(), 0, false, true, 0);
                assertSame(Blocks.AIR.defaultBlockState(), states.getFirst().createLegacyBlock());
                continue;
            }
            assertEquals(source ? 2 : 16, states.size());
            int local = 0;
            for (boolean falling : List.of(true, false)) {
                for (int amount = source ? 8 : 1; amount <= 8; amount++) {
                    FluidState state = states.get(local);
                    int legacy = source ? 0 : 8 - amount + (falling ? 8 : 0);
                    assertEquals(offsets[id] + local++, Fluid.FLUID_STATE_REGISTRY.getId(state));
                    assertEquals(falling, state.getValue(FlowingFluid.FALLING));
                    if (!source) assertEquals(amount, state.getValue(FlowingFluid.LEVEL));
                    assertTraits(state, amount, source, false, legacy);
                    assertEquals(legacy, state.createLegacyBlock().getValue(LiquidBlock.LEVEL));
                    assertSame(id <= 2 ? Blocks.WATER : Blocks.LAVA, state.createLegacyBlock().getBlock());
                }
            }
            assertTrue(fluid.defaultFluidState().getValue(FlowingFluid.FALLING));
        }
    }

    private static void assertTraits(FluidState state, int amount, boolean source, boolean empty, int legacy) {
        assertEquals(Fluid.FLUID_STATE_REGISTRY.getId(state), state.nativeStateId());
        assertEquals(amount, state.getAmount());
        assertEquals(source, state.isSource());
        assertEquals(empty, state.isEmpty());
        assertEquals(Float.floatToRawIntBits(amount / 9.0F), Float.floatToRawIntBits(state.getOwnHeight()));
        assertEquals(empty ? 0.0F : 100.0F, state.getExplosionResistance());
        assertEquals(legacy, state.legacyLevel());
        assertEquals(amount, state.getType().getAmount(state));
        assertEquals(source, state.getType().isSource(state));
        assertEquals(state.getOwnHeight(), state.getType().getOwnHeight(state));
    }

    @Test
    void codecsAndTransitionsRetainCanonicalStateIdentityAfterNativeProjection() {
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            for (FluidState state : fluid.getStateDefinition().getPossibleStates()) {
                var json = FluidState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
                assertSame(state, FluidState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
                if (!state.isEmpty()) {
                    FluidState flipped = state.setValue(FlowingFluid.FALLING, !state.getValue(FlowingFluid.FALLING));
                    assertSame(state, flipped.setValue(FlowingFluid.FALLING, state.getValue(FlowingFluid.FALLING)));
                    assertEquals(state.getAmount(), flipped.getAmount());
                    assertEquals(state.isSource(), flipped.isSource());
                }
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> NativeFluidDefinitions.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> Fluids.WATER.nativeDefinition.states().clear());
    }
}
