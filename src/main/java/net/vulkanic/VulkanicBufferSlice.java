package net.vulkanic;

import java.util.Objects;

/**
 * A sub-range slice of a {@link VulkanicBuffer}.
 */
public class VulkanicBufferSlice {

    private final VulkanicBuffer buffer;
    private final int offset;
    private final int length;

    public VulkanicBufferSlice(VulkanicBuffer buffer, int offset, int length) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    /** Returns the backing buffer. */
    public VulkanicBuffer buffer() {
        return buffer;
    }

    /** Returns the byte offset within the backing buffer. */
    public int offset() {
        return offset;
    }

    /** Returns the byte length of this slice. */
    public int length() {
        return length;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof VulkanicBufferSlice other)) {
            return false;
        }
        return offset == other.offset
            && length == other.length
            && Objects.equals(buffer, other.buffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buffer, offset, length);
    }
}
