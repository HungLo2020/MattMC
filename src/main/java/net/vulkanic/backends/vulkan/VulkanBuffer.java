package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicBuffer;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;

public class VulkanBuffer extends VulkanicBuffer {

    private final long vkBufferHandle;
    private final long vkMemoryHandle;
    private final int usage;
    private final int size;
    private final String debugLabel;
    private final Runnable closeAction;
    private final ByteBuffer diagnosticShadowData;
    private final boolean diagnosticSparseShadowEnabled;
    private final TreeMap<Integer, ByteBuffer> diagnosticSparseShadowRanges = new TreeMap<>();

    private boolean closed;
    private boolean mapped;
    private int diagnosticSparseShadowBytes;

    public VulkanBuffer(long vkBufferHandle,
                        long vkMemoryHandle,
                        int usage,
                        int size,
                        String debugLabel,
                        Runnable closeAction) {
        this(vkBufferHandle, vkMemoryHandle, usage, size, debugLabel, closeAction, null);
    }

    public VulkanBuffer(long vkBufferHandle,
                        long vkMemoryHandle,
                        int usage,
                        int size,
                        String debugLabel,
                        Runnable closeAction,
                        ByteBuffer diagnosticShadowData) {
        this(vkBufferHandle, vkMemoryHandle, usage, size, debugLabel, closeAction, diagnosticShadowData, false);
    }

