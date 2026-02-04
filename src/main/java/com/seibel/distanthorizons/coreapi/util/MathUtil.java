package com.seibel.distanthorizons.coreapi.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * FFM-based MathUtil using Rust native library.
 * This class provides mathematical utility functions implemented in Rust for performance.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @author James Seibel (original design)
 * @version 2024-02-04 (Rust FFM migration)
 */
public class MathUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle clampInt;
	private static final MethodHandle clampFloat;
	private static final MethodHandle clampDouble;
	private static final MethodHandle ceilDiv;
	private static final MethodHandle minByte;
	private static final MethodHandle maxByte;
	private static final MethodHandle fastInvSqrt;
	private static final MethodHandle pow2Float;
	private static final MethodHandle pow2Double;
	private static final MethodHandle pow2Int;
	private static final MethodHandle pow2Long;
	private static final MethodHandle log2;
	
	static {
		try {
			// Determine the platform-specific library name
			String osName = System.getProperty("os.name").toLowerCase();
			String libraryName;
			
			if (osName.contains("win")) {
				libraryName = "mattmc_native.dll";
			} else if (osName.contains("mac")) {
				libraryName = "libmattmc_native.dylib";
			} else {
				libraryName = "libmattmc_native.so";
			}
			
			// Load library from the JAR's native resources
			Path libraryPath = NativeLibraryLoader.loadLibraryFromJar(libraryName);
			
			// Load the library
			SymbolLookup lib = SymbolLookup.libraryLookup(libraryPath, Arena.global());
			LIBRARY = lib;
			
			// Initialize function handles
			clampInt = LINKER.downcallHandle(
				findFunction("mathutil_clamp_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			clampFloat = LINKER.downcallHandle(
				findFunction("mathutil_clamp_float"),
				FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			clampDouble = LINKER.downcallHandle(
				findFunction("mathutil_clamp_double"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			ceilDiv = LINKER.downcallHandle(
				findFunction("mathutil_ceil_div"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			minByte = LINKER.downcallHandle(
				findFunction("mathutil_min_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE)
			);
			
			maxByte = LINKER.downcallHandle(
				findFunction("mathutil_max_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE)
			);
			
			fastInvSqrt = LINKER.downcallHandle(
				findFunction("mathutil_fast_inv_sqrt"),
				FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			pow2Float = LINKER.downcallHandle(
				findFunction("mathutil_pow2_float"),
				FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			pow2Double = LINKER.downcallHandle(
				findFunction("mathutil_pow2_double"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			pow2Int = LINKER.downcallHandle(
				findFunction("mathutil_pow2_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			pow2Long = LINKER.downcallHandle(
				findFunction("mathutil_pow2_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			log2 = LINKER.downcallHandle(
				findFunction("mathutil_log2"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for MathUtil!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	/**
	 * Clamps the given value between the min and max values.
	 * May behave strangely if min > max.
	 */
	public static int clamp(int min, int value, int max) {
		try {
			return (int) clampInt.invokeExact(min, value, max);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native clamp(int)", e);
		}
	}
	
	/**
	 * Clamps the given value between the min and max values.
	 * May behave strangely if min > max.
	 */
	public static float clamp(float min, float value, float max) {
		try {
			return (float) clampFloat.invokeExact(min, value, max);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native clamp(float)", e);
		}
	}
	
	/**
	 * Clamps the given value between the min and max values.
	 * May behave strangely if min > max.
	 */
	public static double clamp(double min, double value, double max) {
		try {
			return (double) clampDouble.invokeExact(min, value, max);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native clamp(double)", e);
		}
	}
	
	/**
	 * Like Math.floorDiv, but reverse in that it is a ceilDiv
	 */
	public static int ceilDiv(int value, int divider) {
		try {
			return (int) ceilDiv.invokeExact(value, divider);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native ceilDiv", e);
		}
	}
	
	// Why is this not in the standard library?! Come on Java!
	public static byte min(byte a, byte b) {
		try {
			return (byte) minByte.invokeExact(a, b);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native min(byte)", e);
		}
	}
	
	public static byte max(byte a, byte b) {
		try {
			return (byte) maxByte.invokeExact(a, b);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native max(byte)", e);
		}
	}
	
	/** This is copied from Minecraft's MathHelper class */
	public static float fastInvSqrt(float numb) {
		try {
			return (float) fastInvSqrt.invokeExact(numb);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native fastInvSqrt", e);
		}
	}
	
	public static float pow2(float x) {
		try {
			return (float) pow2Float.invokeExact(x);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native pow2(float)", e);
		}
	}
	
	public static double pow2(double x) {
		try {
			return (double) pow2Double.invokeExact(x);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native pow2(double)", e);
		}
	}
	
	public static int pow2(int x) {
		try {
			return (int) pow2Int.invokeExact(x);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native pow2(int)", e);
		}
	}
	
	public static long pow2(long x) {
		try {
			return (long) pow2Long.invokeExact(x);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native pow2(long)", e);
		}
	}
	
	/** Equivalent to Log_2(numb) */
	public static int log2(int numb) {
		try {
			return (int) log2.invokeExact(numb);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native log2", e);
		}
	}
}
