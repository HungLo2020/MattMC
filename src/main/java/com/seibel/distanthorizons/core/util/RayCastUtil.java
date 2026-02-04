package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * FFM-based RayCastUtil using Rust native library.
 * This class provides ray casting utilities implemented in Rust for performance.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @author James Seibel (original design)
 * @version 2024-02-04 (Rust FFM migration)
 */
public class RayCastUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle rayIntersectsSquareHandle;
	
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
			rayIntersectsSquareHandle = LINKER.downcallHandle(
				findFunction("raycastutil_ray_intersects_square"),
				FunctionDescriptor.of(
					ValueLayout.JAVA_INT,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE
				)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for RayCastUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for RayCastUtil!");
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
	 * This function should work for any 2 perpendicular axis, X and Y could be replaced with X, Y, or Z
	 *
	 * @param rayX the ray's starting X position
	 * @param rayY the ray's starting Z position
	 * @param squareMinX the square's X corner closest to negative infinity
	 * @param squareMinY the square's Y corner closest to negative infinity
	 */
	public static boolean rayIntersectsSquare(
			double rayX, double rayY, double rayXDirection, double rayYDirection,
			double squareMinX, double squareMinY, double squareWidth)
	{
		try {
			int result = (int) rayIntersectsSquareHandle.invokeExact(
				rayX, rayY, rayXDirection, rayYDirection,
				squareMinX, squareMinY, squareWidth
			);
			return result != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native rayIntersectsSquare", e);
		}
	}
	
}
