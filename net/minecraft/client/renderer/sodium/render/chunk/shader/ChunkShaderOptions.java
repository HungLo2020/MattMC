package net.minecraft.client.renderer.sodium.render.chunk.shader;

import net.minecraft.client.renderer.gl.advanced.shader.ShaderConstants;
import net.minecraft.client.renderer.chunk.advanced.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.chunk.advanced.vertex.format.ChunkVertexType;

public record ChunkShaderOptions(ChunkFogMode fog, TerrainRenderPass pass, ChunkVertexType vertexType) {
    public ShaderConstants constants() {
        ShaderConstants.Builder constants = ShaderConstants.builder();
        constants.addAll(this.fog.getDefines());

        if (this.pass.supportsFragmentDiscard()) {
            constants.add("USE_FRAGMENT_DISCARD");
        }

        constants.add("USE_VERTEX_COMPRESSION"); // TODO: allow compact vertex format to be disabled

        return constants.build();
    }
}
