package net.minecraft.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RustNativePanamaBoundaryTest {
	private static final Path MAIN_JAVA = Path.of("src/main/java");

	@Test
	void rustNativeBindingsUsePanamaInsteadOfJna() throws IOException {
		List<String> offenders = new ArrayList<>();

		try (var paths = Files.walk(MAIN_JAVA)) {
			for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(path);

				if (!isRustNativeBinding(path, source)) {
					continue;
				}

				if (source.contains("com.sun.jna")
						|| source.contains("Native.load")
						|| source.contains("extends Library")
						|| source.contains("loadRustLibrary(")
						|| source.contains("System.loadLibrary(")
						|| source.contains("System.load(")) {
					offenders.add(path.toString());
				}
			}
		}

		assertTrue(offenders.isEmpty(), "Rust native bindings must use Project Panama, not JNA/JNI loaders: " + offenders);
	}

	@Test
	void rustNativeLoaderExposesForeignFunctionApi() throws IOException {
		String source = Files.readString(MAIN_JAVA.resolve("net/minecraft/util/NativeLibraryLoader.java"));

		assertTrue(source.contains("java.lang.foreign"), "Rust native loader should be backed by the Panama FFM API");
		assertTrue(source.contains("downcallHandle"), "Rust native loader should expose Panama downcall handles");
	}

	private static boolean isRustNativeBinding(Path path, String source) {
		String normalized = path.toString().replace('\\', '/');

		return normalized.endsWith("net/minecraft/util/NativeLibraryLoader.java")
				|| source.contains("mattmc_rust")
				|| source.contains("mattmc_");
	}
}
