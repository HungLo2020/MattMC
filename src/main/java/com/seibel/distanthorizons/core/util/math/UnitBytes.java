package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;

/**
 * FFM-based UnitBytes using Rust native library for performance.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @version 2024-02-04 (Rust FFM migration for conversion functions)
 */
public class UnitBytes
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle byteToGBHandle;
	private static final MethodHandle byteToMBHandle;
	private static final MethodHandle byteToKBHandle;
	private static final MethodHandle GBToByteHandle;
	private static final MethodHandle MBToByteHandle;
	private static final MethodHandle KBToByteHandle;
	
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
			byteToGBHandle = LINKER.downcallHandle(
				findFunction("unitbytes_byte_to_gb"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			byteToMBHandle = LINKER.downcallHandle(
				findFunction("unitbytes_byte_to_mb"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			byteToKBHandle = LINKER.downcallHandle(
				findFunction("unitbytes_byte_to_kb"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			GBToByteHandle = LINKER.downcallHandle(
				findFunction("unitbytes_gb_to_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			MBToByteHandle = LINKER.downcallHandle(
				findFunction("unitbytes_mb_to_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			KBToByteHandle = LINKER.downcallHandle(
				findFunction("unitbytes_kb_to_byte"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for UnitBytes: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for UnitBytes!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	public final long value;
	public UnitBytes(long value)
	{
		this.value = value;
	}
	public long value() { return value; }
	
	public static long byteToGB(long v)
	{
		try {
			return (long) byteToGBHandle.invokeExact(v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native byteToGB", e);
		}
	}
	public static long byteToMB(long v)
	{
		try {
			return (long) byteToMBHandle.invokeExact(v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native byteToMB", e);
		}
	}
	public static long byteToKB(long v)
	{
		try {
			return (long) byteToKBHandle.invokeExact(v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native byteToKB", e);
		}
	}
	public static long GBToByte(long v)
	{
		try {
			return (long) GBToByteHandle.invokeExact(v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native GBToByte", e);
		}
	}
	public static long MBToByte(long v)
	{
		try {
			return (long) MBToByteHandle.invokeExact(v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native MBToByte", e);
		}
	}
	public static long KBToByte(long v)
	{
		try {
			return (long) KBToByteHandle.invokeExact(v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native KBToByte", e);
		}
	}
	
	@Override
	public String toString()
	{
		long v = value;
		StringBuilder str = new StringBuilder();
		long GB = byteToGB(v);
		if (GB != 0) str.append(GB).append("GB ");
		v -= GBToByte(GB);
		long MB = byteToMB(v);
		if (MB != 0) str.append(MB).append("MB ");
		v -= MBToByte(MB);
		long KB = byteToKB(v);
		if (KB != 0) str.append(KB).append("KB ");
		v -= KBToByte(KB);
		str.append(v).append("B");
		return str.toString();
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		UnitBytes unitBytes = (UnitBytes) o;
		return value == unitBytes.value;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(value);
	}
	
}
