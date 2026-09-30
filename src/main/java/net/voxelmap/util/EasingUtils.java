package net.voxelmap.util;

import net.minecraft.util.Mth;

public class EasingUtils {
    private static final double num1 = 1.70158;
    private static final double num2 = num1 * 1.525;
    private static final double num3 = num1 + 1.0;

    public static float easeInOutSine(float startValue, float finalValue, float elapsedTime, float totalTime) {
        float timeFactor = elapsedTime / totalTime;
        double easeFactor = -(Math.cos(Math.PI * timeFactor) - 1.0) / 2.0;

        return Mth.lerp((float) easeFactor, startValue, finalValue);
    }

    public static float easeOutExpo(float startValue, float finalValue, float elapsedTime, float totalTime) {
        float timeFactor = elapsedTime / totalTime;
        double easeFactor = timeFactor == 1.0 ? 1.0 : (1.0 - Math.pow(2.0, -10.0 * timeFactor));

        return Mth.lerp((float) easeFactor, startValue, finalValue);
    }

}