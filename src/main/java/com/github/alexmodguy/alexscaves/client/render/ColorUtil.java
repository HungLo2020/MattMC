package com.github.alexmodguy.alexscaves.client.render;

/**
 * Utility class for color conversion in 1.21+
 * The renderToBuffer method now takes a single packed ARGB int instead of 4 separate floats
 */
public class ColorUtil {
    /**
     * Packs RGBA floats (0.0-1.0) into a single ARGB int
     * 
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     * @param a Alpha component (0.0-1.0)
     * @return Packed ARGB color int
     */
    public static int packColor(float r, float g, float b, float a) {
        int aInt = (int) (a * 255.0F) & 0xFF;
        int rInt = (int) (r * 255.0F) & 0xFF;
        int gInt = (int) (g * 255.0F) & 0xFF;
        int bInt = (int) (b * 255.0F) & 0xFF;
        return (aInt << 24) | (rInt << 16) | (gInt << 8) | bInt;
    }

    /**
     * Unpacks the red component from a packed ARGB color
     * @return Red component (0.0-1.0)
     */
    public static float unpackRed(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    /**
     * Unpacks the green component from a packed ARGB color
     * @return Green component (0.0-1.0)
     */
    public static float unpackGreen(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    /**
     * Unpacks the blue component from a packed ARGB color
     * @return Blue component (0.0-1.0)
     */
    public static float unpackBlue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    /**
     * Unpacks the alpha component from a packed ARGB color
     * @return Alpha component (0.0-1.0)
     */
    public static float unpackAlpha(int color) {
        return ((color >> 24) & 0xFF) / 255.0F;
    }

    /**
     * Convenience constant for white opaque color (1.0F, 1.0F, 1.0F, 1.0F)
     */
    public static final int WHITE = 0xFFFFFFFF;
}
