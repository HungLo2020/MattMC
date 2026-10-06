package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.LightTerrainFixtures;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static net.minecraft.world.level.lighting.LightPropagationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeLightPropagationTest {
    @BeforeAll static void load() {
        LightTerrainFixtures.load();
    }

    @AfterAll static void close() {
        NativeLightPropagation.setEnabled(true);
        LightTerrainFixtures.close();
    }

    private static void assertSame(Side java, Side rust, String when) {
        var differences = differences(java.engine(), rust.engine());
        assertTrue(differences.isEmpty(), () -> when + ": " + differences.subList(0, Math.min(8, differences.size())));
        assertEquals(new LongOpenHashSet(java.getter().blockUpdates), new LongOpenHashSet(rust.getter().blockUpdates), when + " block updates");
        assertEquals(new LongOpenHashSet(java.getter().skyUpdates), new LongOpenHashSet(rust.getter().skyUpdates), when + " sky updates");
        for (var side : List.of(java, rust)) {
            side.getter().blockUpdates.clear();
            side.getter().skyUpdates.clear();
        }
    }

    /** Lights a terrain grid on both routes, then edits it, comparing all storage after every pass. */
    private static long scenario(List<ProtoChunk> chunks, boolean sky, long seed, int rounds, boolean wrapSome) {
        var world = new World(chunks);
        if (wrapSome) {
            for (var chunk : chunks) if ((chunk.getPos().x + chunk.getPos().z) % 3 == 0) world.wrapped.add(chunk.getPos().toLong());
        }
        for (var chunk : chunks) chunk.initializeLightSources();
        Side java = Side.of(world, sky, false), rust = Side.of(world, sky, true);
        var sides = List.of(java, rust);
        int[] passes = {0};
        long before = NativeLightPropagation.PASSES.get(), rejected = NativeLightPropagation.REJECTED.get();
        long seeded = NativeLightPropagation.SEEDED.get();
        lightAll(world, sides, seed, () -> assertSame(java, rust, "lighting pass " + passes[0]++));
        var random = new Random(seed);
        int minX = chunks.getFirst().getPos().getMinBlockX(), minZ = chunks.getFirst().getPos().getMinBlockZ();
        int span = (int)Math.round(Math.sqrt(chunks.size())) * 16;
        for (int round = 0; round < rounds; round++) {
            int edits = 1 + random.nextInt(4);
            for (int i = 0; i < edits; i++) edit(world, List.of(java.engine(), rust.engine()), random, minX, minZ, span);
            pass(sides, null);
            assertSame(java, rust, "edit round " + round);
        }
        long rustPasses = NativeLightPropagation.PASSES.get() - before;
        assertTrue(rustPasses > 0, "Rust passes must run");
        // Only the Rust side seeds sky sources natively: once per lit chunk.
        assertEquals(sky ? chunks.size() : 0, NativeLightPropagation.SEEDED.get() - seeded);
        assertEquals(rejected, NativeLightPropagation.REJECTED.get(), "No pass may fall back");
        return rustPasses;
    }

    @Test void overworldTerrainLightsAndEditsIdentically() {
        long passes = 0;
        for (long seed : new long[]{7_340_013L, -42L}) {
            var chunks = LightTerrainFixtures.grid(NoiseGeneratorSettings.OVERWORLD, seed, (int)(seed % 50), -3, 5);
            passes += scenario(chunks, true, seed, 60, false);
        }
        System.out.println("LIGHT_PROPAGATION_PARITY world=overworld rust_passes=" + passes);
    }

    @Test void savedFullTerrainLightsAndEditsIdentically() {
        var chunks = corpus();
        long passes = scenario(chunks, true, 31L, 120, false);
        System.out.println("LIGHT_PROPAGATION_PARITY world=saved_full chunks=" + chunks.size() + " rust_passes=" + passes);
    }

    @Test void floatingEndIslandsOverVoidUnderSkyLight() {
        // Islands above the void leave unstored sections below stored ones down to the
        // lowest data section: the sky engine's empty-section paths.
        long passes = 0;
        for (long seed : new long[]{13L, 21L}) {
            var chunks = LightTerrainFixtures.grid(NoiseGeneratorSettings.END, seed, 4, -2, 5);
            passes += scenario(chunks, true, seed, 80, false);
        }
        System.out.println("LIGHT_PROPAGATION_PARITY world=end_sky rust_passes=" + passes);
    }

    /** Empty chunks with floating blocks and pillars: unstored sections lie below
     * stored ones across every chunk border, where skylight fills empty sections. */
    private static List<ProtoChunk> sparse(long seed) {
        var random = new Random(seed);
        var chunks = new ArrayList<ProtoChunk>();
        for (int cz = 0; cz < 6; cz++) for (int cx = 0; cx < 6; cx++) {
            var sections = new net.minecraft.world.level.chunk.LevelChunkSection[16];
            for (int i = 0; i < sections.length; i++) {
                sections[i] = new net.minecraft.world.level.chunk.LevelChunkSection(new net.minecraft.world.level.chunk.PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(), net.minecraft.world.level.chunk.Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
            }
            var chunk = new ProtoChunk(new ChunkPos(cx, cz), net.minecraft.world.level.chunk.UpgradeData.EMPTY, sections,
                new net.minecraft.world.ticks.ProtoChunkTicks<>(), new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                net.minecraft.world.level.LevelHeightAccessor.create(0, 256), null, null);
            for (int i = random.nextInt(4); i > 0; i--) {
                int x = random.nextBoolean() ? random.nextInt(2) * 15 : random.nextInt(16), z = random.nextBoolean() ? random.nextInt(2) * 15 : random.nextInt(16);
                int y = (2 + random.nextInt(13)) * 16 + (random.nextBoolean() ? 0 : random.nextInt(16));
                sections[y >> 4].setBlockState(x, y & 15, z, Blocks.STONE.defaultBlockState(), false);
            }
            if (random.nextInt(3) == 0) {
                int x = random.nextInt(16), z = random.nextInt(16), top = random.nextInt(200);
                for (int y = 0; y < top; y += 1 + random.nextInt(24)) sections[y >> 4].setBlockState(x, y & 15, z, Blocks.GLASS.defaultBlockState(), false);
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    @Test void sparseSkyCrossesEmptySectionsInEveryDirection() {
        long passes = 0;
        for (long seed = 1; seed <= 6; seed++) passes += scenario(sparse(seed), true, seed, 60, false);
        System.out.println("LIGHT_PROPAGATION_PARITY world=sparse_sky rust_passes=" + passes);
    }

    @Test void netherBlockLightAndModelledAndOtherChunkKinds() {
        var nether = LightTerrainFixtures.grid(NoiseGeneratorSettings.NETHER, 99L, 20, 20, 5);
        long passes = scenario(nether, false, 99L, 60, false);
        var overworld = LightTerrainFixtures.grid(NoiseGeneratorSettings.OVERWORLD, 5L, -200, 300, 4);
        passes += scenario(overworld, true, 5L, 40, true);
        var amplified = LightTerrainFixtures.grid(NoiseGeneratorSettings.AMPLIFIED, 11L, 0, 0, 4);
        passes += scenario(amplified, true, 11L, 40, false);
        System.out.println("LIGHT_PROPAGATION_PARITY world=nether,wrapped,amplified rust_passes=" + passes);
    }

    @Test void inputJavaRejectsIsHandedBackUntouched() {
        // Sources queued for a chunk whose sections were never initialized: Java
        // throws reading unstored light; Rust must hand the pass back unchanged.
        var chunks = LightTerrainFixtures.grid(NoiseGeneratorSettings.NETHER, 3L, 0, 0, 1);
        var world = new World(chunks);
        var chunk = chunks.getFirst();
        chunk.getSection(chunk.getSectionIndex(100)).setBlockState(3, 100 & 15, 5, Blocks.GLOWSTONE.defaultBlockState(), false);
        chunk.initializeLightSources();
        Side java = Side.of(world, false, false), rust = Side.of(world, false, true);
        long rejected = NativeLightPropagation.REJECTED.get();
        RuntimeException a = null, b = null;
        for (var side : List.of(java, rust)) {
            side.engine().propagateLightSources(chunk.getPos());
            try {
                side.run();
            } catch (RuntimeException error) {
                if (side == java) a = error; else b = error;
            }
        }
        assertNotNull(a, "the original must reject this input");
        assertNotNull(b);
        assertEquals(a.getClass(), b.getClass());
        assertEquals(rejected + 1, NativeLightPropagation.REJECTED.get());
    }

    @Test void lightPropertyTablesMatchEveryStateAndFacePair() throws Exception {
        var tables = Class.forName(NativeLightPropagation.class.getName() + "$Tables");
        var typesField = tables.getDeclaredField("TYPES");
        typesField.setAccessible(true);
        char[] types = (char[])typesField.get(null);
        assertEquals(Block.BLOCK_STATE_REGISTRY.size(), types.length);
        // Distinct states per type: equal types must have equal properties and faces.
        var representative = new java.util.HashMap<Character, BlockState>();
        var faces = new IdentityHashMap<VoxelShape, VoxelShape>();
        int pairs = 0;
        for (int id = 0; id < types.length; id++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            assertNotEquals(Character.MAX_VALUE, types[id]);
            BlockState other = representative.putIfAbsent(types[id], state);
            if (other == null) continue;
            assertEquals(Math.max(1, other.getLightBlock()), Math.max(1, state.getLightBlock()));
            assertEquals(other.getLightEmission(), state.getLightEmission());
            assertEquals(LightEngine.isEmptyShape(other), LightEngine.isEmptyShape(state));
            for (Direction direction : Direction.values()) {
                VoxelShape a = LightEngine.getOcclusionShape(other, direction), b = LightEngine.getOcclusionShape(state, direction);
                if (a != b) faces.put(b, a);
            }
        }
        // Faces merged by box list must answer every occlusion pair like the face they merged into.
        var all = new ArrayList<VoxelShape>();
        var seen = new IdentityHashMap<VoxelShape, Boolean>();
        for (int id = 0; id < types.length; id++) {
            for (Direction direction : Direction.values()) {
                VoxelShape face = LightEngine.getOcclusionShape(Block.BLOCK_STATE_REGISTRY.byId(id), direction);
                if (seen.put(face, true) == null) all.add(face);
            }
        }
        for (var merged : faces.entrySet()) {
            for (VoxelShape other : all) {
                assertEquals(Shapes.faceShapeOccludes(merged.getValue(), other), Shapes.faceShapeOccludes(merged.getKey(), other));
                assertEquals(Shapes.faceShapeOccludes(other, merged.getValue()), Shapes.faceShapeOccludes(other, merged.getKey()));
                pairs++;
            }
        }
        System.out.println("LIGHT_TABLE_PARITY states=" + types.length + " types=" + representative.size() + " face_objects=" + all.size()
            + " merged_pairs=" + pairs);
    }

    @Test void blockEdgesAtWorldLimitsAndMissingNeighbours() {
        // A lone chunk at the build limits: neighbours are absent (bedrock) and
        // light reaches the sections above and below the world.
        var chunks = LightTerrainFixtures.grid(NoiseGeneratorSettings.OVERWORLD, 1L, 1_874_998, -1_874_999, 2);
        var world = new World(chunks);
        for (var chunk : chunks) chunk.initializeLightSources();
        Side java = Side.of(world, true, false), rust = Side.of(world, true, true);
        var sides = List.of(java, rust);
        lightAll(world, sides, 1L, () -> assertSame(java, rust, "edge lighting"));
        var engines = List.of(java.engine(), rust.engine());
        var minX = chunks.getFirst().getPos().getMinBlockX();
        var minZ = chunks.getFirst().getPos().getMinBlockZ();
        for (int y : new int[]{world.minY, world.minY + world.height - 1}) {
            for (int i = 0; i < 32; i++) set(world, engines, new BlockPos(minX + i, y, minZ + (i * 7) % 32), i % 2 == 0 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            pass(sides, () -> assertSame(java, rust, "edge edit"));
        }
        assertEquals(checksum(java.engine()), checksum(rust.engine()));
        assertNotNull(new ChunkPos(0, 0));
    }
}
