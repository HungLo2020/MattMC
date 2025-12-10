package net.minecraft.client.renderer.shaders.programs;

/**
 * Fog mode enum.
 * Copied VERBATIM from IRIS FogMode.java.
 */
public enum FogMode {
    OFF,
    LINEAR,
    EXP,
    EXP2;

    public static FogMode parse(String name) {
        if (name.equals("off")) return OFF;
        if (name.equals("linear")) return LINEAR;
        if (name.equals("exp")) return EXP;
        if (name.equals("exp2")) return EXP2;
        return OFF;
    }
}
