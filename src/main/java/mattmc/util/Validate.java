package mattmc.util;

import java.util.Collection;

/**
 * Validation utilities for common precondition checks.
 * Similar to Apache Commons Validate or Guava Preconditions.
 * Provides convenient methods to validate arguments and state.
 */
public final class Validate {
    
    private Validate() {} // Prevent instantiation
    
    /**
     * Check that object is not null.
     * @param obj The object to check
     * @param message The exception message to use if validation fails
     * @param <T> The type of the object
     * @return The validated object (never null)
     * @throws IllegalArgumentException if obj is null
     */
    public static <T> T notNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
    
    /**
     * Check that object is not null.
     * Note: Consider using java.util.Objects.requireNonNull() instead,
     * which is standard library. This method is included for API completeness
     * and consistency with other validation methods in this class.
     * 
     * @param obj The object to check
     * @param message The exception message to use if validation fails
     * @param <T> The type of the object
     * @return The validated object (never null)
     * @throws NullPointerException if obj is null
     */
    public static <T> T requireNonNull(T obj, String message) {
        return java.util.Objects.requireNonNull(obj, message);
    }
    
    /**
     * Check that string is not null or empty.
     * @param str The string to check
     * @param message The exception message to use if validation fails
     * @return The validated string (never null or empty)
     * @throws IllegalArgumentException if str is null or empty
     */
    public static String notEmpty(String str, String message) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }
    
    /**
     * Check that string is not null or blank (empty or whitespace only).
     * @param str The string to check
     * @param message The exception message to use if validation fails
     * @return The validated string (never null or blank)
     * @throws IllegalArgumentException if str is null or blank
     */
    public static String notBlank(String str, String message) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }
    
    /**
     * Check that value is within range [min, max] (inclusive).
     * @param value The value to check
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param message The exception message prefix to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is out of range
     */
    public static int inRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message + " (value=" + value + 
                ", min=" + min + ", max=" + max + ")");
        }
        return value;
    }
    
    /**
     * Check that value is within range [min, max] (inclusive).
     * @param value The value to check
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param message The exception message prefix to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is out of range
     */
    public static long inRange(long value, long min, long max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message + " (value=" + value + 
                ", min=" + min + ", max=" + max + ")");
        }
        return value;
    }
    
    /**
     * Check that value is within range [min, max] (inclusive).
     * @param value The value to check
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param message The exception message prefix to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is out of range
     */
    public static float inRange(float value, float min, float max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message + " (value=" + value + 
                ", min=" + min + ", max=" + max + ")");
        }
        return value;
    }
    
    /**
     * Check that value is within range [min, max] (inclusive).
     * @param value The value to check
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param message The exception message prefix to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is out of range
     */
    public static double inRange(double value, double min, double max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message + " (value=" + value + 
                ", min=" + min + ", max=" + max + ")");
        }
        return value;
    }
    
    /**
     * Check that condition is true.
     * @param condition The condition to check
     * @param message The exception message to use if validation fails
     * @throws IllegalArgumentException if condition is false
     */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Check that condition is false.
     * @param condition The condition to check
     * @param message The exception message to use if validation fails
     * @throws IllegalArgumentException if condition is true
     */
    public static void isFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Check that array is not null or empty.
     * @param array The array to check
     * @param message The exception message to use if validation fails
     * @param <T> The component type of the array
     * @return The validated array (never null or empty)
     * @throws IllegalArgumentException if array is null or empty
     */
    public static <T> T[] notEmpty(T[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }
    
    /**
     * Check that collection is not null or empty.
     * @param collection The collection to check
     * @param message The exception message to use if validation fails
     * @param <T> The type of the collection
     * @return The validated collection (never null or empty)
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static <T extends Collection<?>> T notEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }
    
    /**
     * Check that array is not null or empty.
     * @param array The array to check
     * @param message The exception message to use if validation fails
     * @return The validated array (never null or empty)
     * @throws IllegalArgumentException if array is null or empty
     */
    public static int[] notEmpty(int[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }
    
    /**
     * Check that array is not null or empty.
     * @param array The array to check
     * @param message The exception message to use if validation fails
     * @return The validated array (never null or empty)
     * @throws IllegalArgumentException if array is null or empty
     */
    public static byte[] notEmpty(byte[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }
    
    /**
     * Check that array is not null or empty.
     * @param array The array to check
     * @param message The exception message to use if validation fails
     * @return The validated array (never null or empty)
     * @throws IllegalArgumentException if array is null or empty
     */
    public static float[] notEmpty(float[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }
    
    /**
     * Check that array is not null or empty.
     * @param array The array to check
     * @param message The exception message to use if validation fails
     * @return The validated array (never null or empty)
     * @throws IllegalArgumentException if array is null or empty
     */
    public static double[] notEmpty(double[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }
    
    /**
     * Check that value is positive (greater than zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is not positive
     */
    public static int positive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is positive (greater than zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is not positive
     */
    public static long positive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is positive (greater than zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is not positive
     */
    public static float positive(float value, String message) {
        if (value <= 0.0f) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is positive (greater than zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is not positive
     */
    public static double positive(double value, String message) {
        if (value <= 0.0) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is non-negative (greater than or equal to zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is negative
     */
    public static int nonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is non-negative (greater than or equal to zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is negative
     */
    public static long nonNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is non-negative (greater than or equal to zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is negative
     */
    public static float nonNegative(float value, String message) {
        if (value < 0.0f) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that value is non-negative (greater than or equal to zero).
     * @param value The value to check
     * @param message The exception message to use if validation fails
     * @return The validated value
     * @throws IllegalArgumentException if value is negative
     */
    public static double nonNegative(double value, String message) {
        if (value < 0.0) {
            throw new IllegalArgumentException(message + " (value=" + value + ")");
        }
        return value;
    }
    
    /**
     * Check that the index is valid for an array/list of the given size.
     * @param index The index to check
     * @param size The size of the array/list
     * @param message The exception message to use if validation fails
     * @return The validated index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public static int validIndex(int index, int size, String message) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(message + " (index=" + index + ", size=" + size + ")");
        }
        return index;
    }
}
