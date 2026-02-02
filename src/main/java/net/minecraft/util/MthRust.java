package net.minecraft.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * MthRust - Rust-powered math utilities via JNI
 * 
 * This class provides highly optimized implementations of common math operations
 * using Rust through JNI (Java Native Interface). These implementations can be
 * significantly faster than their Java counterparts for performance-critical code.
 * 
 * This is a proof-of-concept for incrementally migrating Java code to Rust.
 */
public class MthRust {
    
    private static boolean libraryLoaded = false;
    
    static {
        try {
            // Extract and load the native library from resources
            String libraryName = getNativeLibraryName();
            String resourcePath = "/natives/" + libraryName;
            
            // Try to load from resources
            InputStream in = MthRust.class.getResourceAsStream(resourcePath);
            if (in != null) {
                // Create a temporary file to extract the library
                File tempLib = File.createTempFile("mattmc_rust", getNativeLibrarySuffix());
                tempLib.deleteOnExit();
                
                try (OutputStream out = new FileOutputStream(tempLib)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                in.close();
                
                // Load the extracted library
                System.load(tempLib.getAbsolutePath());
                libraryLoaded = true;
                System.out.println("✓ Rust library loaded successfully from resources");
            } else {
                // Fallback: try to load from java.library.path
                System.loadLibrary("mattmc_rust");
                libraryLoaded = true;
                System.out.println("✓ Rust library loaded successfully from java.library.path");
            }
        } catch (Exception e) {
            System.err.println("WARNING: Failed to load Rust library mattmc_rust: " + e.getMessage());
            System.err.println("Falling back to Java implementations");
            libraryLoaded = false;
        }
    }
    
    private static String getNativeLibraryName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "mattmc_rust.dll";
        } else if (os.contains("mac")) {
            return "libmattmc_rust.dylib";
        } else {
            return "libmattmc_rust.so";
        }
    }
    
    private static String getNativeLibrarySuffix() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return ".dll";
        } else if (os.contains("mac")) {
            return ".dylib";
        } else {
            return ".so";
        }
    }
    
    /**
     * Returns true if the Rust library was loaded successfully
     */
    public static boolean isRustAvailable() {
        return libraryLoaded;
    }
    
    // Native method declarations - these are implemented in Rust
    
    private static native int floor(float value);
    private static native int floor(double value);
    private static native long lfloor(double value);
    
    private static native int ceil(float value);
    private static native int ceil(double value);
    private static native long ceilLong(double value);
    
    private static native int clamp(int value, int min, int max);
    private static native long clamp(long value, long min, long max);
    private static native float clamp(float value, float min, float max);
    private static native double clamp(double value, double min, double max);
    
    private static native float abs(float value);
    private static native int abs(int value);
    
    private static native float square(float value);
    private static native double square(double value);
    private static native int square(int value);
    private static native long square(long value);
    
    // Public API methods with fallback to Java implementations
    
    public static int floorFloat(float f) {
        if (libraryLoaded) {
            return floor(f);
        }
        // Fallback to Java implementation
        int i = (int)f;
        return f < i ? i - 1 : i;
    }
    
    public static int floorDouble(double d) {
        if (libraryLoaded) {
            return floor(d);
        }
        // Fallback to Java implementation
        int i = (int)d;
        return d < i ? i - 1 : i;
    }
    
    public static long lfloorDouble(double d) {
        if (libraryLoaded) {
            return lfloor(d);
        }
        // Fallback to Java implementation
        long l = (long)d;
        return d < l ? l - 1L : l;
    }
    
    public static int ceilFloat(float f) {
        if (libraryLoaded) {
            return ceil(f);
        }
        // Fallback to Java implementation
        int i = (int)f;
        return f > i ? i + 1 : i;
    }
    
    public static int ceilDouble(double d) {
        if (libraryLoaded) {
            return ceil(d);
        }
        // Fallback to Java implementation
        int i = (int)d;
        return d > i ? i + 1 : i;
    }
    
    public static long ceilLongDouble(double d) {
        if (libraryLoaded) {
            return ceilLong(d);
        }
        // Fallback to Java implementation
        long l = (long)d;
        return d > l ? l + 1L : l;
    }
    
    public static int clampInt(int i, int j, int k) {
        if (libraryLoaded) {
            return clamp(i, j, k);
        }
        // Fallback to Java implementation
        return Math.min(Math.max(i, j), k);
    }
    
    public static long clampLong(long l, long m, long n) {
        if (libraryLoaded) {
            return clamp(l, m, n);
        }
        // Fallback to Java implementation
        return Math.min(Math.max(l, m), n);
    }
    
    public static float clampFloat(float f, float g, float h) {
        if (libraryLoaded) {
            return clamp(f, g, h);
        }
        // Fallback to Java implementation
        return f < g ? g : Math.min(f, h);
    }
    
    public static double clampDouble(double d, double e, double f) {
        if (libraryLoaded) {
            return clamp(d, e, f);
        }
        // Fallback to Java implementation
        return d < e ? e : Math.min(d, f);
    }
    
    public static float absFloat(float f) {
        if (libraryLoaded) {
            return abs(f);
        }
        // Fallback to Java implementation
        return Math.abs(f);
    }
    
    public static int absInt(int i) {
        if (libraryLoaded) {
            return abs(i);
        }
        // Fallback to Java implementation
        return Math.abs(i);
    }
    
    public static float squareFloat(float f) {
        if (libraryLoaded) {
            return square(f);
        }
        // Fallback to Java implementation
        return f * f;
    }
    
    public static double squareDouble(double d) {
        if (libraryLoaded) {
            return square(d);
        }
        // Fallback to Java implementation
        return d * d;
    }
    
    public static int squareInt(int i) {
        if (libraryLoaded) {
            return square(i);
        }
        // Fallback to Java implementation
        return i * i;
    }
    
    public static long squareLong(long l) {
        if (libraryLoaded) {
            return square(l);
        }
        // Fallback to Java implementation
        return l * l;
    }
}
