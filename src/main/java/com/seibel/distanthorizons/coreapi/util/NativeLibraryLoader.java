package com.seibel.distanthorizons.coreapi.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for loading native libraries from JAR resources.
 * Extracts native libraries to a temporary location and loads them.
 */
public class NativeLibraryLoader {
	
	/**
	 * Loads a native library from the JAR's resources.
	 * The library is extracted to a temporary directory and then loaded.
	 *
	 * @param libraryName The name of the library file (e.g., "libmattmc_native.so")
	 * @return Path to the extracted library file
	 * @throws IOException if the library cannot be extracted or loaded
	 */
	public static Path loadLibraryFromJar(String libraryName) throws IOException {
		// Resource path inside the JAR
		String resourcePath = "/native/" + libraryName;
		
		// Create a temporary directory for the extracted library
		Path tempDir = Files.createTempDirectory("mattmc_native");
		tempDir.toFile().deleteOnExit();
		
		Path libraryPath = tempDir.resolve(libraryName);
		libraryPath.toFile().deleteOnExit();
		
		// Extract the library from the JAR
		try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Native library not found in JAR: " + resourcePath);
			}
			Files.copy(in, libraryPath, StandardCopyOption.REPLACE_EXISTING);
		}
		
		// Make the library executable (required on Unix-like systems)
		if (!System.getProperty("os.name").toLowerCase().contains("win")) {
			libraryPath.toFile().setExecutable(true, false);
		}
		
		return libraryPath;
	}
}
