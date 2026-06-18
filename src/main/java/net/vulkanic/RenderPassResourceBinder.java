package net.vulkanic;

import net.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

/**
 * Optional render-pass extension for callers that know the shader sampler unit
 * attached to a named resource.
 */
public interface RenderPassResourceBinder {
    void bindSampler(String name, @Nullable GpuTextureView view, int textureUnit);

    boolean bindLegacySampler(String name, int textureId, int textureUnit);
}
