package net.sodium.client.render.chunk.vertex;

import net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.UpdatedQuadsList;
import net.sodium.client.render.chunk.translucent_sorting.quad.FullTQuad;
import net.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeChunkMeshEncoderTest {
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
    void assemblesCompactQuadsInSliceOrder() {
        ChunkMeshBufferBuilder[] builders = makeBuilders(ChunkMeshFormats.COMPACT, 16, 9);
        ByteBuffer output = null;

        try {
            pushQuad(builders[ModelQuadFacing.UNASSIGNED.ordinal()], 0.0F, 3);
            pushQuad(builders[ModelQuadFacing.POS_X.ordinal()], 2.0F, 5);
            pushQuad(builders[ModelQuadFacing.NEG_Z.ordinal()], 4.0F, 7);

            long[] addresses = logicalAddresses(builders);
            int[] counts = vertexCounts(builders);
            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            output = nativeOrder(MemoryUtil.memCalloc(12 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));

            NativeChunkMeshEncoder.assemble(addresses, counts, output, segments,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), 9,
                    (1 << ModelQuadFacing.POS_X.ordinal()) | (1 << ModelQuadFacing.NEG_Z.ordinal()),
                    false, true, false);

            assertArrayEquals(new int[] {
                    4, ModelQuadFacing.UNASSIGNED.ordinal(),
                    4, ModelQuadFacing.POS_X.ordinal(),
                    4, ModelQuadFacing.NEG_Z.ordinal(),
                    0, ModelQuadFacing.POS_Y.ordinal(),
                    0, ModelQuadFacing.POS_Z.ordinal(),
                    0, ModelQuadFacing.NEG_X.ordinal(),
                    0, ModelQuadFacing.NEG_Y.ordinal(),
            }, segments);

            assertCompactVertex(output, 0, 0.0F, 3, 9);
            assertCompactVertex(output, 4, 2.0F, 5, 9);
            assertCompactVertex(output, 8, 4.0F, 7, 9);
        } finally {
            free(output);
            destroy(builders);
        }
    }

    @Test
    void assemblesMeshAndSharedIndexBufferInOneNativeCall() {
        ChunkMeshBufferBuilder[] builders = makeBuilders(ChunkMeshFormats.COMPACT, 16, 4);
        ByteBuffer output = null;
        ByteBuffer indexOutput = null;

        try {
            pushQuad(builders[ModelQuadFacing.UNASSIGNED.ordinal()], 0.0F, 3);
            pushQuad(builders[ModelQuadFacing.POS_X.ordinal()], 2.0F, 5);

            long[] addresses = logicalAddresses(builders);
            int[] counts = vertexCounts(builders);
            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            output = nativeOrder(MemoryUtil.memCalloc(8 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            indexOutput = nativeOrder(MemoryUtil.memCalloc(2 * 6 * Integer.BYTES));

            NativeChunkMeshEncoder.assembleWithSharedIndex(addresses, counts, output, segments,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), 4, 1 << ModelQuadFacing.POS_X.ordinal(),
                    false, true, false, indexOutput, Integer.BYTES);

            assertCompactVertex(output, 0, 0.0F, 3, 4);
            assertCompactVertex(output, 4, 2.0F, 5, 4);
            assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4}, readInts(indexOutput, 12));
        } finally {
            free(output);
            free(indexOutput);
            destroy(builders);
        }
    }

    @Test
    void nativeSectionBuilderOwnsPerFacingStagingAndAssembly() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ChunkMeshBufferBuilder posX = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT.getNativeFormat(),
                sectionBuilder, ModelQuadFacing.POS_X.ordinal());
        ChunkMeshBufferBuilder unassigned = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT.getNativeFormat(),
                sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());
        ByteBuffer output = null;

        try {
            sectionBuilder.start(5);
            posX.start(5);
            unassigned.start(5);
            posX.push(quad(2.0F, 5), 5);
            unassigned.push(quad(0.0F, 3), 3);

            assertEquals(4, posX.count());
            assertEquals(4, unassigned.count());
            assertEquals(8, sectionBuilder.totalVertexCount());

            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            output = nativeOrder(MemoryUtil.memCalloc(8 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.POS_X.ordinal(), false, true, false);

            assertArrayEquals(new int[] {
                    4, ModelQuadFacing.UNASSIGNED.ordinal(),
                    4, ModelQuadFacing.POS_X.ordinal(),
                    0, ModelQuadFacing.POS_Y.ordinal(),
                    0, ModelQuadFacing.POS_Z.ordinal(),
                    0, ModelQuadFacing.NEG_X.ordinal(),
                    0, ModelQuadFacing.NEG_Y.ordinal(),
                    0, ModelQuadFacing.NEG_Z.ordinal(),
            }, segments);
            assertCompactVertex(output, 0, 0.0F, 3, 5);
            assertCompactVertex(output, 4, 2.0F, 5, 5);
        } finally {
            free(output);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderFinishesMeshParts() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ChunkMeshBufferBuilder posX = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT.getNativeFormat(),
                sectionBuilder, ModelQuadFacing.POS_X.ordinal());
        ChunkMeshBufferBuilder unassigned = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT.getNativeFormat(),
                sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());

        try {
            sectionBuilder.start(5);
            posX.start(5);
            unassigned.start(5);
            posX.push(quad(2.0F, 5), 5);
            unassigned.push(quad(0.0F, 3), 3);
            posX.flushPending();
            unassigned.flushPending();

            BuiltSectionMeshParts mesh = sectionBuilder.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.POS_X.ordinal(), false, true, false);
            assertNotNull(mesh);

            try {
                assertArrayEquals(new int[] {
                        4, ModelQuadFacing.UNASSIGNED.ordinal(),
                        4, ModelQuadFacing.POS_X.ordinal(),
                        0, ModelQuadFacing.POS_Y.ordinal(),
                        0, ModelQuadFacing.POS_Z.ordinal(),
                        0, ModelQuadFacing.NEG_X.ordinal(),
                        0, ModelQuadFacing.NEG_Y.ordinal(),
                        0, ModelQuadFacing.NEG_Z.ordinal(),
                }, mesh.getVertexSegments());
                ByteBuffer output = mesh.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
                assertCompactVertex(output, 0, 0.0F, 3, 5);
                assertCompactVertex(output, 4, 2.0F, 5, 5);
            } finally {
                mesh.getVertexData().free();
            }
        } finally {
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderFinishesModifiedTranslucentMeshParts() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ChunkMeshBufferBuilder unassigned = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT.getNativeFormat(),
                sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());

        try {
            sectionBuilder.start(12);
            unassigned.start(12);
            for (int index = 0; index < 4; index++) {
                unassigned.push(quad(index * 2.0F, 99), DefaultMaterials.TRANSLUCENT);
            }
            unassigned.flushPending();

            UpdatedQuadsList updates = new UpdatedQuadsList();
            FullTQuad skipped = FullTQuad.fromVertices(quad(1.0F, 99), ModelQuadFacing.UNASSIGNED, 0);
            FullTQuad first = FullTQuad.fromVertices(quad(3.0F, 13), ModelQuadFacing.UNASSIGNED, 0);
            FullTQuad second = FullTQuad.fromVertices(quad(5.0F, 17), ModelQuadFacing.UNASSIGNED, 0);
            skipped.setNoWrite();
            first.setWriteToIndex(1);
            second.setWriteToIndex(3);
            updates.add(skipped);
            updates.add(first);
            updates.add(second);
            updates.setQuadCounts(4, 4);

            BuiltSectionMeshParts mesh = sectionBuilder.finishModifiedTranslucentMesh(updates,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), false);

            try {
                assertArrayEquals(new int[] {
                        0, 0,
                        0, 0,
                        0, 0,
                        0, 0,
                        0, 0,
                        0, 0,
                        16, ModelQuadFacing.UNASSIGNED.ordinal(),
                }, mesh.getVertexSegments());
                ByteBuffer output = mesh.getVertexData().getDirectBuffer().order(ByteOrder.nativeOrder());
                assertCompactVertex(output, 4, 3.0F, DefaultMaterials.TRANSLUCENT.bits(), 12);
                assertCompactVertex(output, 12, 5.0F, DefaultMaterials.TRANSLUCENT.bits(), 12);
            } finally {
                mesh.getVertexData().free();
            }
        } finally {
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderAppendsFilteredQuadBatches() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ByteBuffer batch = null;
        ByteBuffer validity = null;

        try {
            sectionBuilder.start(5);
            batch = nativeOrder(MemoryUtil.memAlloc(2 * NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE));
            validity = MemoryUtil.memAlloc(2);

            NativeChunkMeshEncoder.writeNativeQuad(MemoryUtil.memAddress(batch), quad(2.0F, 5), 5);
            NativeChunkMeshEncoder.writeNativeQuad(MemoryUtil.memAddress(batch) + NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    quad(4.0F, 7), 7);
            validity.put(0, (byte) 1);
            validity.put(1, (byte) 0);

            assertEquals(1, sectionBuilder.appendBatchFiltered(ModelQuadFacing.POS_X.ordinal(),
                    MemoryUtil.memAddress(batch), 2, MemoryUtil.memAddress(validity)));
            assertEquals(4, sectionBuilder.totalVertexCount());
            assertEquals(4, sectionBuilder.facingVertexCount(ModelQuadFacing.POS_X.ordinal()));
        } finally {
            free(batch);
            free(validity);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderOwnsReusableStagingBuffers() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);

        try {
            sectionBuilder.start(5);
            NativeSectionMeshBuilder.StagingBuffers staging =
                    sectionBuilder.stagingBuffers(ModelQuadFacing.POS_X.ordinal());

            assertEquals(256, staging.capacity());
            assertNotEquals(0, staging.quadAddress());
            assertNotEquals(0, staging.packedNormalsAddress());
            assertNotEquals(0, staging.validityAddress());

            NativeChunkMeshEncoder.writeNativeQuad(staging.quadAddress(), quad(2.0F, 5), 5);

            assertEquals(1, sectionBuilder.appendBatch(ModelQuadFacing.POS_X.ordinal(),
                    staging.quadAddress(), 1));
            assertEquals(4, sectionBuilder.totalVertexCount());
            assertEquals(4, sectionBuilder.facingVertexCount(ModelQuadFacing.POS_X.ordinal()));
        } finally {
            sectionBuilder.close();
        }
    }

    @Test
    void chunkMeshHotPathUsesRustOwnedStagingAndBuilderFinalizers() throws Exception {
        String chunkMeshBufferBuilder = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder.java"));
        String updatedQuadsList = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/UpdatedQuadsList.java"));
        String chunkBuildBuffers = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/compile/ChunkBuildBuffers.java"));

        assertArrayEquals(new String[] {
                "sectionBuilder.stagingBuffers(",
                "appendTranslucentBatch(",
        }, new String[] {
                contains(chunkMeshBufferBuilder, "sectionBuilder.stagingBuffers("),
                contains(chunkMeshBufferBuilder, "appendTranslucentBatch("),
        });
        assertEquals("encodeScatteredUnassigned(",
                contains(updatedQuadsList, "encodeScatteredUnassigned("));
        org.junit.jupiter.api.Assertions.assertFalse(chunkMeshBufferBuilder.contains("memAlloc("));
        org.junit.jupiter.api.Assertions.assertFalse(chunkMeshBufferBuilder.contains("memFree("));
        org.junit.jupiter.api.Assertions.assertFalse(updatedQuadsList.contains("NativeChunkMeshEncoder.encodeScattered("));
        assertEquals("finishMesh(", contains(chunkBuildBuffers, "finishMesh("));
        assertEquals("finishModifiedTranslucentMesh(",
                contains(chunkBuildBuffers, "finishModifiedTranslucentMesh("));
        org.junit.jupiter.api.Assertions.assertFalse(chunkBuildBuffers.contains("new NativeBuffer"));
        org.junit.jupiter.api.Assertions.assertFalse(chunkBuildBuffers.contains("totalVertexCount("));
        org.junit.jupiter.api.Assertions.assertFalse(chunkBuildBuffers.contains(".assemble("));
    }


    @Test
    void writesXhfpExtendedAttributesNatively() {
        ChunkVertexType vertexType = FormatAnalyzer.createFormat(true, true, true, true);
        NativeChunkVertexFormat format = vertexType.getNativeFormat();
        ChunkMeshBufferBuilder builder = new ChunkMeshBufferBuilder(vertexType, 16);
        ByteBuffer output = null;

        try {
            builder.start(6);
            builder.push(quad(0.25F, 11), DefaultMaterials.SOLID);

            output = nativeOrder(MemoryUtil.memCalloc(4 * format.stride()));
            int[] segments = new int[ModelQuadFacing.COUNT << 1];

            NativeChunkMeshEncoder.assemble(
                    new long[] {0, 0, 0, 0, 0, 0, builder.logicalAddress()},
                    new int[] {0, 0, 0, 0, 0, 0, builder.count()},
                    output, segments, format, builder.sectionIndex(), ModelQuadFacing.ALL, false, false, false);

            assertEquals(4, segments[ModelQuadFacing.UNASSIGNED.ordinal() << 1]);
            assertEquals(ModelQuadFacing.UNASSIGNED.ordinal(), segments[(ModelQuadFacing.UNASSIGNED.ordinal() << 1) + 1]);
            assertEquals(((41 + 1) << 1) | 1, output.getInt(format.blockIdOffset()));
            assertEquals(encodeOld(0.5F, 0.5F), output.getInt(format.midUvOffset()));
            assertEquals((7 << 24) | packMidBlock(4.5F - 0.25F, 5.5F - 0.25F, 6.5F - 0.25F),
                    output.getInt(format.midBlockOffset()));
            assertNotEquals(0, output.getInt(format.normalOffset()));
            assertNotEquals(0, output.getInt(format.tangentOffset()));
        } finally {
            free(output);
            builder.destroy();
        }
    }

    @Test
    void modifiedTranslucentUpdatesUseBatchedScatteredNativeEncoding() {
        ChunkMeshBufferBuilder builder = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT, 16);
        ByteBuffer output = null;

        try {
            builder.start(12);
            output = nativeOrder(MemoryUtil.memCalloc(16 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));

            UpdatedQuadsList updates = new UpdatedQuadsList();
            FullTQuad skipped = FullTQuad.fromVertices(quad(1.0F, 99), ModelQuadFacing.UNASSIGNED, 0);
            FullTQuad first = FullTQuad.fromVertices(quad(3.0F, 13), ModelQuadFacing.UNASSIGNED, 0);
            FullTQuad second = FullTQuad.fromVertices(quad(5.0F, 17), ModelQuadFacing.UNASSIGNED, 0);

            skipped.setNoWrite();
            first.setWriteToIndex(1);
            second.setWriteToIndex(3);

            updates.add(skipped);
            updates.add(first);
            updates.add(second);
            updates.applyBufferUpdates(builder, output);

            assertEquals(0, output.getInt(0));
            assertCompactVertex(output, 4, 3.0F, DefaultMaterials.TRANSLUCENT.bits(), 12);
            assertEquals(0, output.getInt(8 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            assertCompactVertex(output, 12, 5.0F, DefaultMaterials.TRANSLUCENT.bits(), 12);
        } finally {
            free(output);
            builder.destroy();
        }
    }

    private static ChunkMeshBufferBuilder[] makeBuilders(ChunkVertexType vertexType, int capacity, int sectionIndex) {
        ChunkMeshBufferBuilder[] builders = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

        for (int i = 0; i < builders.length; i++) {
            builders[i] = new ChunkMeshBufferBuilder(vertexType, capacity);
            builders[i].start(sectionIndex);
        }

        return builders;
    }

    private static void pushQuad(ChunkMeshBufferBuilder builder, float baseX, int materialBits) {
        builder.push(quad(baseX, materialBits), materialBits);
    }

    private static String contains(String text, String expected) {
        org.junit.jupiter.api.Assertions.assertTrue(text.contains(expected));
        return expected;
    }

    private static ChunkVertexEncoder.Vertex[] quad(float baseX, int seed) {
        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        writeVertex(vertices[0], baseX, 0.25F, 0.25F, 0.0F, 0.0F, seed);
        writeVertex(vertices[1], baseX + 1.0F, 0.25F, 0.25F, 1.0F, 0.0F, seed);
        writeVertex(vertices[2], baseX + 1.0F, 1.25F, 0.25F, 1.0F, 1.0F, seed);
        writeVertex(vertices[3], baseX, 1.25F, 0.25F, 0.0F, 1.0F, seed);
        return vertices;
    }

    private static void writeVertex(ChunkVertexEncoder.Vertex vertex, float x, float y, float z, float u, float v, int seed) {
        ChunkVertexEncoder.Vertex.writeVertex(vertex, x, y, z, 0xff806040, 0.5F, u, v, 0x00f000f0);
        vertex.iris$setData((byte) 7, (byte) 1, 41, 4, 5, 6);
    }

    private static long[] logicalAddresses(ChunkMeshBufferBuilder[] builders) {
        long[] addresses = new long[ModelQuadFacing.COUNT];

        for (int i = 0; i < builders.length; i++) {
            addresses[i] = builders[i].logicalAddress();
        }

        return addresses;
    }

    private static int[] vertexCounts(ChunkMeshBufferBuilder[] builders) {
        int[] counts = new int[ModelQuadFacing.COUNT];

        for (int i = 0; i < builders.length; i++) {
            counts[i] = builders[i].count();
        }

        return counts;
    }

    private static void assertCompactVertex(ByteBuffer buffer, int vertexIndex, float x, int materialBits, int sectionIndex) {
        int offset = vertexIndex * ChunkMeshFormats.COMPACT.getNativeFormat().stride();
        int packedX = quantizePosition(x);
        int packedY = quantizePosition(0.25F);
        int packedZ = quantizePosition(0.25F);

        assertEquals(packPositionHi(packedX, packedY, packedZ), buffer.getInt(offset));
        assertEquals(packPositionLo(packedX, packedY, packedZ), buffer.getInt(offset + 4));
        assertEquals(packLightAndData(0xf0f0, materialBits, sectionIndex), buffer.getInt(offset + 16));
    }

    private static int quantizePosition(float position) {
        return ((int) (((8.0F + position) / 32.0F) * (1 << 20))) & 0xFFFFF;
    }

    private static int packPositionHi(int x, int y, int z) {
        return (((x >>> 10) & 0x3FF) << 0) | (((y >>> 10) & 0x3FF) << 10) | (((z >>> 10) & 0x3FF) << 20);
    }

    private static int packPositionLo(int x, int y, int z) {
        return ((x & 0x3FF) << 0) | ((y & 0x3FF) << 10) | ((z & 0x3FF) << 20);
    }

    private static int packLightAndData(int light, int material, int section) {
        return ((light & 0xFFFF) << 0) | ((material & 0xFF) << 16) | ((section & 0xFF) << 24);
    }

    private static int encodeOld(float u, float v) {
        return ((Math.round(u * (1 << 15)) & 0xFFFF) << 0) | ((Math.round(v * (1 << 15)) & 0xFFFF) << 16);
    }

    private static int packMidBlock(float x, float y, float z) {
        return (((int) (x * 64.0F)) & 0xFF)
                | ((((int) (y * 64.0F)) & 0xFF) << 8)
                | ((((int) (z * 64.0F)) & 0xFF) << 16);
    }

    private static ByteBuffer nativeOrder(ByteBuffer buffer) {
        return buffer.order(ByteOrder.nativeOrder());
    }

    private static int[] readInts(ByteBuffer buffer, int count) {
        int[] values = new int[count];

        for (int index = 0; index < count; index++) {
            values[index] = buffer.getInt(index * Integer.BYTES);
        }

        return values;
    }

    private static void destroy(ChunkMeshBufferBuilder[] builders) {
        for (ChunkMeshBufferBuilder builder : builders) {
            if (builder != null) {
                builder.destroy();
            }
        }
    }

    private static void free(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }
}
