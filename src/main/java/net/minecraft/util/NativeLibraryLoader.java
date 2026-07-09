package net.minecraft.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NativeLibraryLoader {
	private static final String NATIVES_DIR_PROPERTY = "mattmc.rust.natives.dir";

	private NativeLibraryLoader() {
	}

	public static <T extends Library> T loadRustLibrary(String libraryName, Class<T> interfaceClass) {
		Path libraryPath = resolveNativesDirectory().resolve(platformLibraryFileName(libraryName)).toAbsolutePath().normalize();
		if (!Files.isRegularFile(libraryPath)) {
			throw new UnsatisfiedLinkError("Required Rust native library is missing: " + libraryPath);
		}

		return Native.load(libraryPath.toString(), interfaceClass);
	}

	public static String platformLibraryFileName(String libraryName) {
		return libraryName + "-" + currentPlatformKey() + "." + nativeLibraryExtension();
	}

	private static Path resolveNativesDirectory() {
		String nativesDir = System.getProperty(NATIVES_DIR_PROPERTY);
		return nativesDir == null || nativesDir.isBlank() ? Path.of("natives") : Path.of(nativesDir);
	}

	private static String currentPlatformKey() {
		return osPart() + "-" + archPart();
	}

	private static String osPart() {
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("windows") || osName.contains("win")) {
			return "win";
		} else if (osName.contains("mac") || osName.contains("darwin")) {
			return "mac";
		} else if (osName.contains("linux")) {
			return "linux";
		}

		throw new UnsatisfiedLinkError("Unsupported OS for Rust native library: " + System.getProperty("os.name"));
	}

	private static String archPart() {
		String archName = System.getProperty("os.arch").toLowerCase();
		return switch (archName) {
			case "x86_64", "amd64" -> "x64";
			case "aarch64", "arm64" -> "aarch64";
			default -> throw new UnsatisfiedLinkError("Unsupported architecture for Rust native library: " + System.getProperty("os.arch"));
		};
	}

	private static String nativeLibraryExtension() {
		return switch (osPart()) {
			case "win" -> "dll";
			case "mac" -> "dylib";
			case "linux" -> "so";
			default -> throw new UnsatisfiedLinkError("Unsupported OS for Rust native library: " + System.getProperty("os.name"));
		};
	}
}
