package net.sodium.client.render.chunk.vertex;

import net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.NativeUpdatedQuads;
import net.sodium.client.render.chunk.translucent_sorting.quad.NativeFullTQuad;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void compactChunkVertexClassWasMigratedToRust() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("net.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex"));
    }

    @Test
    void compactChunkVertexFormatMetadataComesFromRust() {
        NativeChunkVertexFormat format = ChunkMeshFormats.COMPACT.getNativeFormat();

        assertEquals(20, format.stride());
        assertEquals(0, format.blockIdOffset());
        assertEquals(0, format.normalOffset());
        assertEquals(0, format.tangentOffset());
        assertEquals(0, format.midUvOffset());
        assertEquals(0, format.midBlockOffset());
        assertEquals(1 << 15, ChunkMeshFormats.COMPACT_TEXTURE_MAX_VALUE);
        assertEquals(1 << 20, ChunkMeshFormats.COMPACT_POSITION_MAX_VALUE);
        assertEquals(20, ChunkMeshFormats.COMPACT.getVertexFormat().getStride());
    }

    @Test
    void assemblesCompactQuadsInSliceOrder() {
        NativeSectionMeshBuilder.FacingBuffer[] builders = makeBuilders(ChunkMeshFormats.COMPACT, 16, 9);
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
        NativeSectionMeshBuilder.FacingBuffer[] builders = makeBuilders(ChunkMeshFormats.COMPACT, 16, 4);
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
        NativeSectionMeshBuilder.FacingBuffer posX = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.POS_X.ordinal());
        NativeSectionMeshBuilder.FacingBuffer unassigned = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());
        ByteBuffer output = null;

        try {
            sectionBuilder.start(5);
            posX.start(5);
            unassigned.start(5);
            pushQuad(posX, 2.0F, 5);
            pushQuad(unassigned, 0.0F, 3);

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
        NativeSectionMeshBuilder.FacingBuffer posX = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.POS_X.ordinal());
        NativeSectionMeshBuilder.FacingBuffer unassigned = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());

        try {
            sectionBuilder.start(5);
            posX.start(5);
            unassigned.start(5);
            pushQuad(posX, 2.0F, 5);
            pushQuad(unassigned, 0.0F, 3);
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
        NativeSectionMeshBuilder.FacingBuffer unassigned = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());

        try {
            sectionBuilder.start(12);
            unassigned.start(12);
            for (int index = 0; index < 4; index++) {
                pushQuad(unassigned, index * 2.0F, DefaultMaterials.TRANSLUCENT.bits());
            }
            unassigned.flushPending();

            NativeUpdatedQuads updates = new NativeUpdatedQuads();
            NativeFullTQuad skipped = nativeFullQuad(1.0F, 99);
            NativeFullTQuad first = nativeFullQuad(3.0F, 13);
            NativeFullTQuad second = nativeFullQuad(5.0F, 17);
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

            writeQuad(MemoryUtil.memAddress(batch), 2.0F, 5, 5);
            writeQuad(MemoryUtil.memAddress(batch) + NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, 4.0F, 7, 7);
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

            writeQuad(staging.quadAddress(), 2.0F, 5, 5);

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
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder.java")));
        String nativeSectionMeshBuilder = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/vertex/format/NativeSectionMeshBuilder.java"));
        String updatedQuadsList = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeUpdatedQuads.java"));
        String chunkBuildBuffers = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/compile/ChunkBuildBuffers.java"));

        assertArrayEquals(new String[] {
                "sectionBuilder.stagingBuffers(",
                "appendTranslucentBatchEncoded(",
        }, new String[] {
                contains(nativeSectionMeshBuilder, "sectionBuilder.stagingBuffers("),
                contains(nativeSectionMeshBuilder, "appendTranslucentBatchEncoded("),
        });
        assertEquals("mattmc_sodium_updated_quads_apply",
                contains(updatedQuadsList, "mattmc_sodium_updated_quads_apply"));
        org.junit.jupiter.api.Assertions.assertFalse(nativeSectionMeshBuilder.contains("memAlloc("));
        org.junit.jupiter.api.Assertions.assertFalse(nativeSectionMeshBuilder.contains("memFree("));
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
        NativeSectionMeshBuilder.FacingBuffer builder = NativeSectionMeshBuilder.createFacingBuffer(vertexType, 16);
        ByteBuffer output = null;

        try {
            builder.start(6);
            pushQuad(builder, 0.25F, DefaultMaterials.SOLID.bits());

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
        NativeSectionMeshBuilder.FacingBuffer builder =
                NativeSectionMeshBuilder.createFacingBuffer(ChunkMeshFormats.COMPACT, 16);
        ByteBuffer output = null;

        try {
            builder.start(12);
            output = nativeOrder(MemoryUtil.memCalloc(16 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));

            NativeUpdatedQuads updates = new NativeUpdatedQuads();
            NativeFullTQuad skipped = nativeFullQuad(1.0F, 99);
            NativeFullTQuad first = nativeFullQuad(3.0F, 13);
            NativeFullTQuad second = nativeFullQuad(5.0F, 17);

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

    private static NativeSectionMeshBuilder.FacingBuffer[] makeBuilders(ChunkVertexType vertexType, int capacity,
            int sectionIndex) {
        NativeSectionMeshBuilder.FacingBuffer[] builders =
                new NativeSectionMeshBuilder.FacingBuffer[ModelQuadFacing.COUNT];

        for (int i = 0; i < builders.length; i++) {
            builders[i] = NativeSectionMeshBuilder.createFacingBuffer(vertexType, capacity);
            builders[i].start(sectionIndex);
        }

        return builders;
    }

    private static void pushQuad(NativeSectionMeshBuilder.FacingBuffer builder, float baseX, int materialBits) {
        long quadAddress = builder.prepareStagedQuad(materialBits, (byte) 7, (byte) 1, false, 41, 4, 5, 6);
        writeQuad(quadAddress, baseX, 7, materialBits);
        builder.commitStagedQuad();
    }

    private static String contains(String text, String expected) {
        org.junit.jupiter.api.Assertions.assertTrue(text.contains(expected));
        return expected;
    }

    private static NativeFullTQuad nativeFullQuad(float baseX, int seed) {
        ByteBuffer buffer = nativeOrder(MemoryUtil.memAlloc(NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE));
        try {
            writeQuad(MemoryUtil.memAddress(buffer), baseX, seed, DefaultMaterials.TRANSLUCENT.bits());
            return NativeFullTQuad.fromNativeQuad(MemoryUtil.memAddress(buffer), ModelQuadFacing.UNASSIGNED, 0);
        } finally {
            free(buffer);
        }
    }

    private static void writeQuad(long quadAddress, float baseX, int seed, int materialBits) {
        NativeChunkMeshEncoder.writeNativeQuad(quadAddress, (byte) 7, (byte) 1, false, 41, 4, 5, 6, materialBits,
                baseX, 0.25F, 0.25F, 0xff806040, 0.5F, 0.0F, 0.0F, 0x00f000f0,
                baseX + 1.0F, 0.25F, 0.25F, 0xff806040, 0.5F, 1.0F, 0.0F, 0x00f000f0,
                baseX + 1.0F, 1.25F, 0.25F, 0xff806040, 0.5F, 1.0F, 1.0F, 0x00f000f0,
                baseX, 1.25F, 0.25F, 0xff806040, 0.5F, 0.0F, 1.0F, 0x00f000f0);
    }

    private static long[] logicalAddresses(NativeSectionMeshBuilder.FacingBuffer[] builders) {
        long[] addresses = new long[ModelQuadFacing.COUNT];

        for (int i = 0; i < builders.length; i++) {
            addresses[i] = builders[i].logicalAddress();
        }

        return addresses;
    }

    private static int[] vertexCounts(NativeSectionMeshBuilder.FacingBuffer[] builders) {
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

    private static void destroy(NativeSectionMeshBuilder.FacingBuffer[] builders) {
        for (NativeSectionMeshBuilder.FacingBuffer builder : builders) {
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
