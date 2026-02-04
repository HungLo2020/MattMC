package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Helps when working with numbers where the type is unknown.
 *
 * This is a hybrid implementation where type-specific comparison functions are implemented
 * in Rust via FFM for performance, while the generic Number-based functions remain in Java.
 *
 * @author coolGi
 * @version 2024-02-04 (Partial Rust FFM migration for type-specific comparisons)
 */
public class NumberUtil
{
	// Rust FFM integration
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle greaterThanIntHandle;
	private static final MethodHandle greaterThanLongHandle;
	private static final MethodHandle greaterThanFloatHandle;
	private static final MethodHandle greaterThanDoubleHandle;
	private static final MethodHandle greaterThanShortHandle;
	private static final MethodHandle greaterThanByteHandle;
	
	private static final MethodHandle lessThanIntHandle;
	private static final MethodHandle lessThanLongHandle;
	private static final MethodHandle lessThanFloatHandle;
	private static final MethodHandle lessThanDoubleHandle;
	private static final MethodHandle lessThanShortHandle;
	private static final MethodHandle lessThanByteHandle;
	
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
			
			// Initialize function handles for greaterThan
			greaterThanIntHandle = LINKER.downcallHandle(
				findFunction("numberutil_greater_than_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			greaterThanLongHandle = LINKER.downcallHandle(
				findFunction("numberutil_greater_than_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			greaterThanFloatHandle = LINKER.downcallHandle(
				findFunction("numberutil_greater_than_float"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			greaterThanDoubleHandle = LINKER.downcallHandle(
				findFunction("numberutil_greater_than_double"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			greaterThanShortHandle = LINKER.downcallHandle(
				findFunction("numberutil_greater_than_short"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT)
			);
			
			greaterThanByteHandle = LINKER.downcallHandle(
				findFunction("numberutil_greater_than_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE)
			);
			
			// Initialize function handles for lessThan
			lessThanIntHandle = LINKER.downcallHandle(
				findFunction("numberutil_less_than_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			lessThanLongHandle = LINKER.downcallHandle(
				findFunction("numberutil_less_than_long"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			lessThanFloatHandle = LINKER.downcallHandle(
				findFunction("numberutil_less_than_float"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			lessThanDoubleHandle = LINKER.downcallHandle(
				findFunction("numberutil_less_than_double"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE)
			);
			
			lessThanShortHandle = LINKER.downcallHandle(
				findFunction("numberutil_less_than_short"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT)
			);
			
			lessThanByteHandle = LINKER.downcallHandle(
				findFunction("numberutil_less_than_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for NumberUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for NumberUtil!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	// Original Java implementation for generic Number types
	// Is there no better way of doing this?
	public static Map<Class<?>, Number> minValues = new HashMap<Class<?>, Number>()
	{{
		this.put(Byte.class, Byte.MIN_VALUE);
		this.put(Short.class, Short.MIN_VALUE);
		this.put(Integer.class, Integer.MIN_VALUE);
		this.put(Long.class, Long.MIN_VALUE);
		this.put(Double.class, Double.MIN_VALUE);
		this.	put(Float.class, Float.MIN_VALUE);
	}};
	public static Map<Class<?>, Number> maxValues = new HashMap<Class<?>, Number>()
	{{
		this.put(Byte.class, Byte.MAX_VALUE);
		this.put(Short.class, Short.MAX_VALUE);
		this.put(Integer.class, Integer.MAX_VALUE);
		this.put(Long.class, Long.MAX_VALUE);
		this.put(Double.class, Double.MAX_VALUE);
		this.put(Float.class, Float.MAX_VALUE);
	}};
	
	
	
	public static Number getMinimum(Class<?> c) { return minValues.get(c); }
	public static Number getMaximum(Class<?> c) { return maxValues.get(c); }
	
	/** Does a greater than (>) operator on any number */
	public static boolean greaterThan(Number a, Number b)
	{
		if (a.getClass() != b.getClass())
		{
			return false;
		}
		Class<?> typeClass = a.getClass();
		
		if (typeClass == Byte.class) return greaterThan(a.byteValue(), b.byteValue());
		if (typeClass == Short.class) return greaterThan(a.shortValue(), b.shortValue());
		if (typeClass == Integer.class) return greaterThan(a.intValue(), b.intValue());
		if (typeClass == Long.class) return greaterThan(a.longValue(), b.longValue());
		if (typeClass == Double.class) return greaterThan(a.doubleValue(), b.doubleValue());
		if (typeClass == Float.class) return greaterThan(a.floatValue(), b.floatValue());
		
		return false;
	}
	
	/** Does a less than (<) operator on any number */
	public static boolean lessThan(Number a, Number b)
	{
		if (a.getClass() != b.getClass())
		{
			return false;
		}
		Class<?> typeClass = a.getClass();
		
		if (typeClass == Byte.class) return lessThan(a.byteValue(), b.byteValue());
		if (typeClass == Short.class) return lessThan(a.shortValue(), b.shortValue());
		if (typeClass == Integer.class) return lessThan(a.intValue(), b.intValue());
		if (typeClass == Long.class) return lessThan(a.longValue(), b.longValue());
		if (typeClass == Double.class) return lessThan(a.doubleValue(), b.doubleValue());
		if (typeClass == Float.class) return lessThan(a.floatValue(), b.floatValue());
		
		return false;
	}
	
	// Type-specific greater than methods using Rust FFM
	
	public static boolean greaterThan(int a, int b) {
		try {
			return ((int) greaterThanIntHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native greaterThan(int)", e);
		}
	}
	
	public static boolean greaterThan(long a, long b) {
		try {
			return ((int) greaterThanLongHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native greaterThan(long)", e);
		}
	}
	
	public static boolean greaterThan(float a, float b) {
		try {
			return ((int) greaterThanFloatHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native greaterThan(float)", e);
		}
	}
	
	public static boolean greaterThan(double a, double b) {
		try {
			return ((int) greaterThanDoubleHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native greaterThan(double)", e);
		}
	}
	
	public static boolean greaterThan(short a, short b) {
		try {
			return ((int) greaterThanShortHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native greaterThan(short)", e);
		}
	}
	
	public static boolean greaterThan(byte a, byte b) {
		try {
			return ((int) greaterThanByteHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native greaterThan(byte)", e);
		}
	}
	
	// Type-specific less than methods using Rust FFM
	
	public static boolean lessThan(int a, int b) {
		try {
			return ((int) lessThanIntHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native lessThan(int)", e);
		}
	}
	
	public static boolean lessThan(long a, long b) {
		try {
			return ((int) lessThanLongHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native lessThan(long)", e);
		}
	}
	
	public static boolean lessThan(float a, float b) {
		try {
			return ((int) lessThanFloatHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native lessThan(float)", e);
		}
	}
	
	public static boolean lessThan(double a, double b) {
		try {
			return ((int) lessThanDoubleHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native lessThan(double)", e);
		}
	}
	
	public static boolean lessThan(short a, short b) {
		try {
			return ((int) lessThanShortHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native lessThan(short)", e);
		}
	}
	
	public static boolean lessThan(byte a, byte b) {
		try {
			return ((int) lessThanByteHandle.invokeExact(a, b)) != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native lessThan(byte)", e);
		}
	}
	
}
