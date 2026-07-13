package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.NativeUpdatedQuads;
import net.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

public final class NativeSectionMeshBuilder implements AutoCloseable {
    private static final int OK = 0;
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_destroy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle START = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_start",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle PREPARE_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_prepare_quad",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle COMMIT_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_commit_quad",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle APPEND_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_BATCH_FILTERED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_batch_filtered",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_FLAT_QUAD_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_flat_quad_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_LIGHT_BLOCK_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_light_block_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_FLUID_FACE_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_fluid_face_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_STATIC_MODEL_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_static_model_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_NATIVE_SECTION_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_native_section_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_NATIVE_SECTION_ALL_PASSES_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builders_append_native_section_all_passes_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_COMPACT_NATIVE_SECTION_ALL_PASSES_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builders_append_compact_native_section_all_passes_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle APPEND_TRANSLUCENT_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_translucent_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle APPEND_TRANSLUCENT_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_translucent_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle APPEND_TRANSLUCENT_FLAT_QUAD_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_translucent_flat_quad_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle APPEND_TRANSLUCENT_FLUID_FACE_BATCH_ENCODED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_append_translucent_fluid_face_batch_encoded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle STAGING_ADDRESSES = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_staging_addresses",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle RECORD_STAGING_ADDRESSES = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_record_staging_addresses",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle FACING_ADDRESS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_facing_address",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle FACING_VERTEX_COUNT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_facing_vertex_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle TOTAL_VERTEX_COUNT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_total_vertex_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle COPY_PROFILE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_copy_profile",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle FLUID_SPRITE_MASK = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_fluid_sprite_mask",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
    private static final MethodHandle ASSEMBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_assemble",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE_SCATTERED_UNASSIGNED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_mesh_builder_encode_scattered_unassigned",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private int sectionIndex;

