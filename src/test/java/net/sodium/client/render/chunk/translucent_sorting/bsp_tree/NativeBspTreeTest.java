package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.translucent_sorting.quad.NativeFullTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.RegularTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.client.util.NativeBuffer;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeBspTreeTest {
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
    void nativeBspBuilderConstructsStaticNormalRelativeLeafInRust() {
        TQuad[] quads = new TQuad[] {
                zRegularQuad(0.0F, ModelQuadFacing.POS_Z,
                        ModelQuadFacing.POS_Z.getPackedAlignedNormal()),
                zRegularQuad(1.0F, ModelQuadFacing.POS_Z,
                        ModelQuadFacing.POS_Z.getPackedAlignedNormal()),
                zRegularQuad(2.0F, ModelQuadFacing.POS_Z,
                        ModelQuadFacing.POS_Z.getPackedAlignedNormal())
        };
        NativeBuffer output = new NativeBuffer(3 * TranslucentData.BYTES_PER_QUAD);

        try (NativeBspBuildResult result = BSPNode.buildBSP(quads, SectionPos.of(0, 0, 0), null, false,
                QuadSplittingMode.OFF)) {
            assertArrayEquals(new int[] {
                    0, 1, 2, 2, 3, 0,
                    4, 5, 6, 6, 7, 4,
                    8, 9, 10, 10, 11, 8
            }, writeAndRead(result, output, 18));
        } finally {
            output.free();
        }
    }

    @Test
    void nativeBspBuilderAcceptsSplitModeNativeFullQuads() {
        TQuad[] quads = new TQuad[] {
                zNativeFullQuad(0.0F, ModelQuadFacing.POS_Z, ModelQuadFacing.POS_Z.getPackedAlignedNormal())
        };
        NativeBuffer output = new NativeBuffer(TranslucentData.BYTES_PER_QUAD);

        try (NativeBspBuildResult result = BSPNode.buildBSP(quads, SectionPos.of(0, 0, 0), null, false,
                QuadSplittingMode.SAFE)) {
            assertNull(result.updatedQuads());
            assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0}, writeAndRead(result, output, 6));
        } finally {
            output.free();
        }
    }

    @Test
    void bspRuntimeStateWasRemovedFromJava() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPSortState.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspSortState.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/LeafSingleBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/LeafDoubleBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/LeafMultiBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPResult.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/InnerFixedDoubleBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/InnerBinaryPartitionBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/InnerMultiPartitionBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/InnerPartitionBSPNode.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/Partition.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPWorkspace.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspTree.java")));
        assertTrue(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspBuildResult.java")));
        assertTrue(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspBuilder.java")));
    }

    @Test
    void dynamicBspDataConsumesNativeBuildResult() throws Exception {
        String dynamicBspData = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/DynamicBSPData.java"));
        String nativeBuildResult = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspBuildResult.java"));
        String bspNode = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPNode.java"));
        String nativeBspBuilder = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspBuilder.java"));

        assertFalse(dynamicBspData.contains("NativeBspTree.fromRoot("));
        assertFalse(nativeBuildResult.contains("NativeBspTree"));
        assertFalse(nativeBuildResult.contains("rootNode.addTo("));
        assertFalse(nativeBuildResult.contains("Builder.create()"));
        assertTrue(dynamicBspData.contains("NativeBspBuildResult"));
        assertTrue(bspNode.contains("NativeBspBuilder.build(quads, result, oldRoot,"));
        assertTrue(bspNode.contains("prepareNodeReuse, quadSplittingMode)"));
        assertTrue(bspNode.contains("mattmc_sodium_translucent_bsp_reusable_root_destroy"));
        assertTrue(nativeBspBuilder.contains("mattmc_sodium_translucent_bsp_build_records"));
        assertTrue(nativeBspBuilder.contains("mattmc_sodium_translucent_bsp_build_full_quads"));
        assertTrue(nativeBspBuilder.contains("reusableRootHandle"));
        assertTrue(nativeBuildResult.contains("mattmc_sodium_translucent_bsp_build_result_write_index_buffer"));
    }

    private static int[] readInts(ByteBuffer buffer, int count) {
        ByteBuffer nativeOrderBuffer = buffer.order(ByteOrder.nativeOrder());
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = nativeOrderBuffer.getInt(index * Integer.BYTES);
        }
        return values;
    }

    private static int[] writeAndRead(NativeBspBuildResult result, NativeBuffer output, int count) {
        result.writeIndexBuffer(output, new Vector3f());
        return readInts(output.getDirectBuffer(), count);
    }

    private static RegularTQuad zRegularQuad(float z, ModelQuadFacing facing, int packedNormal) {
        return RegularTQuad.fromPositions(zPositions(z), facing, packedNormal);
    }

    private static NativeFullTQuad zNativeFullQuad(float z, ModelQuadFacing facing, int packedNormal) {
        ByteBuffer quad = MemoryUtil.memAlloc(NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE).order(ByteOrder.nativeOrder());
        try {
            writeZQuad(MemoryUtil.memAddress(quad), z);
            return NativeFullTQuad.fromNativeQuad(MemoryUtil.memAddress(quad), facing, packedNormal);
        } finally {
            MemoryUtil.memFree(quad);
        }
    }

    private static float[] zPositions(float z) {
        return new float[] {
                0.0F, 0.0F, z,
                1.0F, 0.0F, z,
                1.0F, 1.0F, z,
                0.0F, 1.0F, z
        };
    }

    private static void writeZQuad(long quadAddress, float z) {
        NativeChunkMeshEncoder.writeNativeQuadMetadata(quadAddress, (byte) 0, (byte) 0, false,
                0, 0, 0, 0, 0);
        NativeChunkMeshEncoder.writeNativeQuadVertex(quadAddress, 0, 0.0F, 0.0F, z,
                0xffffffff, 1.0F, 0.0F, 0.0F, 0);
        NativeChunkMeshEncoder.writeNativeQuadVertex(quadAddress, 1, 1.0F, 0.0F, z,
                0xffffffff, 1.0F, 0.0F, 0.0F, 0);
        NativeChunkMeshEncoder.writeNativeQuadVertex(quadAddress, 2, 1.0F, 1.0F, z,
                0xffffffff, 1.0F, 0.0F, 0.0F, 0);
        NativeChunkMeshEncoder.writeNativeQuadVertex(quadAddress, 3, 0.0F, 1.0F, z,
                0xffffffff, 1.0F, 0.0F, 0.0F, 0);
    }
}
