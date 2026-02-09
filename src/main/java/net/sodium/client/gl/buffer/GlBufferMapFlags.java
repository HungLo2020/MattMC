package net.sodium.client.gl.buffer;

import net.sodium.client.gl.util.EnumBit;

public enum GlBufferMapFlags implements EnumBit {
    READ(1),
    WRITE(2),
    PERSISTENT(64),
    INVALIDATE_BUFFER(8),
    INVALIDATE_RANGE(4),
    EXPLICIT_FLUSH(16),
    COHERENT(128),
    UNSYNCHRONIZED(32);

    private final int bit;

    GlBufferMapFlags(int bit) {
        this.bit = bit;
    }

    @Override
    public int getBits() {
        return this.bit;
    }
}
