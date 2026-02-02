package net.minecraft.util;

/**
 * Example demonstrating the Rust-Java integration.
 * This shows how easy it is to use Rust-backed math functions.
 */
public class MthRustExample {
    
    public static void main(String[] args) {
        System.out.println("=== MattMC Rust Integration Example ===\n");
        
        // Check if Rust library is available
        if (MthRust.isRustAvailable()) {
            System.out.println("✓ Rust native library loaded successfully!");
        } else {
            System.out.println("⚠ Rust library not available, using Java fallback");
        }
        
        System.out.println("\n--- Floor Examples ---");
        System.out.println("floor(3.7) = " + MthRust.floorFloat(3.7f));
        System.out.println("floor(-3.2) = " + MthRust.floorFloat(-3.2f));
        System.out.println("floor(3.7d) = " + MthRust.floorDouble(3.7));
        System.out.println("lfloor(3.7d) = " + MthRust.lfloorDouble(3.7));
        
        System.out.println("\n--- Ceil Examples ---");
        System.out.println("ceil(3.2) = " + MthRust.ceilFloat(3.2f));
        System.out.println("ceil(-3.7) = " + MthRust.ceilFloat(-3.7f));
        System.out.println("ceilLong(3.2d) = " + MthRust.ceilLongDouble(3.2));
        
        System.out.println("\n--- Clamp Examples ---");
        System.out.println("clamp(5, 0, 10) = " + MthRust.clampInt(5, 0, 10));
        System.out.println("clamp(-5, 0, 10) = " + MthRust.clampInt(-5, 0, 10));
        System.out.println("clamp(15, 0, 10) = " + MthRust.clampInt(15, 0, 10));
        System.out.println("clamp(5.5f, 0f, 10f) = " + MthRust.clampFloat(5.5f, 0f, 10f));
        
        System.out.println("\n--- Absolute Value Examples ---");
        System.out.println("abs(-42) = " + MthRust.absInt(-42));
        System.out.println("abs(-3.14f) = " + MthRust.absFloat(-3.14f));
        
        System.out.println("\n--- Square Examples ---");
        System.out.println("square(5) = " + MthRust.squareInt(5));
        System.out.println("square(3.0f) = " + MthRust.squareFloat(3.0f));
        System.out.println("square(2.5d) = " + MthRust.squareDouble(2.5));
        System.out.println("square(10L) = " + MthRust.squareLong(10L));
        
        System.out.println("\n=== Example Complete ===");
    }
}
