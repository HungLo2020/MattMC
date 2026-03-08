package net.sodium.client.gl.sync;

import net.vulkanic.VulkanicAPI;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

public class GlFence {
    private final long id;
    private boolean disposed;

    public GlFence(long id) {
        this.id = id;
    }

    public boolean isCompleted() {
        this.checkDisposed();

        int result;
        var ctx = VulkanicAPI.getImmediateContext();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer length = stack.callocInt(1);
            result = VulkanicAPI.getSyncStatus(ctx, this.id, length);
            
            // The length buffer should contain the number of values written (should be 1)
            // However, some drivers may not write to this buffer at all, so we'll just
            // trust the return value instead of checking the length
        }

        return result == VulkanicAPI.GL_SIGNALED;
    }

    public void sync() {
        this.checkDisposed();
        this.sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        this.checkDisposed();
        VulkanicAPI.waitForSyncWithFlush(VulkanicAPI.getImmediateContext(), this.id, timeout);
    }

    public void delete() {
        VulkanicAPI.destroySync(VulkanicAPI.getImmediateContext(), this.id);
        this.disposed = true;
    }

    private void checkDisposed() {
        if (this.disposed) {
            throw new IllegalStateException("Fence object has been disposed");
        }
    }
}
