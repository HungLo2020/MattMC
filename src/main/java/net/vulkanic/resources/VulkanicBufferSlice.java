package net.vulkanic.resources;

/**
 * Vulkanic equivalent of Blaze3D's {@code GpuBufferSlice}.
 *
 * <p>A slice is a window into a {@link VulkanicBuffer} — it describes a contiguous
 * sub-range by byte {@code offset} and {@code length}.  Most buffer-write and
 * buffer-copy operations in the Vulkanic API take a slice rather than a raw
 * buffer, mirroring the Vulkan convention where transfer commands always specify
 * an explicit offset and size.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> translates directly to
 *       {@code glBufferSubData(target, offset, length, data)}.</li>
 *   <li><b>Vulkan backend (future):</b> becomes the {@code srcOffset}/{@code dstOffset}
 *       and {@code size} fields in {@code VkBufferCopy} / {@code VkDescriptorBufferInfo}.</li>
 * </ul>
 *
 * <p>Instances are cheap value objects and may be created on the fly.
 */
public record VulkanicBufferSlice(VulkanicBuffer buffer, int offset, int length) {

    public VulkanicBufferSlice {
        if (length < 0) {
            throw new IllegalArgumentException("Slice length must be >= 0, got " + length);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Slice offset must be >= 0, got " + offset);
        }
        if (offset + length > buffer.getSize()) {
            throw new IllegalArgumentException(
                    "Slice [" + offset + ", " + (offset + length) + ") exceeds buffer size " + buffer.getSize());
        }
    }

    /**
     * Convenience factory: creates a slice covering the entire buffer.
     *
     * @param buffer Source buffer
     * @return A slice starting at 0 with length equal to the buffer size
     */
    public static VulkanicBufferSlice whole(VulkanicBuffer buffer) {
        return new VulkanicBufferSlice(buffer, 0, buffer.getSize());
    }

    /**
     * Returns a sub-slice relative to this slice.
     *
     * @param relativeOffset Offset within this slice (0-based)
     * @param subLength      Length of the sub-slice in bytes
     * @return A new slice whose absolute offset is {@code this.offset + relativeOffset}
     */
    public VulkanicBufferSlice subSlice(int relativeOffset, int subLength) {
        if (relativeOffset < 0 || relativeOffset + subLength > this.length) {
            throw new IllegalArgumentException(
                    "Sub-slice [" + relativeOffset + ", " + (relativeOffset + subLength)
                    + ") is outside parent slice length " + this.length);
        }
        return new VulkanicBufferSlice(buffer, this.offset + relativeOffset, subLength);
    }
}
