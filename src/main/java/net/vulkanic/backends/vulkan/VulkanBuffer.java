package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicBuffer;

import java.nio.ByteBuffer;
import java.util.Objects;

public class VulkanBuffer extends VulkanicBuffer {

    private final long vkBufferHandle;
    private final long vkMemoryHandle;
    private final int usage;
    private final int size;
    private final String debugLabel;
    private final Runnable closeAction;

    private boolean closed;
    private boolean mapped;

    public VulkanBuffer(long vkBufferHandle,
                        long vkMemoryHandle,
                        int usage,
                        int size,
                        String debugLabel,
                        Runnable closeAction) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got: " + size);
        }
        this.vkBufferHandle = vkBufferHandle;
        this.vkMemoryHandle = vkMemoryHandle;
        this.usage = usage;
        this.size = size;
        this.debugLabel = debugLabel == null ? "VulkanBuffer" : debugLabel;
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction must not be null");
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
