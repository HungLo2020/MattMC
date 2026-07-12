package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class NativeChunkMeshEncoder {
    public static final int NATIVE_QUAD_STRIDE = 152;
    public static final int FLAT_QUAD_RECORD_STRIDE = 156;
    public static final int LIGHT_BLOCK_RECORD_STRIDE = 24;
    public static final int FLUID_FACE_RECORD_STRIDE = 172;
    public static final int STATIC_MODEL_VERTEX_RECORD_STRIDE = 28;
    public static final int STATIC_MODEL_QUAD_RECORD_STRIDE = 160;
    public static final int STATIC_MODEL_BLOCK_RECORD_STRIDE = 52;
    public static final int NATIVE_SECTION_BLOCK_RECORD_STRIDE = 316;
    public static final int NATIVE_MODEL_SELECTOR_ENTRY_STRIDE = 8;
    public static final int NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID = 1;

    private static final int OK = 0;
    private static final int VERTEX_STRIDE = 32;
    private static final int VERTEX_X_OFFSET = 0;
    private static final int VERTEX_Y_OFFSET = 4;
    private static final int VERTEX_Z_OFFSET = 8;
    private static final int VERTEX_COLOR_OFFSET = 12;
    private static final int VERTEX_AO_OFFSET = 16;
    private static final int VERTEX_U_OFFSET = 20;
    private static final int VERTEX_V_OFFSET = 24;
    private static final int VERTEX_LIGHT_OFFSET = 28;
    private static final int QUAD_BLOCK_EMISSION_OFFSET = 128;
    private static final int QUAD_RENDER_TYPE_OFFSET = 129;
    private static final int QUAD_IGNORE_MID_BLOCK_OFFSET = 130;
    private static final int QUAD_PADDING_OFFSET = 131;
    private static final int QUAD_BLOCK_ID_OFFSET = 132;
    private static final int QUAD_LOCAL_X_OFFSET = 136;
    private static final int QUAD_LOCAL_Y_OFFSET = 140;
    private static final int QUAD_LOCAL_Z_OFFSET = 144;
    private static final int QUAD_MATERIAL_BITS_OFFSET = 148;
    private static final int FLAT_QUAD_PACKED_NORMAL_OFFSET = 152;
    private static final int LIGHT_BLOCK_MATERIAL_BITS_OFFSET = 0;
    private static final int LIGHT_BLOCK_EMISSION_OFFSET = 4;
    private static final int LIGHT_BLOCK_ID_OFFSET = 8;
    private static final int LIGHT_BLOCK_LOCAL_X_OFFSET = 12;
    private static final int LIGHT_BLOCK_LOCAL_Y_OFFSET = 16;
    private static final int LIGHT_BLOCK_LOCAL_Z_OFFSET = 20;
    private static final int FLUID_FACE_PACKED_NORMAL_OFFSET = 0;
    private static final int FLUID_FACE_MATERIAL_BITS_OFFSET = 4;
    private static final int FLUID_FACE_BLOCK_EMISSION_OFFSET = 8;
    private static final int FLUID_FACE_RENDER_TYPE_OFFSET = 12;
    private static final int FLUID_FACE_IGNORE_MID_BLOCK_OFFSET = 16;
    private static final int FLUID_FACE_BLOCK_ID_OFFSET = 20;
    private static final int FLUID_FACE_LOCAL_X_OFFSET = 24;
    private static final int FLUID_FACE_LOCAL_Y_OFFSET = 28;
    private static final int FLUID_FACE_LOCAL_Z_OFFSET = 32;
    private static final int FLUID_FACE_KIND_OFFSET = 36;
    private static final int FLUID_FACE_FLIP_OFFSET = 40;
    private static final int FLUID_FACE_ORIGIN_X_OFFSET = 44;
    private static final int FLUID_FACE_ORIGIN_Y_OFFSET = 48;
    private static final int FLUID_FACE_ORIGIN_Z_OFFSET = 52;
    private static final int FLUID_FACE_Y_OFFSET_OFFSET = 56;
    private static final int FLUID_FACE_HEIGHTS_OFFSET = 60;
    private static final int FLUID_FACE_SIDE_COORDS_OFFSET = 76;
    private static final int FLUID_FACE_UVS_OFFSET = 92;
    private static final int FLUID_FACE_COLORS_OFFSET = 124;
    private static final int FLUID_FACE_AO_OFFSET = 140;
    private static final int FLUID_FACE_LIGHTS_OFFSET = 156;
    private static final int STATIC_MODEL_QUAD_VERTICES_OFFSET = 0;
    private static final int STATIC_MODEL_QUAD_MATERIAL_BITS_OFFSET = 112;
    private static final int STATIC_MODEL_QUAD_CULL_FACE_OFFSET = 116;
    private static final int STATIC_MODEL_QUAD_NORMAL_FACE_OFFSET = 120;
    private static final int STATIC_MODEL_QUAD_PACKED_NORMAL_OFFSET = 124;
    private static final int STATIC_MODEL_QUAD_BLOCK_EMISSION_OFFSET = 128;
    private static final int STATIC_MODEL_QUAD_RENDER_TYPE_OFFSET = 132;
    private static final int STATIC_MODEL_QUAD_SHADE_OFFSET = 136;
    private static final int STATIC_MODEL_QUAD_FLAGS_OFFSET = 140;
    private static final int STATIC_MODEL_QUAD_LIGHT_FACE_OFFSET = 144;
    private static final int STATIC_MODEL_QUAD_TINT_INDEX_OFFSET = 148;
    private static final int STATIC_MODEL_QUAD_HAS_AO_OFFSET = 152;
    private static final int STATIC_MODEL_QUAD_PASS_ID_OFFSET = 156;
    private static final int STATIC_MODEL_BLOCK_MODEL_ID_OFFSET = 0;
    private static final int STATIC_MODEL_BLOCK_MATERIAL_BITS_OFFSET = 4;
    private static final int STATIC_MODEL_BLOCK_EMISSION_OFFSET = 8;
    private static final int STATIC_MODEL_BLOCK_RENDER_TYPE_OFFSET = 12;
    private static final int STATIC_MODEL_BLOCK_ID_OFFSET = 16;
    private static final int STATIC_MODEL_BLOCK_LOCAL_X_OFFSET = 20;
    private static final int STATIC_MODEL_BLOCK_LOCAL_Y_OFFSET = 24;
    private static final int STATIC_MODEL_BLOCK_LOCAL_Z_OFFSET = 28;
    private static final int STATIC_MODEL_BLOCK_CULL_MASK_OFFSET = 32;
    private static final int STATIC_MODEL_BLOCK_OFFSET_X_OFFSET = 40;
    private static final int STATIC_MODEL_BLOCK_OFFSET_Y_OFFSET = 44;
    private static final int STATIC_MODEL_BLOCK_OFFSET_Z_OFFSET = 48;
    private static final int NATIVE_SECTION_BLOCK_STATE_ID_OFFSET = 0;
    private static final int NATIVE_SECTION_BLOCK_BLOCK_ID_OFFSET = 4;
    private static final int NATIVE_SECTION_BLOCK_LOCAL_X_OFFSET = 8;
    private static final int NATIVE_SECTION_BLOCK_LOCAL_Y_OFFSET = 12;
    private static final int NATIVE_SECTION_BLOCK_LOCAL_Z_OFFSET = 16;
    private static final int NATIVE_SECTION_BLOCK_SEED_LO_OFFSET = 20;
    private static final int NATIVE_SECTION_BLOCK_SEED_HI_OFFSET = 24;
    private static final int NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET = 28;
    private static final int NATIVE_SECTION_BLOCK_LIGHT_WORDS_OFFSET = 52;
    private static final int NATIVE_SECTION_BLOCK_NEIGHBORHOOD_STATE_IDS_OFFSET = 160;
    private static final int NATIVE_SECTION_BLOCK_TINT_OFFSET = 268;
    private static final int NATIVE_SECTION_BLOCK_FLUID_TINT_OFFSET = 272;
    private static final int NATIVE_SECTION_BLOCK_FLUID_FLOW_X_OFFSET = 276;
    private static final int NATIVE_SECTION_BLOCK_FLUID_FLOW_Z_OFFSET = 280;
    private static final int NATIVE_SECTION_BLOCK_ABSOLUTE_X_OFFSET = 284;
    private static final int NATIVE_SECTION_BLOCK_ABSOLUTE_Y_OFFSET = 288;
    private static final int NATIVE_SECTION_BLOCK_ABSOLUTE_Z_OFFSET = 292;
    private static final int NATIVE_SECTION_BLOCK_LEGACY_OFFSET_X_OFFSET = 296;
    private static final int NATIVE_SECTION_BLOCK_LEGACY_OFFSET_Y_OFFSET = 300;
    private static final int NATIVE_SECTION_BLOCK_LEGACY_OFFSET_Z_OFFSET = 304;
    private static final int NATIVE_SECTION_BLOCK_RESERVED_OFFSET = 308;
    private static final int NATIVE_SECTION_BLOCK_FLAGS_OFFSET = 312;
    private static final int NATIVE_MODEL_SELECTOR_ENTRY_TARGET_OFFSET = 0;
    private static final int NATIVE_MODEL_SELECTOR_ENTRY_WEIGHT_OFFSET = 4;
    public static final int COMPACT_VALUE_STRIDE = 0;
    public static final int COMPACT_VALUE_POSITION_OFFSET = 1;
    public static final int COMPACT_VALUE_COLOR_OFFSET = 2;
    public static final int COMPACT_VALUE_TEXTURE_OFFSET = 3;
    public static final int COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET = 4;
    public static final int COMPACT_VALUE_BLOCK_ID_OFFSET = 5;
    public static final int COMPACT_VALUE_NORMAL_OFFSET = 6;
    public static final int COMPACT_VALUE_TANGENT_OFFSET = 7;
    public static final int COMPACT_VALUE_MID_UV_OFFSET = 8;
    public static final int COMPACT_VALUE_MID_BLOCK_OFFSET = 9;
    public static final int COMPACT_VALUE_POSITION_MAX_VALUE = 10;
    public static final int COMPACT_VALUE_TEXTURE_MAX_VALUE = 11;
    private static final int POSITION_COMPONENT_X = 0;
    private static final int POSITION_COMPONENT_Y = 1;
    private static final int POSITION_COMPONENT_Z = 2;
    private static final int INDEX_MODE_NONE = 0;
    private static final int INDEX_MODE_SHARED = 1;
    private static final int INDEX_MODE_SORTED_QUADS = 2;
    private static final int INDEX_MODE_KEY_SORTED = 3;
    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle COMPACT_FORMAT_VALUE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_compact_format_value",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_NATIVE_QUAD_METADATA = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_write_metadata",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_NATIVE_QUAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_NATIVE_QUAD_VERTEX = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_write_vertex",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle NATIVE_QUAD_POSITION = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_native_quad_position",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_encode",
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
                    ValueLayout.JAVA_INT));
    private static final MethodHandle SCATTERED_ENCODE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_scattered_encode",
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
    private static final MethodHandle ASSEMBLE_OUTPUT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_mesh_output_assemble",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
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
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_SHARED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_shared_quad_index_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_SORTED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_sorted_quad_index_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle WRITE_KEY_SORTED = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_chunk_key_sorted_quad_index_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private NativeChunkMeshEncoder() {
    }

    public static void verifyAvailable() {
        check(invokeVerify(), "native chunk mesh encoder verification");
    }

    public static int compactFormatValue(int value) {
        int result = invokeCompactFormatValue(value);
        if (result < 0) {
            throw new IllegalArgumentException("Unknown compact chunk vertex format value: " + value);
        }
        return result;
    }

    public static NativeChunkVertexFormat compactNativeFormat() {
        return new NativeChunkVertexFormat(
                compactFormatValue(COMPACT_VALUE_STRIDE),
                compactFormatValue(COMPACT_VALUE_BLOCK_ID_OFFSET),
                compactFormatValue(COMPACT_VALUE_NORMAL_OFFSET),
                compactFormatValue(COMPACT_VALUE_TANGENT_OFFSET),
                compactFormatValue(COMPACT_VALUE_MID_UV_OFFSET),
                compactFormatValue(COMPACT_VALUE_MID_BLOCK_OFFSET));
    }

    public static void encode(
            ByteBuffer logicalVertices,
            int vertexCount,
            ByteBuffer output,
            int outputVertexOffset,
            NativeChunkVertexFormat format,
            int sectionIndex,
            boolean separateAo
    ) {
        if (vertexCount == 0) {
            return;
        }

        long inputAddress = MemoryUtil.memAddress(logicalVertices);
        long outputAddress = MemoryUtil.memAddress(output, outputVertexOffset * format.stride());

        check(invokeEncode(
                inputAddress,
                vertexCount,
                outputAddress,
                output.remaining() - outputVertexOffset * format.stride(),
                NATIVE_QUAD_STRIDE,
                format.stride(),
                format.blockIdOffset(),
                format.normalOffset(),
                format.tangentOffset(),
                format.midUvOffset(),
                format.midBlockOffset(),
                sectionIndex,
                separateAo ? 1 : 0
        ), "native chunk vertex encoding");
    }

    public static void encodeScattered(
            long inputAddress,
            int[] outputVertexOffsets,
            int updateCount,
            ByteBuffer output,
            NativeChunkVertexFormat format,
            int sectionIndex,
            boolean separateAo
    ) {
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

            check(invokeScatteredEncode(
                    inputAddress,
                    outputVertexOffsetsSegment,
                    updateCount,
                    MemoryUtil.memAddress(output),
                    output.remaining(),
                    NATIVE_QUAD_STRIDE,
                    format.stride(),
                    format.blockIdOffset(),
                    format.normalOffset(),
                    format.tangentOffset(),
                    format.midUvOffset(),
                    format.midBlockOffset(),
                    sectionIndex,
                    separateAo ? 1 : 0
            ), "native scattered chunk vertex encoding");
        }
    }

    public static void writeNativeQuadMetadata(long ptr, byte blockEmission, byte renderType, boolean ignoreMidBlock,
            int blockId, int localX, int localY, int localZ, int materialBits) {
        check(invokeWriteNativeQuadMetadata(ptr, blockEmission, renderType, ignoreMidBlock ? 1 : 0, blockId, localX,
                localY, localZ, materialBits), "native quad metadata writing");
    }

    public static void writeNativeQuadVertex(long ptr, int vertexIndex, float x, float y, float z, int color,
            float ao, float u, float v, int light) {
        if (vertexIndex < 0 || vertexIndex >= 4) {
            throw new IllegalArgumentException("Invalid quad vertex index: " + vertexIndex);
        }

        check(invokeWriteNativeQuadVertex(ptr, vertexIndex, x, y, z, color, ao, u, v, light),
                "native quad vertex writing");
    }

    public static void writeNativeQuad(
            long ptr,
            byte blockEmission,
            byte renderType,
            boolean ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        check(invokeWriteNativeQuad(ptr, blockEmission, renderType, ignoreMidBlock ? 1 : 0, blockId, localX, localY,
                localZ, materialBits,
                x0, y0, z0, color0, ao0, u0, v0, light0,
                x1, y1, z1, color1, ao1, u1, v1, light1,
                x2, y2, z2, color2, ao2, u2, v2, light2,
                x3, y3, z3, color3, ao3, u3, v3, light3), "native quad writing");
    }

    public static void writeNativeQuadMemory(
            long ptr,
            byte blockEmission,
            byte renderType,
            boolean ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        writeNativeQuadMemoryUnchecked(ptr, blockEmission, renderType, ignoreMidBlock, blockId, localX, localY,
                localZ, materialBits,
                x0, y0, z0, color0, ao0, u0, v0, light0,
                x1, y1, z1, color1, ao1, u1, v1, light1,
                x2, y2, z2, color2, ao2, u2, v2, light2,
                x3, y3, z3, color3, ao3, u3, v3, light3);
    }

    public static void writeFlatQuadRecord(
            long ptr,
            int packedNormal,
            byte blockEmission,
            byte renderType,
            boolean ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        writeNativeQuadMemoryUnchecked(ptr, blockEmission, renderType, ignoreMidBlock, blockId, localX, localY,
                localZ, materialBits,
                x0, y0, z0, color0, ao0, u0, v0, light0,
                x1, y1, z1, color1, ao1, u1, v1, light1,
                x2, y2, z2, color2, ao2, u2, v2, light2,
                x3, y3, z3, color3, ao3, u3, v3, light3);
        MemoryUtil.memPutInt(ptr + FLAT_QUAD_PACKED_NORMAL_OFFSET, packedNormal);
    }

    public static void writeLightBlockRecord(long ptr, int materialBits, byte blockEmission, int blockId,
            int localX, int localY, int localZ) {
        MemoryUtil.memPutInt(ptr + LIGHT_BLOCK_MATERIAL_BITS_OFFSET, materialBits);
        MemoryUtil.memPutInt(ptr + LIGHT_BLOCK_EMISSION_OFFSET, blockEmission & 0xff);
        MemoryUtil.memPutInt(ptr + LIGHT_BLOCK_ID_OFFSET, blockId);
        MemoryUtil.memPutInt(ptr + LIGHT_BLOCK_LOCAL_X_OFFSET, localX);
        MemoryUtil.memPutInt(ptr + LIGHT_BLOCK_LOCAL_Y_OFFSET, localY);
        MemoryUtil.memPutInt(ptr + LIGHT_BLOCK_LOCAL_Z_OFFSET, localZ);
    }

    public static void writeFluidFaceRecord(long ptr, int packedNormal, int materialBits, byte blockEmission,
            byte renderType, boolean ignoreMidBlock, int blockId, int localX, int localY, int localZ,
            int faceKind, boolean flip, int originX, int originY, int originZ, float yOffset,
            float height0, float height1, float height2, float height3,
            float sideX1, float sideZ1, float sideX2, float sideZ2,
            float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
            int color0, int color1, int color2, int color3,
            float ao0, float ao1, float ao2, float ao3,
            int light0, int light1, int light2, int light3) {
        MemoryUtil.memPutInt(ptr + FLUID_FACE_PACKED_NORMAL_OFFSET, packedNormal);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_MATERIAL_BITS_OFFSET, materialBits);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_BLOCK_EMISSION_OFFSET, blockEmission & 0xff);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_RENDER_TYPE_OFFSET, renderType & 0xff);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_IGNORE_MID_BLOCK_OFFSET, ignoreMidBlock ? 1 : 0);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_BLOCK_ID_OFFSET, blockId);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LOCAL_X_OFFSET, localX);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LOCAL_Y_OFFSET, localY);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LOCAL_Z_OFFSET, localZ);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_KIND_OFFSET, faceKind);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_FLIP_OFFSET, flip ? 1 : 0);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_ORIGIN_X_OFFSET, originX);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_ORIGIN_Y_OFFSET, originY);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_ORIGIN_Z_OFFSET, originZ);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_Y_OFFSET_OFFSET, yOffset);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_HEIGHTS_OFFSET, height0);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_HEIGHTS_OFFSET + 4, height1);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_HEIGHTS_OFFSET + 8, height2);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_HEIGHTS_OFFSET + 12, height3);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_SIDE_COORDS_OFFSET, sideX1);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_SIDE_COORDS_OFFSET + 4, sideZ1);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_SIDE_COORDS_OFFSET + 8, sideX2);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_SIDE_COORDS_OFFSET + 12, sideZ2);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET, u0);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 4, v0);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 8, u1);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 12, v1);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 16, u2);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 20, v2);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 24, u3);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_UVS_OFFSET + 28, v3);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_COLORS_OFFSET, color0);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_COLORS_OFFSET + 4, color1);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_COLORS_OFFSET + 8, color2);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_COLORS_OFFSET + 12, color3);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_AO_OFFSET, ao0);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_AO_OFFSET + 4, ao1);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_AO_OFFSET + 8, ao2);
        MemoryUtil.memPutFloat(ptr + FLUID_FACE_AO_OFFSET + 12, ao3);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LIGHTS_OFFSET, light0);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LIGHTS_OFFSET + 4, light1);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LIGHTS_OFFSET + 8, light2);
        MemoryUtil.memPutInt(ptr + FLUID_FACE_LIGHTS_OFFSET + 12, light3);
    }

    public static void writeStaticModelQuadRecord(long ptr, int materialBits, int cullFace, int normalFace,
            int packedNormal, byte blockEmission, byte renderType, boolean shade, int flags, int lightFace,
            int tintIndex, boolean hasAo,
            float x0, float y0, float z0, int color0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float u3, float v3, int light3) {
        writeStaticModelQuadRecord(ptr, materialBits, -1, cullFace, normalFace, packedNormal, blockEmission,
                renderType, shade, flags, lightFace, tintIndex, hasAo,
                x0, y0, z0, color0, u0, v0, light0,
                x1, y1, z1, color1, u1, v1, light1,
                x2, y2, z2, color2, u2, v2, light2,
                x3, y3, z3, color3, u3, v3, light3);
    }

    public static void writeStaticModelQuadRecord(long ptr, int materialBits, int passId, int cullFace, int normalFace,
            int packedNormal, byte blockEmission, byte renderType, boolean shade, int flags, int lightFace,
            int tintIndex, boolean hasAo,
            float x0, float y0, float z0, int color0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float u3, float v3, int light3) {
        writeStaticModelVertexRecord(ptr + STATIC_MODEL_QUAD_VERTICES_OFFSET,
                x0, y0, z0, color0, u0, v0, light0);
        writeStaticModelVertexRecord(ptr + STATIC_MODEL_QUAD_VERTICES_OFFSET + STATIC_MODEL_VERTEX_RECORD_STRIDE,
                x1, y1, z1, color1, u1, v1, light1);
        writeStaticModelVertexRecord(ptr + STATIC_MODEL_QUAD_VERTICES_OFFSET + 2L * STATIC_MODEL_VERTEX_RECORD_STRIDE,
                x2, y2, z2, color2, u2, v2, light2);
        writeStaticModelVertexRecord(ptr + STATIC_MODEL_QUAD_VERTICES_OFFSET + 3L * STATIC_MODEL_VERTEX_RECORD_STRIDE,
                x3, y3, z3, color3, u3, v3, light3);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_MATERIAL_BITS_OFFSET, materialBits);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_CULL_FACE_OFFSET, cullFace);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_NORMAL_FACE_OFFSET, normalFace);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_PACKED_NORMAL_OFFSET, packedNormal);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_BLOCK_EMISSION_OFFSET, blockEmission & 0xff);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_RENDER_TYPE_OFFSET, renderType & 0xff);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_SHADE_OFFSET, shade ? 1 : 0);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_FLAGS_OFFSET, flags);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_LIGHT_FACE_OFFSET, lightFace);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_TINT_INDEX_OFFSET, tintIndex);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_HAS_AO_OFFSET, hasAo ? 1 : 0);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_QUAD_PASS_ID_OFFSET, passId);
    }

    public static void writeStaticModelQuadRecord(long ptr, int materialBits, int cullFace, int normalFace,
            int packedNormal, byte blockEmission, byte renderType, boolean shade,
            float x0, float y0, float z0, int color0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float u3, float v3, int light3) {
        writeStaticModelQuadRecord(ptr, materialBits, -1, cullFace, normalFace, packedNormal, blockEmission, renderType,
                shade, 0, cullFace >= 0 ? cullFace : 1, -1, true,
                x0, y0, z0, color0, u0, v0, light0,
                x1, y1, z1, color1, u1, v1, light1,
                x2, y2, z2, color2, u2, v2, light2,
                x3, y3, z3, color3, u3, v3, light3);
    }

    public static void writeStaticModelBlockRecord(long ptr, int modelId, int materialBits, byte blockEmission,
            byte renderType, int blockId, int localX, int localY, int localZ, int cullMask,
            float offsetX, float offsetY, float offsetZ) {
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_MODEL_ID_OFFSET, modelId);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_MATERIAL_BITS_OFFSET, materialBits);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_EMISSION_OFFSET, blockEmission & 0xff);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_RENDER_TYPE_OFFSET, renderType & 0xff);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_ID_OFFSET, blockId);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_LOCAL_X_OFFSET, localX);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_LOCAL_Y_OFFSET, localY);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_LOCAL_Z_OFFSET, localZ);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_CULL_MASK_OFFSET, cullMask);
        MemoryUtil.memPutInt(ptr + STATIC_MODEL_BLOCK_CULL_MASK_OFFSET + 4, 0);
        MemoryUtil.memPutFloat(ptr + STATIC_MODEL_BLOCK_OFFSET_X_OFFSET, offsetX);
        MemoryUtil.memPutFloat(ptr + STATIC_MODEL_BLOCK_OFFSET_Y_OFFSET, offsetY);
        MemoryUtil.memPutFloat(ptr + STATIC_MODEL_BLOCK_OFFSET_Z_OFFSET, offsetZ);
    }

    public static void writeNativeSectionBlockRecord(long ptr, int stateId, int blockId, int localX, int localY,
            int localZ, long seed, int neighborDown, int neighborUp, int neighborNorth, int neighborSouth,
            int neighborWest, int neighborEast, int[] lightWords, int[] neighborhoodStateIds, int tint,
            int fluidTint, float fluidFlowX, float fluidFlowZ, int absoluteX, int absoluteY, int absoluteZ) {
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_STATE_ID_OFFSET, stateId);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_BLOCK_ID_OFFSET, blockId);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_LOCAL_X_OFFSET, localX);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_LOCAL_Y_OFFSET, localY);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_LOCAL_Z_OFFSET, localZ);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_SEED_LO_OFFSET, (int) seed);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_SEED_HI_OFFSET, (int) (seed >>> 32));
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET, neighborDown);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET + 4, neighborUp);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET + 8, neighborNorth);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET + 12, neighborSouth);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET + 16, neighborWest);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBOR_IDS_OFFSET + 20, neighborEast);
        for (int i = 0; i < 27; i++) {
            MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_LIGHT_WORDS_OFFSET + (long) i * 4, lightWords[i]);
            MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_NEIGHBORHOOD_STATE_IDS_OFFSET + (long) i * 4, neighborhoodStateIds[i]);
        }
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_TINT_OFFSET, tint);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_FLUID_TINT_OFFSET, fluidTint);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_FLUID_FLOW_X_OFFSET, fluidFlowX);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_FLUID_FLOW_Z_OFFSET, fluidFlowZ);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_ABSOLUTE_X_OFFSET, absoluteX);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_ABSOLUTE_Y_OFFSET, absoluteY);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_ABSOLUTE_Z_OFFSET, absoluteZ);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_LEGACY_OFFSET_X_OFFSET, 0.0F);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_LEGACY_OFFSET_Y_OFFSET, 0.0F);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_LEGACY_OFFSET_Z_OFFSET, 0.0F);
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_RESERVED_OFFSET, -1);
        for (int offset = 0; offset < 8; offset += 4) {
            if (offset != 0) {
                MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_RESERVED_OFFSET + offset, 0);
            }
        }
    }

    public static void writeNativeSectionBlockFluidBlockId(long ptr, int fluidBlockId) {
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_RESERVED_OFFSET, fluidBlockId);
    }

    public static void writeNativeSectionBlockFlags(long ptr, int flags) {
        MemoryUtil.memPutInt(ptr + NATIVE_SECTION_BLOCK_FLAGS_OFFSET, flags);
    }

    public static void writeNativeSectionBlockRecord(long ptr, int stateId, int blockId, int localX, int localY,
            int localZ, long seed, int neighborDown, int neighborUp, int neighborNorth, int neighborSouth,
            int neighborWest, int neighborEast, int lightmap, float offsetX, float offsetY, float offsetZ) {
        int[] lightWords = new int[27];
        int packedWord = ((LightTextureBlock(lightmap) & 0xF) | ((LightTextureSky(lightmap) & 0xF) << 4));
        for (int i = 0; i < lightWords.length; i++) {
            lightWords[i] = packedWord;
        }
        int[] neighborhoodStateIds = new int[27];
        for (int i = 0; i < neighborhoodStateIds.length; i++) {
            neighborhoodStateIds[i] = 0;
        }
        neighborhoodStateIds[13] = stateId;
        writeNativeSectionBlockRecord(ptr, stateId, blockId, localX, localY, localZ, seed, neighborDown, neighborUp,
                neighborNorth, neighborSouth, neighborWest, neighborEast, lightWords, neighborhoodStateIds, -1, -1,
                0.0F, 0.0F, localX, localY, localZ);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_LEGACY_OFFSET_X_OFFSET, offsetX);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_LEGACY_OFFSET_Y_OFFSET, offsetY);
        MemoryUtil.memPutFloat(ptr + NATIVE_SECTION_BLOCK_LEGACY_OFFSET_Z_OFFSET, offsetZ);
    }

    private static int LightTextureBlock(int lightmap) {
        return (lightmap >> 4) & 0xF;
    }

    private static int LightTextureSky(int lightmap) {
        return (lightmap >> 20) & 0xF;
    }

    public static void writeNativeModelSelectorEntry(long ptr, int targetId, int weight) {
        MemoryUtil.memPutInt(ptr + NATIVE_MODEL_SELECTOR_ENTRY_TARGET_OFFSET, targetId);
        MemoryUtil.memPutInt(ptr + NATIVE_MODEL_SELECTOR_ENTRY_WEIGHT_OFFSET, weight);
    }

    private static void writeStaticModelVertexRecord(long ptr, float x, float y, float z, int color,
            float u, float v, int light) {
        MemoryUtil.memPutFloat(ptr, x);
        MemoryUtil.memPutFloat(ptr + 4, y);
        MemoryUtil.memPutFloat(ptr + 8, z);
        MemoryUtil.memPutInt(ptr + 12, color);
        MemoryUtil.memPutFloat(ptr + 16, u);
        MemoryUtil.memPutFloat(ptr + 20, v);
        MemoryUtil.memPutInt(ptr + 24, light);
    }

    private static void writeNativeQuadMemoryUnchecked(
            long ptr,
            byte blockEmission,
            byte renderType,
            boolean ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        writeNativeQuadVertexMemory(ptr, 0, x0, y0, z0, color0, ao0, u0, v0, light0);
        writeNativeQuadVertexMemory(ptr, 1, x1, y1, z1, color1, ao1, u1, v1, light1);
        writeNativeQuadVertexMemory(ptr, 2, x2, y2, z2, color2, ao2, u2, v2, light2);
        writeNativeQuadVertexMemory(ptr, 3, x3, y3, z3, color3, ao3, u3, v3, light3);
        MemoryUtil.memPutByte(ptr + QUAD_BLOCK_EMISSION_OFFSET, blockEmission);
        MemoryUtil.memPutByte(ptr + QUAD_RENDER_TYPE_OFFSET, renderType);
        MemoryUtil.memPutByte(ptr + QUAD_IGNORE_MID_BLOCK_OFFSET, (byte) (ignoreMidBlock ? 1 : 0));
        MemoryUtil.memPutByte(ptr + QUAD_PADDING_OFFSET, (byte) 0);
        MemoryUtil.memPutInt(ptr + QUAD_BLOCK_ID_OFFSET, blockId);
        MemoryUtil.memPutInt(ptr + QUAD_LOCAL_X_OFFSET, localX);
        MemoryUtil.memPutInt(ptr + QUAD_LOCAL_Y_OFFSET, localY);
        MemoryUtil.memPutInt(ptr + QUAD_LOCAL_Z_OFFSET, localZ);
        MemoryUtil.memPutInt(ptr + QUAD_MATERIAL_BITS_OFFSET, materialBits);
    }

    private static void writeNativeQuadVertexMemory(long ptr, int vertexIndex, float x, float y, float z,
            int color, float ao, float u, float v, int light) {
        long vertexPtr = ptr + (long) vertexIndex * VERTEX_STRIDE;
        MemoryUtil.memPutFloat(vertexPtr + VERTEX_X_OFFSET, x);
        MemoryUtil.memPutFloat(vertexPtr + VERTEX_Y_OFFSET, y);
        MemoryUtil.memPutFloat(vertexPtr + VERTEX_Z_OFFSET, z);
        MemoryUtil.memPutInt(vertexPtr + VERTEX_COLOR_OFFSET, color);
        MemoryUtil.memPutFloat(vertexPtr + VERTEX_AO_OFFSET, ao);
        MemoryUtil.memPutFloat(vertexPtr + VERTEX_U_OFFSET, u);
        MemoryUtil.memPutFloat(vertexPtr + VERTEX_V_OFFSET, v);
        MemoryUtil.memPutInt(vertexPtr + VERTEX_LIGHT_OFFSET, light);
    }

    public static float nativeQuadX(long ptr, int vertexIndex) {
        return nativeQuadPosition(ptr, vertexIndex, POSITION_COMPONENT_X);
    }

    public static float nativeQuadY(long ptr, int vertexIndex) {
        return nativeQuadPosition(ptr, vertexIndex, POSITION_COMPONENT_Y);
    }

    public static float nativeQuadZ(long ptr, int vertexIndex) {
        return nativeQuadPosition(ptr, vertexIndex, POSITION_COMPONENT_Z);
    }

    private static float nativeQuadPosition(long ptr, int vertexIndex, int component) {
        if (vertexIndex < 0 || vertexIndex >= 4) {
            throw new IllegalArgumentException("Invalid quad vertex index: " + vertexIndex);
        }

        return invokeNativeQuadPosition(ptr, vertexIndex, component);
    }

    public static void assemble(
            long[] inputAddresses,
            int[] inputVertexCounts,
            ByteBuffer output,
            int[] vertexSegments,
            NativeChunkVertexFormat format,
            int sectionIndex,
            int visibleSlices,
            boolean forceUnassigned,
            boolean sliceReordering,
            boolean separateAo
    ) {
        assembleOutput(inputAddresses, inputVertexCounts, output, vertexSegments, format, sectionIndex,
                visibleSlices, forceUnassigned, sliceReordering, separateAo, null, INDEX_MODE_NONE, 0, null, 0);
    }

    public static void assembleWithSharedIndex(
            long[] inputAddresses,
            int[] inputVertexCounts,
            ByteBuffer output,
            int[] vertexSegments,
            NativeChunkVertexFormat format,
            int sectionIndex,
            int visibleSlices,
            boolean forceUnassigned,
            boolean sliceReordering,
            boolean separateAo,
            ByteBuffer indexOutput,
            int indexStride
    ) {
        assembleOutput(inputAddresses, inputVertexCounts, output, vertexSegments, format, sectionIndex,
                visibleSlices, forceUnassigned, sliceReordering, separateAo,
                indexOutput, INDEX_MODE_SHARED, indexStride, null, 0);
    }

    private static void assembleOutput(
            long[] inputAddresses,
            int[] inputVertexCounts,
            ByteBuffer output,
            int[] vertexSegments,
            NativeChunkVertexFormat format,
            int sectionIndex,
            int visibleSlices,
            boolean forceUnassigned,
            boolean sliceReordering,
            boolean separateAo,
            ByteBuffer indexOutput,
            int indexMode,
            int indexStride,
            int[] indexValues,
            int indexValueCount
    ) {
        if (inputAddresses.length != ModelQuadFacing.COUNT || inputVertexCounts.length != ModelQuadFacing.COUNT) {
            throw new IllegalArgumentException("Expected one input buffer per chunk quad facing");
        }

        if (vertexSegments.length != ModelQuadFacing.COUNT << 1) {
            throw new IllegalArgumentException("Unexpected vertex segment array length: " + vertexSegments.length);
        }
        if (indexValueCount < 0 || (indexValues != null && indexValueCount > indexValues.length)) {
            throw new IllegalArgumentException("Invalid index value count: " + indexValueCount);
        }
        if (indexMode != INDEX_MODE_NONE && indexOutput == null) {
            throw new IllegalArgumentException("Index output buffer is required for index mode " + indexMode);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inputAddressesSegment = arena.allocate(ValueLayout.JAVA_LONG, inputAddresses.length);
            MemorySegment inputVertexCountsSegment = arena.allocate(ValueLayout.JAVA_INT, inputVertexCounts.length);
            MemorySegment vertexSegmentsSegment = arena.allocate(ValueLayout.JAVA_INT, vertexSegments.length);
            MemorySegment indexValuesSegment = MemorySegment.NULL;

            for (int index = 0; index < inputAddresses.length; index++) {
                inputAddressesSegment.setAtIndex(ValueLayout.JAVA_LONG, index, inputAddresses[index]);
                inputVertexCountsSegment.setAtIndex(ValueLayout.JAVA_INT, index, inputVertexCounts[index]);
            }
            if (indexValues != null && indexValueCount > 0) {
                indexValuesSegment = arena.allocate(ValueLayout.JAVA_INT, indexValueCount);

                for (int index = 0; index < indexValueCount; index++) {
                    indexValuesSegment.setAtIndex(ValueLayout.JAVA_INT, index, indexValues[index]);
                }
            }

            check(invokeAssembleOutput(
                    inputAddressesSegment,
                    inputVertexCountsSegment,
                    ModelQuadFacing.COUNT,
                    MemoryUtil.memAddress(output),
                    output.remaining(),
                    vertexSegmentsSegment,
                    vertexSegments.length,
                    NATIVE_QUAD_STRIDE,
                    format.stride(),
                    format.blockIdOffset(),
                    format.normalOffset(),
                    format.tangentOffset(),
                    format.midUvOffset(),
                    format.midBlockOffset(),
                    sectionIndex,
                    visibleSlices,
                    forceUnassigned ? 1 : 0,
                    sliceReordering ? 1 : 0,
                    separateAo ? 1 : 0,
                    indexOutput == null ? 0L : MemoryUtil.memAddress(indexOutput),
                    indexOutput == null ? 0 : indexOutput.remaining(),
                    indexMode,
                    indexStride,
                    indexValuesSegment,
                    indexValueCount
            ), "native chunk mesh output assembly");

            for (int index = 0; index < vertexSegments.length; index++) {
                vertexSegments[index] = vertexSegmentsSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }
    }

    public static void writeSharedQuadIndexBuffer(ByteBuffer output, int indexStride, int primitiveCount) {
        if (primitiveCount == 0) {
            return;
        }

        check(invokeWriteShared(MemoryUtil.memAddress(output), output.remaining(), indexStride, primitiveCount),
                "native shared quad index buffer writing");
    }

    public static void writeQuadVertexIndexes(IntBuffer output, int[] quadIndexes) {
        writeQuadVertexIndexes(output, quadIndexes, quadIndexes.length);
    }

    public static void writeQuadVertexIndexes(IntBuffer output, int[] quadIndexes, int quadIndexCount) {
        if (quadIndexCount < 0 || quadIndexCount > quadIndexes.length) {
            throw new IllegalArgumentException("Invalid quad index count: " + quadIndexCount);
        }
        if (quadIndexCount == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment quadIndexSegment = arena.allocate(ValueLayout.JAVA_INT, quadIndexCount);

            for (int index = 0; index < quadIndexCount; index++) {
                quadIndexSegment.setAtIndex(ValueLayout.JAVA_INT, index, quadIndexes[index]);
            }

            check(invokeWriteSorted(MemoryUtil.memAddress(output), output.remaining(), quadIndexSegment, quadIndexCount),
                    "native sorted quad index buffer writing");
            output.position(output.position() + quadIndexCount * 6);
        }
    }

    public static void writeQuadVertexIndexesSortedByKey(IntBuffer output, int[] keys) {
        writeQuadVertexIndexesSortedByKey(output, keys, 0, keys.length);
    }

    public static void writeQuadVertexIndexesSortedByKey(IntBuffer output, int[] keys, int offset, int count) {
        if (offset < 0 || count < 0 || offset + count > keys.length) {
            throw new IllegalArgumentException("Invalid key range: offset=" + offset + ", count=" + count);
        }
        if (count == 0) {
            return;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keysSegment = arena.allocate(ValueLayout.JAVA_INT, count);

            for (int index = 0; index < count; index++) {
                keysSegment.setAtIndex(ValueLayout.JAVA_INT, index, keys[offset + index]);
            }

            check(invokeWriteKeySorted(MemoryUtil.memAddress(output), output.remaining(), keysSegment, count),
                    "native key-sorted quad index buffer writing");
            output.position(output.position() + count * 6);
        }
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeVerify() {
        try {
            return (int) VERIFY.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk mesh verification downcall failed", throwable);
        }
    }

    private static int invokeCompactFormatValue(int value) {
        try {
            return (int) COMPACT_FORMAT_VALUE.invokeExact(value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust compact chunk vertex format downcall failed", throwable);
        }
    }

    private static int invokeWriteNativeQuadMetadata(
            long ptr,
            int blockEmission,
            int renderType,
            int ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits
    ) {
        try {
            return (int) WRITE_NATIVE_QUAD_METADATA.invokeExact(ptr, blockEmission, renderType, ignoreMidBlock,
                    blockId, localX, localY, localZ, materialBits);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad metadata downcall failed", throwable);
        }
    }

    private static int invokeWriteNativeQuadVertex(
            long ptr,
            int vertexIndex,
            float x,
            float y,
            float z,
            int color,
            float ao,
            float u,
            float v,
            int light
    ) {
        try {
            return (int) WRITE_NATIVE_QUAD_VERTEX.invokeExact(ptr, vertexIndex, x, y, z, color, ao, u, v, light);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad vertex downcall failed", throwable);
        }
    }

    private static int invokeWriteNativeQuad(
            long ptr,
            int blockEmission,
            int renderType,
            int ignoreMidBlock,
            int blockId,
            int localX,
            int localY,
            int localZ,
            int materialBits,
            float x0, float y0, float z0, int color0, float ao0, float u0, float v0, int light0,
            float x1, float y1, float z1, int color1, float ao1, float u1, float v1, int light1,
            float x2, float y2, float z2, int color2, float ao2, float u2, float v2, int light2,
            float x3, float y3, float z3, int color3, float ao3, float u3, float v3, int light3
    ) {
        try {
            return (int) WRITE_NATIVE_QUAD.invokeExact(ptr, blockEmission, renderType, ignoreMidBlock, blockId,
                    localX, localY, localZ, materialBits,
                    x0, y0, z0, color0, ao0, u0, v0, light0,
                    x1, y1, z1, color1, ao1, u1, v1, light1,
                    x2, y2, z2, color2, ao2, u2, v2, light2,
                    x3, y3, z3, color3, ao3, u3, v3, light3);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad downcall failed", throwable);
        }
    }

    private static float invokeNativeQuadPosition(long ptr, int vertexIndex, int component) {
        try {
            return (float) NATIVE_QUAD_POSITION.invokeExact(ptr, vertexIndex, component);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native quad position downcall failed", throwable);
        }
    }

    private static int invokeEncode(
            long inputAddress,
            int vertexCount,
            long outputAddress,
            int outputCapacity,
            int quadStride,
            int vertexStride,
            int blockIdOffset,
            int normalOffset,
            int tangentOffset,
            int midUvOffset,
            int midBlockOffset,
            int sectionIndex,
            int separateAo
    ) {
        try {
            return (int) ENCODE.invokeExact(inputAddress, vertexCount, outputAddress, outputCapacity, quadStride, vertexStride,
                    blockIdOffset, normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk vertex encoding downcall failed", throwable);
        }
    }

    private static int invokeScatteredEncode(
            long inputAddress,
            MemorySegment outputVertexOffsets,
            int updateCount,
            long outputAddress,
            int outputCapacity,
            int quadStride,
            int vertexStride,
            int blockIdOffset,
            int normalOffset,
            int tangentOffset,
            int midUvOffset,
            int midBlockOffset,
            int sectionIndex,
            int separateAo
    ) {
        try {
            return (int) SCATTERED_ENCODE.invokeExact(inputAddress, outputVertexOffsets, updateCount, outputAddress,
                    outputCapacity, quadStride, vertexStride, blockIdOffset, normalOffset, tangentOffset, midUvOffset,
                    midBlockOffset, sectionIndex, separateAo);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust scattered chunk vertex encoding downcall failed", throwable);
        }
    }

    private static int invokeAssembleOutput(
            MemorySegment inputAddresses,
            MemorySegment inputVertexCounts,
            int inputCount,
            long outputAddress,
            int outputCapacity,
            MemorySegment vertexSegments,
            int vertexSegmentsLength,
            int quadStride,
            int vertexStride,
            int blockIdOffset,
            int normalOffset,
            int tangentOffset,
            int midUvOffset,
            int midBlockOffset,
            int sectionIndex,
            int visibleSlices,
            int forceUnassigned,
            int sliceReordering,
            int separateAo,
            long indexOutputAddress,
            int indexOutputCapacity,
            int indexMode,
            int indexStride,
            MemorySegment indexValues,
            int indexValueCount
    ) {
        try {
            return (int) ASSEMBLE_OUTPUT.invokeExact(inputAddresses, inputVertexCounts, inputCount, outputAddress,
                    outputCapacity, vertexSegments, vertexSegmentsLength, quadStride, vertexStride, blockIdOffset,
                    normalOffset, tangentOffset, midUvOffset, midBlockOffset, sectionIndex, visibleSlices,
                    forceUnassigned, sliceReordering, separateAo, indexOutputAddress, indexOutputCapacity, indexMode,
                    indexStride, indexValues, indexValueCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust chunk mesh output assembly downcall failed", throwable);
        }
    }

    private static int invokeWriteShared(long outputAddress, int outputCapacity, int indexStride, int primitiveCount) {
        try {
            return (int) WRITE_SHARED.invokeExact(outputAddress, outputCapacity, indexStride, primitiveCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust shared quad index buffer downcall failed", throwable);
        }
    }

    private static int invokeWriteSorted(long outputAddress, int outputCapacity, MemorySegment quadIndexes, int quadIndexCount) {
        try {
            return (int) WRITE_SORTED.invokeExact(outputAddress, outputCapacity, quadIndexes, quadIndexCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust sorted quad index buffer downcall failed", throwable);
        }
    }

    private static int invokeWriteKeySorted(long outputAddress, int outputCapacity, MemorySegment keys, int keyCount) {
        try {
            return (int) WRITE_KEY_SORTED.invokeExact(outputAddress, outputCapacity, keys, keyCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust key-sorted quad index buffer downcall failed", throwable);
        }
    }
}
