package net.minecraft.client.renderer.item;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemStackRenderStateItemEntityOwnershipTest {

	@Test
	void semanticCoverageCollectorNeverEntersRuntimeItemEntityOwnershipScope() throws Exception {
		String source = Files.readString(Path.of(
			System.getProperty("user.dir"),
			"src/main/java/net/minecraft/client/renderer/entity/ItemEntityRenderer.java"
		));
		int coverageCheck = source.indexOf("submitNodeCollector.isSemanticCoverageOnly()");
		int beginScope = source.indexOf("RustGalWorldPrimitiveRenderer.beginItemEntitySubmission()", coverageCheck);
		int coverageSubmit = source.indexOf("submitMultipleFromCount(", coverageCheck);

		assertTrue(coverageCheck >= 0, "item-entity producer must distinguish count-only semantic coverage");
		assertTrue(coverageSubmit > coverageCheck, "coverage-only execution must still inventory item semantics");
		assertTrue(beginScope > coverageSubmit, "runtime Rust item-entity scope must begin only outside coverage-only execution");
	}
}
