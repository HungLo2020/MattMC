package net.minecraft.client.renderer.shaders.shadows;

/**
 * Tracks shadow rendering state.
 * Based on IRIS ShadowRenderingState.java.
 */
public class ShadowRenderingState {
    private static boolean shadowsBeingRendered = false;

    public static void startShadowPass() {
        shadowsBeingRendered = true;
    }

    public static void endShadowPass() {
        shadowsBeingRendered = false;
    }

    public static boolean areShadowsCurrentlyBeingRendered() {
        return shadowsBeingRendered;
    }
}
