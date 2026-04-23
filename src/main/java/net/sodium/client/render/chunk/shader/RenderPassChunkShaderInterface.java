package net.sodium.client.render.chunk.shader;

import net.blaze3d.systems.RenderPass;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;

import java.util.Collection;

/**
 * Exposes chunk-shader resources that must also be mirrored into a render pass.
 */
public interface RenderPassChunkShaderInterface extends ChunkShaderInterface {
    void bindRenderPassResources(RenderPass renderPass, TerrainRenderPass pass);

    Collection<String> getRenderPassSamplerNames();
}