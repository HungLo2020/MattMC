package net.minecraft.client.renderer.chunk.advanced.shader;

import net.minecraft.client.renderer.chunk.advanced.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.joml.Matrix4fc;

public interface ChunkShaderInterface {
    @Deprecated
    void setupState(TerrainRenderPass pass, FogParameters parameters);

    @Deprecated
    void resetState();

    void setProjectionMatrix(Matrix4fc matrix);

    void setModelViewMatrix(Matrix4fc matrix);

    void setRegionOffset(float x, float y, float z);
}