    public VulkanBuffer(long vkBufferHandle,
                        long vkMemoryHandle,
                        int usage,
                        int size,
                        String debugLabel,
                        Runnable closeAction,
                        ByteBuffer diagnosticShadowData,
                        boolean diagnosticSparseShadowEnabled) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got: " + size);
        }
        this.vkBufferHandle = vkBufferHandle;
        this.vkMemoryHandle = vkMemoryHandle;
        this.usage = usage;
        this.size = size;
        this.debugLabel = debugLabel == null ? "VulkanBuffer" : debugLabel;
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction must not be null");
        this.diagnosticShadowData = diagnosticShadowData;
        this.diagnosticSparseShadowEnabled = diagnosticSparseShadowEnabled;
    }

    long getVkBufferHandle() {
        return vkBufferHandle;
    }

    long getVkMemoryHandle() {
        return vkMemoryHandle;
    }

    void beginMappedScope() {
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Cannot map closed buffer: " + debugLabel);
            }
            if (mapped) {
                throw new IllegalStateException("Buffer is already mapped: " + debugLabel);
            }
            mapped = true;
        }
    }

    void endMappedScope() {
        synchronized (this) {
            mapped = false;
        }
    }

    public synchronized ByteBuffer diagnosticShadowRead(int offset, int length) {
        if (diagnosticShadowData == null || closed || offset < 0 || length < 0 || offset + length > diagnosticShadowData.capacity()) {
            return diagnosticSparseShadowRead(offset, length);
        }

        ByteBuffer source = diagnosticShadowData.duplicate();
        source.position(offset);
        source.limit(offset + length);
        ByteBuffer copy = org.lwjgl.BufferUtils.createByteBuffer(length)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        copy.put(source.slice());
        copy.flip();
        return copy;
    }

    private ByteBuffer diagnosticSparseShadowRead(int offset, int length) {
        if (!diagnosticSparseShadowEnabled || closed || offset < 0 || length < 0 || offset + length > size) {
            return null;
        }
        if (length == 0) {
            return org.lwjgl.BufferUtils.createByteBuffer(0).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        }

        ByteBuffer copy = org.lwjgl.BufferUtils.createByteBuffer(length)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        int end = offset + length;
        Map.Entry<Integer, ByteBuffer> entry = diagnosticSparseShadowRanges.floorEntry(offset);
        if (entry == null || entry.getKey() + entry.getValue().capacity() <= offset) {
            entry = diagnosticSparseShadowRanges.ceilingEntry(offset);
        }

        boolean copiedAny = false;
        while (entry != null && entry.getKey() < end) {
            int rangeOffset = entry.getKey();
            ByteBuffer range = entry.getValue().duplicate();
            int rangeEnd = rangeOffset + range.capacity();
            int copyStart = Math.max(offset, rangeOffset);
            int copyEnd = Math.min(end, rangeEnd);
            if (copyStart < copyEnd) {
                ByteBuffer source = range.duplicate();
                source.position(copyStart - rangeOffset);
                source.limit(copyEnd - rangeOffset);
                ByteBuffer target = copy.duplicate();
                target.position(copyStart - offset);
                target.put(source.slice());
                copiedAny = true;
            }
            entry = diagnosticSparseShadowRanges.higherEntry(rangeOffset);
        }

        if (!copiedAny) {
            return null;
        }
        copy.position(0);
        return copy;
    }

    synchronized void diagnosticShadowWrite(int offset, ByteBuffer sourceData) {
        if (closed || sourceData == null) {
            return;
        }

        ByteBuffer source = sourceData.duplicate();
        int length = source.remaining();
        if (diagnosticShadowData == null) {
            diagnosticSparseShadowWrite(offset, source);
            return;
        }
        if (offset < 0 || offset + length > diagnosticShadowData.capacity()) {
            return;
        }

        ByteBuffer target = diagnosticShadowData.duplicate();
        target.position(offset);
        target.put(source);
    }

    private void diagnosticSparseShadowWrite(int offset, ByteBuffer sourceData) {
        if (!diagnosticSparseShadowEnabled) {
            return;
        }

        ByteBuffer source = sourceData.duplicate();
        int length = source.remaining();
        if (length <= 0 || offset < 0 || offset + length > size) {
            return;
        }

        ByteBuffer previous = diagnosticSparseShadowRanges.remove(offset);
        if (previous != null) {
            diagnosticSparseShadowBytes -= previous.capacity();
            VulkanBackend.releaseDiagnosticGeometryShadowBytes(previous.capacity());
        }

        if (!VulkanBackend.reserveDiagnosticGeometryShadowBytes(length)) {
            return;
        }

        ByteBuffer copy = org.lwjgl.BufferUtils.createByteBuffer(length)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        copy.put(source);
        copy.flip();
        diagnosticSparseShadowRanges.put(offset, copy);
        diagnosticSparseShadowBytes += length;
    }

    synchronized void diagnosticShadowCopyFrom(VulkanBuffer sourceBuffer, int sourceOffset, int destinationOffset, int length) {
        if (diagnosticShadowData == null || sourceBuffer == null || length <= 0) {
            return;
        }

        ByteBuffer source = sourceBuffer.diagnosticShadowRead(sourceOffset, length);
        if (source != null) {
            diagnosticShadowWrite(destinationOffset, source);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int usage() {
        return usage;
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            if (diagnosticSparseShadowBytes > 0) {
                VulkanBackend.releaseDiagnosticGeometryShadowBytes(diagnosticSparseShadowBytes);
                diagnosticSparseShadowBytes = 0;
                diagnosticSparseShadowRanges.clear();
            }
        }
        closeAction.run();
    }

    @Override
    public String toString() {
        return "VulkanBuffer{"
            + "label='" + debugLabel + '\''
            + ", vkBuffer=0x" + Long.toHexString(vkBufferHandle)
            + ", vkMemory=0x" + Long.toHexString(vkMemoryHandle)
            + ", size=" + size
            + ", closed=" + closed
            + '}';
    }

    public static class VulkanMappedView implements VulkanicBuffer.MappedView {

        private final ByteBuffer data;
        private final Runnable unmapAction;
        private boolean closed;

        public VulkanMappedView(ByteBuffer data, Runnable unmapAction) {
            this.data = Objects.requireNonNull(data, "data must not be null");
            this.unmapAction = Objects.requireNonNull(unmapAction, "unmapAction must not be null");
        }

        @Override
        public ByteBuffer data() {
            return data;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            unmapAction.run();
        }
    }
}
