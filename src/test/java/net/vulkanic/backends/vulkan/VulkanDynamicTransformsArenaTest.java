package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanDynamicTransformsArenaTest {
    private record TestBuffer(int id, int size, ByteBuffer data) {
    }

    private static final class Harness {
        private int nextId = 1;
        private final List<Integer> destroyed = new ArrayList<>();
        private final VulkanDynamicTransformsArena<TestBuffer> arena =
            new VulkanDynamicTransformsArena<>(
                2,
                256,
                512,
                2048,
                8,
                (size, growth) -> new TestBuffer(nextId++, size, ByteBuffer.allocate(size)),
                (buffer, offset, payload) -> {
                    ByteBuffer target = buffer.data().duplicate();
                    target.position(offset);
                    target.put(payload.duplicate());
                },
                buffer -> destroyed.add(buffer.id())
            );
    }

    @Test
    void repeatedContentReusesOneAlignedRegionWithinFrameSlot() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);

        VulkanDynamicTransformsArena.Allocation<TestBuffer> first = harness.arena.allocate(0, payload(1, 164));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> second = harness.arena.allocate(0, payload(1, 164));

        assertEquals(first.buffer(), second.buffer());
        assertEquals(first.offset(), second.offset());
        assertEquals(first.contentHash(), second.contentHash());
        assertEquals(0, first.offset());
        assertEquals(256, first.reservedBytes());
        assertEquals(164, first.writtenBytes());
        assertEquals(0, second.writtenBytes());
        assertEquals(256, harness.arena.highWaterBytes(0));
        assertEquals(1, harness.arena.cachedPayloadCount(0));
    }

    @Test
    void reuseScopeKeepsLayoutIncompatiblePayloadsSeparate() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);

        VulkanDynamicTransformsArena.Allocation<TestBuffer> first =
            harness.arena.allocate(0, "programA|bytes=164", payload(1, 164));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> sameScope =
            harness.arena.allocate(0, "programA|bytes=164", payload(1, 164));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> otherScope =
            harness.arena.allocate(0, "programB|bytes=164", payload(1, 164));

        assertEquals(first.offset(), sameScope.offset());
        assertNotEquals(first.offset(), otherScope.offset());
        assertEquals(256, otherScope.offset());
    }

    @Test
    void diagnosticNameAppearsInPayloadValidation() {
        VulkanDynamicTransformsArena<TestBuffer> arena = new VulkanDynamicTransformsArena<>(
            "StandaloneUniforms",
            1,
            256,
            512,
            2048,
            8,
            (size, growth) -> new TestBuffer(1, size, ByteBuffer.allocate(size)),
            (buffer, offset, payload) -> {},
            buffer -> {}
        );

        IllegalArgumentException exception =
            assertThrows(IllegalArgumentException.class, () -> arena.allocate(0, ByteBuffer.allocate(0)));
        assertEquals("StandaloneUniforms payload must not be empty", exception.getMessage());
    }

    @Test
    void changedContentReceivesDistinctAlignedRegion() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);

        VulkanDynamicTransformsArena.Allocation<TestBuffer> first = harness.arena.allocate(0, payload(1, 164));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> second = harness.arena.allocate(0, payload(2, 164));

        assertEquals(first.buffer(), second.buffer());
        assertNotEquals(first.offset(), second.offset());
        assertEquals(0, first.offset());
        assertEquals(256, second.offset());
        assertEquals(512, harness.arena.highWaterBytes(0));
    }

    @Test
    void frameSlotsAreIsolatedAndResetOnlyWhenRequested() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);
        harness.arena.beginFrameSlot(1);

        VulkanDynamicTransformsArena.Allocation<TestBuffer> slot0 = harness.arena.allocate(0, payload(1, 164));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> slot1 = harness.arena.allocate(1, payload(1, 164));

        assertNotEquals(slot0.buffer(), slot1.buffer());
        assertEquals(0, slot0.offset());
        assertEquals(0, slot1.offset());

        harness.arena.beginFrameSlot(0);
        VulkanDynamicTransformsArena.Allocation<TestBuffer> slot0Again = harness.arena.allocate(0, payload(1, 164));
        assertEquals(slot0.buffer(), slot0Again.buffer());
        assertEquals(0, slot0Again.offset());
    }

    @Test
    void growthKeepsInFlightBufferAliveUntilFrameSlotReset() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);

        VulkanDynamicTransformsArena.Allocation<TestBuffer> first = harness.arena.allocate(0, payload(1, 400));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> second = harness.arena.allocate(0, payload(2, 400));

        assertNotEquals(first.buffer(), second.buffer());
        assertEquals(List.of(), harness.destroyed);

        harness.arena.beginFrameSlot(0);
        assertEquals(List.of(first.buffer().id()), harness.destroyed);
    }

    @Test
    void oversizedPayloadFailsWithoutPublishingWrite() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);

        assertThrows(IllegalStateException.class, () -> harness.arena.allocate(0, payload(1, 4096)));
        assertEquals(0, harness.arena.highWaterBytes(0));
    }

    @Test
    void closeDestroysLiveAndRetiredBuffers() {
        Harness harness = new Harness();
        harness.arena.beginFrameSlot(0);
        VulkanDynamicTransformsArena.Allocation<TestBuffer> first = harness.arena.allocate(0, payload(1, 400));
        VulkanDynamicTransformsArena.Allocation<TestBuffer> second = harness.arena.allocate(0, payload(2, 400));

        harness.arena.close();

        assertEquals(Set.of(first.buffer().id(), second.buffer().id()), new HashSet<>(harness.destroyed));
    }

    private static ByteBuffer payload(int value, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i = 0; i < length; i++) {
            buffer.put((byte) (value + i));
        }
        buffer.flip();
        return buffer;
    }
}
