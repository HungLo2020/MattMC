package net.minecraft.world.level.block;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every state's facts in the Rust registry against Java's live answers. */
class NativeBlockRegistryTest {
    private static int states;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        states = Block.BLOCK_STATE_REGISTRY.size();
    }

    private static BlockState state(int id) {
        return Block.BLOCK_STATE_REGISTRY.byId(id);
    }

    @Test
    void installsJavasRegistry() {
        assertTrue(NativeBlockRegistry.ready(), "Rust must accept Java's registry");
        int[] blocks = NativeBlockRegistry.column(0);
        assertEquals(states, blocks.length);
        for (int id = 0; id < states; id++) assertEquals(BuiltInRegistries.BLOCK.getId(state(id).getBlock()), blocks[id], "block of " + id);
        int[] defaults = NativeBlockRegistry.column(7);
        assertEquals(BuiltInRegistries.BLOCK.size(), defaults.length);
        for (Block block : BuiltInRegistries.BLOCK) {
            assertEquals(Block.BLOCK_STATE_REGISTRY.getId(block.defaultBlockState()), defaults[BuiltInRegistries.BLOCK.getId(block)]);
        }
        System.out.println("BLOCK_REGISTRY_PARITY blocks=" + defaults.length + " states=" + states);
    }

    @Test
    void flagsAndLightMatchEveryState() throws Exception {
        Method isEmptyShape = LightEngine.class.getDeclaredMethod("isEmptyShape", BlockState.class);
        isEmptyShape.setAccessible(true);
        int[] flags = NativeBlockRegistry.column(1), lightBlock = NativeBlockRegistry.column(2), emission = NativeBlockRegistry.column(3);
        for (int id = 0; id < states; id++) {
            BlockState s = state(id);
            int f = flags[id];
            assertEquals(s.isAir(), (f & NativeBlockRegistry.AIR) != 0);
            assertEquals(s.blocksMotion(), (f & NativeBlockRegistry.BLOCKS_MOTION) != 0);
            assertEquals(!s.getFluidState().isEmpty(), (f & NativeBlockRegistry.HAS_FLUID) != 0);
            assertEquals(s.isRandomlyTicking(), (f & NativeBlockRegistry.RANDOM_TICKS) != 0);
            assertEquals((boolean)isEmptyShape.invoke(null, s), (f & NativeBlockRegistry.LIGHT_EMPTY_SHAPE) != 0);
            assertEquals(s.getBlock() instanceof LeavesBlock, (f & NativeBlockRegistry.LEAVES) != 0);
            assertEquals(s.getClass() != BlockState.class, (f & NativeBlockRegistry.CUSTOM) != 0);
            assertEquals(s.isSolidRender(), (f & NativeBlockRegistry.SOLID_RENDER) != 0);
            assertEquals(s.canOcclude(), (f & NativeBlockRegistry.CAN_OCCLUDE) != 0);
            assertEquals(s.hasBlockEntity(), (f & NativeBlockRegistry.BLOCK_ENTITY) != 0);
            var fluid = s.getFluidState();
            assertEquals(fluid.hasProperty(net.minecraft.world.level.material.FlowingFluid.FALLING)
                && fluid.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING), (f & NativeBlockRegistry.FLUID_FALLING) != 0);
            assertEquals(s.getLightBlock(), lightBlock[id], "light block of " + id);
            assertEquals(s.getLightEmission(), emission[id], "emission of " + id);
        }
    }

    @Test
    void fluidsAndOffsetsMatchEveryState() {
        int[] kinds = NativeBlockRegistry.column(9), heights = NativeBlockRegistry.column(10), offsets = NativeBlockRegistry.column(11);
        int[] maxOffsets = NativeBlockRegistry.column(12), fluidIds = NativeBlockRegistry.column(13);
        int water = 0, lava = 0, offset = 0;
        for (int id = 0; id < states; id++) {
            BlockState s = state(id);
            var fluid = s.getFluidState();
            int kind = fluid.isEmpty() ? 0 : fluid.is(net.minecraft.world.level.material.Fluids.WATER)
                || fluid.is(net.minecraft.world.level.material.Fluids.FLOWING_WATER) ? 1
                : fluid.is(net.minecraft.world.level.material.Fluids.LAVA) || fluid.is(net.minecraft.world.level.material.Fluids.FLOWING_LAVA) ? 2 : 3;
            assertEquals(net.minecraft.world.level.material.Fluid.FLUID_STATE_REGISTRY.getId(fluid), fluidIds[id], "fluid state of " + s);
            assertEquals(kind, kinds[id], "fluid of " + s);
            assertEquals(Float.floatToRawIntBits(fluid.isEmpty() ? 0.0F : fluid.getOwnHeight()), heights[id], "fluid height of " + s);
            assertEquals(s.sodium$getOffsetType().ordinal(), offsets[id], "offset of " + s);
            water += kind == 1 ? 1 : 0;
            lava += kind == 2 ? 1 : 0;
            offset += offsets[id] != 0 ? 1 : 0;
        }
        assertEquals(BuiltInRegistries.BLOCK.size() * 2, maxOffsets.length);
        for (Block block : BuiltInRegistries.BLOCK) {
            int b = BuiltInRegistries.BLOCK.getId(block);
            assertEquals(Float.floatToRawIntBits(block.defaultBlockState().sodium$getMaxHorizontalOffset()), maxOffsets[b * 2], "offset of " + block);
            assertEquals(Float.floatToRawIntBits(block.defaultBlockState().sodium$getMaxVerticalOffset()), maxOffsets[b * 2 + 1], "offset of " + block);
        }
        System.out.println("BLOCK_REGISTRY_FLUIDS water=" + water + " lava=" + lava + " offset_states=" + offset);
    }

    @Test
    void faceIdsAreExactBoxListsWithJavasTruthTable() {
        int[] faces = NativeBlockRegistry.column(4);
        int[] matrix = NativeBlockRegistry.column(8);
        int count = (int)Math.round(Math.sqrt(matrix.length));
        assertEquals(count * count, matrix.length);
        var boxes = new HashMap<Integer, List<AABB>>();
        var shapes = new VoxelShape[count];
        var byBoxes = new HashMap<List<AABB>, Integer>();
        Direction[] directions = Direction.values();
        for (int id = 0; id < states; id++) {
            for (int d = 0; d < 6; d++) {
                VoxelShape shape = LightEngine.getOcclusionShape(state(id), directions[d]);
                int face = faces[id * 6 + d];
                var key = List.copyOf(shape.toAabbs());
                assertEquals(key, boxes.computeIfAbsent(face, f -> key), "one box list per face id");
                assertEquals(face, byBoxes.computeIfAbsent(key, k -> face), "one face id per box list");
                shapes[face] = shape;
            }
        }
        assertEquals(List.of(), boxes.get(0), "face 0 is empty");
        for (int a = 0; a < count; a++) {
            for (int b = 0; b < count; b++) {
                if (shapes[a] == null || shapes[b] == null) continue;
                assertEquals(Shapes.faceShapeOccludes(shapes[a], shapes[b]) ? 1 : 0, matrix[a * count + b], "occludes " + a + " " + b);
            }
        }
        System.out.println("BLOCK_REGISTRY_FACES faces=" + count);
    }

    @Test
    void propertyArithmeticMatchesGetAndSetValue() {
        int[] values = NativeBlockRegistry.column(5);
        int[] with = NativeBlockRegistry.column(6);
        int v = 0, w = 0, checks = 0;
        for (int id = 0; id < states; id++) {
            BlockState s = state(id);
            for (Property<?> property : s.getBlock().getStateDefinition().getProperties()) {
                List<?> possible = property.getPossibleValues();
                assertEquals(possible.indexOf(s.getValue(property)), values[v++], "value of " + property.getName() + " in " + id);
                for (Object value : possible) {
                    assertEquals(Block.BLOCK_STATE_REGISTRY.getId(set(s, property, value)), with[w++]);
                    checks++;
                }
            }
        }
        assertEquals(values.length, v);
        assertEquals(with.length, w);
        System.out.println("BLOCK_REGISTRY_ARITHMETIC set_value_checks=" + checks);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState set(BlockState state, Property property, Object value) {
        return state.setValue(property, (Comparable)value);
    }
}
