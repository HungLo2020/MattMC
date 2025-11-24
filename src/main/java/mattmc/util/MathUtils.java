package mattmc.util;

/**
 * Common mathematical operations and utilities.
 * Provides convenient methods for clamping, interpolation, and float/double comparisons.
 */
public final class MathUtils {
    
    /**
     * Epsilon value for float comparisons.
     * Used to determine if two floats are approximately equal (within 0.000001).
     */
    public static final float EPSILON = 1e-6f;
    
    /**
     * Epsilon value for double comparisons.
     * Used to determine if two doubles are approximately equal (within 0.0000000001).
     */
    public static final double EPSILON_D = 1e-10;
    
    private MathUtils() {} // Prevent instantiation
    
    /**
     * Clamp an integer value between min and max (inclusive).
     * @param value The value to clamp
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @return The clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a float value between min and max (inclusive).
     * @param value The value to clamp
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @return The clamped value
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a double value between min and max (inclusive).
     * @param value The value to clamp
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @return The clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Linear interpolation between two values.
     * @param a Start value
     * @param b End value
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated value
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * Linear interpolation between two values.
     * @param a Start value
     * @param b End value
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated value
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
    
    /**
     * Check if two floats are approximately equal.
     * Uses EPSILON for comparison tolerance.
     * @param a First value
     * @param b Second value
     * @return true if the values are approximately equal
     */
    public static boolean approximately(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }
    
    /**
     * Check if two doubles are approximately equal.
     * Uses EPSILON_D for comparison tolerance.
     * @param a First value
     * @param b Second value
     * @return true if the values are approximately equal
     */
    public static boolean approximately(double a, double b) {
        return Math.abs(a - b) < EPSILON_D;
    }
    
    /**
     * Check if a float is approximately zero.
     * Uses EPSILON for comparison tolerance.
     * @param value The value to check
     * @return true if the value is approximately zero
     */
    public static boolean isZero(float value) {
        return Math.abs(value) < EPSILON;
    }
    
    /**
     * Check if a double is approximately zero.
     * Uses EPSILON_D for comparison tolerance.
     * @param value The value to check
     * @return true if the value is approximately zero
     */
    public static boolean isZero(double value) {
        return Math.abs(value) < EPSILON_D;
    }
    
    /**
     * Floor division (always rounds down, even for negative numbers).
     * This is equivalent to Math.floorDiv().
     * @param a The dividend
     * @param b The divisor
     * @return The largest (closest to positive infinity) int value that is less than or equal to the algebraic quotient
     */
    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }
    
    /**
     * Floor modulo (always positive remainder).
     * This is equivalent to Math.floorMod().
     * @param a The dividend
     * @param b The divisor
     * @return The floor modulus a - (floorDiv(a, b) * b)
     */
    public static int floorMod(int a, int b) {
        return Math.floorMod(a, b);
    }
    
    /**
     * Returns the minimum of two integers.
     * This is a convenience method that delegates to Math.min().
     * @param a First value
     * @param b Second value
     * @return The smaller value
     */
    public static int min(int a, int b) {
        return Math.min(a, b);
    }
    
    /**
     * Returns the maximum of two integers.
     * This is a convenience method that delegates to Math.max().
     * @param a First value
     * @param b Second value
     * @return The larger value
     */
    public static int max(int a, int b) {
        return Math.max(a, b);
    }
    
    /**
     * Returns the minimum of two floats.
     * This is a convenience method that delegates to Math.min().
     * @param a First value
     * @param b Second value
     * @return The smaller value
     */
    public static float min(float a, float b) {
        return Math.min(a, b);
    }
    
    /**
     * Returns the maximum of two floats.
     * This is a convenience method that delegates to Math.max().
     * @param a First value
     * @param b Second value
     * @return The larger value
     */
    public static float max(float a, float b) {
        return Math.max(a, b);
    }
    
    /**
     * Returns the minimum of two doubles.
     * This is a convenience method that delegates to Math.min().
     * @param a First value
     * @param b Second value
     * @return The smaller value
     */
    public static double min(double a, double b) {
        return Math.min(a, b);
    }
    
    /**
     * Returns the maximum of two doubles.
     * This is a convenience method that delegates to Math.max().
     * @param a First value
     * @param b Second value
     * @return The larger value
     */
    public static double max(double a, double b) {
        return Math.max(a, b);
    }
    
    /**
     * Returns the absolute value of an integer.
     * This is a convenience method that delegates to Math.abs().
     * @param value The value
     * @return The absolute value
     */
    public static int abs(int value) {
        return Math.abs(value);
    }
    
    /**
     * Returns the absolute value of a float.
     * This is a convenience method that delegates to Math.abs().
     * @param value The value
     * @return The absolute value
     */
    public static float abs(float value) {
        return Math.abs(value);
    }
    
    /**
     * Returns the absolute value of a double.
     * This is a convenience method that delegates to Math.abs().
     * @param value The value
     * @return The absolute value
     */
    public static double abs(double value) {
        return Math.abs(value);
    }
    
    /**
     * Rounds a float to the nearest integer.
     * This is a convenience method that delegates to Math.round().
     * @param value The value
     * @return The rounded value
     */
    public static int round(float value) {
        return Math.round(value);
    }
    
    /**
     * Rounds a double to the nearest long.
     * This is a convenience method that delegates to Math.round().
     * @param value The value
     * @return The rounded value
     */
    public static long round(double value) {
        return Math.round(value);
    }
    
    /**
     * Returns the largest (closest to positive infinity) double value 
     * that is less than or equal to the argument and is equal to a mathematical integer.
     * @param value The value
     * @return The floor value
     */
    public static double floor(double value) {
        return Math.floor(value);
    }
    
    /**
     * Returns the smallest (closest to negative infinity) double value 
     * that is greater than or equal to the argument and is equal to a mathematical integer.
     * @param value The value
     * @return The ceiling value
     */
    public static double ceil(double value) {
        return Math.ceil(value);
    }
    
    /**
     * Returns the largest (closest to positive infinity) float value 
     * that is less than or equal to the argument and is equal to a mathematical integer.
     * @param value The value
     * @return The floor value as int
     */
    public static int floor(float value) {
        return (int) Math.floor(value);
    }
    
    /**
     * Returns the smallest (closest to negative infinity) float value 
     * that is greater than or equal to the argument and is equal to a mathematical integer.
     * @param value The value
     * @return The ceiling value as int
     */
    public static int ceil(float value) {
        return (int) Math.ceil(value);
    }
    
    /**
     * Returns the square of a value.
     * @param value The value
     * @return value * value
     */
    public static int square(int value) {
        return value * value;
    }
    
    /**
     * Returns the square of a value.
     * @param value The value
     * @return value * value
     */
    public static float square(float value) {
        return value * value;
    }
    
    /**
     * Returns the square of a value.
     * @param value The value
     * @return value * value
     */
    public static double square(double value) {
        return value * value;
    }
    
    /**
     * Returns the square root of a value.
     * This is a convenience method that delegates to Math.sqrt().
     * @param value The value
     * @return The square root
     */
    public static double sqrt(double value) {
        return Math.sqrt(value);
    }
    
    /**
     * Converts degrees to radians.
     * @param degrees Angle in degrees
     * @return Angle in radians
     */
    public static double toRadians(double degrees) {
        return Math.toRadians(degrees);
    }
    
    /**
     * Converts radians to degrees.
     * @param radians Angle in radians
     * @return Angle in degrees
     */
    public static double toDegrees(double radians) {
        return Math.toDegrees(radians);
    }
}
