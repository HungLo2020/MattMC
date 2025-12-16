package net.minecraft.client.renderer.sodium.gl.buffer;

import net.minecraft.client.renderer.gl.advanced.util.EnumBitField;

public class GlImmutableBuffer extends GlBuffer {
    private final EnumBitField<GlBufferStorageFlags> flags;

    public GlImmutableBuffer(EnumBitField<GlBufferStorageFlags> flags) {
        this.flags = flags;
    }

    public EnumBitField<GlBufferStorageFlags> getFlags() {
        return this.flags;
    }
}
