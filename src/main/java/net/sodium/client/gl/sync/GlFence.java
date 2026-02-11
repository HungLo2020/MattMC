package net.sodium.client.gl.sync;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

public class GlFence {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    
    private final long id;
    private boolean disposed;

    public GlFence(long id) {
        this.id = id;
    }

    public boolean isCompleted() {
        this.checkDisposed();

        int result;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer length = stack.callocInt(1);
            result = VulkanicAPI.querySyncStatus(this.id, 37140, length); // GL_SYNC_STATUS = 0x9114
            
            // The length buffer should contain the number of values written (should be 1)
            // However, some drivers may not write to this buffer at all, so we'll just
            // trust the return value instead of checking the length
        }

        return result == 37889; // GL_SIGNALED
    }

    public void sync() {
        this.checkDisposed();
        this.sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        this.checkDisposed();
        VulkanicAPI.waitForSync(CTX, this.id, 1, timeout); // GL_SYNC_FLUSH_COMMANDS_BIT
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
