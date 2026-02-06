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

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            result = VulkanicAPI.querySyncStatus(this.id, 37143, count); // GL_SYNC_STATUS

            if (count.get(0) != 1) {
                throw new RuntimeException("glGetSync returned more than one value");
            }
        }

        return result == 37889; // GL_SIGNALED
    }

    public void sync() {
        this.checkDisposed();
        this.sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        this.checkDisposed();
        VulkanicAPI.waitForSync(this.id, 1, timeout); // GL_SYNC_FLUSH_COMMANDS_BIT
    }

    public void delete() {
        VulkanicAPI.destroySync(this.id);
        this.disposed = true;
    }

    private void checkDisposed() {
        if (this.disposed) {
            throw new IllegalStateException("Fence object has been disposed");
        }
    }
}
