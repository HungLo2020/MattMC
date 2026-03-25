package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;

/**
 * Native Vulkan command context backed by a VkCommandBuffer handle.
 */
public final class VulkanCommandContext implements CommandContext {

    private final long commandBufferHandle;
    private final String debugName;

    public VulkanCommandContext(long commandBufferHandle, String debugName) {
        if (commandBufferHandle == 0L) {
            throw new IllegalArgumentException("commandBufferHandle must not be 0");
        }
        this.commandBufferHandle = commandBufferHandle;
        this.debugName = (debugName == null || debugName.isBlank())
            ? "Vulkan-CommandBuffer"
            : debugName;
    }

    @Override
    public boolean isImmediate() {
        return false;
    }

    @Override
    public long getHandle() {
        return commandBufferHandle;
    }

    @Override
    public String getDebugName() {
        return debugName;
    }

    @Override
    public String toString() {
        return "VulkanCommandContext{" + debugName + ", handle=0x" + Long.toHexString(commandBufferHandle) + "}";
    }
}
