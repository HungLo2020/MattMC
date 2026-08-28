package net.voxelmap.util;

import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class AllocatedTexture extends AbstractTexture {
    public AllocatedTexture(GpuTexture texture) {
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException("Java VoxelMap allocated texture views are unavailable on the Rust Vulkan route");
        }
        this.texture = texture;
        this.textureView = net.vulkanic.VulkanicAPI.createTextureView(texture);
    }
}
