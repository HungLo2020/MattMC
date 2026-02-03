package net.voxelmap.util;

import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class AllocatedTexture extends AbstractTexture {
    public AllocatedTexture(GpuTexture texture) {
        this.texture = texture;
        this.textureView = RenderSystem.getDevice().createTextureView(texture);
    }
}
