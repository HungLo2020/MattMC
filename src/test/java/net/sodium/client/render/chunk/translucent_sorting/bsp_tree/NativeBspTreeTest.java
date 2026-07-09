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
    void bspRuntimeStateWasRemovedFromJava() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPSortState.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspSortState.java")));
        assertTrue(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspTree.java")));
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
