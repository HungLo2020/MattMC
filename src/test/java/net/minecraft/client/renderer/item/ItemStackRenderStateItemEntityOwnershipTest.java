package net.minecraft.client.renderer.item;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemStackRenderStateItemEntityOwnershipTest {
	@Test
	void rustOwnedDroppedItemLayersBypassIrisJavaAndFabricFallbacks() throws Exception {
		String source = Files.readString(Path.of(
			System.getProperty("user.dir"),
			"src/main/java/net/minecraft/client/renderer/item/ItemStackRenderState.java"
		));

		int itemEntityScope = source.indexOf("RustGalWorldPrimitiveRenderer.isIndexedItemSubmissionActive()");
		int selectedVulkan = source.indexOf("boolean selectedVulkan = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()");
		int ownership = source.indexOf("ItemEntityRenderOwnershipPolicy.currentOwnershipRoute()", itemEntityScope);
		int rustBranch = source.indexOf("ownership.usesRustWholeFrameVulkan()", ownership);
		int failClosed = source.indexOf("selected Vulkan cannot fall through to Java item rendering", ownership);
		int specialUnavailable = source.indexOf("unavailableReason = \"special-renderer\"", rustBranch);
		int fabricSemantic = source.indexOf("access.fabric_submitItem(", rustBranch);
		int semanticEligibility = source.indexOf("RustGalWorldPrimitiveRenderer.itemEntityMeshIneligibility(", rustBranch);
		int rustEnqueue = source.indexOf("RustGalWorldPrimitiveRenderer.enqueueItemEntityMesh(", semanticEligibility);
		int irisCapture = source.indexOf("CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity()", rustBranch);
		int javaSubmit = source.indexOf("submitNodeCollector.submitItemSemantic(", irisCapture);
		int fabricSubmit = source.indexOf("access.fabric_submitItem(", irisCapture);

		assertTrue(itemEntityScope >= 0, "resolved item layers must recognize real dropped-item submission scope");
		assertTrue(selectedVulkan >= 0 && selectedVulkan < ownership,
			"all selected Vulkan item callsites must enter the explicit ownership gate");
		assertTrue(ownership > itemEntityScope, "dropped-item ownership must be resolved independently before admission");
		assertTrue(failClosed > ownership, "selected Vulkan must fail closed before shell activation rather than use Java items");
		assertTrue(rustBranch > ownership, "Rust whole-frame ownership must gate the private semantic path");
		assertTrue(specialUnavailable > rustBranch, "special item renderers must remain unavailable under Rust ownership");
		assertTrue(fabricSemantic > rustBranch, "FRAPI mesh layers must enter the copied semantic route under Rust ownership");
		assertTrue(semanticEligibility > fabricSemantic, "ordinary layers must use the existing bounded semantic eligibility check");
		assertTrue(rustEnqueue > semanticEligibility, "only admitted ordinary layers may enqueue Rust mesh work");
		assertTrue(irisCapture > rustEnqueue, "Rust-owned dropped-item layers must return before any Iris runtime state access");
		assertTrue(javaSubmit > irisCapture, "Java item submission must remain exclusively in the compatibility path");
		assertTrue(fabricSubmit > irisCapture, "FRAPI submission must remain exclusively in the compatibility path");
		assertTrue(source.contains("\"rust-vulkan-unavailable\""), "unavailable Rust-owned item layers must be observable");
		assertFalse(
			source.substring(rustBranch, irisCapture).contains("submitNodeCollector.submitItem("),
			"Rust-owned dropped-item layers must not submit through Java"
		);
		assertTrue(
			source.substring(rustBranch, irisCapture).contains("access.fabric_submitItem("),
			"Rust-owned Fabric layers must submit through the copied semantic collector"
		);
	}

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
