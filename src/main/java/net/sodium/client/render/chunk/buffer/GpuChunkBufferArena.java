package net.sodium.client.render.chunk.buffer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.CommandEncoder;
import net.sodium.client.gl.arena.PendingUpload;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.device.CommandList;
import net.vulkanic.VulkanicAPI;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Backend-owned chunk arena for Vulkan terrain buffers.
 *
 * <p>This mirrors Sodium's arena allocation behavior, but stores chunk geometry
 * in Blaze/Vulkanic {@link GpuBuffer} objects so the Vulkan renderer does not
 * need to create or bind Sodium GL buffer handles for terrain data.</p>
 */
public class GpuChunkBufferArena implements ChunkBufferArena {
    private final Supplier<String> label;
    private final int usage;
    private final int resizeIncrement;
    private final int stride;

    private Segment head;
    private GpuBuffer arenaBuffer;
    private long capacity;
    private long used;

    public GpuChunkBufferArena(Supplier<String> label, int usage, int initialCapacity, int stride) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        if (stride <= 0) {
            throw new IllegalArgumentException("stride must be positive");
        }

        this.label = label;
        this.usage = usage | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;
        this.capacity = initialCapacity;
        this.resizeIncrement = Math.max(1, initialCapacity / 16);
        this.stride = stride;
        this.head = new Segment(this, 0, initialCapacity);
        this.head.setFree(true);
        this.arenaBuffer = createBuffer(this.label, this.usage, this.capacity * this.stride);
    }

    @Override
    public boolean upload(CommandList commandList, Stream<PendingUpload> stream) {
        GpuBuffer previousBuffer = this.arenaBuffer;
        List<PendingUpload> queue = stream.collect(Collectors.toCollection(LinkedList::new));

        this.tryUploads(queue);

        if (!queue.isEmpty()) {
            long remainingElements = queue.stream()
                    .mapToLong(upload -> ceilDiv(upload.getDataBuffer().getDirectBuffer().remaining(), this.stride))
                    .sum();

            this.ensureCapacity(remainingElements);
            this.tryUploads(queue);

            if (!queue.isEmpty()) {
                throw new IllegalStateException("Failed to upload all buffers");
            }
        }

        return this.arenaBuffer != previousBuffer;
    }

    private void tryUploads(List<PendingUpload> queue) {
        queue.removeIf(this::tryUpload);
    }

    private boolean tryUpload(PendingUpload upload) {
        ByteBuffer data = upload.getDataBuffer().getDirectBuffer();
        int byteCount = data.remaining();
        long elementCount = ceilDiv(byteCount, this.stride);
        Segment dst = this.alloc(elementCount);

        if (dst == null) {
            return false;
        }

        int writeOffset = checkedByteOffset(dst.getOffset(), this.stride);
        CommandEncoder encoder = VulkanicAPI.createCommandEncoder();
        encoder.writeToBuffer(this.arenaBuffer.slice(writeOffset, byteCount), data.duplicate());

        upload.setResult(dst);
        return true;
    }

    private Segment alloc(long size) {
        Segment free = this.findFree(size);

        if (free == null) {
            return null;
        }

        Segment result;
        if (free.getLength() == size) {
            free.setFree(false);
            result = free;
        } else {
            result = new Segment(this, free.getEnd() - size, size);
            result.setNext(free.getNext());
            result.setPrev(free);

            if (result.getNext() != null) {
                result.getNext().setPrev(result);
            }

            free.setLength(free.getLength() - size);
            free.setNext(result);
        }

        this.used += result.getLength();
        return result;
    }

    private Segment findFree(long size) {
        Segment entry = this.head;
        Segment best = null;

        while (entry != null) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                }
                if (entry.getLength() >= size && (best == null || best.getLength() > entry.getLength())) {
                    best = entry;
                }
            }

            entry = entry.getNext();
        }

        return best;
    }

    private void ensureCapacity(long elementCount) {
        long elementsNeeded = elementCount - (this.capacity - this.used);

        this.resize(Math.max(this.capacity + this.resizeIncrement, this.capacity + elementsNeeded));
    }

    private void resize(long newCapacity) {
        if (this.used > newCapacity) {
            throw new IllegalArgumentException("New capacity must be larger than used size");
        }

        long tail = newCapacity - this.used;
        List<Segment> usedSegments = this.getUsedSegments();
        List<PendingCopy> pendingCopies = this.buildTransferList(usedSegments, tail);
        this.transferSegments(pendingCopies, newCapacity);

        this.head = new Segment(this, 0, tail);
        this.head.setFree(true);

        if (!usedSegments.isEmpty()) {
            this.head.setNext(usedSegments.getFirst());
            this.head.getNext().setPrev(this.head);
        }
    }

    private List<PendingCopy> buildTransferList(List<Segment> usedSegments, long base) {
        List<PendingCopy> pendingCopies = new ArrayList<>();
        PendingCopy currentCopy = null;
        long writeOffset = base;

        for (int i = 0; i < usedSegments.size(); i++) {
            Segment segment = usedSegments.get(i);

            if (currentCopy == null || currentCopy.readOffset + currentCopy.length != segment.getOffset()) {
                if (currentCopy != null) {
                    pendingCopies.add(currentCopy);
                }
                currentCopy = new PendingCopy(segment.getOffset(), writeOffset, segment.getLength());
            } else {
                currentCopy.length += segment.getLength();
            }

            segment.setOffset(writeOffset);

            if (i + 1 < usedSegments.size()) {
                segment.setNext(usedSegments.get(i + 1));
            } else {
                segment.setNext(null);
            }

            if (i == 0) {
                segment.setPrev(null);
            } else {
                segment.setPrev(usedSegments.get(i - 1));
            }

            writeOffset += segment.getLength();
        }

        if (currentCopy != null) {
            pendingCopies.add(currentCopy);
        }

        return pendingCopies;
    }

    private void transferSegments(Collection<PendingCopy> copies, long newCapacity) {
        GpuBuffer src = this.arenaBuffer;
        GpuBuffer dst = createBuffer(this.label, this.usage, newCapacity * this.stride);

        CommandEncoder encoder = VulkanicAPI.createCommandEncoder();
        for (PendingCopy copy : copies) {
            int readOffset = checkedByteOffset(copy.readOffset, this.stride);
            int writeOffset = checkedByteOffset(copy.writeOffset, this.stride);
            int bytes = checkedByteCount(copy.length, this.stride);
            encoder.copyToBuffer(src.slice(readOffset, bytes), dst.slice(writeOffset, bytes));
        }

        src.close();
        this.arenaBuffer = dst;
        this.capacity = newCapacity;
    }

    private ArrayList<Segment> getUsedSegments() {
        ArrayList<Segment> usedSegments = new ArrayList<>();
        Segment segment = this.head;

        while (segment != null) {
            Segment next = segment.getNext();
            if (!segment.isFree()) {
                usedSegments.add(segment);
            }
            segment = next;
        }

        return usedSegments;
    }

    private void free(Segment segment) {
        if (segment.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        segment.setFree(true);
        this.used -= segment.getLength();

        Segment next = segment.getNext();
        if (next != null && next.isFree()) {
            segment.mergeInto(next);
        }

        Segment prev = segment.getPrev();
        if (prev != null && prev.isFree()) {
            prev.mergeInto(segment);
        }
    }

    @Override
    public void delete(CommandList commandList) {
        this.arenaBuffer.close();
    }

    @Override
    public boolean isEmpty() {
        return this.used <= 0;
    }

    @Override
    public long getDeviceUsedMemory() {
        return this.used * this.stride;
    }

    @Override
    public long getDeviceAllocatedMemory() {
        return this.capacity * this.stride;
    }

    @Override
    public GpuBuffer gpuBufferView(Supplier<String> label, int usage) {
        return this.arenaBuffer;
    }

    @Override
    public GlBuffer legacyGlBuffer() {
        throw new UnsupportedOperationException("GpuChunkBufferArena does not expose a legacy GL buffer");
    }

    private static GpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Chunk buffer arena exceeds maximum GpuBuffer size: " + size);
        }
        return VulkanicAPI.createBuffer(label, usage, Math.toIntExact(size));
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int checkedByteOffset(long elements, int stride) {
        return Math.toIntExact(elements * stride);
    }

    private static int checkedByteCount(long elements, int stride) {
        return Math.toIntExact(elements * stride);
    }

    private static final class PendingCopy {
        private final long readOffset;
        private final long writeOffset;
        private long length;

        private PendingCopy(long readOffset, long writeOffset, long length) {
            this.readOffset = readOffset;
            this.writeOffset = writeOffset;
            this.length = length;
        }
    }

    private static final class Segment implements ChunkBufferAllocation {
        private final GpuChunkBufferArena arena;
        private long offset;
        private long length;
        private boolean free;
        private Segment next;
        private Segment prev;

        private Segment(GpuChunkBufferArena arena, long offset, long length) {
            this.arena = arena;
            this.offset = offset;
            this.length = length;
        }

        private long getEnd() {
            return this.offset + this.length;
        }

        @Override
        public long getOffset() {
            return this.offset;
        }

        @Override
        public long getLength() {
            return this.length;
        }

        private void setOffset(long offset) {
            this.offset = offset;
        }

        private void setLength(long length) {
            this.length = length;
        }

        private boolean isFree() {
            return this.free;
        }

        private void setFree(boolean free) {
            this.free = free;
        }

        private Segment getNext() {
            return this.next;
        }

        private void setNext(Segment next) {
            this.next = next;
        }

        private Segment getPrev() {
            return this.prev;
        }

        private void setPrev(Segment prev) {
            this.prev = prev;
        }

        @Override
        public void delete() {
            this.arena.free(this);
        }

        private void mergeInto(Segment segment) {
            this.length += segment.length;
            this.next = segment.next;
            if (this.next != null) {
                this.next.prev = this;
            }
        }
    }
}
