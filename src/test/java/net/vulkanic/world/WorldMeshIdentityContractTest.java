package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WorldMeshIdentityContractTest {
	@Test
	void standaloneBlockModelsDoNotMislabelEntityIdentity() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(source.contains("meshContentHash(vertices, indexBytes, byteSections, \"block-model-feature\")"),
			"the producer hash remains domain-separated for block-model assets");
		assertFalse(source.contains("indexType, vertices, indexBytes, byteSections, \"block-model-feature\")"),
			"block-model feature meshes must leave the entity identity field empty");
	}
}
