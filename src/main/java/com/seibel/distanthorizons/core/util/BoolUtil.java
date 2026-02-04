package com.seibel.distanthorizons.core.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

/**
 * FFM-based BoolUtil using Rust native library.
 * This class provides boolean utility functions implemented in Rust for performance.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @author James Seibel (original design)
 * @version 2024-02-04 (Rust FFM migration)
 */
public class BoolUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle falseIfNullHandle;
	
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
			falseIfNullHandle = LINKER.downcallHandle(
				findFunction("boolutil_false_if_null"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for BoolUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for BoolUtil!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	/** Used to prevent null {@link Boolean} objects in if statements */
	public static boolean falseIfNull(Boolean value) 
	{
		try {
			int isNull = (value == null) ? 1 : 0;
			int boolValue = (value != null && value) ? 1 : 0;
			int result = (int) falseIfNullHandle.invokeExact(boolValue, isNull);
			return result != 0;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native falseIfNull", e);
		}
	}
	
}
