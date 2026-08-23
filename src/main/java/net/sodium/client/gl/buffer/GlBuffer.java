package net.sodium.client.gl.buffer;

import net.sodium.client.gl.GlObject;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public abstract class GlBuffer extends GlObject {
    private GlBufferMapping activeMapping;

    protected GlBuffer() {
        if (net.vulkanic.VulkanicAPI.isVulkanBackendInitializedAndSelected()
            || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException("Java Sodium buffers are unavailable while Rust owns whole-frame presentation");
        }
        CommandContext ctx = VulkanicAPI.getCommandContext();
        this.setHandle(VulkanicAPI.createBuffer(ctx));
    }

    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }
}
