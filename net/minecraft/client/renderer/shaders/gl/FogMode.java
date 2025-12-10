package net.minecraft.client.renderer.shaders.gl;

/**
 * Fog rendering mode.
 * Copied VERBATIM from IRIS 1.21.9 FogMode.java.
 */
public enum FogMode {
    /**
     * Fog is disabled.
     */
    OFF,

    /**
     * Per-vertex fog, applicable to most geometry.
     */
    PER_VERTEX,

    /**
     * Per-fragment fog, for extra-long geometry like beacon beams.
     */
    PER_FRAGMENT
}
