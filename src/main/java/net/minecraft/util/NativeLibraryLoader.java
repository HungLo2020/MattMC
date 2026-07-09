package net.minecraft.util;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class NativeLibraryLoader {
	private static final String NATIVES_DIR_PROPERTY = "mattmc.rust.natives.dir";
	private static final Linker LINKER = Linker.nativeLinker();
	private static final Arena LIBRARY_ARENA = Arena.global();
	private static final ConcurrentMap<String, SymbolLookup> RUST_LIBRARIES = new ConcurrentHashMap<>();

	private NativeLibraryLoader() {
	}

	public static MethodHandle downcallHandle(String libraryName, String symbolName, FunctionDescriptor descriptor) {
		return LINKER.downcallHandle(loadRustSymbol(libraryName, symbolName), descriptor);
	}

	private static MemorySegment loadRustSymbol(String libraryName, String symbolName) {
		return rustLibraryLookup(libraryName)
				.find(symbolName)
				.orElseThrow(() -> new UnsatisfiedLinkError("Required Rust native symbol is missing: " + symbolName));
	}

	private static SymbolLookup rustLibraryLookup(String libraryName) {
		return RUST_LIBRARIES.computeIfAbsent(libraryName, NativeLibraryLoader::loadRustLibraryLookup);
	}

	private static SymbolLookup loadRustLibraryLookup(String libraryName) {
		Path libraryPath = resolveNativesDirectory().resolve(platformLibraryFileName(libraryName)).toAbsolutePath().normalize();
		if (!Files.isRegularFile(libraryPath)) {
			throw new UnsatisfiedLinkError("Required Rust native library is missing: " + libraryPath);
		}

		return SymbolLookup.libraryLookup(libraryPath, LIBRARY_ARENA);
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
