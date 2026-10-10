package net.sodium.client.render.chunk.compile.tasks;

import static org.junit.jupiter.api.Assertions.*;
import java.io.DataInputStream;
import java.lang.foreign.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.*;
import net.minecraft.core.Direction;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.vertex.format.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeTerrainCullingTest {
    private static final int CELLS = 18 * 18 * 18;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); assertTrue(NativeBlockRegistry.ready());
    }
    private static boolean admits(int id) {
        return NativeTerrainCulling.admitsState(id, Block.BLOCK_STATE_REGISTRY.byId(id));
    }
    @Test void cachedSourceIdentitiesPreserveTagAndHookCallbacks() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ids = arena.allocateFrom(ValueLayout.JAVA_INT, new int[CELLS]);
            assertTrue(NativeTerrainCulling.admitsGrid(ids));
            assertTrue(admits(Block.getId(Blocks.STONE.defaultBlockState())));
            assertTrue(admits(Block.getId(Blocks.GLASS.defaultBlockState())));
            assertFalse(admits(Block.getId(Blocks.OAK_LEAVES.defaultBlockState())));
            assertFalse(admits(Block.getId(Blocks.IRON_BARS.defaultBlockState())));
            assertFalse(NativeTerrainCulling.admitsState(Block.getId(Blocks.STONE.defaultBlockState()), Blocks.GLASS.defaultBlockState()));
        }
    }
    @Test void allAdmittedProductionQueriesMatchActualFrozenOutputs() throws Exception {
        Path fixture = Path.of("src/main/rust/render/chunk/meshing/face_policy/frozen-face-policy.bin.gz");
        try (var input = new DataInputStream(new GZIPInputStream(Files.newInputStream(fixture)))) {
            assertEquals(0x54434f32, input.readInt()); for (int n = 0; n < 4; n++) input.readUTF();
            int shapes = input.readInt();
            for (int n = 0; n < shapes; n++) {
                input.readBoolean(); input.readBoolean(); int boxes = input.readInt();
                for (int b = 0; b < boxes * 6; b++) input.readDouble();
            }
            int count = input.readInt(); assertEquals(Block.BLOCK_STATE_REGISTRY.size(), count);
            for (int id = 0; id < count; id++) {
                assertEquals(id, input.readInt()); input.readInt(); input.readUTF(); input.readUTF();
                input.readByte(); input.readByte(); input.readBoolean(); input.readBoolean(); input.readInt(); input.readInt();
                for (int d = 0; d < 6; d++) input.readInt();
            }
            int queries = input.readInt(); assertEquals(787992, queries);
            int[] triples = new int[queries * 3]; byte[] expected = new byte[queries];
            for (int q = 0; q < queries; q++) {
                for (int d = 0; d < 3; d++) triples[q * 3 + d] = input.readInt();
                input.readBoolean(); expected[q] = (byte)(input.readBoolean() ? 2 : 1);
            }
            byte[] actual = NativeTerrainCulling.queryForVerification(triples);
            int nativeQueries = 0, callbacks = 0;
            for (int q = 0; q < queries; q++) {
                if (admits(triples[q * 3])) { assertEquals(expected[q], actual[q], "Frozen row " + q); nativeQueries++; }
                else { assertEquals(0, actual[q]); callbacks++; }
            }
            assertTrue(nativeQueries > 700000); assertTrue(callbacks > 0);
            int pairs = input.readInt(); assertEquals(21316, pairs);
            for (int p = 0; p < pairs; p++) { input.readInt(); input.readInt(); input.readBoolean(); }
            assertEquals(-1, input.read());
        }
    }
    @Test void invalidSpansIdsAndExpiredArenasAreRejected() {
        try (Arena arena = Arena.ofConfined()) {
            int[] bad = new int[CELLS]; bad[CELLS - 1] = -1;
            assertThrows(IllegalArgumentException.class, () -> NativeTerrainCulling.admitsGrid(arena.allocateFrom(ValueLayout.JAVA_INT, bad)));
            bad[CELLS - 1] = Block.BLOCK_STATE_REGISTRY.size();
            assertThrows(IllegalArgumentException.class, () -> NativeTerrainCulling.admitsGrid(arena.allocateFrom(ValueLayout.JAVA_INT, bad)));
            assertThrows(IllegalArgumentException.class, () -> NativeTerrainCulling.admitsGrid(arena.allocate(4)));
            assertThrows(IllegalArgumentException.class, () -> NativeTerrainCulling.queryForVerification(new int[]{0,0,6}));
        }
        MemorySegment expired;
        try (Arena arena = Arena.ofConfined()) { expired = arena.allocate(CELLS * 4L, 4); }
        assertThrows(IllegalStateException.class, () -> NativeTerrainCulling.admitsGrid(expired));
    }

    @Test void compactBuilderConsumesNativePolicyWithoutJavaCullMasks() {
        int glass = Block.getId(Blocks.GLASS.defaultBlockState());
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        assertTrue(admits(glass));
        try (Arena arena = Arena.ofConfined();
             NativeSectionMeshBuilder solid = NativeSectionMeshBuilder.create(1);
             NativeSectionMeshBuilder cutout = NativeSectionMeshBuilder.create(1);
             NativeSectionMeshBuilder translucent = NativeSectionMeshBuilder.create(1)) {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.register(77, (address, index) ->
                NativeChunkMeshEncoder.writeStaticModelQuadRecord(address, 0,
                    Direction.NORTH.get3DDataValue(), ModelQuadFacing.NEG_Z.ordinal(),
                    ModelQuadFacing.NEG_Z.getPackedAlignedNormal(), (byte)0, (byte)0, true,
                    0,0,0,-1,0,0,-1, 1,0,0,-1,1,0,-1,
                    1,1,0,-1,1,1,-1, 0,1,0,-1,0,1,-1), 1);
            NativeStaticBlockModelCache.registerSelector(9, 0,
                (address, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(address, 77, 1), 1);
            // No solid/full flags or skip-group hints: only retained native policy
            // can cull this glass/glass face.
            NativeStaticBlockModelCache.registerState(glass, 9, 1 << 1, 0, 0, 0, 0, glass, 0, -1, -1, 1);
            NativeStaticBlockModelCache.registerState(air, -1, 1, 0, -1, 0, 0, air, 0, -1, -1, 0);
            MemorySegment header = arena.allocate(NativeChunkMeshEncoder.COMPACT_SECTION_SNAPSHOT_HEADER_STRIDE, 8);
            header.set(ValueLayout.JAVA_INT, 0, NativeChunkMeshEncoder.COMPACT_SECTION_SNAPSHOT_VERSION);
            header.set(ValueLayout.JAVA_INT, 4, 1);
            MemorySegment[] lanes = new MemorySegment[13];
            for (int n = 0; n < lanes.length; n++) {
                long bytes = n == 0 ? 2 : n == 1 || n == 2 ? CELLS * 4L : n == 12 ? 4096 * 64 * 4L : 4096 * 4L;
                lanes[n] = arena.allocate(bytes, 8);
                header.set(ValueLayout.JAVA_LONG, 24 + n * 8L, lanes[n].address());
            }
            lanes[1].setAtIndex(ValueLayout.JAVA_INT, (1 * 18 + 1) * 18 + 1, glass);
            int north = (1 * 18 + 0) * 18 + 1;
            lanes[1].setAtIndex(ValueLayout.JAVA_INT, north, glass);
            lanes[11].setAtIndex(ValueLayout.JAVA_INT, 0, NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_FLAG_NATIVE_CULL);
            solid.start(0); cutout.start(0); translucent.start(0);
            assertArrayEquals(new int[]{0,0,0}, NativeSectionMeshBuilder.appendCompactNativeSectionAllPassesEncoded(
                solid, cutout, translucent, header.address(), ChunkMeshFormats.COMPACT.getNativeFormat(), 0, false, 0));
            lanes[1].setAtIndex(ValueLayout.JAVA_INT, north, air);
            assertArrayEquals(new int[]{1,0,0}, NativeSectionMeshBuilder.appendCompactNativeSectionAllPassesEncoded(
                solid, cutout, translucent, header.address(), ChunkMeshFormats.COMPACT.getNativeFormat(), 0, false, 0));
            assertEquals(4, solid.totalVertexCount());
            NativeStaticBlockModelCache.clear();
            assertThrows(IllegalStateException.class, () -> NativeSectionMeshBuilder.appendCompactNativeSectionAllPassesEncoded(
                solid, cutout, translucent, header.address(), ChunkMeshFormats.COMPACT.getNativeFormat(), 0, false, 0));
        } finally {
            NativeStaticBlockModelCache.clear();
        }
    }
}
