package net.vulkanic.backends.vulkan;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns per-frame-slot dynamic uniform arena bookkeeping.
 *
 * <p>The arena never owns Vulkan commands. It owns only stable frame-slot buffers,
 * aligned region assignment, frame-local content reuse, and fence-safe reset
 * boundaries. The backend supplies buffer creation, writes, and destruction.</p>
 */
final class VulkanDynamicTransformsArena<B> implements AutoCloseable {
    interface BufferFactory<B> {
        B create(int size, boolean growth);
    }

    interface BufferWriter<B> {
        void write(B buffer, int offset, ByteBuffer payload);
    }

    record Allocation<B>(
        B buffer,
        int offset,
        int range,
        long contentHash,
        boolean reused,
        int reservedBytes,
        int writtenBytes
    ) {
        Allocation {
            Objects.requireNonNull(buffer, "buffer");
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            if (range <= 0) {
                throw new IllegalArgumentException("range must be > 0");
            }
            if (reservedBytes < range) {
                throw new IllegalArgumentException("reservedBytes must cover range");
            }
            if (writtenBytes < 0) {
                throw new IllegalArgumentException("writtenBytes must be >= 0");
            }
        }
    }

    private static final class PayloadKey {
        private final String scope;
        private final long hash;
        private final byte[] bytes;

        private PayloadKey(String scope, byte[] bytes) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            this.hash = contentHash(ByteBuffer.wrap(bytes));
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                || (other instanceof PayloadKey key
                && scope.equals(key.scope)
                && hash == key.hash
                && Arrays.equals(bytes, key.bytes));
        }

