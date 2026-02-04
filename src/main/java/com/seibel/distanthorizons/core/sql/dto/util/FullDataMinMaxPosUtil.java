package com.seibel.distanthorizons.core.sql.dto.util;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * Handles encoding/decoding of min/max X/Z relative {@link FullDataSourceV2#dataPoints}
 * positions using Rust FFM for performance. <br>
 * Needed so we can keep the same format between complete data sources
 * and incomplete adjacent-only data sources.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @version 2024-02-04 (Rust FFM migration for core encoding/decoding)
 */
public class FullDataMinMaxPosUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle encodeHandle;
	private static final MethodHandle getMinXHandle;
	private static final MethodHandle getMaxXHandle;
	private static final MethodHandle getMinZHandle;
	private static final MethodHandle getMaxZHandle;
	
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
			encodeHandle = LINKER.downcallHandle(
				findFunction("fulldataminmaxposutil_encode"),
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT, ValueLayout.JAVA_SHORT)
			);
			
			getMinXHandle = LINKER.downcallHandle(
				findFunction("fulldataminmaxposutil_get_min_x"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
			);
			
			getMaxXHandle = LINKER.downcallHandle(
				findFunction("fulldataminmaxposutil_get_max_x"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
			);
			
			getMinZHandle = LINKER.downcallHandle(
				findFunction("fulldataminmaxposutil_get_min_z"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
			);
			
			getMaxZHandle = LINKER.downcallHandle(
				findFunction("fulldataminmaxposutil_get_max_z"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for FullDataMinMaxPosUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for FullDataMinMaxPosUtil!");
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
	 * Encodes min/max X/Z relative {@link FullDataSourceV2#dataPoints}
	 * positions. <br>
	 * Needed so we can keep the same format between complete data sources
	 * and incomplete adjacent-only data sources.
	 */
	public static long getEncodedMinMaxPos(EDhDirection direction)
	{
		// 4 shorts can fit in a long, and we won't need anything longer than 64 anyway
		short minX;
		short maxX;
		short minZ;
		short maxZ;
		
		switch (direction)
		{
			case NORTH:
				// one row closest to the negative Z axis
				minX = 0;
				maxX = FullDataSourceV2.WIDTH;
				
				minZ = 0;
				maxZ = 1;
				break;
			
			case SOUTH:
				// one row closest to the positive Z axis
				minX = 0;
				maxX = FullDataSourceV2.WIDTH;
				
				minZ = FullDataSourceV2.WIDTH - 1;
				maxZ = FullDataSourceV2.WIDTH;
				break;
			
			case EAST:
				// one row closest to the positive X axis
				minX = FullDataSourceV2.WIDTH - 1;
				maxX = FullDataSourceV2.WIDTH;
				
				minZ = 0;
				maxZ = FullDataSourceV2.WIDTH;
				break;
			
			case WEST:
				// one row closest to the Negative X axis
				minX = 0;
				maxX = 1;
				
				minZ = 0;
				maxZ = FullDataSourceV2.WIDTH;
				break;
			
			default:
				throw new IllegalArgumentException("Unsupported direction [" + direction + "].");
		}
		
		return encodeAdjMinMaxPos(
				minX, maxX,
				minZ, maxZ);
	}
	
	public static long encodeAdjMinMaxPos(
			short minX, short maxX,
			short minZ, short maxZ
	)
	{
		try {
			return (long) encodeHandle.invokeExact(minX, maxX, minZ, maxZ);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native encodeAdjMinMaxPos", e);
		}
	}
	
	public static int getAdjMinX(long encodedMinMaxPos)
	{
		try {
			return (int) getMinXHandle.invokeExact(encodedMinMaxPos);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getAdjMinX", e);
		}
	}
	
	public static int getAdjMaxX(long encodedMinMaxPos)
	{
		try {
			return (int) getMaxXHandle.invokeExact(encodedMinMaxPos);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getAdjMaxX", e);
		}
	}
	
	public static int getAdjMinZ(long encodedMinMaxPos)
	{
		try {
			return (int) getMinZHandle.invokeExact(encodedMinMaxPos);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getAdjMinZ", e);
		}
	}
	
	public static int getAdjMaxZ(long encodedMinMaxPos)
	{
		try {
			return (int) getMaxZHandle.invokeExact(encodedMinMaxPos);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getAdjMaxZ", e);
		}
	}
	
	
	
}
