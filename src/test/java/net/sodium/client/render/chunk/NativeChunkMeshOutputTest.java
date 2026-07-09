package net.sodium.client.render.chunk;

import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeChunkMeshOutputTest {
    @Test
    void writesSharedIntQuadIndexBuffer() {
        NativeBufferHandle buffer = NativeBufferHandle.allocate(2 * TranslucentData.INDICES_PER_QUAD * Integer.BYTES);

        try {
            SharedQuadIndexBuffer.IndexType.INTEGER.createIndexBuffer(buffer.byteBuffer, 2);

            int[] values = new int[12];
            buffer.byteBuffer.asIntBuffer().get(values);
            assertArrayEquals(new int[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4}, values);
        } finally {
            buffer.free();
        }
    }

    @Test
    void writesSharedShortQuadIndexBuffer() {
        NativeBufferHandle buffer = NativeBufferHandle.allocate(2 * TranslucentData.INDICES_PER_QUAD * Short.BYTES);

        try {
            SharedQuadIndexBuffer.IndexType.SHORT.createIndexBuffer(buffer.byteBuffer, 2);

            short[] values = new short[12];
            buffer.byteBuffer.asShortBuffer().get(values);
            assertArrayEquals(new short[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4}, values);
        } finally {
            buffer.free();
        }
    }

    @Test
    void writesSortedQuadIndexesAndAdvancesPosition() {
        NativeBufferHandle buffer = NativeBufferHandle.allocate(3 * TranslucentData.INDICES_PER_QUAD * Integer.BYTES);

        try {
            IntBuffer intBuffer = buffer.byteBuffer.asIntBuffer();

            TranslucentData.writeQuadVertexIndexes(intBuffer, new int[] {2, 0, 1});

            assertEquals(18, intBuffer.position());
            intBuffer.rewind();

            int[] values = new int[18];
            intBuffer.get(values);
            assertArrayEquals(new int[] {8, 9, 10, 10, 11, 8, 0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4}, values);
        } finally {
            buffer.free();
        }
    }

    @Test
    void sortsUnsignedKeysAndWritesQuadIndexes() {
        NativeBufferHandle buffer = NativeBufferHandle.allocate(4 * TranslucentData.INDICES_PER_QUAD * Integer.BYTES);

        try {
            IntBuffer intBuffer = buffer.byteBuffer.asIntBuffer();

            TranslucentData.writeQuadVertexIndexesSortedByKey(intBuffer, new int[] {30, -1, 10, 20});

            assertEquals(24, intBuffer.position());
            intBuffer.rewind();

            int[] values = new int[24];
            intBuffer.get(values);
            assertArrayEquals(new int[] {
                    8, 9, 10, 10, 11, 8,
                    12, 13, 14, 14, 15, 12,
                    0, 1, 2, 2, 3, 0,
                    4, 5, 6, 6, 7, 4,
            }, values);
        } finally {
            buffer.free();
        }
    }

    private record NativeBufferHandle(ByteBuffer byteBuffer) {
        static NativeBufferHandle allocate(int size) {
            return new NativeBufferHandle(MemoryUtil.memCalloc(size).order(ByteOrder.nativeOrder()));
        }

        void free() {
            MemoryUtil.memFree(this.byteBuffer);
        }
    }
}
