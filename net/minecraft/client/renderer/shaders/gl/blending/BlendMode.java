package net.minecraft.client.renderer.shaders.gl.blending;

/**
 * Represents an OpenGL blend mode configuration.
 * IRIS 1.21.9 VERBATIM - from net.irisshaders.iris.gl.blending.BlendMode
 */
public record BlendMode(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
}
