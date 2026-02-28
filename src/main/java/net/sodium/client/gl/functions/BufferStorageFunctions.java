package net.sodium.client.gl.functions;

import net.sodium.client.gl.buffer.GlBufferStorageFlags;
import net.sodium.client.gl.buffer.GlBufferTarget;
import net.sodium.client.gl.device.RenderDevice;
import net.sodium.client.gl.util.EnumBitField;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;

public enum BufferStorageFunctions {
    NONE {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            throw new UnsupportedOperationException();
        }
    },
    CORE {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            VulkanicAPI.bufferStorage(VulkanicAPI.getImmediateContext(), target.getTargetParameter(), length, flags.getBitField());
        }
    },
    ARB {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            VulkanicAPI.bufferStorage(VulkanicAPI.getImmediateContext(), target.getTargetParameter(), length, flags.getBitField());
        }
    };

    public static BufferStorageFunctions pickBest(RenderDevice device) {
        GraphicsCapabilities capabilities = device.getCapabilities();

        if (capabilities.OpenGL44) {
            return CORE;
        } else if (capabilities.GL_ARB_buffer_storage) {
            return ARB;
        } else {
            return NONE;
        }
    }


    public abstract void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags);
}
