package net.sodium.client.render.chunk.translucent_sorting;

import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeTranslucentGeometryAnalyzerTest {
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
    void staticTopoSortOrdersVisibleParallelPlanesBackToFront() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();

        assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
        assertFalse(analyzer.appendQuad(zQuad(0.0F), ModelQuadFacing.POS_Z,
                ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

        assertArrayEquals(new int[] {1, 0}, analyzer.staticTopoSort(false));
    }

    @Test
    void sectionGeometryDistanceSortWritesFarQuadsBeforeNearQuads() {
        NativeTranslucentGeometryAnalyzer analyzer = new NativeTranslucentGeometryAnalyzer();
        ByteBuffer indexBuffer = MemoryUtil.memAlloc(2 * 6 * Integer.BYTES);
        NativeTranslucentSectionGeometry geometry = null;

        try {
            assertFalse(analyzer.appendQuad(zQuad(1.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));
            assertFalse(analyzer.appendQuad(zQuad(4.0F), ModelQuadFacing.POS_Z,
                    ModelQuadFacing.POS_Z.getPackedAlignedNormal()));

            geometry = analyzer.createSectionGeometry();
            geometry.writeDistanceSortedIndexBuffer(MemoryUtil.memAddress(indexBuffer), indexBuffer.capacity(),
                    new Vector3f(0.0F, 0.0F, 0.0F));

            assertArrayEquals(new int[] {4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0},
                    readInts(indexBuffer, 12));
        } finally {
            if (geometry != null) {
                geometry.close();
            }
            analyzer.destroy();
            MemoryUtil.memFree(indexBuffer);
        }
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
}