        @Override
        public int hashCode() {
            int result = scope.hashCode();
            result = 31 * result + Long.hashCode(hash);
            result = 31 * result + Arrays.hashCode(bytes);
            return result;
        }
    }

    private final class Slot {
        @Nullable
        private B buffer;
        private int capacity;
        private int cursor;
        private int highWater;
        private final Map<PayloadKey, Integer> offsetsByPayload = new HashMap<>();
        private final List<B> retiredBuffers = new ArrayList<>();
    }

    private final Slot[] slots;
    private final String debugName;
    private final int alignment;
    private final int initialCapacity;
    private final int maxCapacity;
    private final int maxCachedPayloads;
    private final BufferFactory<B> bufferFactory;
    private final BufferWriter<B> bufferWriter;
    private final Consumer<B> bufferDestroyer;

    VulkanDynamicTransformsArena(
        String debugName,
        int frameSlots,
        long alignment,
        int initialCapacity,
        int maxCapacity,
        int maxCachedPayloads,
        BufferFactory<B> bufferFactory,
        BufferWriter<B> bufferWriter,
        Consumer<B> bufferDestroyer
    ) {
        this.debugName = Objects.requireNonNull(debugName, "debugName");
        if (frameSlots <= 0) {
            throw new IllegalArgumentException("frameSlots must be positive");
        }
        if (alignment <= 0 || alignment > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("alignment must be in [1, Integer.MAX_VALUE]");
        }
        this.alignment = (int) alignment;
        this.initialCapacity = Math.max(this.alignment, alignPositive(initialCapacity, this.alignment));
        this.maxCapacity = Math.max(this.initialCapacity, alignPositive(maxCapacity, this.alignment));
        this.maxCachedPayloads = Math.max(0, maxCachedPayloads);
        this.bufferFactory = Objects.requireNonNull(bufferFactory, "bufferFactory");
        this.bufferWriter = Objects.requireNonNull(bufferWriter, "bufferWriter");
        this.bufferDestroyer = Objects.requireNonNull(bufferDestroyer, "bufferDestroyer");
        this.slots = new VulkanDynamicTransformsArena.Slot[frameSlots];
        for (int i = 0; i < frameSlots; i++) {
            this.slots[i] = new Slot();
        }
    }

    VulkanDynamicTransformsArena(
        int frameSlots,
        long alignment,
        int initialCapacity,
        int maxCapacity,
        int maxCachedPayloads,
        BufferFactory<B> bufferFactory,
        BufferWriter<B> bufferWriter,
        Consumer<B> bufferDestroyer
    ) {
        this(
            "DynamicTransforms",
            frameSlots,
            alignment,
            initialCapacity,
            maxCapacity,
            maxCachedPayloads,
            bufferFactory,
            bufferWriter,
            bufferDestroyer
        );
    }

    synchronized void beginFrameSlot(int frameSlot) {
        Slot slot = slot(frameSlot);
        destroyRetired(slot);
        slot.cursor = 0;
        slot.highWater = 0;
        slot.offsetsByPayload.clear();
    }

    synchronized Allocation<B> allocate(int frameSlot, ByteBuffer payload) {
        return allocate(frameSlot, "", payload);
    }

    synchronized Allocation<B> allocate(int frameSlot, String reuseScope, ByteBuffer payload) {
        Objects.requireNonNull(payload, "payload");
        String normalizedScope = Objects.requireNonNullElse(reuseScope, "");
        ByteBuffer source = payload.duplicate();
        int length = source.remaining();
        if (length <= 0) {
            throw new IllegalArgumentException(debugName + " payload must not be empty");
        }
        if (length > maxCapacity) {
            throw new IllegalStateException(
                debugName + " payload length " + length + " exceeds arena maximum " + maxCapacity);
        }

        Slot slot = slot(frameSlot);
        byte[] bytes = new byte[length];
        source.get(bytes);
        PayloadKey key = new PayloadKey(normalizedScope, bytes);
        Integer existingOffset = slot.offsetsByPayload.get(key);
        if (existingOffset != null) {
            return new Allocation<>(
                requireBuffer(slot),
                existingOffset,
                length,
                key.hash,
                true,
                alignPositive(length, alignment),
                0
            );
        }

        int reserved = alignPositive(length, alignment);
        ensureWritableCapacity(slot, reserved);
        int offset = slot.cursor;
        slot.cursor += reserved;
        slot.highWater = Math.max(slot.highWater, slot.cursor);

        ByteBuffer writePayload = ByteBuffer.wrap(bytes);
        bufferWriter.write(requireBuffer(slot), offset, writePayload);
        if (slot.offsetsByPayload.size() < maxCachedPayloads) {
            slot.offsetsByPayload.put(key, offset);
        }
        return new Allocation<>(
            requireBuffer(slot),
            offset,
            length,
            key.hash,
            false,
            reserved,
            length
        );
    }

    synchronized int highWaterBytes(int frameSlot) {
        return slot(frameSlot).highWater;
    }

    synchronized int cachedPayloadCount(int frameSlot) {
        return slot(frameSlot).offsetsByPayload.size();
    }

    synchronized int capacityBytes(int frameSlot) {
        return slot(frameSlot).capacity;
    }

    @Override
    public synchronized void close() {
        for (Slot slot : slots) {
            if (slot.buffer != null) {
                bufferDestroyer.accept(slot.buffer);
                slot.buffer = null;
            }
            destroyRetired(slot);
            slot.capacity = 0;
            slot.cursor = 0;
            slot.highWater = 0;
            slot.offsetsByPayload.clear();
        }
    }

    private void ensureWritableCapacity(Slot slot, int reserved) {
        if (slot.buffer != null && slot.cursor <= slot.capacity - reserved) {
            return;
        }
        int required = slot.cursor + reserved;
        int nextCapacity = Math.max(initialCapacity, slot.capacity);
        while (nextCapacity < required && nextCapacity < maxCapacity) {
            nextCapacity = Math.min(maxCapacity, nextCapacity << 1);
        }
        if (nextCapacity < required) {
            throw new IllegalStateException(
                debugName + " arena overflow: required=" + required + ", max=" + maxCapacity);
        }

        B previous = slot.buffer;
        B next = bufferFactory.create(nextCapacity, previous != null);
        if (previous != null) {
            if (slot.cursor > 0) {
                slot.retiredBuffers.add(previous);
            } else {
                bufferDestroyer.accept(previous);
            }
        }
        slot.buffer = next;
        slot.capacity = nextCapacity;
        slot.cursor = 0;
        slot.highWater = 0;
        slot.offsetsByPayload.clear();
    }

    private Slot slot(int frameSlot) {
        if (frameSlot < 0 || frameSlot >= slots.length) {
            throw new IllegalArgumentException("Invalid " + debugName + " frame slot: " + frameSlot);
        }
        return slots[frameSlot];
    }

    private B requireBuffer(Slot slot) {
        B buffer = slot.buffer;
        if (buffer == null) {
            throw new IllegalStateException(debugName + " arena buffer is not allocated");
        }
        return buffer;
    }

    private void destroyRetired(Slot slot) {
        for (B retired : slot.retiredBuffers) {
            bufferDestroyer.accept(retired);
        }
        slot.retiredBuffers.clear();
    }

    private static int alignPositive(int value, int alignment) {
        if (value <= 0) {
            return alignment;
        }
        int remainder = value % alignment;
        if (remainder == 0) {
            return value;
        }
        return Math.addExact(value, alignment - remainder);
    }

    static long contentHash(ByteBuffer data) {
        long hash = 0xcbf29ce484222325L;
        ByteBuffer source = data.duplicate();
        while (source.hasRemaining()) {
            hash ^= (source.get() & 0xFFL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
