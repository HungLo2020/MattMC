package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.util.NativeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void nativeBspTreeWritesMultiLeafOrder() {
        NativeBuffer output = new NativeBuffer(2 * TranslucentData.BYTES_PER_QUAD);

        try (NativeBspTree.Builder builder = NativeBspTree.Builder.create()) {
            int leaf = builder.addLeafMulti(new int[] {1, 0});
            try (NativeBspTree tree = builder.finish(leaf, 2)) {
                tree.writeIndexBuffer(output, new Vector3f());

                assertArrayEquals(new int[] {4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0},
                        readInts(output.getDirectBuffer(), 12));
            }
        } finally {
            output.free();
        }
    }

    @Test
    void nativeBspTreeTraversesBinaryPartitionByCameraSide() {
        NativeBuffer output = new NativeBuffer(3 * TranslucentData.BYTES_PER_QUAD);

        try (NativeBspTree.Builder builder = NativeBspTree.Builder.create()) {
            int inside = builder.addLeafSingle(0);
            int outside = builder.addLeafSingle(1);
            int root = builder.addBinary(NativeBspTree.Remap.NONE, new Vector3f(1.0f, 0.0f, 0.0f),
                    0.5f, inside, outside, new int[] {2});

            try (NativeBspTree tree = builder.finish(root, 3)) {
                tree.writeIndexBuffer(output, new Vector3f(0.0f, 0.0f, 0.0f));
                assertArrayEquals(new int[] {
                        4, 5, 6, 6, 7, 4,
                        8, 9, 10, 10, 11, 8,
                        0, 1, 2, 2, 3, 0
                }, readInts(output.getDirectBuffer(), 18));

                tree.writeIndexBuffer(output, new Vector3f(1.0f, 0.0f, 0.0f));
                assertArrayEquals(new int[] {
                        0, 1, 2, 2, 3, 0,
                        8, 9, 10, 10, 11, 8,
                        4, 5, 6, 6, 7, 4
                }, readInts(output.getDirectBuffer(), 18));
            }
        } finally {
            output.free();
        }
    }

    @Test
    void nativeBspTreeTraversesFixedDoubleInNativeOrder() {
        NativeBuffer output = new NativeBuffer(3 * TranslucentData.BYTES_PER_QUAD);

        try (NativeBspTree.Builder builder = NativeBspTree.Builder.create()) {
            int first = builder.addLeafDouble(0, 1);
            int second = builder.addLeafSingle(2);
            int root = builder.addFixedDouble(NativeBspTree.Remap.NONE, first, second);

            try (NativeBspTree tree = builder.finish(root, 3)) {
                tree.writeIndexBuffer(output, new Vector3f());

                assertArrayEquals(new int[] {
                        0, 1, 2, 2, 3, 0,
                        4, 5, 6, 6, 7, 4,
                        8, 9, 10, 10, 11, 8
                }, readInts(output.getDirectBuffer(), 18));
            }
        } finally {
            output.free();
        }
    }

    @Test
    void nativeBspTreeTraversesMultiPartitionByCameraInterval() {
        NativeBuffer output = new NativeBuffer(5 * TranslucentData.BYTES_PER_QUAD);

        try (NativeBspTree.Builder builder = NativeBspTree.Builder.create()) {
            int first = builder.addLeafSingle(0);
            int middle = builder.addLeafSingle(1);
            int last = builder.addLeafSingle(2);
            int root = builder.addMultiPartition(NativeBspTree.Remap.NONE, new Vector3f(1.0f, 0.0f, 0.0f),
                    new float[] {0.5f, 1.5f}, new int[] {first, middle, last}, new int[][] {{3}, {4}});

            try (NativeBspTree tree = builder.finish(root, 5)) {
                tree.writeIndexBuffer(output, new Vector3f(0.0f, 0.0f, 0.0f));
                assertArrayEquals(new int[] {
                        8, 9, 10, 10, 11, 8,
                        16, 17, 18, 18, 19, 16,
                        4, 5, 6, 6, 7, 4,
                        12, 13, 14, 14, 15, 12,
                        0, 1, 2, 2, 3, 0
                }, readInts(output.getDirectBuffer(), 30));

                tree.writeIndexBuffer(output, new Vector3f(2.0f, 0.0f, 0.0f));
                assertArrayEquals(new int[] {
                        0, 1, 2, 2, 3, 0,
                        12, 13, 14, 14, 15, 12,
                        4, 5, 6, 6, 7, 4,
                        16, 17, 18, 18, 19, 16,
                        8, 9, 10, 10, 11, 8
                }, readInts(output.getDirectBuffer(), 30));
            }
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
        assertTrue(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspTree.java")));
        assertTrue(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspBuildResult.java")));
    }

    @Test
    void dynamicBspDataConsumesNativeBuildResult() throws Exception {
        String dynamicBspData = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/data/DynamicBSPData.java"));
        String nativeBuildResult = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspBuildResult.java"));
        String nativeBspTree = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspTree.java"));
        String bspNode = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPNode.java"));
        String bspWorkspace = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPWorkspace.java"));

        assertFalse(dynamicBspData.contains("NativeBspTree.fromRoot("));
        assertFalse(nativeBspTree.contains("fromRoot("));
        assertFalse(nativeBuildResult.contains("rootNode.addTo("));
        assertFalse(nativeBuildResult.contains("Builder.create()"));
        assertTrue(dynamicBspData.contains("NativeBspBuildResult"));
        assertTrue(bspNode.contains("workspace.finishNativeTree(rootNode, indexQuadCount)"));
        assertTrue(bspNode.contains("FIXED_DOUBLE"));
        assertTrue(bspNode.contains("BINARY"));
        assertTrue(bspNode.contains("MULTI_PARTITION"));
        assertTrue(bspNode.contains("workspace.addNativeFixedDouble"));
        assertTrue(bspNode.contains("workspace.addNativeBinary"));
        assertTrue(bspNode.contains("workspace.addNativeMultiPartition"));
        assertTrue(bspWorkspace.contains("NativeBspTree.Builder nativeTreeBuilder"));
        assertTrue(bspWorkspace.contains("addNativeBinary"));
        assertTrue(bspWorkspace.contains("addNativeMultiPartition"));
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
}
