package com.seibel.distanthorizons.coreapi.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * FFM-based BitShiftUtil using Rust native library.
 * A list of helper methods to make code easier to read.
 * Specifically written because bit shifts short circuit James' brain.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @author James Seibel (original design)
 * @version 2024-02-04 (Rust FFM migration)
 */
public class BitShiftUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle powerOfTwoInt;
	private static final MethodHandle powerOfTwoLong;
	private static final MethodHandle halfInt;
	private static final MethodHandle halfLong;
	private static final MethodHandle divideByPowerOfTwoInt;
	private static final MethodHandle divideByPowerOfTwoLong;
	private static final MethodHandle squareInt;
	private static final MethodHandle squareLong;
	private static final MethodHandle powInt;
	private static final MethodHandle powLong;
	
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
			powerOfTwoInt = LINKER.downcallHandle(
				findFunction("bitshiftutil_power_of_two_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			powerOfTwoLong = LINKER.downcallHandle(
				findFunction("bitshiftutil_power_of_two_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			halfInt = LINKER.downcallHandle(
				findFunction("bitshiftutil_half_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			halfLong = LINKER.downcallHandle(
				findFunction("bitshiftutil_half_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			divideByPowerOfTwoInt = LINKER.downcallHandle(
				findFunction("bitshiftutil_divide_by_power_of_two_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			divideByPowerOfTwoLong = LINKER.downcallHandle(
				findFunction("bitshiftutil_divide_by_power_of_two_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			squareInt = LINKER.downcallHandle(
				findFunction("bitshiftutil_square_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			squareLong = LINKER.downcallHandle(
				findFunction("bitshiftutil_square_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			powInt = LINKER.downcallHandle(
				findFunction("bitshiftutil_pow_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			powLong = LINKER.downcallHandle(
				findFunction("bitshiftutil_pow_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for BitShiftUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for BitShiftUtil!");
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
	 * Equivalent to: <br>
	 * {@literal 1 << value, } <br>
	 * 2^value, <br>
	 * Math.pow(2, value) <br><br>
	 *
	 * Note: Math.pow() isn't identical for large values where bits would be lost in the shift, however for medium to small values they function the same. <br><br>
	 *
	 * Can also be used to replace bit shifts in the format: <br>
	 * {@literal multiplier << value; } <br>
	 * multiplier * powerOfTwo(value);
	 */
	public static int powerOfTwo(int value) {
		try {
			return (int) powerOfTwoInt.invokeExact(value);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native powerOfTwo(int)", e);
		}
	}
	
	/** see {@link BitShiftUtil#powerOfTwo(int)} for documentation */
	public static long powerOfTwo(long value) {
		try {
			return (long) powerOfTwoLong.invokeExact(value);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native powerOfTwo(long)", e);
		}
	}
	
	/**
	 * Equivalent to: <br>
	 * value >> 1, <br>
	 * value / 2 <br><br>
	 *
	 * Note: value / 2 isn't identical for negative values
	 */
	public static int half(int value) {
		try {
			return (int) halfInt.invokeExact(value);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native half(int)", e);
		}
	}
	
	/** see {@link BitShiftUtil#half(int)} for documentation */
	public static long half(long value) {
		try {
			return (long) halfLong.invokeExact(value);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native half(long)", e);
		}
	}
	
	/**
	 * Equivalent to: <br>
	 * value >> power, <br>
	 * value / 2^power <br><br>
	 *
	 * Note: value / 2^power isn't identical for negative values
	 */
	public static int divideByPowerOfTwo(int value, int power) {
		try {
			return (int) divideByPowerOfTwoInt.invokeExact(value, power);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native divideByPowerOfTwo(int, int)", e);
		}
	}
	
	/** see {@link BitShiftUtil#divideByPowerOfTwo(int, int)} for documentation */
	public static long divideByPowerOfTwo(long value, long power) {
		try {
			return (long) divideByPowerOfTwoLong.invokeExact(value, power);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native divideByPowerOfTwo(long, long)", e);
		}
	}
	
	/**
	 * Equivalent to: <br>
	 * {@literal value << 1, } <br>
	 * value^2, <br>
	 * Math.pow(value, 2) <br><br>
	 *
	 * Note: Math.pow() isn't identical for large values where bits would be lost in the shift, however for medium to small values they function the same.
	 */
	public static int square(int value) {
		try {
			return (int) squareInt.invokeExact(value);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native square(int)", e);
		}
	}
	
	/** see {@link BitShiftUtil#square(int)} for documentation */
	public static long square(long value) {
		try {
			return (long) squareLong.invokeExact(value);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native square(long)", e);
		}
	}
	
	/**
	 * Equivalent to: <br>
	 * {@literal value << power, } <br>
	 * value^power, <br>
	 * Math.pow(value, power) <br><br>
	 *
	 * Note: Math.pow() isn't identical for large values where bits would be lost in the shift, however for medium to small values they function the same.
	 */
	public static int pow(int value, int power) {
		try {
			return (int) powInt.invokeExact(value, power);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native pow(int, int)", e);
		}
	}
	
	/** see {@link BitShiftUtil#pow(int, int)} for documentation */
	public static long pow(long value, long power) {
		try {
			return (long) powLong.invokeExact(value, power);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native pow(long, long)", e);
		}
	}
}
