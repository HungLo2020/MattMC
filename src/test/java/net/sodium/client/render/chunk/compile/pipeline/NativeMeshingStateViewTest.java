package net.sodium.client.render.chunk.compile.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.sodium.client.render.chunk.vertex.format.NativeStaticBlockModelCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every state's meshing record registered from the block registry equals
 * the record registered with the facts Java computes, field for field, so
 * the meshers see the same input. */
class NativeMeshingStateViewTest {
    private static final MethodHandle SNAPSHOT = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_sodium_native_meshing_state_snapshot",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterAll static void clear() {
        NativeStaticBlockModelCache.clear();
    }

    /** The facts NativeStaticBlockModelRegistry.registerState computes in Java. */
    private static int javaFlags(BlockState state, int renderFlags, boolean javaFluids) {
        int flags = renderFlags;
        if (state.isAir()) flags |= NativeStaticBlockModelRegistry.STATE_FLAG_AIR;
        if (!javaFluids && !state.getFluidState().isEmpty()) flags |= NativeStaticBlockModelRegistry.STATE_FLAG_FLUID;
        if (state.isSolidRender()) flags |= NativeStaticBlockModelRegistry.STATE_FLAG_SOLID_RENDER | NativeStaticBlockModelRegistry.STATE_FLAG_FULL_OCCLUSION;
        if (state.hasBlockEntity()) flags |= NativeStaticBlockModelRegistry.STATE_FLAG_BLOCK_ENTITY;
        if (state.canOcclude()) flags |= NativeStaticBlockModelRegistry.STATE_FLAG_CAN_OCCLUDE;
        if (state.blocksMotion()) flags |= NativeStaticBlockModelRegistry.STATE_FLAG_BLOCKS_MOTION;
        return flags;
    }

    private static int javaFluidType(BlockState state, boolean nativeFluid) {
        var fluid = state.getFluidState();
        if (!nativeFluid) return 0;
        if (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) return 1;
        if (fluid.is(Fluids.LAVA) || fluid.is(Fluids.FLOWING_LAVA)) return 2;
        return 0;
    }

    private record Snapshot(int[] ints, float[] floats) {}

    private static Snapshot snapshot(int stateId) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ints = arena.allocate(17 * 4L, 4), floats = arena.allocate(18 * 4L, 4);
            assertEquals(0, (int)SNAPSHOT.invokeExact(stateId, ints, floats));
            return new Snapshot(ints.toArray(ValueLayout.JAVA_INT), floats.toArray(ValueLayout.JAVA_FLOAT));
        }
    }

    @Test void everyStateMatchesItsJavaFacts() throws Throwable {
        assertTrue(NativeBlockRegistry.ready());
        int states = Block.BLOCK_STATE_REGISTRY.size(), compared = 0, fluids = 0;
        for (boolean javaFluids : new boolean[]{false, true}) {
            for (int id = 0; id < states; id++) {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                var fluid = state.getFluidState();
                boolean nativeFluid = !javaFluids && !fluid.isEmpty() && (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)
                    || fluid.is(Fluids.LAVA) || fluid.is(Fluids.FLOWING_LAVA));
                // Distinct render-owned columns per state, so passthrough is checked too.
                int renderFlags = (id % 3 == 0 ? NativeStaticBlockModelRegistry.STATE_FLAG_MODEL : 0)
                    | (id % 5 == 0 ? NativeStaticBlockModelRegistry.STATE_FLAG_MODEL_FACE_CULLABLE : 0)
                    | NativeStaticBlockModelRegistry.fluidOverlayFlags(state);
                float[] sprites = new float[15];
                for (int i = 0; i < 15; i++) sprites[i] = id * 0.001F + i;
                int selector = id % 7 - 1, material = id * 3, pass = id % 4 - 1, blockId = id * 5 + 1, fluidMaterial = id % 11,
                    fluidPass = id % 3 - 1, fluidBlock = id % 13 - 1, skipGroup = id % 17, skipMask = id % 64, tint = id % 11,
                    overlayValid = id % 2;
                NativeStaticBlockModelCache.registerState(id, selector, javaFlags(state, renderFlags, javaFluids), material, pass,
                    state.getLightEmission(), 0, blockId, fluidMaterial, fluidPass, fluidBlock, skipGroup, skipMask,
                    javaFluidType(state, nativeFluid), fluid.isEmpty() ? 0.0F : fluid.getOwnHeight(),
                    fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING) ? 1 : 0,
                    state.sodium$getOffsetType().ordinal(), state.sodium$getMaxHorizontalOffset(), state.sodium$getMaxVerticalOffset(), tint,
                    sprites[0], sprites[1], sprites[2], sprites[3], sprites[4], sprites[5], sprites[6], sprites[7], sprites[8], sprites[9],
                    sprites[10], sprites[11], sprites[12], sprites[13], sprites[14], overlayValid);
                Snapshot java = snapshot(id);
                int control = (javaFluids ? NativeStaticBlockModelCache.CONTROL_JAVA_FLUIDS : 0)
                    | (nativeFluid ? NativeStaticBlockModelCache.CONTROL_NATIVE_FLUID : 0);
                assertTrue(NativeStaticBlockModelCache.registerStateView(id, selector, renderFlags, material, pass, 0, blockId,
                    fluidMaterial, fluidPass, fluidBlock, skipGroup, skipMask, tint, control, sprites, overlayValid), "view of " + state);
                Snapshot view = snapshot(id);
                assertArrayEquals(java.ints(), view.ints(), "fields of " + state + " java fluids " + javaFluids);
                for (int i = 0; i < 18; i++) {
                    assertEquals(Float.floatToRawIntBits(java.floats()[i]), Float.floatToRawIntBits(view.floats()[i]),
                        "float " + i + " of " + state);
                }
                compared++;
                fluids += nativeFluid ? 1 : 0;
            }
        }
        System.out.println("MESHING_STATE_VIEW_PARITY records=" + compared + " native_fluid_records=" + fluids);
    }
}
