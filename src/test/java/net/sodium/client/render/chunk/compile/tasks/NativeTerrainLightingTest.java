package net.sodium.client.render.chunk.compile.tasks;

import static org.junit.jupiter.api.Assertions.*;
import java.io.DataInputStream;
import java.lang.foreign.*;
import java.nio.file.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeTerrainLightingTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        assertTrue(NativeBlockRegistry.ready());
    }

    private static final class World implements net.minecraft.world.level.BlockAndTintGetter {
        net.minecraft.world.level.block.state.BlockState state;
        public int getBrightness(net.minecraft.world.level.LightLayer type, net.minecraft.core.BlockPos pos) {
            throw new AssertionError("Context producer must not project Java light values");
        }
        public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
            return pos.equals(net.minecraft.core.BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState();
        }
        public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) { return null; }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
        public float getShade(net.minecraft.core.Direction direction, boolean shade) { return 1; }
        public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { throw new AssertionError("Unexpected engine query"); }
        public int getBlockTint(net.minecraft.core.BlockPos pos, net.minecraft.world.level.ColorResolver resolver) { throw new AssertionError("Unexpected tint query"); }
    }

    private record Fixture(int[][] rows, int[][] statePatterns) {
        static Fixture read() throws Exception {
            try (var input = new DataInputStream(Files.newInputStream(Path.of(
                    "src/main/rust/render/chunk/meshing/preparation/frozen-terrain-light.bin")))) {
                assertEquals(0x544c4932, input.readInt());
                int states = input.readInt(); assertEquals(31809, states); assertEquals(8, input.readInt());
                int[][] rows = new int[input.readInt()][7];
                for (int[] row : rows) for (int i = 0; i < row.length; i++) row[i] = input.readInt();
                int[][] patterns = new int[input.readInt()][8];
                for (int[] pattern : patterns) for (int i = 0; i < 8; i++) pattern[i] = input.readUnsignedShort();
                int[][] statePatterns = new int[states][]; int runs = input.readInt(), state = 0;
                for (int run = 0; run < runs; run++) {
                    int count = input.readUnsignedShort(), pattern = input.readUnsignedShort();
                    for (int n = 0; n < count; n++) statePatterns[state++] = patterns[pattern];
                }
                assertEquals(states, state); assertEquals(-1, input.read());
                return new Fixture(rows, statePatterns);
            }
        }
    }

    @Test void productionBoundaryMatchesAll254472FrozenWords() throws Exception {
        Fixture fixture = Fixture.read();
        int cells = NativeTerrainLighting.CELLS;
        World world = new World();
        var settings = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE;
        float previousAo = settings.getAmbientOcclusionLevel();
        // Current has no public Iris GPU-runtime setters. Supply equivalent
        // semantic shade inputs in this CPU oracle fixture only.
        var aoField = settings.getClass().getDeclaredField("ambientOcclusionLevel");
        aoField.setAccessible(true);
        try (Arena arena = Arena.ofConfined()) {
            var ids = arena.allocate(cells * 4L, 8);
            var contexts = arena.allocate(cells * 8L, 8);
            var output = arena.allocate(cells * 4L, 8);
            int compared = 0;
            for (int variant = 0; variant < 8; variant++) {
                aoField.setFloat(settings, variant % 3 * .5f);
                int[] example = fixture.rows[fixture.statePatterns[0][variant]];
                MemorySegment[] views = new MemorySegment[54];
                for (int i = 0; i < views.length; i++) views[i] = new DataLayer(example[i % 2 == 0 ? 4 : 5]).nativeLightView();
                for (int start = 0; start < fixture.statePatterns.length; start += cells) {
                    for (int i = 0; i < cells; i++) {
                        int state = Math.min(start + i, fixture.statePatterns.length - 1);
                        int[] row = fixture.rows[fixture.statePatterns[state][variant]];
                        assertEquals(example[4], row[4]); assertEquals(example[5], row[5]);
                        ids.setAtIndex(ValueLayout.JAVA_INT, i, state);
                        world.state = Block.BLOCK_STATE_REGISTRY.byId(state);
                        NativeTerrainLighting.writeContext(contexts.address() + i * 8L,
                            world, world.state, net.minecraft.core.BlockPos.ZERO);
                    }
                    assertTrue(NativeTerrainLighting.admits(ids));
                    NativeTerrainLighting.prepare(views, ids, contexts, output);
                    for (int i = 0; i < cells && start + i < fixture.statePatterns.length; i++) {
                        int state = start + i;
                        assertEquals(fixture.rows[fixture.statePatterns[state][variant]][6],
                            output.getAtIndex(ValueLayout.JAVA_INT, i), "state=" + state + " variant=" + variant);
                        compared++;
                    }
                }
            }
            assertEquals(254472, compared);
        } finally { aoField.setFloat(settings, previousAo); }
    }

    @Test void retainedCpuLeasesSurviveOwnerCollectionAndFill() {
        MemorySegment[] views = new MemorySegment[54];
        for (int i = 0; i < views.length; i++) {
            DataLayer owner = new DataLayer(i - 20);
            views[i] = owner.nativeLightView();
            assertTrue(views[i].isReadOnly());
            owner.fill(3);
        }
        System.gc();
        try (Arena arena = Arena.ofConfined()) {
            int cells = NativeTerrainLighting.CELLS;
            var ids = arena.allocate(cells * 4L, 8); var contexts = arena.allocate(cells * 8L, 8);
            var output = arena.allocate(cells * 4L, 8);
            for (int i = 0; i < cells; i++) {
                ids.setAtIndex(ValueLayout.JAVA_INT, i, Block.getId(Blocks.AIR.defaultBlockState()));
                contexts.set(ValueLayout.JAVA_INT, i * 8L, 1); contexts.set(ValueLayout.JAVA_FLOAT, i * 8L + 4, 1);
            }
            NativeTerrainLighting.prepare(views, ids, contexts, output);
            for (int i = 0; i < cells; i++) {
                int x = i % 18, z = i / 18 % 18, y = i / 18 / 18;
                int section = (((y + 15) / 16 * 3 + (z + 15) / 16) * 3 + (x + 15) / 16) * 2;
                assertEquals(((section - 20) & 15) | (((section - 19) & 15) << 4)
                    | (4096 << 12) | (1 << 28), output.getAtIndex(ValueLayout.JAVA_INT, i));
            }
        }
    }

    @Test void invalidAdmissionAndContextsPublishNothingAndArraysKeepCompatibility() {
        assertNull(new DataLayer(new byte[2048]).nativeLightView());
        assertNull(new DataLayer(1) {}.nativeLightView());
        DataLayer escaped = new DataLayer(2); escaped.getData(); assertNull(escaped.nativeLightView());
        int cells = NativeTerrainLighting.CELLS;
        try (Arena arena = Arena.ofConfined()) {
            var ids = arena.allocate(cells * 4L, 8); var contexts = arena.allocate(cells * 8L, 8);
            var output = arena.allocate(cells * 4L, 8); output.fill((byte)0x2A);
            MemorySegment[] expiredViews = new MemorySegment[54];
            try (Arena expired = Arena.ofConfined()) { expiredViews[0] = expired.allocate(16, 8).asReadOnly(); }
            assertThrows(IllegalStateException.class, () -> NativeTerrainLighting.prepare(expiredViews, ids, contexts, output));
            ids.setAtIndex(ValueLayout.JAVA_INT, cells - 1, -1);
            assertFalse(NativeTerrainLighting.admits(ids));
            assertThrows(IllegalStateException.class, () -> NativeTerrainLighting.prepare(new MemorySegment[54], ids, contexts, output));
            for (int i = 0; i < cells; i++) assertEquals(0x2A2A2A2A, output.getAtIndex(ValueLayout.JAVA_INT, i));
            ids.setAtIndex(ValueLayout.JAVA_INT, cells - 1, 0);
            contexts.set(ValueLayout.JAVA_INT, (cells - 1) * 8L, 16);
            assertThrows(IllegalStateException.class, () -> NativeTerrainLighting.prepare(new MemorySegment[54], ids, contexts, output));
            for (int i = 0; i < cells; i++) assertEquals(0x2A2A2A2A, output.getAtIndex(ValueLayout.JAVA_INT, i));
        }
    }
}
