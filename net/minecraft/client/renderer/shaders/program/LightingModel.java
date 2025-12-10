package net.minecraft.client.renderer.shaders.program;

/**
 * Lighting model for shader programs.
 * Copied VERBATIM from IRIS 1.21.9 ShaderKey.java inner enum.
 */
public enum LightingModel {
    FULLBRIGHT,
    LIGHTMAP,
    DIFFUSE,
    DIFFUSE_LM
}