    private NativeSectionMeshBuilder(long handle) {
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, this.state);
    }

    public static NativeSectionMeshBuilder create(int initialQuadCapacity) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handleSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeCreate(initialQuadCapacity, handleSegment), "native section mesh builder creation");
            long handle = handleSegment.get(ValueLayout.JAVA_LONG, 0);
            if (handle == 0) {
                throw new IllegalStateException("Native section mesh builder creation returned a null handle");
            }
            return new NativeSectionMeshBuilder(handle);
        }
    }

    public static FacingBuffer createFacingBuffer(ChunkVertexType vertexType, int initialCapacity) {
        return new FacingBuffer(vertexType.getNativeFormat(),
                NativeSectionMeshBuilder.create(Math.max(1, (initialCapacity + 3) >> 2)),
                ModelQuadFacing.UNASSIGNED.ordinal(), true, true);
    }

    public static FacingBuffer createEncodedFacingBuffer(ChunkVertexType vertexType, int initialCapacity) {
        return new FacingBuffer(vertexType.getNativeFormat(),
                NativeSectionMeshBuilder.create(Math.max(1, (initialCapacity + 3) >> 2)),
                ModelQuadFacing.UNASSIGNED.ordinal(), true, false);
    }

    public void start(int sectionIndex) {
        check(invokeStart(this.state.getHandle()), "native section mesh builder start");
        this.sectionIndex = sectionIndex;
    }

    public long prepareQuadAddress(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokePrepareQuad(this.state.getHandle(), facing, addressSegment),
                    "native section mesh builder quad preparation");
            return addressSegment.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    public void commitQuad(int facing) {
        check(invokeCommitQuad(this.state.getHandle(), facing), "native section mesh builder quad commit");
    }

    public int appendBatch(int facing, long batchAddress, int quadCount) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendBatch(this.state.getHandle(), facing, batchAddress, quadCount, committedCountSegment),
                    "native section mesh builder batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendBatchEncoded(int facing, long batchAddress, int quadCount, NativeChunkVertexFormat format,
            int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendBatchEncoded(this.state.getHandle(), facing, batchAddress, quadCount,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, storeRawQuads ? 1 : 0, committedCountSegment),
                    "native section mesh builder encoded batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendFlatQuadBatchEncoded(int facing, long recordAddress, int quadCount,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendFlatQuadBatchEncoded(this.state.getHandle(), facing, recordAddress, quadCount,
                    NativeChunkMeshEncoder.FLAT_QUAD_RECORD_STRIDE, NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), sectionIndex, separateAo ? 1 : 0,
                    storeRawQuads ? 1 : 0, committedCountSegment),
                    "native section mesh builder flat quad encoded batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendLightBlockBatchEncoded(int facing, long recordAddress, int quadCount,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendLightBlockBatchEncoded(this.state.getHandle(), facing, recordAddress, quadCount,
                    NativeChunkMeshEncoder.LIGHT_BLOCK_RECORD_STRIDE, NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), sectionIndex, separateAo ? 1 : 0,
                    storeRawQuads ? 1 : 0, committedCountSegment),
                    "native section mesh builder light block encoded batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendFluidFaceBatchEncoded(int facing, long recordAddress, int quadCount,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendFluidFaceBatchEncoded(this.state.getHandle(), facing, recordAddress, quadCount,
                    NativeChunkMeshEncoder.FLUID_FACE_RECORD_STRIDE, NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), sectionIndex, separateAo ? 1 : 0,
                    storeRawQuads ? 1 : 0, committedCountSegment),
                    "native section mesh builder fluid-face encoded batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendStaticModelBatchEncoded(long recordAddress, int blockCount,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (blockCount < 0) {
            throw new IllegalArgumentException("Invalid static model block count: " + blockCount);
        }
        if (blockCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendStaticModelBatchEncoded(this.state.getHandle(), recordAddress, blockCount,
                    NativeChunkMeshEncoder.STATIC_MODEL_BLOCK_RECORD_STRIDE, NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), sectionIndex, separateAo ? 1 : 0,
                    storeRawQuads ? 1 : 0, committedCountSegment),
                    "native section mesh builder static model encoded batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int appendNativeSectionEncoded(long recordAddress, int blockCount, int passId,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        return this.appendNativeSectionEncoded(recordAddress, blockCount, passId, format, sectionIndex, separateAo,
                storeRawQuads, 0L);
    }

    public int appendNativeSectionEncoded(long recordAddress, int blockCount, int passId,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, boolean storeRawQuads,
            long translucentAnalyzerHandle) {
        if (blockCount < 0) {
            throw new IllegalArgumentException("Invalid native section block count: " + blockCount);
        }
        if (blockCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendNativeSectionEncoded(this.state.getHandle(), recordAddress, blockCount,
                    NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE, passId,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, storeRawQuads ? 1 : 0, translucentAnalyzerHandle,
                    committedCountSegment),
                    "native section mesh builder native section encoded append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public static int[] appendNativeSectionAllPassesEncoded(NativeSectionMeshBuilder solid,
            NativeSectionMeshBuilder cutout, NativeSectionMeshBuilder translucent, long recordAddress, int blockCount,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, long translucentAnalyzerHandle) {
        if (blockCount < 0) {
            throw new IllegalArgumentException("Invalid native section block count: " + blockCount);
        }
        if (blockCount == 0) {
            return new int[] { 0, 0, 0 };
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 3);
            check(invokeAppendNativeSectionAllPassesEncoded(solid.state.getHandle(), cutout.state.getHandle(),
                    translucent.state.getHandle(), recordAddress, blockCount,
                    NativeChunkMeshEncoder.NATIVE_SECTION_BLOCK_RECORD_STRIDE,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, translucentAnalyzerHandle, countsSegment),
                    "native section mesh builder all-pass native section encoded append");
            return new int[] {
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 2)
            };
        }
    }

    public static int[] appendCompactNativeSectionAllPassesEncoded(NativeSectionMeshBuilder solid,
            NativeSectionMeshBuilder cutout, NativeSectionMeshBuilder translucent, long snapshotAddress,
            NativeChunkVertexFormat format, int sectionIndex, boolean separateAo, long translucentAnalyzerHandle) {
        if (snapshotAddress == 0L) {
            return new int[] { 0, 0, 0 };
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 3);
            check(invokeAppendCompactNativeSectionAllPassesEncoded(solid.state.getHandle(), cutout.state.getHandle(),
                    translucent.state.getHandle(), snapshotAddress,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, translucentAnalyzerHandle, countsSegment),
                    "native section mesh builder all-pass compact native section encoded append");
            return new int[] {
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 2)
            };
        }
    }

    public int appendBatchFiltered(int facing, long batchAddress, int quadCount, long validityAddress) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return 0;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment committedCountSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeAppendBatchFiltered(this.state.getHandle(), facing, batchAddress, quadCount,
                    validityAddress, committedCountSegment), "native section mesh builder filtered batch append");
            return committedCountSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public TranslucentBatchResult appendTranslucentBatch(int facing, long batchAddress, int quadCount,
            long analyzerHandle, int translucentFacing, long packedNormalsAddress) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return new TranslucentBatchResult(0, 0);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeAppendTranslucentBatch(this.state.getHandle(), facing, batchAddress, quadCount,
                    analyzerHandle, translucentFacing, packedNormalsAddress, countsSegment, 2),
                    "native section mesh builder translucent batch append");
            return new TranslucentBatchResult(countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1));
        }
    }

    public TranslucentBatchResult appendTranslucentBatchEncoded(int facing, long batchAddress, int quadCount,
            long analyzerHandle, int translucentFacing, long packedNormalsAddress, NativeChunkVertexFormat format,
            int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return new TranslucentBatchResult(0, 0);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeAppendTranslucentBatchEncoded(this.state.getHandle(), facing, batchAddress, quadCount,
                    analyzerHandle, translucentFacing, packedNormalsAddress,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, storeRawQuads ? 1 : 0, countsSegment, 2),
                    "native section mesh builder encoded translucent batch append");
            return new TranslucentBatchResult(countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1));
        }
    }

    public TranslucentBatchResult appendTranslucentFlatQuadBatchEncoded(int facing, long recordAddress,
            int quadCount, long analyzerHandle, int translucentFacing, NativeChunkVertexFormat format,
            int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return new TranslucentBatchResult(0, 0);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeAppendTranslucentFlatQuadBatchEncoded(this.state.getHandle(), facing, recordAddress,
                    quadCount, analyzerHandle, translucentFacing, NativeChunkMeshEncoder.FLAT_QUAD_RECORD_STRIDE,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, storeRawQuads ? 1 : 0, countsSegment, 2),
                    "native section mesh builder encoded translucent flat quad batch append");
            return new TranslucentBatchResult(countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1));
        }
    }

    public TranslucentBatchResult appendTranslucentFluidFaceBatchEncoded(int facing, long recordAddress,
            int quadCount, long analyzerHandle, int translucentFacing, NativeChunkVertexFormat format,
            int sectionIndex, boolean separateAo, boolean storeRawQuads) {
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid quad count: " + quadCount);
        }
        if (quadCount == 0) {
            return new TranslucentBatchResult(0, 0);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countsSegment = arena.allocate(ValueLayout.JAVA_INT, 2);
            check(invokeAppendTranslucentFluidFaceBatchEncoded(this.state.getHandle(), facing, recordAddress,
                    quadCount, analyzerHandle, translucentFacing, NativeChunkMeshEncoder.FLUID_FACE_RECORD_STRIDE,
                    NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE, format.stride(), format.blockIdOffset(),
                    format.normalOffset(), format.tangentOffset(), format.midUvOffset(), format.midBlockOffset(),
                    sectionIndex, separateAo ? 1 : 0, storeRawQuads ? 1 : 0, countsSegment, 2),
                    "native section mesh builder encoded translucent fluid-face batch append");
            return new TranslucentBatchResult(countsSegment.getAtIndex(ValueLayout.JAVA_INT, 0),
                    countsSegment.getAtIndex(ValueLayout.JAVA_INT, 1));
        }
    }

    public StagingBuffers stagingBuffers(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment quadAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment packedNormalsAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment validityAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment capacitySegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeStagingAddresses(this.state.getHandle(), facing, quadAddressSegment,
                    packedNormalsAddressSegment, validityAddressSegment, capacitySegment),
                    "native section mesh builder staging address query");
            return new StagingBuffers(
                    quadAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    packedNormalsAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    validityAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    capacitySegment.get(ValueLayout.JAVA_INT, 0));
        }
    }

    public RecordStagingBuffers recordStagingBuffers(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment flatQuadRecordAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment lightBlockRecordAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment fluidFaceRecordAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment staticModelBlockRecordAddressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment capacitySegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeRecordStagingAddresses(this.state.getHandle(), facing, flatQuadRecordAddressSegment,
                    lightBlockRecordAddressSegment, fluidFaceRecordAddressSegment,
                    staticModelBlockRecordAddressSegment, capacitySegment),
                    "native section mesh builder record staging address query");
            return new RecordStagingBuffers(
                    flatQuadRecordAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    lightBlockRecordAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    fluidFaceRecordAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    staticModelBlockRecordAddressSegment.get(ValueLayout.JAVA_LONG, 0),
                    capacitySegment.get(ValueLayout.JAVA_INT, 0));
        }
    }

    public long facingAddress(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addressSegment = arena.allocate(ValueLayout.JAVA_LONG);
            check(invokeFacingAddress(this.state.getHandle(), facing, addressSegment),
                    "native section mesh builder facing address query");
            return addressSegment.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    public int facingVertexCount(int facing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeFacingVertexCount(this.state.getHandle(), facing, countSegment),
                    "native section mesh builder facing count query");
            return countSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public int totalVertexCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeTotalVertexCount(this.state.getHandle(), countSegment),
                    "native section mesh builder total count query");
            return countSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public long[] copyProfile() {
        long[] values = new long[Profile.METRIC_COUNT];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment valuesSegment = arena.allocate(ValueLayout.JAVA_LONG, values.length);
            check(invokeCopyProfile(this.state.getHandle(), valuesSegment, values.length),
                    "native section mesh builder profile copy");
            for (int index = 0; index < values.length; index++) {
                values[index] = valuesSegment.getAtIndex(ValueLayout.JAVA_LONG, index);
            }
        }
        return values;
    }

    public int fluidSpriteMask() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment maskSegment = arena.allocate(ValueLayout.JAVA_INT);
            check(invokeFluidSpriteMask(this.state.getHandle(), maskSegment),
                    "native section mesh builder fluid sprite mask query");
            return maskSegment.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public void assemble(ByteBuffer output, int[] vertexSegments, NativeChunkVertexFormat format,
            int visibleSlices, boolean forceUnassigned, boolean sliceReordering, boolean separateAo) {
        if (vertexSegments.length != 14) {
            throw new IllegalArgumentException("Unexpected vertex segment array length: " + vertexSegments.length);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment vertexSegmentsSegment = arena.allocate(ValueLayout.JAVA_INT, vertexSegments.length);
            check(invokeAssemble(this.state.getHandle(), MemoryUtil.memAddress(output), output.remaining(),
                    vertexSegmentsSegment, vertexSegments.length, NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), this.sectionIndex, visibleSlices,
                    forceUnassigned ? 1 : 0, sliceReordering ? 1 : 0, separateAo ? 1 : 0),
                    "native section mesh builder assembly");

            for (int index = 0; index < vertexSegments.length; index++) {
                vertexSegments[index] = vertexSegmentsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }
    }

    public BuiltSectionMeshParts finishMesh(NativeChunkVertexFormat format, int visibleSlices,
            boolean forceUnassigned, boolean sliceReordering, boolean separateAo) {
        int vertexTotal = this.totalVertexCount();
        if (vertexTotal == 0) {
            return null;
        }

        int[] vertexSegments = createVertexSegments();
        NativeBuffer mergedBuffer = new NativeBuffer(vertexTotal * format.stride());
        this.assemble(mergedBuffer.getDirectBuffer(), vertexSegments, format, visibleSlices, forceUnassigned,
                sliceReordering, separateAo);
        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public BuiltSectionMeshParts finishModifiedTranslucentMesh(NativeUpdatedQuads updatedQuads,
            NativeChunkVertexFormat format, boolean separateAo) {
        int vertexTotal = updatedQuads.getMeshQuadCount() * 4;
        NativeBuffer mergedBuffer = new NativeBuffer(vertexTotal * format.stride());
        ByteBuffer mergedBufferBuilder = mergedBuffer.getDirectBuffer();

        this.assemble(mergedBufferBuilder, createVertexSegments(), format, 0, true, false, separateAo);
        updatedQuads.applyBufferUpdates(format, this.sectionIndex, mergedBufferBuilder);

        int[] vertexSegments = createVertexSegments();
        int unassignedSegmentIndex = ModelQuadFacing.UNASSIGNED.ordinal() << 1;
        vertexSegments[unassignedSegmentIndex] = vertexTotal;
        vertexSegments[unassignedSegmentIndex + 1] = ModelQuadFacing.UNASSIGNED.ordinal();

        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public void encodeScatteredUnassigned(int[] outputVertexOffsets, int updateCount, ByteBuffer output,
            NativeChunkVertexFormat format, boolean separateAo) {
        if (updateCount < 0 || updateCount > outputVertexOffsets.length) {
            throw new IllegalArgumentException("Invalid scattered encode update count: " + updateCount);
        }
        if (updateCount == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outputVertexOffsetsSegment = arena.allocate(ValueLayout.JAVA_INT, updateCount);
            for (int index = 0; index < updateCount; index++) {
                outputVertexOffsetsSegment.setAtIndex(ValueLayout.JAVA_INT, index, outputVertexOffsets[index]);
            }

            check(invokeEncodeScatteredUnassigned(this.state.getHandle(), outputVertexOffsetsSegment, updateCount,
                    MemoryUtil.memAddress(output), output.remaining(), NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE,
                    format.stride(), format.blockIdOffset(), format.normalOffset(), format.tangentOffset(),
                    format.midUvOffset(), format.midBlockOffset(), this.sectionIndex, separateAo ? 1 : 0),
                    "native section mesh builder scattered update encoding");
        }
    }

    public int sectionIndex() {
        return this.sectionIndex;
    }

    private static int[] createVertexSegments() {
        return new int[ModelQuadFacing.COUNT << 1];
    }

    public static final class Profile {
        public static final String[] STAGE_NAMES = {
                "section_scanning",
                "native_model_lookup_and_emission",
                "fluid_visibility_and_height",
                "fluid_geometry_and_uv",
                "lighting_ao_and_tint",
                "material_and_pass_routing",
                "quad_staging",
                "translucent_analyzer_ingestion",
                "translucent_metadata_key_generation",
                "sorting",
                "vertex_packing",
                "index_emission",
                "final_mesh_assembly",
                "static_state_selector_lookup",
                "static_weighted_multipart_resolution",
                "static_cached_model_lookup",
                "static_culling",
                "static_quad_iteration",
                "static_lighting_ao",
                "static_tint",
                "static_position_offset_transform",
                "static_sprite_material_pass",
                "static_native_quad_creation",
                "static_staging",
                "fluid_top_face_construction",
                "fluid_side_face_construction",
                "fluid_bottom_face_construction",
                "fluid_corner_height_use",
                "fluid_still_vs_flowing_uv",
                "fluid_overlay_selection",
                "fluid_lighting_tint",
                "fluid_normal_backface",
                "fluid_material_sprite_routing",
                "fluid_native_quad_append",
                "scan_active_record_iteration",
                "scan_record_decoding",
                "scan_state_model_fluid_dispatch",
                "scan_cache_lookup",
                "scan_culling",
                "scan_lighting_ao",
                "scan_tinting",
                "scan_model_emission",
                "scan_fluid_emission",
                "scan_pass_material_routing",
                "scan_quad_append",
                "staging_quad_append",
                "staging_pending_write",
                "staging_flush",
                "staging_vertex_encoding",
                "staging_index_write",
                "staging_final_buffer_assembly",
                "template_lookup",
                "template_instance_patching",
                "template_direct_vertex_encoding",
                "template_retained_translucent_metadata",
                "template_final_assembly_copy"
        };
        public static final String[] COUNT_NAMES = {
                "scanned_blocks",
                "native_model_blocks",
                "native_model_quads",
                "fluid_blocks",
                "fluid_faces",
                "translucent_quads",
                "sorted_quads",
                "emitted_quads",
                "direct_template_quads",
                "generic_native_quads",
                "direct_template_bytes_written",
                "generic_native_bytes_retained",
                "selector_resolutions",
                "selector_cache_hits",
                "selector_cache_misses",
                "multipart_children_tested",
                "multipart_children_selected",
                "weighted_entries_visited",
                "model_cache_hits",
                "model_cache_misses",
                "temporary_vector_clears",
                "translucent_retained_bytes",
                "translucent_analyzer_entries",
                "translucent_validity_bytes"
        };
        public static final int STAGE_COUNT = STAGE_NAMES.length;
        public static final int COUNT_COUNT = COUNT_NAMES.length;
        public static final int METRIC_COUNT = STAGE_COUNT + COUNT_COUNT;

        private Profile() {
        }
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeCreate(int initialQuadCapacity, MemorySegment handleOutput) {
        try {
            return (int) CREATE.invokeExact(initialQuadCapacity, handleOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder creation downcall failed", throwable);
        }
    }

    private static int invokeDestroy(long handle) {
        try {
            return (int) DESTROY.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder destroy downcall failed", throwable);
        }
    }

    private static int invokeStart(long handle) {
        try {
            return (int) START.invokeExact(handle);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder start downcall failed", throwable);
        }
    }

    private static int invokePrepareQuad(long handle, int facing, MemorySegment addressOutput) {
        try {
            return (int) PREPARE_QUAD.invokeExact(handle, facing, addressOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder prepare downcall failed", throwable);
        }
    }

    private static int invokeCommitQuad(long handle, int facing) {
        try {
            return (int) COMMIT_QUAD.invokeExact(handle, facing);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder commit downcall failed", throwable);
        }
    }

    private static int invokeAppendBatch(long handle, int facing, long batchAddress, int quadCount,
            MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_BATCH.invokeExact(handle, facing, batchAddress, quadCount, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder batch append downcall failed", throwable);
        }
    }

    private static int invokeAppendBatchFiltered(long handle, int facing, long batchAddress, int quadCount,
            long validityAddress, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_BATCH_FILTERED.invokeExact(handle, facing, batchAddress, quadCount,
                    validityAddress, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder filtered batch append downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendBatchEncoded(long handle, int facing, long batchAddress, int quadCount,
            int quadStride, int vertexStride, int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset,
            int midBlockOffset, int sectionIndex, int separateAo, int storeRawQuads,
            MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_BATCH_ENCODED.invokeExact(handle, facing, batchAddress, quadCount, quadStride,
                    vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset,
                    sectionIndex, separateAo, storeRawQuads, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder encoded batch append downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendFlatQuadBatchEncoded(long handle, int facing, long recordAddress, int quadCount,
            int recordStride, int quadStride, int vertexStride, int blockIdOffset, int normalOffset,
            int tangentOffset, int midUvOffset, int midBlockOffset, int sectionIndex, int separateAo,
            int storeRawQuads, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_FLAT_QUAD_BATCH_ENCODED.invokeExact(handle, facing, recordAddress, quadCount,
                    recordStride, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo, storeRawQuads, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder flat quad encoded batch downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendLightBlockBatchEncoded(long handle, int facing, long recordAddress, int quadCount,
            int recordStride, int quadStride, int vertexStride, int blockIdOffset, int normalOffset,
            int tangentOffset, int midUvOffset, int midBlockOffset, int sectionIndex, int separateAo,
            int storeRawQuads, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_LIGHT_BLOCK_BATCH_ENCODED.invokeExact(handle, facing, recordAddress, quadCount,
                    recordStride, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo, storeRawQuads, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder light block encoded batch downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendFluidFaceBatchEncoded(long handle, int facing, long recordAddress, int quadCount,
            int recordStride, int quadStride, int vertexStride, int blockIdOffset, int normalOffset,
            int tangentOffset, int midUvOffset, int midBlockOffset, int sectionIndex, int separateAo,
            int storeRawQuads, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_FLUID_FACE_BATCH_ENCODED.invokeExact(handle, facing, recordAddress, quadCount,
                    recordStride, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo, storeRawQuads, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder fluid-face encoded batch downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendStaticModelBatchEncoded(long handle, long recordAddress, int blockCount,
            int recordStride, int quadStride, int vertexStride, int blockIdOffset, int normalOffset,
            int tangentOffset, int midUvOffset, int midBlockOffset, int sectionIndex, int separateAo,
            int storeRawQuads, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_STATIC_MODEL_BATCH_ENCODED.invokeExact(handle, recordAddress, blockCount,
                    recordStride, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo, storeRawQuads, committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder static model encoded batch downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendNativeSectionEncoded(long handle, long recordAddress, int blockCount,
            int recordStride, int passId, int quadStride, int vertexStride, int blockIdOffset, int normalOffset,
            int tangentOffset, int midUvOffset, int midBlockOffset, int sectionIndex, int separateAo,
            int storeRawQuads, long translucentAnalyzerHandle, MemorySegment committedCountOutput) {
        try {
            return (int) APPEND_NATIVE_SECTION_ENCODED.invokeExact(handle, recordAddress, blockCount, recordStride,
                    passId, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo, storeRawQuads, translucentAnalyzerHandle,
                    committedCountOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder native section encoded downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendNativeSectionAllPassesEncoded(long solidHandle, long cutoutHandle,
            long translucentHandle, long recordAddress, int blockCount, int recordStride, int quadStride,
            int vertexStride, int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset,
            int midBlockOffset, int sectionIndex, int separateAo, long translucentAnalyzerHandle,
            MemorySegment committedCountsOutput) {
        try {
            return (int) APPEND_NATIVE_SECTION_ALL_PASSES_ENCODED.invokeExact(solidHandle, cutoutHandle,
                    translucentHandle, recordAddress, blockCount, recordStride, quadStride, vertexStride,
                    blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex,
                    separateAo, translucentAnalyzerHandle, committedCountsOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder all-pass native section downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendCompactNativeSectionAllPassesEncoded(long solidHandle, long cutoutHandle,
            long translucentHandle, long snapshotAddress, int quadStride, int vertexStride, int blockIdOffset,
            int normalOffset, int tangentOffset, int midUvOffset, int midBlockOffset, int sectionIndex,
            int separateAo, long translucentAnalyzerHandle, MemorySegment committedCountsOutput) {
        try {
            return (int) APPEND_COMPACT_NATIVE_SECTION_ALL_PASSES_ENCODED.invokeExact(solidHandle, cutoutHandle,
                    translucentHandle, snapshotAddress, quadStride, vertexStride, blockIdOffset, normalOffset,
                    tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo, translucentAnalyzerHandle,
                    committedCountsOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder all-pass compact native section downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendTranslucentBatch(long handle, int facing, long batchAddress, int quadCount,
            long analyzerHandle, int translucentFacing, long packedNormalsAddress, MemorySegment outputCounts,
            int outputCountsLength) {
        try {
            return (int) APPEND_TRANSLUCENT_BATCH.invokeExact(handle, facing, batchAddress, quadCount,
                    analyzerHandle, translucentFacing, packedNormalsAddress, outputCounts, outputCountsLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder translucent batch downcall failed", throwable);
        }
    }

    private static int invokeAppendTranslucentBatchEncoded(long handle, int facing, long batchAddress, int quadCount,
            long analyzerHandle, int translucentFacing, long packedNormalsAddress, int quadStride, int vertexStride,
            int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset, int midBlockOffset,
            int sectionIndex, int separateAo, int storeRawQuads, MemorySegment outputCounts, int outputCountsLength) {
        try {
            return (int) APPEND_TRANSLUCENT_BATCH_ENCODED.invokeExact(handle, facing, batchAddress, quadCount,
                    analyzerHandle, translucentFacing, packedNormalsAddress, quadStride, vertexStride, blockIdOffset,
                    normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo, storeRawQuads,
                    outputCounts, outputCountsLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder encoded translucent batch downcall failed",
                    throwable);
        }
    }

    private static int invokeAppendTranslucentFlatQuadBatchEncoded(long handle, int facing, long recordAddress,
            int quadCount, long analyzerHandle, int translucentFacing, int recordStride, int quadStride,
            int vertexStride, int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset,
            int midBlockOffset, int sectionIndex, int separateAo, int storeRawQuads, MemorySegment outputCounts,
            int outputCountsLength) {
        try {
            return (int) APPEND_TRANSLUCENT_FLAT_QUAD_BATCH_ENCODED.invokeExact(handle, facing, recordAddress,
                    quadCount, analyzerHandle, translucentFacing, recordStride, quadStride, vertexStride,
                    blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo,
                    storeRawQuads, outputCounts, outputCountsLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException(
                    "Rust section mesh builder encoded translucent flat quad batch downcall failed", throwable);
        }
    }

    private static int invokeAppendTranslucentFluidFaceBatchEncoded(long handle, int facing, long recordAddress,
            int quadCount, long analyzerHandle, int translucentFacing, int recordStride, int quadStride,
            int vertexStride, int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset,
            int midBlockOffset, int sectionIndex, int separateAo, int storeRawQuads, MemorySegment outputCounts,
            int outputCountsLength) {
        try {
            return (int) APPEND_TRANSLUCENT_FLUID_FACE_BATCH_ENCODED.invokeExact(handle, facing, recordAddress,
                    quadCount, analyzerHandle, translucentFacing, recordStride, quadStride, vertexStride,
                    blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo,
                    storeRawQuads, outputCounts, outputCountsLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException(
                    "Rust section mesh builder encoded translucent fluid-face batch downcall failed", throwable);
        }
    }

    private static int invokeStagingAddresses(long handle, int facing, MemorySegment quadAddressOutput,
            MemorySegment packedNormalsAddressOutput, MemorySegment validityAddressOutput,
            MemorySegment capacityOutput) {
        try {
            return (int) STAGING_ADDRESSES.invokeExact(handle, facing, quadAddressOutput,
                    packedNormalsAddressOutput, validityAddressOutput, capacityOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder staging address downcall failed", throwable);
        }
    }

    private static int invokeRecordStagingAddresses(long handle, int facing,
            MemorySegment flatQuadRecordAddressOutput, MemorySegment lightBlockRecordAddressOutput,
            MemorySegment fluidFaceRecordAddressOutput, MemorySegment staticModelBlockRecordAddressOutput,
            MemorySegment capacityOutput) {
        try {
            return (int) RECORD_STAGING_ADDRESSES.invokeExact(handle, facing, flatQuadRecordAddressOutput,
                    lightBlockRecordAddressOutput, fluidFaceRecordAddressOutput,
                    staticModelBlockRecordAddressOutput, capacityOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder record staging address downcall failed",
                    throwable);
        }
    }

    private static int invokeFacingAddress(long handle, int facing, MemorySegment addressOutput) {
        try {
            return (int) FACING_ADDRESS.invokeExact(handle, facing, addressOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder address downcall failed", throwable);
        }
    }

    private static int invokeFacingVertexCount(long handle, int facing, MemorySegment countOutput) {
        try {
            return (int) FACING_VERTEX_COUNT.invokeExact(handle, facing, countOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder count downcall failed", throwable);
        }
    }

    private static int invokeTotalVertexCount(long handle, MemorySegment countOutput) {
        try {
            return (int) TOTAL_VERTEX_COUNT.invokeExact(handle, countOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder total count downcall failed", throwable);
        }
    }

    private static int invokeCopyProfile(long handle, MemorySegment outputValues, int outputLength) {
        try {
            return (int) COPY_PROFILE.invokeExact(handle, outputValues, outputLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder profile downcall failed", throwable);
        }
    }

    private static int invokeFluidSpriteMask(long handle, MemorySegment maskOutput) {
        try {
            return (int) FLUID_SPRITE_MASK.invokeExact(handle, maskOutput);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder fluid sprite mask downcall failed", throwable);
        }
    }

    private static int invokeAssemble(long handle, long outputAddress, int outputCapacity,
            MemorySegment vertexSegments, int vertexSegmentsLength, int quadStride, int vertexStride,
            int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset, int midBlockOffset,
            int sectionIndex, int visibleSlices, int forceUnassigned, int sliceReordering, int separateAo) {
        try {
            return (int) ASSEMBLE.invokeExact(handle, outputAddress, outputCapacity, vertexSegments,
                    vertexSegmentsLength, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset,
                    midUvOffset, midBlockOffset, sectionIndex, visibleSlices, forceUnassigned, sliceReordering,
                    separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder assembly downcall failed", throwable);
        }
    }

    private static int invokeEncodeScatteredUnassigned(long handle, MemorySegment outputVertexOffsets,
            int updateCount, long outputAddress, int outputCapacity, int quadStride, int vertexStride,
            int blockIdOffset, int normalOffset, int tangentOffset, int midUvOffset, int midBlockOffset,
            int sectionIndex, int separateAo) {
        try {
            return (int) ENCODE_SCATTERED_UNASSIGNED.invokeExact(handle, outputVertexOffsets, updateCount,
                    outputAddress, outputCapacity, quadStride, vertexStride, blockIdOffset, normalOffset,
                    tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section mesh builder scattered update downcall failed", throwable);
        }
    }

    public record StagingBuffers(long quadAddress, long packedNormalsAddress, long validityAddress, int capacity) {
    }

    public record RecordStagingBuffers(long flatQuadRecordAddress, long lightBlockRecordAddress,
            long fluidFaceRecordAddress, long staticModelBlockRecordAddress, int capacity) {
    }

    public record TranslucentBatchResult(int validCount, int committedCount) {
    }

    public static final class FacingBuffer {
        private static final int PENDING_NATIVE_QUADS = 0;
        private static final int PENDING_FLAT_QUAD_RECORDS = 1;
        private static final int PENDING_LIGHT_BLOCK_RECORDS = 2;
        private static final int PENDING_FLUID_FACE_RECORDS = 3;
        private static final int PENDING_STATIC_MODEL_BLOCK_RECORDS = 4;

        private final NativeChunkVertexFormat nativeFormat;
        private final int nativeQuadStride = NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE;
        private final NativeSectionMeshBuilder sectionBuilder;
        private final int facing;
        private final boolean ownsSectionBuilder;
        private final boolean storeRawQuads;
        private final StagingBuffers stagingBuffers;
        private final RecordStagingBuffers recordStagingBuffers;

        private int sectionIndex;
        private int pendingQuadCount;
        private int pendingKind;
        private TranslucentGeometryCollector pendingCollector;
        private ModelQuadFacing pendingCollectorFacing;

        public FacingBuffer(NativeChunkVertexFormat nativeFormat, NativeSectionMeshBuilder sectionBuilder,
                int facing) {
            this(nativeFormat, sectionBuilder, facing, false, true);
        }

        public FacingBuffer(NativeChunkVertexFormat nativeFormat, NativeSectionMeshBuilder sectionBuilder,
                int facing, boolean storeRawQuads) {
            this(nativeFormat, sectionBuilder, facing, false, storeRawQuads);
        }

        private FacingBuffer(NativeChunkVertexFormat nativeFormat, NativeSectionMeshBuilder sectionBuilder,
                int facing, boolean ownsSectionBuilder, boolean storeRawQuads) {
            this.nativeFormat = nativeFormat;
            this.sectionBuilder = sectionBuilder;
            this.facing = facing;
            this.ownsSectionBuilder = ownsSectionBuilder;
            this.storeRawQuads = storeRawQuads;
            this.stagingBuffers = sectionBuilder.stagingBuffers(facing);
            this.recordStagingBuffers = sectionBuilder.recordStagingBuffers(facing);
            if (this.recordStagingBuffers.capacity() != this.stagingBuffers.capacity()) {
                throw new IllegalStateException("Native staging capacities differ for facing " + facing);
            }
        }

        public long prepareQuadAddress() {
            this.flushPending();
            return this.sectionBuilder.prepareQuadAddress(this.facing);
        }

        public void commitPreparedQuad() {
            this.sectionBuilder.commitQuad(this.facing);
        }

        public long prepareStagedQuad(int materialBits, byte blockEmission, byte renderType, boolean ignoreMidBlock,
                int blockId, int localX, int localY, int localZ) {
            if (!this.matchesPendingMode(PENDING_NATIVE_QUADS, null, null)) {
                this.flushPending();
            }

            if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
                this.flushPending();
            }

            this.pendingKind = PENDING_NATIVE_QUADS;
            this.pendingCollector = null;
            this.pendingCollectorFacing = null;
            return this.pendingQuadAddress();
        }

        public void commitStagedQuad() {
            this.pendingQuadCount++;
        }

        public long prepareStagedTranslucentQuad(int materialBits, TranslucentGeometryCollector collector,
                ModelQuadFacing collectorFacing, byte blockEmission, byte renderType, boolean ignoreMidBlock,
                int blockId, int localX, int localY, int localZ) {
            if (!collector.supportsNativeBatching()) {
                return this.prepareStagedQuad(materialBits, blockEmission, renderType, ignoreMidBlock,
                        blockId, localX, localY, localZ);
            }

            if (!this.matchesPendingMode(PENDING_NATIVE_QUADS, collector, collectorFacing)) {
                this.flushPending();
            }

            if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
                this.flushPending();
            }

            this.pendingKind = PENDING_NATIVE_QUADS;
            this.pendingCollector = collector;
            this.pendingCollectorFacing = collectorFacing;
            return this.pendingQuadAddress();
        }

        public boolean commitStagedTranslucentQuad(long quadAddress, TranslucentGeometryCollector collector,
                ModelQuadFacing collectorFacing, int packedNormal) {
            if (!collector.supportsNativeBatching()) {
                if (collector.appendNativeQuad(quadAddress, collectorFacing, packedNormal)) {
                    return true;
                }

                this.commitStagedQuad();
                return false;
            }

            MemoryUtil.memPutInt(this.stagingBuffers.packedNormalsAddress()
                    + (long) this.pendingQuadCount * Integer.BYTES, packedNormal);
            this.pendingQuadCount++;
            return false;
        }

        public void start(int sectionIndex) {
            this.sectionIndex = sectionIndex;
            this.clearPending();

            if (this.ownsSectionBuilder) {
                this.sectionBuilder.start(sectionIndex);
            }
        }

        public void destroy() {
            if (this.ownsSectionBuilder) {
                this.sectionBuilder.close();
            }
        }

        public boolean isEmpty() {
            return this.count() == 0;
        }

        public ByteBuffer slice() {
            this.flushPending();

            if (this.isEmpty()) {
                throw new IllegalStateException("No vertex data in buffer");
            }

            return MemoryUtil.memByteBuffer(this.logicalAddress(), this.nativeQuadStride * (this.count() >> 2));
        }

        public int count() {
            this.flushPending();
            return this.sectionBuilder.facingVertexCount(this.facing);
        }

        public long logicalAddress() {
            this.flushPending();
            return this.sectionBuilder.facingAddress(this.facing);
        }

        public NativeChunkVertexFormat nativeFormat() {
            return this.nativeFormat;
        }

        public int sectionIndex() {
            return this.sectionIndex;
        }

        public NativeSectionMeshBuilder sectionBuilder() {
            this.flushPending();
            return this.sectionBuilder;
        }

        public void appendLightBlockQuad(int materialBits, byte blockEmission, int blockId, int localX, int localY,
                int localZ) {
            this.prepareLightBlockRecord();
            long recordAddress = this.recordStagingBuffers.lightBlockRecordAddress()
                    + (long) this.pendingQuadCount * NativeChunkMeshEncoder.LIGHT_BLOCK_RECORD_STRIDE;
            NativeChunkMeshEncoder.writeLightBlockRecord(recordAddress, materialBits, blockEmission, blockId,
                    localX, localY, localZ);
            this.pendingQuadCount++;
        }

        public void appendFluidFace(int materialBits, byte blockEmission, byte renderType, boolean ignoreMidBlock,
                int blockId, int localX, int localY, int localZ, int faceKind, boolean flip, int packedNormal,
                int originX, int originY, int originZ, float yOffset,
                float height0, float height1, float height2, float height3,
                float sideX1, float sideZ1, float sideX2, float sideZ2,
                float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                int color0, int color1, int color2, int color3,
                float ao0, float ao1, float ao2, float ao3,
                int light0, int light1, int light2, int light3) {
            this.prepareFluidFaceRecord(null, null);
            this.writePendingFluidFaceRecord(materialBits, blockEmission, renderType, ignoreMidBlock, blockId,
                    localX, localY, localZ, faceKind, flip, packedNormal, originX, originY, originZ, yOffset,
                    height0, height1, height2, height3, sideX1, sideZ1, sideX2, sideZ2,
                    u0, v0, u1, v1, u2, v2, u3, v3,
                    color0, color1, color2, color3, ao0, ao1, ao2, ao3,
                    light0, light1, light2, light3);
            this.pendingQuadCount++;
        }

        public void appendStaticModelBlock(int modelId, int materialBits, byte blockEmission, byte renderType,
                int blockId, int localX, int localY, int localZ, int cullMask,
                float offsetX, float offsetY, float offsetZ) {
            this.prepareStaticModelBlockRecord();
            long recordAddress = this.recordStagingBuffers.staticModelBlockRecordAddress()
                    + (long) this.pendingQuadCount * NativeChunkMeshEncoder.STATIC_MODEL_BLOCK_RECORD_STRIDE;
            NativeChunkMeshEncoder.writeStaticModelBlockRecord(recordAddress, modelId, materialBits, blockEmission,
                    renderType, blockId, localX, localY, localZ, cullMask, offsetX, offsetY, offsetZ);
            this.pendingQuadCount++;
        }

        public boolean appendTranslucentFluidFace(int materialBits, TranslucentGeometryCollector collector,
                ModelQuadFacing collectorFacing, int packedNormal, byte blockEmission, byte renderType,
                boolean ignoreMidBlock, int blockId, int localX, int localY, int localZ, int faceKind, boolean flip,
                int originX, int originY, int originZ, float yOffset,
                float height0, float height1, float height2, float height3,
                float sideX1, float sideZ1, float sideX2, float sideZ2,
                float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                int color0, int color1, int color2, int color3,
                float ao0, float ao1, float ao2, float ao3,
                int light0, int light1, int light2, int light3) {
            if (!collector.supportsNativeBatching()) {
                return this.appendFlatTranslucentQuad(materialBits, collector, collectorFacing, packedNormal,
                        blockEmission, renderType, ignoreMidBlock, blockId, localX, localY, localZ,
                        originX, originY, originZ, color0, ao0, u0, v0, light0,
                        originX, originY, originZ, color1, ao1, u1, v1, light1,
                        originX, originY, originZ, color2, ao2, u2, v2, light2,
                        originX, originY, originZ, color3, ao3, u3, v3, light3);
            }

            this.prepareFluidFaceRecord(collector, collectorFacing);
            this.writePendingFluidFaceRecord(materialBits, blockEmission, renderType, ignoreMidBlock, blockId,
                    localX, localY, localZ, faceKind, flip, packedNormal, originX, originY, originZ, yOffset,
                    height0, height1, height2, height3, sideX1, sideZ1, sideX2, sideZ2,
                    u0, v0, u1, v1, u2, v2, u3, v3,
                    color0, color1, color2, color3, ao0, ao1, ao2, ao3,
                    light0, light1, light2, light3);
            this.pendingQuadCount++;
            return false;
        }

        public void appendFlatQuad(int materialBits, byte blockEmission, byte renderType, boolean ignoreMidBlock,
                int blockId, int localX, int localY, int localZ,
                float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
                float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
                float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
                float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3) {
            this.prepareFlatQuadRecord(null, null);
            writePendingFlatQuadRecord(0, materialBits, blockEmission, renderType, ignoreMidBlock, blockId,
                    localX, localY, localZ,
                    x0, y0, z0, color0, ao0, u0, v0, light0,
                    x1, y1, z1, color1, ao1, u1, v1, light1,
                    x2, y2, z2, color2, ao2, u2, v2, light2,
                    x3, y3, z3, color3, ao3, u3, v3, light3);
            this.pendingQuadCount++;
        }

        public boolean appendFlatTranslucentQuad(int materialBits, TranslucentGeometryCollector collector,
                ModelQuadFacing collectorFacing, int packedNormal, byte blockEmission, byte renderType,
                boolean ignoreMidBlock, int blockId, int localX, int localY, int localZ,
                float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
                float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
                float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
                float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3) {
            if (!collector.supportsNativeBatching()) {
                long quadAddress = this.prepareStagedQuad(materialBits, blockEmission, renderType, ignoreMidBlock,
                        blockId, localX, localY, localZ);
                NativeChunkMeshEncoder.writeNativeQuadMemory(quadAddress, blockEmission, renderType, ignoreMidBlock,
                        blockId, localX, localY, localZ, materialBits,
                        x0, y0, z0, color0, ao0, u0, v0, light0,
                        x1, y1, z1, color1, ao1, u1, v1, light1,
                        x2, y2, z2, color2, ao2, u2, v2, light2,
                        x3, y3, z3, color3, ao3, u3, v3, light3);
                return this.commitStagedTranslucentQuad(quadAddress, collector, collectorFacing, packedNormal);
            }

            this.prepareFlatQuadRecord(collector, collectorFacing);
            writePendingFlatQuadRecord(packedNormal, materialBits, blockEmission, renderType, ignoreMidBlock,
                    blockId, localX, localY, localZ,
                    x0, y0, z0, color0, ao0, u0, v0, light0,
                    x1, y1, z1, color1, ao1, u1, v1, light1,
                    x2, y2, z2, color2, ao2, u2, v2, light2,
                    x3, y3, z3, color3, ao3, u3, v3, light3);
            this.pendingQuadCount++;
            return false;
        }

        public void flushPending() {
            if (this.pendingQuadCount == 0) {
                return;
            }

            int quadCount = this.pendingQuadCount;
            TranslucentGeometryCollector collector = this.pendingCollector;
            boolean separateAo = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE
                    .shouldUseSeparateAo();

            if (this.pendingKind == PENDING_LIGHT_BLOCK_RECORDS) {
                int committedCount = this.sectionBuilder.appendLightBlockBatchEncoded(this.facing,
                        this.recordStagingBuffers.lightBlockRecordAddress(), quadCount, this.nativeFormat,
                        this.sectionIndex, separateAo, this.storeRawQuads);
                if (committedCount != quadCount) {
                    throw new IllegalStateException("Native light-block batch committed " + committedCount
                            + " quads from an unfiltered batch of " + quadCount + " quads");
                }
            } else if (this.pendingKind == PENDING_STATIC_MODEL_BLOCK_RECORDS) {
                int committedCount = this.sectionBuilder.appendStaticModelBatchEncoded(
                        this.recordStagingBuffers.staticModelBlockRecordAddress(), quadCount, this.nativeFormat,
                        this.sectionIndex, separateAo, this.storeRawQuads);
                if (committedCount < 0) {
                    throw new IllegalStateException("Native static model batch returned invalid committed count "
                            + committedCount);
                }
            } else if (this.pendingKind == PENDING_FLUID_FACE_RECORDS && collector == null) {
                int committedCount = this.sectionBuilder.appendFluidFaceBatchEncoded(this.facing,
                        this.recordStagingBuffers.fluidFaceRecordAddress(), quadCount, this.nativeFormat,
                        this.sectionIndex, separateAo, this.storeRawQuads);
                if (committedCount != quadCount) {
                    throw new IllegalStateException("Native fluid-face batch committed " + committedCount
                            + " quads from an unfiltered batch of " + quadCount + " quads");
                }
            } else if (collector == null) {
                int committedCount;
                if (this.pendingKind == PENDING_FLAT_QUAD_RECORDS) {
                    committedCount = this.sectionBuilder.appendFlatQuadBatchEncoded(this.facing,
                            this.recordStagingBuffers.flatQuadRecordAddress(), quadCount, this.nativeFormat,
                            this.sectionIndex, separateAo, this.storeRawQuads);
                } else {
                    committedCount = this.sectionBuilder.appendBatchEncoded(this.facing,
                            this.stagingBuffers.quadAddress(), quadCount, this.nativeFormat, this.sectionIndex,
                            separateAo, this.storeRawQuads);
                }
                if (committedCount != quadCount) {
                    throw new IllegalStateException("Native batch committed " + committedCount
                            + " quads from an unfiltered batch of " + quadCount + " quads");
                }
            } else {
                TranslucentBatchResult result;
                if (this.pendingKind == PENDING_FLUID_FACE_RECORDS) {
                    result = this.sectionBuilder.appendTranslucentFluidFaceBatchEncoded(this.facing,
                            this.recordStagingBuffers.fluidFaceRecordAddress(), quadCount,
                            collector.nativeAnalyzerHandle(), this.pendingCollectorFacing.ordinal(),
                            this.nativeFormat, this.sectionIndex, separateAo, this.storeRawQuads);
                } else if (this.pendingKind == PENDING_FLAT_QUAD_RECORDS) {
                    result = this.sectionBuilder.appendTranslucentFlatQuadBatchEncoded(this.facing,
                            this.recordStagingBuffers.flatQuadRecordAddress(), quadCount, collector.nativeAnalyzerHandle(),
                            this.pendingCollectorFacing.ordinal(), this.nativeFormat, this.sectionIndex, separateAo,
                            this.storeRawQuads);
                } else {
                    result = this.sectionBuilder.appendTranslucentBatchEncoded(
                            this.facing, this.stagingBuffers.quadAddress(), quadCount, collector.nativeAnalyzerHandle(),
                            this.pendingCollectorFacing.ordinal(), this.stagingBuffers.packedNormalsAddress(),
                            this.nativeFormat, this.sectionIndex, separateAo, this.storeRawQuads);
                }

                if (result.validCount() != result.committedCount()) {
                    throw new IllegalStateException("Native translucent batch accepted " + result.validCount()
                            + " quads but committed " + result.committedCount() + " quads");
                }
            }

            this.clearPending();
        }

        private long pendingQuadAddress() {
            return this.stagingBuffers.quadAddress() + (long) this.pendingQuadCount * this.nativeQuadStride;
        }

        private void prepareFlatQuadRecord(TranslucentGeometryCollector collector, ModelQuadFacing collectorFacing) {
            if (!this.matchesPendingMode(PENDING_FLAT_QUAD_RECORDS, collector, collectorFacing)) {
                this.flushPending();
            }

            if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
                this.flushPending();
            }

            this.pendingKind = PENDING_FLAT_QUAD_RECORDS;
            this.pendingCollector = collector;
            this.pendingCollectorFacing = collectorFacing;
        }

        private void prepareLightBlockRecord() {
            if (!this.matchesPendingMode(PENDING_LIGHT_BLOCK_RECORDS, null, null)) {
                this.flushPending();
            }

            if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
                this.flushPending();
            }

            this.pendingKind = PENDING_LIGHT_BLOCK_RECORDS;
            this.pendingCollector = null;
            this.pendingCollectorFacing = null;
        }

        private void prepareStaticModelBlockRecord() {
            if (!this.matchesPendingMode(PENDING_STATIC_MODEL_BLOCK_RECORDS, null, null)) {
                this.flushPending();
            }

            if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
                this.flushPending();
            }

            this.pendingKind = PENDING_STATIC_MODEL_BLOCK_RECORDS;
            this.pendingCollector = null;
            this.pendingCollectorFacing = null;
        }

        private void prepareFluidFaceRecord(TranslucentGeometryCollector collector,
                ModelQuadFacing collectorFacing) {
            if (!this.matchesPendingMode(PENDING_FLUID_FACE_RECORDS, collector, collectorFacing)) {
                this.flushPending();
            }

            if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
                this.flushPending();
            }

            this.pendingKind = PENDING_FLUID_FACE_RECORDS;
            this.pendingCollector = collector;
            this.pendingCollectorFacing = collectorFacing;
        }

        private void writePendingFlatQuadRecord(int packedNormal, int materialBits, byte blockEmission,
                byte renderType, boolean ignoreMidBlock, int blockId, int localX, int localY, int localZ,
                float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
                float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
                float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
                float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3) {
            long recordAddress = this.recordStagingBuffers.flatQuadRecordAddress()
                    + (long) this.pendingQuadCount * NativeChunkMeshEncoder.FLAT_QUAD_RECORD_STRIDE;
            NativeChunkMeshEncoder.writeFlatQuadRecord(recordAddress, packedNormal, blockEmission, renderType,
                    ignoreMidBlock, blockId, localX, localY, localZ, materialBits,
                    x0, y0, z0, color0, ao0, u0, v0, light0,
                    x1, y1, z1, color1, ao1, u1, v1, light1,
                    x2, y2, z2, color2, ao2, u2, v2, light2,
                    x3, y3, z3, color3, ao3, u3, v3, light3);
        }

        private void writePendingFluidFaceRecord(int materialBits, byte blockEmission, byte renderType,
                boolean ignoreMidBlock, int blockId, int localX, int localY, int localZ, int faceKind,
                boolean flip, int packedNormal, int originX, int originY, int originZ, float yOffset,
                float height0, float height1, float height2, float height3,
                float sideX1, float sideZ1, float sideX2, float sideZ2,
                float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                int color0, int color1, int color2, int color3,
                float ao0, float ao1, float ao2, float ao3,
                int light0, int light1, int light2, int light3) {
            long recordAddress = this.recordStagingBuffers.fluidFaceRecordAddress()
                    + (long) this.pendingQuadCount * NativeChunkMeshEncoder.FLUID_FACE_RECORD_STRIDE;
            NativeChunkMeshEncoder.writeFluidFaceRecord(recordAddress, packedNormal, materialBits, blockEmission,
                    renderType, ignoreMidBlock, blockId, localX, localY, localZ, faceKind, flip,
                    originX, originY, originZ, yOffset, height0, height1, height2, height3,
                    sideX1, sideZ1, sideX2, sideZ2, u0, v0, u1, v1, u2, v2, u3, v3,
                    color0, color1, color2, color3, ao0, ao1, ao2, ao3, light0, light1, light2, light3);
        }

        private boolean matchesPendingMode(int pendingKind, TranslucentGeometryCollector collector,
                ModelQuadFacing collectorFacing) {
            if (this.pendingQuadCount == 0) {
                return true;
            }
            if (this.pendingKind != pendingKind) {
                return false;
            }
            if (this.pendingCollector != collector) {
                return false;
            }

            return collector == null || this.pendingCollectorFacing == collectorFacing;
        }

        private void clearPending() {
            this.pendingQuadCount = 0;
            this.pendingKind = PENDING_NATIVE_QUADS;
            this.pendingCollector = null;
            this.pendingCollectorFacing = null;
        }

    }

    private static final class State implements Runnable {
        private long handle;

        private State(long handle) {
            this.handle = handle;
        }

        private synchronized long getHandle() {
            if (this.handle == 0) {
                throw new IllegalStateException("Native section mesh builder has been closed");
            }
            return this.handle;
        }

        @Override
        public synchronized void run() {
            long handle = this.handle;
            if (handle == 0) {
                return;
            }

            check(invokeDestroy(handle), "native section mesh builder destroy");
            this.handle = 0;
        }
    }
}
