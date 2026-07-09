package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.util.NativeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeBspSortStateTest {
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
    void nativeBspSortStateWritesQuadIndexBufferFromNativeOrderStorage() {
        NativeBuffer output = new NativeBuffer(2 * TranslucentData.BYTES_PER_QUAD);

        try (NativeBspSortState sortState = NativeBspSortState.create(2)) {
            sortState.append(1);
            sortState.append(0);
            sortState.writeIndexBuffer(output);

            assertArrayEquals(new int[] {4, 5, 6, 6, 7, 4, 0, 1, 2, 2, 3, 0},
                    readInts(output.getDirectBuffer(), 12));
        } finally {
            output.free();
        }
    }

    @Test
    void bspSortStateUsesRustOwnedOrderStorageAndNativeFinalWrite() throws Exception {
        String bspSortState = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/BSPSortState.java"));
        String nativeBspSortState = Files.readString(Path.of(
                "src/main/java/net/sodium/client/render/chunk/translucent_sorting/bsp_tree/NativeBspSortState.java"));

        assertTrue(bspSortState.contains("NativeBspSortState.create("));
        assertTrue(nativeBspSortState.contains("MemoryUtil.memPutInt("));
        assertTrue(nativeBspSortState.contains("writeIndexBuffer("));
        assertFalse(bspSortState.contains("private final int[] quadIndexes"));
        assertFalse(bspSortState.contains("TranslucentData.writeQuadVertexIndexes("));
        assertFalse(nativeBspSortState.contains("_append\""));
        assertFalse(nativeBspSortState.contains("_append_batch\""));
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
