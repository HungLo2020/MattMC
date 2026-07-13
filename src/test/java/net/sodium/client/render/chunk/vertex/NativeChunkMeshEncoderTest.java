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
import net.sodium.client.render.chunk.vertex.format.NativeStaticBlockModelCache;
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
    void nativeSectionBuilderAppendsFlatAndLightBlockBatches() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        NativeSectionMeshBuilder.FacingBuffer unassigned = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());
        ByteBuffer output = null;

        try {
            sectionBuilder.start(6);
            unassigned.start(6);
            unassigned.appendFlatQuad(5, (byte) 7, (byte) 1, false, 41, 4, 5, 6,
                    2.0F, 0.25F, 0.25F, 0xff806040, 0.5F, 0.0F, 0.0F, 0x00f000f0,
                    3.0F, 0.25F, 0.25F, 0xff806040, 0.5F, 1.0F, 0.0F, 0x00f000f0,
                    3.0F, 1.25F, 0.25F, 0xff806040, 0.5F, 1.0F, 1.0F, 0x00f000f0,
                    2.0F, 1.25F, 0.25F, 0xff806040, 0.5F, 0.0F, 1.0F, 0x00f000f0);
            unassigned.appendLightBlockQuad(7, (byte) 12, 99, 4, 0, 0);
            unassigned.flushPending();

            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            output = nativeOrder(MemoryUtil.memCalloc(8 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    0, false, true, false);

            assertEquals(8, sectionBuilder.totalVertexCount());
            assertCompactVertex(output, 0, 2.0F, 5, 6);
            assertCompactVertex(output, 4, 4.25F, 0.25F, 0.25F, 0xc0c0, 7, 6);
        } finally {
            free(output);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderAppendsSemanticFluidFaceBatches() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        NativeSectionMeshBuilder.FacingBuffer posZ = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.POS_Z.ordinal());
        ByteBuffer output = null;

        try {
            sectionBuilder.start(8);
            posZ.start(8);
            posZ.appendFluidFace(5, (byte) 7, (byte) 1, false, 41, 4, 5, 6,
                    3, false, 0, 2, 3, 4, 0.001F,
                    0.75F, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F,
                    0.0F, 0.2F, 0.5F, 0.6F, 1.0F, 0.6F, 1.0F, 0.1F,
                    0xff806040, 0xff806040, 0xff806040, 0xff806040,
                    0.5F, 0.5F, 0.5F, 0.5F,
                    0x00f000f0, 0x00f000f0, 0x00f000f0, 0x00f000f0);
            posZ.flushPending();

            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            output = nativeOrder(MemoryUtil.memCalloc(4 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.POS_Z.ordinal(), false, true, false);

            assertEquals(4, sectionBuilder.totalVertexCount());
            assertCompactVertex(output, 0, 3.0F, 3.5F, 5.0F, 0xf0f0, 5, 8);
            assertCompactVertex(output, 3, 2.0F, 3.75F, 5.0F, 0xf0f0, 5, 8);
        } finally {
            free(output);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderBuildsCachedStaticModelBlocks() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        NativeSectionMeshBuilder.FacingBuffer staging = new NativeSectionMeshBuilder.FacingBuffer(
                ChunkMeshFormats.COMPACT.getNativeFormat(), sectionBuilder, ModelQuadFacing.UNASSIGNED.ordinal());
        ByteBuffer output = null;

        try {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.register(77, (recordAddress, index) ->
                    NativeChunkMeshEncoder.writeStaticModelQuadRecord(recordAddress, 5,
                            net.minecraft.core.Direction.NORTH.get3DDataValue(), ModelQuadFacing.NEG_Z.ordinal(),
                            ModelQuadFacing.NEG_Z.getPackedAlignedNormal(), (byte) 7, (byte) 1, true,
                            0.0F, 0.0F, 0.0F, 0xff806040, 0.0F, 0.0F, 0x00f000f0,
                            1.0F, 0.0F, 0.0F, 0xff806040, 1.0F, 0.0F, 0x00f000f0,
                            1.0F, 1.0F, 0.0F, 0xff806040, 1.0F, 1.0F, 0x00f000f0,
                            0.0F, 1.0F, 0.0F, 0xff806040, 0.0F, 1.0F, 0x00f000f0), 1);

            sectionBuilder.start(3);
            staging.start(3);
            staging.appendStaticModelBlock(77, 0, (byte) 0, (byte) 0, 41, 4, 5, 6, 0,
                    0.25F, 0.0F, 0.5F);
            staging.flushPending();

            output = nativeOrder(MemoryUtil.memCalloc(4 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.NEG_Z.ordinal(), false, true, false);

            assertEquals(4, sectionBuilder.totalVertexCount());
            assertCompactVertex(output, 0, 4.25F, 5.0F, 6.5F, 0xf0f0, 5, 3);
            assertSegmentPresent(segments, 4, ModelQuadFacing.NEG_Z.ordinal());

            sectionBuilder.start(3);
            staging.start(3);
            staging.appendStaticModelBlock(77, 0, (byte) 0, (byte) 0, 41, 4, 5, 6,
                    1 << net.minecraft.core.Direction.NORTH.get3DDataValue(), 0.0F, 0.0F, 0.0F);
            staging.flushPending();
            assertEquals(0, sectionBuilder.totalVertexCount());
        } finally {
            NativeStaticBlockModelCache.clear();
            free(output);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderScansStaticSectionSnapshotRecords() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ByteBuffer records = null;

        try {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.register(77, (recordAddress, index) ->
                    NativeChunkMeshEncoder.writeStaticModelQuadRecord(recordAddress, 5,
                            net.minecraft.core.Direction.NORTH.get3DDataValue(), ModelQuadFacing.NEG_Z.ordinal(),
                            ModelQuadFacing.NEG_Z.getPackedAlignedNormal(), (byte) 0, (byte) 0, true,
                            0.0F, 0.0F, 0.0F, 0xffffffff, 0.0F, 0.0F, 0x00f000f0,
                            1.0F, 0.0F, 0.0F, 0xffffffff, 1.0F, 0.0F, 0x00f000f0,
                            1.0F, 1.0F, 0.0F, 0xffffffff, 1.0F, 1.0F, 0x00f000f0,
                            0.0F, 1.0F, 0.0F, 0xffffffff, 0.0F, 1.0F, 0x00f000f0), 1);

            records = MemoryUtil.memAlloc(3 * NativeChunkMeshEncoder.STATIC_MODEL_BLOCK_RECORD_STRIDE);
            long base = MemoryUtil.memAddress(records);
            NativeChunkMeshEncoder.writeStaticModelBlockRecord(base, -1, 0, (byte) 0, (byte) 0,
                    -1, 0, 0, 0, 0, 0.0F, 0.0F, 0.0F);
            NativeChunkMeshEncoder.writeStaticModelBlockRecord(base + NativeChunkMeshEncoder.STATIC_MODEL_BLOCK_RECORD_STRIDE,
                    -2, 5, (byte) 15, (byte) 0, 99, 1, 2, 3, 0, 0.0F, 0.0F, 0.0F);
            NativeChunkMeshEncoder.writeStaticModelBlockRecord(base + 2L * NativeChunkMeshEncoder.STATIC_MODEL_BLOCK_RECORD_STRIDE,
                    77, 5, (byte) 0, (byte) 0, 41, 4, 5, 6, 0, 0.0F, 0.0F, 0.0F);

            sectionBuilder.start(3);
            int committed = sectionBuilder.appendStaticModelBatchEncoded(base, 3, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    3, false, false);

            assertEquals(2, committed);
            assertEquals(8, sectionBuilder.totalVertexCount());
        } finally {
            NativeStaticBlockModelCache.clear();
            free(records);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderScansSerializableSectionPath() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ByteBuffer records = null;
        ByteBuffer output = null;

        try {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.register(77, (recordAddress, index) ->
                    NativeChunkMeshEncoder.writeStaticModelQuadRecord(recordAddress, 5,
                            net.minecraft.core.Direction.NORTH.get3DDataValue(), ModelQuadFacing.NEG_Z.ordinal(),
                            ModelQuadFacing.NEG_Z.getPackedAlignedNormal(), (byte) 0, (byte) 0, true,
                            0.0F, 0.0F, 0.0F, 0xffffffff, 0.0F, 0.0F, -1,
                            1.0F, 0.0F, 0.0F, 0xffffffff, 1.0F, 0.0F, -1,
                            1.0F, 1.0F, 0.0F, 0xffffffff, 1.0F, 1.0F, -1,
                            0.0F, 1.0F, 0.0F, 0xffffffff, 0.0F, 1.0F, -1), 1);
            NativeStaticBlockModelCache.registerSelector(8, 0,
                    (recordAddress, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, 77, 1),
                    1);
            NativeStaticBlockModelCache.registerSelector(9, 1,
                    (recordAddress, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, 8, 1),
                    1);
            NativeStaticBlockModelCache.registerState(0, -1, 1, 0, -1, 0, 0, -1, 0, -1, -1, 0);
            NativeStaticBlockModelCache.registerState(100, 9, 1 << 1, 5, 0, 0, 0, 41, 0, -1, -1, 1);

            records = MemoryUtil.memAlloc(NativeChunkMeshEncoder.LEGACY_NATIVE_SECTION_BLOCK_RECORD_STRIDE);
            long base = MemoryUtil.memAddress(records);
            NativeChunkMeshEncoder.writeLegacyNativeSectionBlockRecord(base, 100, 41, 4, 5, 6, 1234L,
                    0, 0, 0, 0, 0, 0, 0x00f000f0, 0.25F, 0.0F, 0.5F);

            sectionBuilder.start(3);
            int committed = sectionBuilder.appendLegacyNativeSectionRecordsEncoded(base, 1, 0,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), 3, false, false);

            assertEquals(1, committed);
            assertEquals(4, sectionBuilder.totalVertexCount());

            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            output = nativeOrder(MemoryUtil.memCalloc(4 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.NEG_Z.ordinal(), false, true, false);
            assertCompactVertex(output, 0, 4.25F, 5.0F, 6.5F, 0xf8f8, 5, 3);
            assertSegmentPresent(segments, 4, ModelQuadFacing.NEG_Z.ordinal());
        } finally {
            NativeStaticBlockModelCache.clear();
            free(records);
            free(output);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderIgnoresZeroedAirSectionSnapshot() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ByteBuffer records = null;

        try {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.registerState(0, -1, 1, 0, -1, 0, 0, -1, 0, -1, -1, 0);

            records = MemoryUtil.memCalloc(4096 * NativeChunkMeshEncoder.LEGACY_NATIVE_SECTION_BLOCK_RECORD_STRIDE);
            long base = MemoryUtil.memAddress(records);

            for (int pass = 0; pass < 3; pass++) {
                sectionBuilder.start(3);
                assertEquals(0, sectionBuilder.appendLegacyNativeSectionRecordsEncoded(base, 4096, pass,
                        ChunkMeshFormats.COMPACT.getNativeFormat(), 3, false, false));
                assertEquals(0, sectionBuilder.totalVertexCount());
            }
        } finally {
            NativeStaticBlockModelCache.clear();
            free(records);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderAppliesNativeTintAndRustOffset() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ByteBuffer records = null;
        ByteBuffer output = null;

        try {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.register(88, (recordAddress, index) ->
                    NativeChunkMeshEncoder.writeStaticModelQuadRecord(recordAddress, 5,
                            -1, ModelQuadFacing.UNASSIGNED.ordinal(), ModelQuadFacing.POS_Y.getPackedAlignedNormal(),
                            (byte) 0, (byte) 0, true, 0, net.minecraft.core.Direction.UP.get3DDataValue(), 0, false,
                            0.0F, 0.0F, 0.0F, 0xffffffff, 0.0F, 0.0F, -1,
                            1.0F, 0.0F, 0.0F, 0xffffffff, 1.0F, 0.0F, -1,
                            1.0F, 1.0F, 0.0F, 0xffffffff, 1.0F, 1.0F, -1,
                            0.0F, 1.0F, 0.0F, 0xffffffff, 0.0F, 1.0F, -1), 1);
            NativeStaticBlockModelCache.registerSelector(18, 0,
                    (recordAddress, index) -> NativeChunkMeshEncoder.writeNativeModelSelectorEntry(recordAddress, 88, 1),
                    1);
            NativeStaticBlockModelCache.registerState(0, -1, 1, 0, -1, 0, 0, -1, 0, -1, -1, 0);
            NativeStaticBlockModelCache.registerState(120, 18, 1 << 1, 5, 0, 0, 0, 41,
                    0, -1, -1, 1, 0, 0.0F, 0, 1, 0.25F, 0.2F, 5);

            records = MemoryUtil.memAlloc(NativeChunkMeshEncoder.LEGACY_NATIVE_SECTION_BLOCK_RECORD_STRIDE);
            int[] lightWords = fullBrightLightWords();
            int[] states = neighborhoodStates(0);
            states[13] = 120;
            long base = MemoryUtil.memAddress(records);
            NativeChunkMeshEncoder.writeLegacyNativeSectionBlockRecord(base, 120, 41, 4, 5, 6, 1234L,
                    0, 0, 0, 0, 0, 0, lightWords, states, 0xff204080, -1,
                    0.0F, 0.0F, 20, 64, 30);

            sectionBuilder.start(3);
            assertEquals(1, sectionBuilder.appendLegacyNativeSectionRecordsEncoded(base, 1, 0,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), 3, false, false));

            output = nativeOrder(MemoryUtil.memCalloc(4 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.UNASSIGNED.ordinal(), false, true, false);

            int unoffsetX = quantizePosition(4.0F);
            int unoffsetY = quantizePosition(5.0F);
            int unoffsetZ = quantizePosition(6.0F);
            assertNotEquals(packPositionHi(unoffsetX, unoffsetY, unoffsetZ), output.getInt(0));
            assertEquals(packLightAndData(0xf8f8, 5, 3), output.getInt(16));
            assertEquals(0xff804020, output.getInt(8));
        } finally {
            NativeStaticBlockModelCache.clear();
            free(records);
            free(output);
            sectionBuilder.close();
        }
    }

    @Test
    void nativeSectionBuilderProducesFullBuiltInFluidGeometry() {
        NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(1);
        ByteBuffer records = null;
        ByteBuffer output = null;

        try {
            NativeStaticBlockModelCache.clear();
            NativeStaticBlockModelCache.registerState(0, -1, 1, 0, -1, 0, 0, -1, 0, -1, -1, 0);
            NativeStaticBlockModelCache.registerState(200, -1, 1 << 2, 5, -1, 0, 0, 77,
                    5, 2, 77, 1, 1, 0.875F, 0, 0, 0.25F, 0.2F, 3,
                    0.125F, 0.625F, 0.25F, 0.75F, 0.0F,
                    0.25F, 0.75F, 0.125F, 0.625F, 0.0F,
                    0.5F, 1.0F, 0.5F, 1.0F, 0.0F, 1);

            records = MemoryUtil.memAlloc(NativeChunkMeshEncoder.LEGACY_NATIVE_SECTION_BLOCK_RECORD_STRIDE);
            int[] lightWords = fullBrightLightWords();
            int[] states = neighborhoodStates(0);
            states[13] = 200;
            NativeChunkMeshEncoder.writeLegacyNativeSectionBlockRecord(MemoryUtil.memAddress(records), 200, 77,
                    8, 8, 8, 99L, 0, 0, 0, 0, 0, 0, lightWords, states,
                    -1, 0xff3f76e4, 0.0F, 0.0F, 8, 64, 8);

            sectionBuilder.start(3);
            int committed = sectionBuilder.appendLegacyNativeSectionRecordsEncoded(MemoryUtil.memAddress(records), 1, 2,
                    ChunkMeshFormats.COMPACT.getNativeFormat(), 3, false, false);

            assertEquals(11, committed);
            assertEquals(44, sectionBuilder.totalVertexCount());

            output = nativeOrder(MemoryUtil.memCalloc(44 * ChunkMeshFormats.COMPACT.getNativeFormat().stride()));
            int[] segments = new int[ModelQuadFacing.COUNT << 1];
            sectionBuilder.assemble(output, segments, ChunkMeshFormats.COMPACT.getNativeFormat(),
                    1 << ModelQuadFacing.POS_Y.ordinal(), false, true, false);
            assertEquals(packTextureForQuad(0.625F, 0.25F,
                    0.625F, 0.25F,
                    0.125F, 0.25F,
                    0.125F, 0.75F,
                    0.625F, 0.75F), output.getInt(12));
        } finally {
            NativeStaticBlockModelCache.clear();
            free(records);
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
        assertCompactVertex(buffer, vertexIndex, x, 0.25F, 0.25F, 0xf0f0, materialBits, sectionIndex);
    }

    private static void assertCompactVertex(ByteBuffer buffer, int vertexIndex, float x, float y, float z,
            int light, int materialBits, int sectionIndex) {
        int offset = vertexIndex * ChunkMeshFormats.COMPACT.getNativeFormat().stride();
        int packedX = quantizePosition(x);
        int packedY = quantizePosition(y);
        int packedZ = quantizePosition(z);

        assertEquals(packPositionHi(packedX, packedY, packedZ), buffer.getInt(offset));
        assertEquals(packPositionLo(packedX, packedY, packedZ), buffer.getInt(offset + 4));
        assertEquals(packLightAndData(light, materialBits, sectionIndex), buffer.getInt(offset + 16));
    }

    private static int quantizePosition(float position) {
        return ((int) (((8.0F + position) / 32.0F) * (1 << 20))) & 0xFFFFF;
    }

    private static int[] fullBrightLightWords() {
        int[] words = new int[27];
        for (int i = 0; i < words.length; i++) {
            words[i] = 0x010000FF;
        }
        return words;
    }

    private static int[] neighborhoodStates(int stateId) {
        int[] states = new int[27];
        for (int i = 0; i < states.length; i++) {
            states[i] = stateId;
        }
        return states;
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

    private static int packTextureForQuad(float vertexU, float vertexV,
            float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        float centerU = (u0 + u1 + u2 + u3) * 0.25F;
        float centerV = (v0 + v1 + v2 + v3) * 0.25F;
        int u = encodeTexture(centerU, vertexU);
        int v = encodeTexture(centerV, vertexV);
        return (u & 0xFFFF) | ((v & 0xFFFF) << 16);
    }

    private static int encodeTexture(float center, float value) {
        int bias = value < center ? 1 : -1;
        int quantized = Math.round(value * (1 << 15)) + bias;
        return (quantized & 0x7fff) | ((bias >>> 31) << 15);
    }

    private static void assertSegmentPresent(int[] segments, int vertexCount, int facing) {
        for (int index = 0; index < segments.length; index += 2) {
            if (segments[index] == vertexCount && segments[index + 1] == facing) {
                return;
            }
        }

        org.junit.jupiter.api.Assertions.fail("Missing segment " + vertexCount + "/" + facing
                + " in " + java.util.Arrays.toString(segments));
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
