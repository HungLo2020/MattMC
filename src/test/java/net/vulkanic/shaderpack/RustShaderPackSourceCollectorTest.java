package net.vulkanic.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RustShaderPackSourceCollectorTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void collectsOnlyOrderedShaderSourceAndConfigurationFiles() throws Exception {
		Files.createDirectories(temporaryDirectory.resolve("lib"));
		Files.createDirectories(temporaryDirectory.resolve("lib/antialiasing"));
		Files.createDirectories(temporaryDirectory.resolve("mattmc"));
		Files.writeString(temporaryDirectory.resolve("program.fsh"), "fragment");
		Files.writeString(temporaryDirectory.resolve("lib/common.glsl"), "common");
		Files.writeString(temporaryDirectory.resolve("lib/antialiasing/jitter.glsl"), "jitter");
		Files.writeString(temporaryDirectory.resolve("mattmc/terrain-resource-bindings.properties"), "tex=material_atlas");
		Files.writeString(temporaryDirectory.resolve("shaders.properties"), "profile.test=TEST");
		Files.write(temporaryDirectory.resolve("albedo.png"), new byte[] {1, 2, 3});

		RustShaderPackSourceCollector.SourceGeneration source = RustShaderPackSourceCollector.collect(
			temporaryDirectory,
			"test-pack",
			7L
		);

		assertEquals(7L, source.generation());
		assertEquals(5, source.files().size());
		assertEquals("lib/antialiasing/jitter.glsl", source.files().get(0).path());
		assertEquals("lib/common.glsl", source.files().get(1).path());
		assertEquals("mattmc/terrain-resource-bindings.properties", source.files().get(2).path());
		assertEquals("program.fsh", source.files().get(3).path());
		assertEquals("shaders.properties", source.files().get(4).path());
	}

	@Test
	void rejectsOversizedSourceBeforeTransport() throws Exception {
		Path source = temporaryDirectory.resolve("large.vsh");
		try (java.io.OutputStream output = Files.newOutputStream(source)) {
			output.write(new byte[RustShaderPackSourceCollector.MAX_FILE_BYTES + 1]);
		}
		assertThrows(IOException.class, () -> RustShaderPackSourceCollector.collect(temporaryDirectory, "test", 1L));
	}

	@Test
	void disabledGenerationIsExplicitAndContainsNoStaleFiles() {
		RustShaderPackSourceCollector.SourceGeneration source = RustShaderPackSourceCollector.disabled(3L);
		assertEquals("disabled", source.packName());
		assertEquals(3L, source.generation());
		assertEquals(0, source.files().size());
	}

	@Test
	void collectsTheSameRelativeSourcePathsFromAnArchiveRoot() throws Exception {
		Path archive = temporaryDirectory.resolve("test-pack.zip");
		URI uri = URI.create("jar:" + archive.toUri());
		try (java.nio.file.FileSystem fileSystem = java.nio.file.FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
			Path root = fileSystem.getPath("/shaders");
			Files.createDirectories(root.resolve("lib"));
			Files.writeString(root.resolve("gbuffers_terrain.fsh"), "fragment");
			Files.writeString(root.resolve("lib/common.glsl"), "common");
			RustShaderPackSourceCollector.SourceGeneration source = RustShaderPackSourceCollector.collect(root, "archive-pack", 9L);
			assertEquals(2, source.files().size());
			assertEquals("gbuffers_terrain.fsh", source.files().get(0).path());
			assertEquals("lib/common.glsl", source.files().get(1).path());
		}
	}

	@Test
	void runtimeOptionSnapshotIsSortedBoundedAndReserved() throws Exception {
		RustShaderPackSourceCollector.SourceGeneration source = RustShaderPackSourceCollector.collect(
			temporaryDirectory,
			"configured-pack",
			11L
		);
		RustShaderPackSourceCollector.SourceGeneration configured =
			RustShaderPackSourceCollector.withRuntimeOptionSnapshot(source, Map.of(
				"Z_OPTION", "low",
				"A_OPTION", "1"
			));

		assertEquals(1, configured.files().size());
		assertEquals(RustShaderPackSourceCollector.RUNTIME_OPTIONS_PATH, configured.files().get(0).path());
		assertEquals("A_OPTION=1\nZ_OPTION=low\n", new String(configured.files().get(0).contentsUtf8()));
		assertThrows(IOException.class, () -> RustShaderPackSourceCollector.withRuntimeOptionSnapshot(
			configured,
			Map.of("BAD-OPTION", "1")
		));
		RustShaderPackSourceCollector.SourceGeneration fresh = RustShaderPackSourceCollector.collect(
			temporaryDirectory,
			"configured-pack",
			12L
		);
		assertThrows(IOException.class, () -> RustShaderPackSourceCollector.withRuntimeOptionSnapshot(
			fresh,
			Map.of("VALID_OPTION", "not a token")
		));
		assertThrows(IOException.class, () -> RustShaderPackSourceCollector.withRuntimeOptionSnapshot(
			fresh,
			Map.of("VALID_OPTION", "")
		));
	}

	@Test
	void runtimeEnvironmentSnapshotUsesSeparateReservedSemanticPath() throws Exception {
		RustShaderPackSourceCollector.SourceGeneration source = RustShaderPackSourceCollector.collect(
			temporaryDirectory,
			"configured-pack",
			13L
		);
		RustShaderPackSourceCollector.SourceGeneration configured =
			RustShaderPackSourceCollector.withRuntimeEnvironmentSnapshot(source, Map.of(
				"IS_IRIS", "",
				"IRIS_VERSION", "12000"
			));

		assertEquals(1, configured.files().size());
		assertEquals(RustShaderPackSourceCollector.RUNTIME_ENVIRONMENT_PATH, configured.files().get(0).path());
		assertEquals("IRIS_VERSION=12000\nIS_IRIS=1\n", new String(configured.files().get(0).contentsUtf8()));
		assertThrows(IOException.class, () -> RustShaderPackSourceCollector.withRuntimeEnvironmentSnapshot(
			configured,
			Map.of("MC_VERSION", "12105")
		));
	}
}
