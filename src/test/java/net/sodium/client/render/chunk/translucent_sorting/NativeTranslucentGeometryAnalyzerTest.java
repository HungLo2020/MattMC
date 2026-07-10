package net.sodium.client.render.chunk.translucent_sorting;

import net.minecraft.core.SectionPos;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.data.StaticNormalRelativeData;
import net.sodium.client.render.chunk.translucent_sorting.data.StaticTopoData;
import net.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.sodium.client.render.chunk.translucent_sorting.quad.RegularTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.client.render.chunk.translucent_sorting.trigger.NativeGfniTriggers;
import net.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeTranslucentGeometryAnalyzerTest {
    @BeforeAll
    static void installDefaultSodiumOptionsForNativeBufferAllocation() throws Exception {
        try {
            SodiumClientMod.options();
            return;
        } catch (IllegalStateException ignored) {
            // Unit tests do not run Sodium's full client initializer.
        }

        Field configField = SodiumClientMod.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        configField.set(null, SodiumGameOptions.defaults());
    }

    @Test
    void opposingAlignedFacesNeedNoSorting() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();

        assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
        assertFalse(analyzer.appendQuad(zQuad(0.0F), ModelQuadFacing.NEG_Z,
                ModelQuadFacing.NEG_Z.getPackedAlignedNormal()));

        NativeTranslucentGeometryAnalyzer.Analysis analysis = analyzer.analyze(SortBehavior.SortMode.DYNAMIC);

        assertEquals(SortType.NONE, analysis.sortType());
        assertEquals(2, analysis.quadCount());
        assertEquals((1 << ModelQuadFacing.POS_Z.ordinal()) | (1 << ModelQuadFacing.NEG_Z.ordinal()),
                analysis.alignedFacingBitmap());
        assertArrayEquals(new int[] {0, 0, 1, 0, 0, 1, 0}, analysis.meshFacingCounts());
    }

    @Test
    void sameAlignedFacingProducesStaticNormalRelativeKeys() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();

        assertFalse(analyzer.appendQuad(zQuad(0.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
        assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

        NativeTranslucentGeometryAnalyzer.Analysis analysis = analyzer.analyze(SortBehavior.SortMode.DYNAMIC);

        assertEquals(SortType.STATIC_NORMAL_RELATIVE, analysis.sortType());
        assertEquals(2, analysis.staticKeys().length);
        assertTrue(analysis.staticKeys()[0] < analysis.staticKeys()[1]);
    }

    @Test
    void invalidDuplicateVertexQuadIsDiscardedBeforeNativeAnalysis() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ChunkVertexEncoder.Vertex[] vertices = zQuad(0.0F);
        vertices[1].x = vertices[0].x;
        vertices[1].y = vertices[0].y;
        vertices[1].z = vertices[0].z;
        vertices[3].x = vertices[2].x;
        vertices[3].y = vertices[2].y;
        vertices[3].z = vertices[2].z;

        assertTrue(analyzer.appendQuad(vertices, ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

        NativeTranslucentGeometryAnalyzer.Analysis analysis = analyzer.analyze(SortBehavior.SortMode.DYNAMIC);

        assertEquals(0, analysis.quadCount());
        assertEquals(SortType.NONE, analysis.sortType());
    }

    @Test
    void analyzerAppendsFromNativeStagedQuadWithoutJavaRecordStorage() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ChunkMeshBufferBuilder quadBuffer = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT.getNativeFormat(),
                sectionBuilder, ModelQuadFacing.POS_Z.ordinal());

        try {
            sectionBuilder.start(3);
            quadBuffer.start(3);
            long address = quadBuffer.prepareQuadAddress();
            NativeChunkMeshEncoder.writeNativeQuad(address, zQuad(2.0F), DefaultMaterials.TRANSLUCENT.bits());

            assertFalse(analyzer.appendNativeQuad(address, ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            quadBuffer.commitPreparedQuad();

            NativeTranslucentGeometryAnalyzer.Analysis analysis = analyzer.analyze(SortBehavior.SortMode.DYNAMIC);
            assertEquals(1, analysis.quadCount());
            assertEquals(4, quadBuffer.count());
        } finally {
            analyzer.destroy();
            sectionBuilder.close();
        }
    }

    @Test
    void analyzerAppendsNativeQuadBatchesAndMarksInvalidRecords() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ByteBuffer batch = null;
        ByteBuffer packedNormals = null;
        ByteBuffer validity = null;

        try {
            batch = nativeOrder(MemoryUtil.memAlloc(2 * NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE));
            packedNormals = nativeOrder(MemoryUtil.memAlloc(2 * Integer.BYTES));
            validity = MemoryUtil.memAlloc(2);

            ChunkVertexEncoder.Vertex[] invalid = zQuad(4.0F);
            invalid[1].x = invalid[0].x;
            invalid[1].y = invalid[0].y;
            invalid[1].z = invalid[0].z;
            invalid[3].x = invalid[2].x;
            invalid[3].y = invalid[2].y;
            invalid[3].z = invalid[2].z;

            NativeChunkMeshEncoder.writeNativeQuad(MemoryUtil.memAddress(batch), zQuad(2.0F),
                    DefaultMaterials.TRANSLUCENT.bits());
            NativeChunkMeshEncoder.writeNativeQuad(MemoryUtil.memAddress(batch) + NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    invalid, DefaultMaterials.TRANSLUCENT.bits());
            packedNormals.putInt(0, ModelQuadFacing.POS_Z.getPackedAlignedNormal());
            packedNormals.putInt(Integer.BYTES, ModelQuadFacing.POS_Z.getPackedAlignedNormal());

            assertEquals(1, analyzer.appendNativeQuadBatch(MemoryUtil.memAddress(batch), 2, ModelQuadFacing.POS_Z,
                    MemoryUtil.memAddress(packedNormals), MemoryUtil.memAddress(validity)));
            assertEquals(1, validity.get(0));
            assertEquals(0, validity.get(1));

            NativeTranslucentGeometryAnalyzer.Analysis analysis = analyzer.analyze(SortBehavior.SortMode.DYNAMIC);
            assertEquals(1, analysis.quadCount());
            assertEquals(1, analysis.meshFacingCounts()[ModelQuadFacing.POS_Z.ordinal()]);
        } finally {
            analyzer.destroy();
            free(batch);
            free(packedNormals);
            free(validity);
        }
    }

    @Test
    void staticTopoSortOrdersVisibleParallelPlanesBackToFront() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();

        assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
        assertFalse(analyzer.appendQuad(zQuad(0.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

        assertArrayEquals(new int[] {1, 0}, analyzer.staticTopoSort(false));
    }

    @Test
    void topoGraphSortFromJavaQuadsRunsNativelyAndAppliesActiveRemap() {
        TQuad[] quads = new TQuad[] {
                RegularTQuad.fromVertices(zQuad(1.0F), ModelQuadFacing.POS_Z,
                        ModelQuadFacing.POS_Z.getPackedAlignedNormal()),
                RegularTQuad.fromVertices(zQuad(0.0F), ModelQuadFacing.POS_Z,
                        ModelQuadFacing.POS_Z.getPackedAlignedNormal())
        };

        assertArrayEquals(new int[] {7, 42},
                NativeTranslucentGeometryAnalyzer.topoGraphSort(quads, quads.length, new int[] {42, 7}, false));
    }

    @Test
    void bspDoubleLeafDecisionUsesRustOwnedTopoQuadStore() {
        TQuad quadA = RegularTQuad.fromVertices(zQuad(1.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal());
        TQuad quadB = RegularTQuad.fromVertices(zQuad(0.0F), ModelQuadFacing.NEG_Z,
                ModelQuadFacing.NEG_Z.getPackedAlignedNormal());

        try (NativeTranslucentGeometryAnalyzer.TopoQuadStore store =
                     NativeTranslucentGeometryAnalyzer.createTopoQuadStore(new TQuad[] {quadA, quadB})) {
            assertTrue(store.bspDoubleLeafPossible(0, 1, false));
        }
    }

    @Test
    void topoGraphSortingClassWasRemovedFromJava() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/TopoGraphSorting.java")));
    }

    @Test
    void fullSplitQuadClassWasMigratedToRust() throws Exception {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/quad/FullTQuad.java")));

        String nativeFullQuad = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/quad/NativeFullTQuad.java"));
        String rustTranslucent = Files.readString(Path.of(
                "src/main/rust/net/sodium/client/render/chunk/translucent.rs"));

        assertTrue(nativeFullQuad.contains("mattmc_sodium_translucent_full_quad_split_even"));
        assertTrue(nativeFullQuad.contains("mattmc_sodium_translucent_full_quad_split_triangle_vertex"));
        assertTrue(rustTranslucent.contains("fn full_quad_split_even("));
        assertTrue(rustTranslucent.contains("fn interpolate_full_quad_attributes("));
        assertTrue(rustTranslucent.contains("fn mix_color("));
    }

    @Test
    void nativeSortDataStaticTopoWritesPreparedIndexData() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        Sorter sorter = null;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(0.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            NativeTranslucentSortData sortData = analyzer.createStaticTopoSortData(false);
            sorter = sortData.createStaticSorter();
            sorter.writeIndexBuffer(null, true);

            assertArrayEquals(new int[] {4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0},
                    readInts(sorter.getIndexBuffer().getDirectBuffer(), 12));
        } finally {
            if (sorter != null) {
                sorter.destroy();
            }
            analyzer.destroy();
        }
    }

    @Test
    void staticTopoFromNativeOrderWritesThroughRustOwnedSortData() {
        Sorter sorter = null;

        try {
            StaticTopoData data = StaticTopoData.fromNativeOrder(3, new int[] {2, 0, 1}, SectionPos.of(0, 0, 0));
            assertNotNull(data);
            sorter = data.getSorter();
            sorter.writeIndexBuffer(null, true);

            assertArrayEquals(new int[] {
                    8, 9, 10, 10, 11, 8,
                    0, 1, 2, 2, 3, 0,
                    4, 5, 6, 6, 7, 4
            }, readInts(sorter.getIndexBuffer().getDirectBuffer(), 18));
        } finally {
            if (sorter != null) {
                sorter.destroy();
            }
        }
    }

    @Test
    void staticNormalRelativeFromMeshWritesThroughRustOwnedSortData() {
        Sorter sorter = null;

        try {
            TQuad[] quads = new TQuad[] {
                    RegularTQuad.fromVertices(zQuad(1.0F), ModelQuadFacing.POS_Z,
                            ModelQuadFacing.POS_Z.getPackedAlignedNormal()),
                    RegularTQuad.fromVertices(zQuad(0.0F), ModelQuadFacing.POS_Z,
                            ModelQuadFacing.POS_Z.getPackedAlignedNormal())
            };

            StaticNormalRelativeData data = StaticNormalRelativeData.fromMesh(
                    new int[] {0, 0, 2, 0, 0, 0, 0}, quads, SectionPos.of(0, 0, 0), false);
            assertNotNull(data);
            sorter = data.getSorter();
            sorter.writeIndexBuffer(null, true);

            assertArrayEquals(new int[] {
                    4, 5, 6, 6, 7, 4,
                    0, 1, 2, 2, 3, 0
            }, readInts(sorter.getIndexBuffer().getDirectBuffer(), 12));
        } finally {
            if (sorter != null) {
                sorter.destroy();
            }
        }
    }

    @Test
    void nativeSortDataDirectDistanceSortWritesFarQuadsBeforeNearQuads() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ByteBuffer indexBuffer = MemoryUtil.memAlloc(2 * 6 * Integer.BYTES);
        NativeTranslucentSortData sortData = null;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(4.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            sortData = analyzer.createDynamicTopoSortData();
            NativeTranslucentSortData.DynamicSortResult result = sortData.writeDynamicSortedIndexBuffer(
                    MemoryUtil.memAddress(indexBuffer), indexBuffer.capacity(), new Vector3f(0.0F, 0.0F, 0.0F),
                    false, true, false, true, 1);

            assertFalse(result.gfniTrigger());
            assertTrue(result.directTrigger());
            assertEquals(1, result.consecutiveTopoSortFailures());
            assertArrayEquals(new int[] {4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0},
                    readInts(indexBuffer, 12));
        } finally {
            if (sortData != null) {
                sortData.close();
            }
            analyzer.destroy();
            MemoryUtil.memFree(indexBuffer);
        }
    }

    @Test
    void dynamicDirectTriggerWritesDistanceSortNatively() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ByteBuffer indexBuffer = MemoryUtil.memAlloc(2 * 6 * Integer.BYTES);
        NativeTranslucentSortData sortData = null;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(4.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            sortData = analyzer.createDynamicTopoSortData();
            NativeTranslucentSortData.DynamicSortResult result = sortData.writeDynamicSortedIndexBuffer(
                    MemoryUtil.memAddress(indexBuffer), indexBuffer.capacity(), new Vector3f(0.0F, 0.0F, 0.0F),
                    false, true, false, true, 1);

            assertFalse(result.gfniTrigger());
            assertTrue(result.directTrigger());
            assertEquals(1, result.consecutiveTopoSortFailures());
            assertArrayEquals(new int[] {4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0},
                    readInts(indexBuffer, 12));
        } finally {
            if (sortData != null) {
                sortData.close();
            }
            analyzer.destroy();
            MemoryUtil.memFree(indexBuffer);
        }
    }

    @Test
    void dynamicTopoSuccessKeepsGfniTriggeringNatively() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ByteBuffer indexBuffer = MemoryUtil.memAlloc(2 * 6 * Integer.BYTES);
        NativeTranslucentSortData sortData = null;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(4.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            sortData = analyzer.createDynamicTopoSortData();
            NativeTranslucentSortData.DynamicSortResult result = sortData.writeDynamicSortedIndexBuffer(
                    MemoryUtil.memAddress(indexBuffer), indexBuffer.capacity(), new Vector3f(0.0F, 0.0F, 8.0F),
                    true, false, true, false, 2);

            assertTrue(result.gfniTrigger());
            assertFalse(result.directTrigger());
            assertEquals(0, result.consecutiveTopoSortFailures());
            assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4},
                    readInts(indexBuffer, 12));
        } finally {
            if (sortData != null) {
                sortData.close();
            }
            analyzer.destroy();
            MemoryUtil.memFree(indexBuffer);
        }
    }

    @Test
    void nativeSortDataDynamicTopoWritesThroughOpaqueHandle() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ByteBuffer indexBuffer = MemoryUtil.memAlloc(2 * 6 * Integer.BYTES);
        NativeTranslucentSortData sortData = null;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(4.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            sortData = analyzer.createDynamicTopoSortData();
            NativeTranslucentSortData.DynamicSortResult result = sortData.writeDynamicSortedIndexBuffer(
                    MemoryUtil.memAddress(indexBuffer), indexBuffer.capacity(), new Vector3f(0.0F, 0.0F, 8.0F),
                    true, false, true, false, 2);

            assertTrue(result.gfniTrigger());
            assertFalse(result.directTrigger());
            assertEquals(0, result.consecutiveTopoSortFailures());
            assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4},
                    readInts(indexBuffer, 12));
        } finally {
            if (sortData != null) {
                sortData.close();
            }
            analyzer.destroy();
            MemoryUtil.memFree(indexBuffer);
        }
    }

    @Test
    void nativeAnalyzerCreatesGeometryPlanesInRust() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        long geometryPlanes = 0;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(4.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            geometryPlanes = analyzer.createGeometryPlanesHandle();
            assertEquals(2, NativeGfniTriggers.getGeometryPlaneCount(geometryPlanes));
        } finally {
            NativeGfniTriggers.destroyGeometryPlanes(geometryPlanes);
            analyzer.destroy();
        }
    }

    @Test
    void quadRecordFallbackCreatesGeometryPlanesInRust() {
        TQuad quad = RegularTQuad.fromVertices(zQuad(2.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal());
        long geometryPlanes = NativeTranslucentGeometryAnalyzer.createGeometryPlanes(new TQuad[] {quad});

        try {
            assertEquals(1, NativeGfniTriggers.getGeometryPlaneCount(geometryPlanes));
        } finally {
            NativeGfniTriggers.destroyGeometryPlanes(geometryPlanes);
        }
    }

    @Test
    void translucentNativePathsUseOpaqueSortDataHandles() throws Exception {
        String collector = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector.java"));
        String dynamicTopoData = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/DynamicTopoData.java"));
        String staticTopoData = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/StaticTopoData.java"));
        String staticNormalRelativeData = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/StaticNormalRelativeData.java"));
        String analyzer = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/NativeTranslucentGeometryAnalyzer.java"));

        assertTrue(collector.contains("createStaticTopoSortData("));
        assertTrue(collector.contains("NativeTranslucentSortData.createDynamicTopo("));
        assertTrue(dynamicTopoData.contains("NativeTranslucentSortData nativeSortData"));
        assertFalse(dynamicTopoData.contains("NativeTranslucentSectionGeometry nativeGeometry"));
        assertFalse(analyzer.contains("createSectionGeometry("));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/NativeTranslucentSectionGeometry.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/StaticSorter.java")));
        assertFalse(staticTopoData.contains("new StaticSorter"));
        assertFalse(staticNormalRelativeData.contains("new StaticSorter"));
    }

    private static ChunkVertexEncoder.Vertex[] zQuad(float z) {
        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        writeVertex(vertices[0], 0.0F, 0.0F, z);
        writeVertex(vertices[1], 1.0F, 0.0F, z);
        writeVertex(vertices[2], 1.0F, 1.0F, z);
        writeVertex(vertices[3], 0.0F, 1.0F, z);
        return vertices;
    }

    private static void writeVertex(ChunkVertexEncoder.Vertex vertex, float x, float y, float z) {
        ChunkVertexEncoder.Vertex.writeVertex(vertex, x, y, z, 0xffffffff, 1.0F, 0.0F, 0.0F, 0);
    }

    private static int[] readInts(ByteBuffer buffer, int count) {
        ByteBuffer nativeOrderBuffer = buffer.order(ByteOrder.nativeOrder());
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = nativeOrderBuffer.getInt(index * Integer.BYTES);
        }
        return values;
    }

    private static ByteBuffer nativeOrder(ByteBuffer buffer) {
        return buffer.order(ByteOrder.nativeOrder());
    }

    private static void free(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }
}
